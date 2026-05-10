package omnibeat.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlaybackState(
    val selectedIndex: Int = -1,
    val selectedStation: Station? = null,
    val trackText: String = "No stream is playing",
    val resolving: Boolean = false,
    val buffering: Boolean = false,
    val isPlaying: Boolean = false,
    val errorText: String? = null,
    val volume: Float = 0.75f,
    val bitrateText: String? = null,
)

@OptIn(UnstableApi::class)
class PlaybackService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var repository: StationRepository
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: Player
    private var mediaSession: MediaSession? = null
    private var stations: List<Station> = emptyList()
    private var resolveJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private var lastSessionMetadata: Pair<String, String?>? = null
    private var currentStreamIsHls = false

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository(applicationContext)
        player = ExoPlayer.Builder(this).build()
        sessionPlayer = OmniBeatSessionPlayer(player)
        mediaSession = MediaSession.Builder(this, sessionPlayer).build()
        createNotificationChannel()
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)

        scope.launch {
            repository.stations.collect { savedStations ->
                stations = savedStations
                val currentStation = state.value.selectedStation
                if (currentStation == null) {
                    updateNotification()
                    return@collect
                }
                val current = savedStations.indexOfFirst { it.id == currentStation.id }
                if (current == -1) {
                    stopPlayback(clearSelection = true)
                } else {
                    _state.update {
                        it.copy(
                            selectedIndex = current,
                            selectedStation = savedStations[current],
                        )
                    }
                    updateNotification()
                }
            }
        }
        scope.launch {
            repository.appVolume.collect { savedVolume ->
                player.volume = savedVolume
                _state.update { it.copy(volume = savedVolume) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_STATION -> playStationAt(intent.getIntExtra(EXTRA_INDEX, -1))
            ACTION_PLAY_PAUSE -> playOrPause()
            ACTION_PREVIOUS -> playAdjacentStation(-1)
            ACTION_NEXT -> playAdjacentStation(1)
            ACTION_RANDOM -> playRandomStation()
            ACTION_STOP -> {
                stopPlayback(clearSelection = true)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        resolveJob?.cancel()
        notificationUpdateJob?.cancel()
        player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        mediaSession?.release()
        player.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun playOrPause() {
        when {
            player.isPlaying -> player.pause()
            state.value.selectedStation == null -> playStationAt(0)
            else -> player.play()
        }
        updateNotification(immediate = true)
    }

    private fun playStationAt(index: Int) {
        val station = stations.getOrNull(index)
        if (station == null) {
            _state.update {
                it.copy(
                    selectedIndex = index,
                    selectedStation = null,
                    trackText = "Loading stations...",
                    resolving = true,
                    buffering = false,
                    errorText = null,
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification())
            scope.launch {
                stations = repository.stations.first()
                if (stations.getOrNull(index) == null) {
                    stopPlayback(clearSelection = true)
                    stopSelf()
                } else {
                    playStationAt(index)
                }
            }
            return
        }
        resolveJob?.cancel()
        player.stop()
        lastSessionMetadata = null
        currentStreamIsHls = false
        _state.update {
            it.copy(
                selectedIndex = index,
                selectedStation = station,
                trackText = "Resolving stream...",
                resolving = true,
                buffering = false,
                errorText = null,
                bitrateText = null,
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification())

        resolveJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    StreamResolver.resolveStream(station.streamUrl)
                }
            }.onSuccess { resolvedStream ->
                currentStreamIsHls = resolvedStream.playableUrl.lowercase().contains(".m3u8")
                _state.update {
                    it.copy(
                        trackText = "Waiting for metadata...",
                        resolving = false,
                        bitrateText = resolvedStream.bitrateLabel,
                    )
                }
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(resolvedStream.playableUrl)
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                        .build(),
                )
                player.prepare()
                player.play()
                updateNotification()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        errorText = "Could not resolve stream: ${error.message}",
                        resolving = false,
                        buffering = false,
                    )
                }
                updateNotification()
            }
        }
    }

    private fun playAdjacentStation(direction: Int) {
        if (stations.isEmpty()) return
        val currentIndex = state.value.selectedIndex
        val nextIndex = if (currentIndex in stations.indices) {
            (currentIndex + direction + stations.size) % stations.size
        } else if (direction > 0) {
            0
        } else {
            stations.lastIndex
        }
        playStationAt(nextIndex)
    }

    private fun playRandomStation() {
        if (stations.isEmpty()) return
        val nextIndex = if (stations.size == 1) {
            0
        } else {
            var randomIndex: Int
            do {
                randomIndex = Random.nextInt(stations.size)
            } while (randomIndex == state.value.selectedIndex)
            randomIndex
        }
        playStationAt(nextIndex)
    }

    private fun stopPlayback(clearSelection: Boolean) {
        resolveJob?.cancel()
        notificationUpdateJob?.cancel()
        lastSessionMetadata = null
        currentStreamIsHls = false
        player.stop()
        if (clearSelection) {
            _state.update {
                it.copy(
                    selectedIndex = -1,
                    selectedStation = null,
                    trackText = "No stream is playing",
                    resolving = false,
                    buffering = false,
                    isPlaying = false,
                    errorText = null,
                    bitrateText = null,
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    isPlaying = player.isPlaying,
                )
            }
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            updateNotification(immediate = true)
        }

        override fun onTracksChanged(tracks: Tracks) {
            if (currentStreamIsHls) {
                return
            }

            val selectedBitrates = tracks.groups
                .asSequence()
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter { group.isTrackSelected(it) }
                        .map { group.getTrackFormat(it).bitrate }
                }
                .filter { it > 0 }
                .distinct()
                .toList()

            if (selectedBitrates.size == 1) {
                formatBitrateLabel(selectedBitrates.first())?.let(::updateBitrateText)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString().orEmpty()
            val artist = mediaMetadata.artist?.toString().orEmpty()
            val stationTitle = state.value.selectedStation?.title.orEmpty()
            if (title.isBlank() || title == stationTitle) {
                return
            }
            val nextTrackText = if (artist.isNotBlank()) "$artist - $title" else title
            _state.update {
                it.copy(trackText = nextTrackText)
            }
            refreshCurrentMediaMetadata()
            updateNotification()
        }

        override fun onMetadata(metadata: Metadata) {
            repeat(metadata.length()) { index ->
                val streamTitle = IcyMetadataParser.readStreamTitle(metadata[index].toString())
                if (!streamTitle.isNullOrBlank()) {
                    _state.update { it.copy(trackText = streamTitle) }
                    refreshCurrentMediaMetadata()
                    updateNotification()
                    return
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(
                    errorText = error.message ?: "Playback error",
                    resolving = false,
                    buffering = false,
                )
            }
            updateNotification(immediate = true)
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: MediaLoadData) {
            mediaLoadData.trackFormat?.let { format ->
                val codecLabel = format.sampleMimeType?.audioCodecLabel()
                if (currentStreamIsHls && format.sampleRate <= 0 && codecLabel == null) {
                    return
                }
                formatPlaybackFormatLabel(format, codecLabel)?.let(::updateBitrateText)
            }
        }
    }

    private fun updateBitrateText(bitrateText: String) {
        if (state.value.bitrateText != bitrateText) {
            _state.update { it.copy(bitrateText = bitrateText) }
            updateNotification()
        }
    }

    private fun formatPlaybackFormatLabel(format: Format, codecLabel: String? = format.sampleMimeType?.audioCodecLabel()): String? {
        val parts = buildList {
            formatBitrateLabel(format.bitrate)?.let(::add)
            if (format.sampleRate > 0) {
                add("${format.sampleRate / 1000} kHz")
            }
            codecLabel?.let(::add)
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }

    private fun formatBitrateLabel(bitrate: Int): String? {
        return bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
    }

    private fun String.audioCodecLabel(): String? {
        return when {
            contains("aac", ignoreCase = true) || contains("mp4a", ignoreCase = true) -> "AAC"
            contains("mpeg", ignoreCase = true) -> "MP3"
            contains("opus", ignoreCase = true) -> "Opus"
            contains("vorbis", ignoreCase = true) -> "Vorbis"
            contains("flac", ignoreCase = true) -> "FLAC"
            else -> null
        }
    }

    private fun updateNotification(immediate: Boolean = false) {
        if (state.value.selectedStation != null) {
            if (immediate) {
                notificationUpdateJob?.cancel()
                notificationUpdateJob = null
                startForeground(NOTIFICATION_ID, buildNotification())
                return
            }

            if (notificationUpdateJob?.isActive == true) {
                return
            }
            notificationUpdateJob = scope.launch {
                delay(NOTIFICATION_UPDATE_DELAY_MS)
                if (state.value.selectedStation != null) {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
                notificationUpdateJob = null
            }
        }
    }

    private fun refreshCurrentMediaMetadata() {
        val current = state.value
        val station = current.selectedStation ?: return
        val metadataKey = station.title to current.trackText
        if (lastSessionMetadata == metadataKey) {
            return
        }
        lastSessionMetadata = metadataKey
        player.setPlaylistMetadata(buildSessionMetadata(station, current.trackText))
    }

    private fun buildSessionMetadata(station: Station, trackText: String?): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(station.title)
            .setArtist(trackText?.takeIf { it.isNotBlank() && it != "Waiting for metadata..." })
            .build()
    }

    private fun buildNotification(): Notification {
        val current = state.value
        val playPauseAction = if (current.isPlaying) {
            notificationAction(R.drawable.ic_pause, "Pause", ACTION_PLAY_PAUSE)
        } else {
            notificationAction(R.drawable.ic_play_arrow, "Play", ACTION_PLAY_PAUSE)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_play_arrow)
            .setContentTitle(current.selectedStation?.title ?: "OmniBeat")
            .setContentText(current.errorText ?: current.trackText)
            .setContentIntent(activityPendingIntent())
            .setOngoing(current.isPlaying || current.resolving || current.buffering)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFF8F5CFF.toInt())
            .addAction(notificationAction(R.drawable.ic_skip_previous, "Previous", ACTION_PREVIOUS))
            .addAction(playPauseAction)
            .addAction(notificationAction(R.drawable.ic_skip_next, "Next", ACTION_NEXT))
            .setStyle(
                Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.platformToken),
            )
            .build()
    }

    private fun notificationAction(
        iconResId: Int,
        title: String,
        action: String,
    ): Notification.Action {
        return Notification.Action.Builder(
            Icon.createWithResource(this, iconResId),
            title,
            servicePendingIntent(action),
        ).build()
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        return PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun activityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private inner class OmniBeatSessionPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands()
                .buildUpon()
                .addAll(
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                )
                .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .remove(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_BACK)
                .remove(Player.COMMAND_SEEK_FORWARD)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun seekToPrevious() {
            playAdjacentStation(-1)
        }

        override fun seekToPreviousMediaItem() {
            playAdjacentStation(-1)
        }

        override fun seekToNext() {
            playAdjacentStation(1)
        }

        override fun seekToNextMediaItem() {
            playAdjacentStation(1)
        }

        override fun getMediaMetadata(): MediaMetadata {
            val current = state.value
            val station = current.selectedStation ?: return super.getMediaMetadata()
            return buildSessionMetadata(station, current.trackText)
        }

        override fun isCurrentMediaItemLive(): Boolean = true

        override fun getDuration(): Long = C.TIME_UNSET
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_DELAY_MS = 500L
        private const val EXTRA_INDEX = "index"

        private const val ACTION_PLAY_STATION = "omnibeat.app.action.PLAY_STATION"
        private const val ACTION_PLAY_PAUSE = "omnibeat.app.action.PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "omnibeat.app.action.PREVIOUS"
        private const val ACTION_NEXT = "omnibeat.app.action.NEXT"
        private const val ACTION_RANDOM = "omnibeat.app.action.RANDOM"
        private const val ACTION_STOP = "omnibeat.app.action.STOP"

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()

        fun playStation(context: Context, index: Int) {
            context.startForegroundService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_STATION)
                    .putExtra(EXTRA_INDEX, index),
            )
        }

        fun playOrPause(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY_PAUSE))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
        }
    }
}
