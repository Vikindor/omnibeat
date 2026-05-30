package omnibeat.app.playback

import omnibeat.app.R

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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.MainActivity
import omnibeat.app.data.StationRepository
import omnibeat.app.model.Station
import omnibeat.app.network.NetworkStatus
import omnibeat.app.stream.IcyMetadataParser
import omnibeat.app.stream.StreamResolver
import java.util.concurrent.CancellationException
import kotlin.random.Random

const val TRACK_TEXT_STOPPED = "No stream is playing"
const val TRACK_TEXT_LOADING_STATIONS = "Loading stations..."
const val TRACK_TEXT_RESOLVING = "Resolving stream..."
const val TRACK_TEXT_WAITING_METADATA = "Waiting for metadata..."
const val TRACK_TEXT_NO_METADATA = "No metadata"
private const val PLAYER_USER_AGENT = "OmniBeat Android"

data class PlaybackState(
    val selectedIndex: Int = -1,
    val selectedStation: Station? = null,
    val previewing: Boolean = false,
    val trackText: String = TRACK_TEXT_STOPPED,
    val resolving: Boolean = false,
    val buffering: Boolean = false,
    val isPlaying: Boolean = false,
    val errorText: String? = null,
    val volume: Float = 0.75f,
    val streamInfo: PlaybackStreamInfo = PlaybackStreamInfo(),
)

data class PlaybackStreamInfo(
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val formatLabel: String? = null,
) {
    val bitrateText: String?
        get() = bitrateKbps?.let { "$it kbps" }

    val sampleRateText: String?
        get() = sampleRateHz?.takeIf { it > 0 }?.let { "${it / 1000} kHz" }
}

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
    private var lastPlayedStationId: String? = null
    private var rememberLastStation = true
    private var navigationQueueIds: List<String> = emptyList()

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository(applicationContext)
        player = buildPlayer()
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
                if (state.value.previewing) {
                    updateNotification()
                    return@collect
                }
                val current = savedStations.indexOfFirst { it.id == currentStation.id }
                if (current == -1) {
                    stopPlayback()
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
        scope.launch {
            repository.lastPlayedStationId.collect { stationId ->
                lastPlayedStationId = stationId
            }
        }
        scope.launch {
            repository.rememberLastStation.collect { remember ->
                rememberLastStation = remember
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_STATION -> playStationAt(
                index = intent.getIntExtra(EXTRA_INDEX, -1),
                queueIds = intent.getStringArrayListExtra(EXTRA_QUEUE_IDS).orEmpty(),
            )
            ACTION_PLAY_PREVIEW -> playPreviewStation(intent.toPreviewStation())
            ACTION_PLAY_STOP -> playOrStop()
            ACTION_PREVIOUS -> playAdjacentStation(-1)
            ACTION_NEXT -> playAdjacentStation(1)
            ACTION_RANDOM -> playRandomStation()
            ACTION_STOP -> {
                stopPlayback()
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

    private fun playOrStop() {
        val current = state.value
        when {
            player.isPlaying || current.resolving || current.buffering -> {
                stopPlayback()
                stopSelf()
            }
            current.selectedStation == null -> playLastOrFirstStation()
            else -> player.play()
        }
        updateNotification(immediate = true)
    }

    private fun playLastOrFirstStation() {
        scope.launch {
            if (stations.isEmpty()) {
                stations = repository.stations.first()
            }
            val nextIndex = if (rememberLastStation) {
                lastPlayedStationId
                    ?.let { stationId -> stations.indexOfFirst { it.id == stationId } }
                    ?.takeIf { it >= 0 }
                    ?: 0
            } else {
                0
            }
            playStationAt(nextIndex)
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(PLAYER_USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    private fun playStationAt(
        index: Int,
        queueIds: List<String> = navigationQueueIds,
    ) {
        navigationQueueIds = queueIds
        val station = stations.getOrNull(index)
        if (station == null) {
            _state.update {
                it.copy(
                    selectedIndex = index,
                    selectedStation = null,
                    previewing = false,
                    trackText = TRACK_TEXT_LOADING_STATIONS,
                    resolving = true,
                    buffering = false,
                    errorText = null,
                )
            }
            startForeground(NOTIFICATION_ID, buildNotification())
            scope.launch {
                stations = repository.stations.first()
                if (stations.getOrNull(index) == null) {
                    stopPlayback()
                    stopSelf()
                } else {
                    playStationAt(index, queueIds)
                }
            }
            return
        }
        lastPlayedStationId = station.id
        scope.launch { repository.saveLastPlayedStationId(station.id) }
        playStation(station = station, index = index, previewing = false)
    }

    private fun playPreviewStation(station: Station?) {
        if (station == null) return
        playStation(station = station, index = -1, previewing = true)
    }

    private fun playStation(
        station: Station,
        index: Int,
        previewing: Boolean,
    ) {
        resolveJob?.cancel()
        player.stop()
        lastSessionMetadata = null
        currentStreamIsHls = false
        if (!NetworkStatus.isOnline(this)) {
            _state.update {
                it.copy(
                    selectedIndex = index,
                    selectedStation = station,
                    previewing = previewing,
                    trackText = TRACK_TEXT_STOPPED,
                    resolving = false,
                    buffering = false,
                    isPlaying = false,
                    errorText = getString(R.string.toast_no_internet),
                    streamInfo = PlaybackStreamInfo(),
                )
            }
            updateNotification(immediate = true)
            return
        }
        _state.update {
            it.copy(
                selectedIndex = index,
                selectedStation = station,
                previewing = previewing,
                trackText = TRACK_TEXT_RESOLVING,
                resolving = true,
                buffering = false,
                errorText = null,
                streamInfo = PlaybackStreamInfo(),
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
                        trackText = TRACK_TEXT_WAITING_METADATA,
                        resolving = false,
                        streamInfo = PlaybackStreamInfo(
                            bitrateKbps = resolvedStream.bitrateKbps,
                        ),
                    )
                }
                player.setMediaItem(
                    MediaItem.Builder()
                        .setUri(resolvedStream.playableUrl)
                        .setMimeType(resolvedStream.playableUrl.mediaMimeType())
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                        .build(),
                )
                player.prepare()
                player.play()
                updateNotification()
            }.onFailure { error ->
                if (error is CancellationException) {
                    return@onFailure
                }
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
        val navigationStations = navigationStations()
        if (navigationStations.isEmpty()) return
        val currentStationId = state.value.selectedStation?.id
        val currentIndex = navigationStations.indexOfFirst { it.id == currentStationId }
        val nextStation = if (currentIndex in navigationStations.indices) {
            navigationStations[(currentIndex + direction + navigationStations.size) % navigationStations.size]
        } else if (direction > 0) {
            navigationStations.first()
        } else {
            navigationStations.last()
        }
        stations.indexOfFirst { it.id == nextStation.id }
            .takeIf { it >= 0 }
            ?.let { playStationAt(it) }
    }

    private fun playRandomStation() {
        val navigationStations = navigationStations()
        if (navigationStations.isEmpty()) return
        val currentStationId = state.value.selectedStation?.id
        val nextStation = if (navigationStations.size == 1) {
            navigationStations.first()
        } else {
            var randomIndex: Int
            do {
                randomIndex = Random.nextInt(navigationStations.size)
            } while (navigationStations[randomIndex].id == currentStationId)
            navigationStations[randomIndex]
        }
        stations.indexOfFirst { it.id == nextStation.id }
            .takeIf { it >= 0 }
            ?.let { playStationAt(it) }
    }

    private fun navigationStations(): List<Station> {
        if (navigationQueueIds.isEmpty()) {
            return stations
        }
        val stationById = stations.associateBy { it.id }
        val queuedStations = navigationQueueIds.mapNotNull(stationById::get)
        return queuedStations.ifEmpty { stations }
    }

    private fun stopPlayback() {
        resolveJob?.cancel()
        notificationUpdateJob?.cancel()
        lastSessionMetadata = null
        currentStreamIsHls = false
        navigationQueueIds = emptyList()
        player.stop()
        _state.update {
            it.copy(
                selectedIndex = -1,
                selectedStation = null,
                previewing = false,
                trackText = TRACK_TEXT_STOPPED,
                resolving = false,
                buffering = false,
                isPlaying = false,
                errorText = null,
                streamInfo = PlaybackStreamInfo(),
            )
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
            showNoMetadataIfPlaybackStarted()
            updateNotification()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            showNoMetadataIfPlaybackStarted()
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
                updateStreamInfo(bitrate = selectedBitrates.first())
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                saveCurrentStationImageUrl(imageUrl)
            }
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
            resolveJob?.cancel()
            notificationUpdateJob?.cancel()
            lastSessionMetadata = null
            currentStreamIsHls = false
            player.stop()
            _state.update {
                it.copy(
                    errorText = error.message ?: "Playback error",
                    resolving = false,
                    buffering = false,
                    isPlaying = false,
                    streamInfo = PlaybackStreamInfo(),
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime, mediaLoadData: MediaLoadData) {
            mediaLoadData.trackFormat?.let { format ->
                val codecLabel = format.sampleMimeType?.audioCodecLabel()
                if (currentStreamIsHls && format.sampleRate <= 0 && codecLabel == null) {
                    return
                }
                updateStreamInfo(format, codecLabel)
            }
        }
    }

    private fun updateStreamInfo(bitrate: Int) {
        val bitrateKbps = bitrateKbps(bitrate) ?: return
        if (state.value.streamInfo.bitrateKbps != bitrateKbps) {
            _state.update { it.copy(streamInfo = it.streamInfo.copy(bitrateKbps = bitrateKbps)) }
            updateNotification()
        }
    }

    private fun updateStreamInfo(format: Format, codecLabel: String? = format.sampleMimeType?.audioCodecLabel()) {
        val nextInfo = state.value.streamInfo.copy(
            bitrateKbps = bitrateKbps(format.bitrate) ?: state.value.streamInfo.bitrateKbps,
            sampleRateHz = format.sampleRate.takeIf { it > 0 } ?: state.value.streamInfo.sampleRateHz,
            formatLabel = codecLabel ?: state.value.streamInfo.formatLabel,
        )
        if (state.value.streamInfo != nextInfo) {
            _state.update { it.copy(streamInfo = nextInfo) }
            updateNotification()
        }
    }

    private fun bitrateKbps(bitrate: Int): Int? {
        return bitrate.takeIf { it > 0 }?.let { it / 1000 }
    }

    private fun showNoMetadataIfPlaybackStarted() {
        val current = state.value
        if (
            player.isPlaying &&
            !current.resolving &&
            !current.buffering &&
            current.trackText == TRACK_TEXT_WAITING_METADATA
        ) {
            _state.update { it.copy(trackText = TRACK_TEXT_NO_METADATA) }
            refreshCurrentMediaMetadata()
        }
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

    private fun String.mediaMimeType(): String? {
        return when {
            contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
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
        player.playlistMetadata = buildSessionMetadata(station, current.trackText)
    }

    private fun buildSessionMetadata(station: Station, trackText: String?): MediaMetadata {
        return MediaMetadata.Builder()
            .setTitle(station.title)
            .setArtist(trackText?.takeIf { it.isPublicTrackText() })
            .build()
    }

    private fun saveCurrentStationImageUrl(imageUrl: String) {
        val current = state.value
        val station = current.selectedStation ?: return
        if (current.previewing || station.imageUrl == imageUrl) {
            return
        }
        val stationId = station.id
        _state.update { playbackState ->
            playbackState.copy(selectedStation = playbackState.selectedStation?.copy(imageUrl = imageUrl))
        }
        scope.launch { repository.saveStationImageUrl(stationId, imageUrl) }
    }

    private fun String.isPublicTrackText(): Boolean {
        return isNotBlank() && this !in SERVICE_TRACK_TEXTS
    }

    private fun buildNotification(): Notification {
        val current = state.value
        val playStopAction = if (current.isPlaying || current.resolving || current.buffering) {
            notificationAction(R.drawable.ic_stop, "Stop", ACTION_PLAY_STOP)
        } else {
            notificationAction(R.drawable.ic_play_arrow, "Play", ACTION_PLAY_STOP)
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
            .addAction(playStopAction)
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
                    COMMAND_SEEK_TO_PREVIOUS,
                    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT,
                    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                )
                .remove(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .remove(COMMAND_SEEK_TO_DEFAULT_POSITION)
                .remove(COMMAND_SEEK_TO_MEDIA_ITEM)
                .remove(COMMAND_SEEK_BACK)
                .remove(COMMAND_SEEK_FORWARD)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return availableCommands.contains(command)
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

        override fun play() {
            if (state.value.selectedStation == null) {
                playLastOrFirstStation()
            } else {
                super.play()
            }
        }

        override fun pause() {
            stopPlayback()
            stopSelf()
        }

        override fun getMediaMetadata(): MediaMetadata {
            val current = state.value
            val station = current.selectedStation ?: return super.mediaMetadata
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
        private const val EXTRA_QUEUE_IDS = "queue_ids"
        private const val EXTRA_PREVIEW_ID = "preview_id"
        private const val EXTRA_PREVIEW_TITLE = "preview_title"
        private const val EXTRA_PREVIEW_STREAM_URL = "preview_stream_url"
        private const val EXTRA_PREVIEW_TAGS = "preview_tags"
        private const val EXTRA_PREVIEW_IMAGE_URL = "preview_image_url"
        private val SERVICE_TRACK_TEXTS = setOf(
            TRACK_TEXT_STOPPED,
            TRACK_TEXT_LOADING_STATIONS,
            TRACK_TEXT_RESOLVING,
            TRACK_TEXT_WAITING_METADATA,
            TRACK_TEXT_NO_METADATA,
        )

        private const val ACTION_PLAY_STATION = "omnibeat.app.action.PLAY_STATION"
        private const val ACTION_PLAY_PREVIEW = "omnibeat.app.action.PLAY_PREVIEW"
        private const val ACTION_PLAY_STOP = "omnibeat.app.action.PLAY_STOP"
        private const val ACTION_PREVIOUS = "omnibeat.app.action.PREVIOUS"
        private const val ACTION_NEXT = "omnibeat.app.action.NEXT"
        private const val ACTION_RANDOM = "omnibeat.app.action.RANDOM"
        private const val ACTION_STOP = "omnibeat.app.action.STOP"

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()

        fun playStation(
            context: Context,
            index: Int,
            queueIds: List<String> = emptyList(),
        ) {
            context.startForegroundService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_STATION)
                    .putExtra(EXTRA_INDEX, index)
                    .putStringArrayListExtra(EXTRA_QUEUE_IDS, ArrayList(queueIds)),
            )
        }

        fun playPreview(context: Context, station: Station) {
            context.startForegroundService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_PREVIEW)
                    .putExtra(EXTRA_PREVIEW_ID, station.id)
                    .putExtra(EXTRA_PREVIEW_TITLE, station.title)
                    .putExtra(EXTRA_PREVIEW_STREAM_URL, station.streamUrl)
                    .putExtra(EXTRA_PREVIEW_TAGS, station.tags.joinToString(","))
                    .putExtra(EXTRA_PREVIEW_IMAGE_URL, station.imageUrl.orEmpty()),
            )
        }

        fun playOrStop(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_STOP),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_STOP),
            )
        }

        private fun Intent.toPreviewStation(): Station? {
            val title = extras?.getString(EXTRA_PREVIEW_TITLE)?.trim().orEmpty()
            val streamUrl = extras?.getString(EXTRA_PREVIEW_STREAM_URL)?.trim().orEmpty()
            if (title.isBlank() || streamUrl.isBlank()) {
                return null
            }
            return Station(
                id = extras?.getString(EXTRA_PREVIEW_ID)?.takeIf { it.isNotBlank() } ?: "preview:$streamUrl",
                title = title,
                streamUrl = streamUrl,
                tags = extras?.getString(EXTRA_PREVIEW_TAGS)
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                imageUrl = extras?.getString(EXTRA_PREVIEW_IMAGE_URL)?.trim()?.takeIf { it.isNotBlank() },
                isFavorite = false,
                dateAdded = "",
            )
        }
    }
}
