package omnibeat.app.playback

import omnibeat.app.data.appString
import omnibeat.app.R

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.AudioAttributes
import androidx.media3.common.SimpleBasePlayer
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
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.MainActivity
import omnibeat.app.data.DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES
import omnibeat.app.data.StationRepository
import omnibeat.app.data.StationArtworkCache
import omnibeat.app.data.STOP_SERVICE_AFTER_PAUSE_NEVER
import omnibeat.app.model.Station
import omnibeat.app.network.NetworkStatus
import omnibeat.app.stream.IcyMetadataParser
import omnibeat.app.stream.StreamResolver
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val PLAYER_USER_AGENT = "OmniBeat Android"

enum class PlaybackTrackStatus {
    Stopped,
    Paused,
    LoadingStations,
    Resolving,
    WaitingMetadata,
    NoMetadata,
}

data class PlaybackState(
    val selectedIndex: Int = -1,
    val selectedStation: Station? = null,
    val previewing: Boolean = false,
    val trackText: String = "",
    val trackStatus: PlaybackTrackStatus? = PlaybackTrackStatus.Stopped,
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
    val sampleRateText: String?
        get() = sampleRateHz?.takeIf { it > 0 }?.let { "${it / 1000} kHz" }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var repository: StationRepository
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: OmniBeatSessionPlayer
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var library: StationMediaLibrary
    private var stations: List<Station> = emptyList()
    private var resolveJob: Job? = null
    private var noMetadataJob: Job? = null
    private var playbackRequested = false
    private var recordedStationId: String? = null
    private var currentStreamIsHls = false
    private var lastPlayedStationId: String? = null
    private var rememberLastStation = true
    private var stopServiceAfterPauseMinutes = DEFAULT_STOP_SERVICE_AFTER_PAUSE_MINUTES
    private var showAndroidAutoArtwork = false
    private var navigationQueueIds: List<String> = emptyList()
    private var stopAfterPauseJob: Job? = null

    private fun trackText(status: PlaybackTrackStatus): String {
        return when (status) {
            PlaybackTrackStatus.Stopped -> resources.appString(R.string.track_text_stopped)
            PlaybackTrackStatus.Paused -> resources.appString(R.string.track_text_paused)
            PlaybackTrackStatus.LoadingStations -> resources.appString(R.string.track_text_loading_stations)
            PlaybackTrackStatus.Resolving -> resources.appString(R.string.track_text_resolving)
            PlaybackTrackStatus.WaitingMetadata -> resources.appString(R.string.track_text_waiting_metadata)
            PlaybackTrackStatus.NoMetadata -> resources.appString(R.string.track_text_no_metadata)
        }
    }

    private fun PlaybackState.withTrackStatus(status: PlaybackTrackStatus): PlaybackState {
        return copy(trackText = trackText(status), trackStatus = status)
    }

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository(applicationContext)
        player = buildPlayer()
        sessionPlayer = OmniBeatSessionPlayer()
        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        library = StationMediaLibrary(
            this, repository, scope,
            onStationsLoaded = { stations = it },
            onPlaybackRejected = { if (!playbackRequested) stopSelf() },
        )
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().apply {
            setSmallIcon(R.drawable.ic_play_arrow)
        })
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, library)
            .setSessionActivity(activityPendingIntent()).build()
        addSession(requireNotNull(mediaSession))
        scope.launch { state.collect { sessionPlayer.syncState() } }
        scope.launch {
            combine(state, repository.showAndroidAutoArtwork) { playback, show ->
                playback.selectedStation?.imageUrl?.takeIf { show }
            }.distinctUntilChanged().collectLatest { url ->
                if (url != null) coroutineScope {
                    launch {
                        StationArtworkCache.updates(url).collect {
                            syncSession()
                            mediaSession?.let { library.notifyChanged(it) }
                        }
                    }
                    StationArtworkCache.load(this@PlaybackService, url, 256, 256).collect {}
                }
            }
        }

        scope.launch {
            repository.stations.distinctUntilChanged().collect { savedStations ->
                stations = savedStations
                mediaSession?.let { library.notifyChanged(it) }
                val currentStation = state.value.selectedStation
                if (currentStation == null) {
                    syncSession()
                    return@collect
                }
                if (state.value.previewing) {
                    syncSession()
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
                    syncSession()
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
        scope.launch {
            repository.stopServiceAfterPauseMinutes.collect { minutes ->
                stopServiceAfterPauseMinutes = minutes
            }
        }
        scope.launch {
            repository.showAndroidAutoArtwork.distinctUntilChanged().collect { show ->
                showAndroidAutoArtwork = show
                syncSession()
                mediaSession?.let { library.notifyChanged(it) }
            }
        }
        scope.launch {
            repository.recentlyPlayedStationIds.distinctUntilChanged().collect {
                mediaSession?.let { library.notifyChanged(it) }
            }
        }
        scope.launch {
            combine(
                repository.stationSortState,
                repository.customStationOrder,
                repository.customFavoriteOrder,
            ) { sortState, stationOrder, favoriteOrder ->
                Triple(sortState, stationOrder, favoriteOrder)
            }.distinctUntilChanged().collect {
                mediaSession?.let { library.notifyChanged(it) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY_STATION -> playStationAt(
                index = intent.getIntExtra(EXTRA_INDEX, -1),
                queueIds = intent.getStringArrayListExtra(EXTRA_QUEUE_IDS).orEmpty(),
            )
            ACTION_PLAY_PREVIEW -> playPreviewStation(intent.toPreviewStation())
            ACTION_PLAY_PAUSE -> playOrPause()
            ACTION_STOP -> {
                stopPlayback()
                stopSelf()
            }
        }
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        resolveJob?.cancel()
        noMetadataJob?.cancel()
        stopAfterPauseJob?.cancel()
        player.removeAnalyticsListener(analyticsListener)
        player.removeListener(playerListener)
        mediaSession?.release()
        sessionPlayer.release()
        player.release()
        _state.update { it.copy(isPlaying = false, resolving = false, buffering = false) }
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun playOrPause() {
        val current = state.value
        if (player.isPlaying || current.resolving || current.buffering) {
            pausePlayback()
        } else {
            playFromCurrentState()
        }
        syncSession()
    }

    private fun playFromCurrentState() {
        val current = state.value
        if (player.isPlaying || current.resolving) return
        if (player.mediaItemCount > 0 && current.errorText == null &&
            current.trackStatus != PlaybackTrackStatus.Paused
        ) {
            playbackRequested = true
            player.play()
            return
        }
        if (current.selectedStation == null) {
            playLastOrFirstStation()
        } else {
            playStation(
                station = current.selectedStation,
                index = current.selectedIndex,
                previewing = current.previewing,
            )
        }
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
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
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
                    resolving = true,
                    buffering = false,
                    errorText = null,
                ).withTrackStatus(PlaybackTrackStatus.LoadingStations)
            }
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
        startPlayback: Boolean = true,
    ) {
        resolveJob?.cancel()
        stopAfterPauseJob?.cancel()
        noMetadataJob?.cancel()
        playbackRequested = startPlayback
        if (!previewing) {
            lastPlayedStationId = station.id
            scope.launch { repository.saveLastPlayedStationId(station.id) }
        }
        recordedStationId = null
        currentStreamIsHls = false
        if (!NetworkStatus.isOnline(this)) {
            playbackRequested = false
            player.stop()
            _state.update {
                it.copy(
                    selectedIndex = index,
                    selectedStation = station,
                    previewing = previewing,
                    resolving = false,
                    buffering = false,
                    isPlaying = false,
                    errorText = resources.appString(R.string.toast_no_internet),
                    streamInfo = PlaybackStreamInfo(),
                ).withTrackStatus(PlaybackTrackStatus.Stopped)
            }
            syncSession()
            stopSelf()
            return
        }
        _state.update {
            it.copy(
                selectedIndex = index,
                selectedStation = station,
                previewing = previewing,
                resolving = true,
                buffering = false,
                isPlaying = false,
                errorText = null,
                streamInfo = PlaybackStreamInfo(),
            ).withTrackStatus(PlaybackTrackStatus.Resolving)
        }
        player.stop()
        syncSession()

        resolveJob = scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    StreamResolver.resolveStream(station.streamUrl)
                }
            }.onSuccess { resolvedStream ->
                currentStreamIsHls = resolvedStream.playableUrl.lowercase().contains(".m3u8")
                _state.update {
                    it.copy(
                        streamInfo = PlaybackStreamInfo(
                            bitrateKbps = resolvedStream.bitrateKbps,
                        ),
                    ).withTrackStatus(PlaybackTrackStatus.WaitingMetadata)
                }
                player.setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(StationMediaLibrary.stationItem(this@PlaybackService, station).mediaId)
                        .setUri(resolvedStream.playableUrl)
                        .setMimeType(resolvedStream.playableUrl.mediaMimeType())
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                        .build(),
                )
                player.prepare()
                player.playWhenReady = playbackRequested
                _state.update { it.copy(resolving = false) }
                scheduleNoMetadataIfPlaybackStarted()
                syncSession()
            }.onFailure { error ->
                if (error is CancellationException) {
                    return@onFailure
                }
                playbackRequested = false
                _state.update {
                    it.copy(
                        errorText = "Could not resolve stream: ${error.message}",
                        resolving = false,
                        buffering = false,
                    )
                }
                syncSession()
                stopSelf()
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
        noMetadataJob?.cancel()
        stopAfterPauseJob?.cancel()
        playbackRequested = false
        currentStreamIsHls = false
        navigationQueueIds = emptyList()
        player.stop()
        player.clearMediaItems()
        _state.update {
            it.copy(
                selectedIndex = -1,
                selectedStation = null,
                previewing = false,
                resolving = false,
                buffering = false,
                isPlaying = false,
                errorText = null,
                streamInfo = PlaybackStreamInfo(),
            ).withTrackStatus(PlaybackTrackStatus.Stopped)
        }
    }

    private fun pausePlayback() {
        resolveJob?.cancel()
        playbackRequested = false
        noMetadataJob?.cancel()
        currentStreamIsHls = false
        player.stop()
        player.clearMediaItems()
        _state.update {
            it.copy(
                resolving = false,
                buffering = false,
                isPlaying = false,
                errorText = null,
            ).withTrackStatus(PlaybackTrackStatus.Paused)
        }
        syncSession()
        scheduleStopAfterPause()
    }

    private fun scheduleStopAfterPause() {
        stopAfterPauseJob?.cancel()
        val minutes = stopServiceAfterPauseMinutes
        if (minutes == STOP_SERVICE_AFTER_PAUSE_NEVER) return
        stopAfterPauseJob = scope.launch {
            delay(minutes.minutes)
            val current = state.value
            if (!current.isPlaying && !current.resolving && !current.buffering && current.trackStatus == PlaybackTrackStatus.Paused) {
                stopPlayback()
                stopSelf()
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    isPlaying = player.isPlaying,
                )
            }
            scheduleNoMetadataIfPlaybackStarted()
            syncSession()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            scheduleNoMetadataIfPlaybackStarted()
            val current = state.value
            val stationId = current.selectedStation?.id
            if (isPlaying && !current.previewing && stationId != null && recordedStationId != stationId) {
                recordedStationId = stationId
                scope.launch { repository.recordStationPlayed(stationId) }
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason in listOf(
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
                )
            ) {
                playbackRequested = false
                _state.update { it.withTrackStatus(PlaybackTrackStatus.Paused) }
                scheduleStopAfterPause()
            }
            syncSession()
        }

        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            syncSession()
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
            noMetadataJob?.cancel()
            _state.update { it.copy(trackText = nextTrackText, trackStatus = null) }
            syncSession()
        }

        override fun onMetadata(metadata: Metadata) {
            repeat(metadata.length()) { index ->
                val streamTitle = IcyMetadataParser.readStreamTitle(metadata[index].toString())
                if (!streamTitle.isNullOrBlank()) {
                    noMetadataJob?.cancel()
                    _state.update { it.copy(trackText = streamTitle, trackStatus = null) }
                    syncSession()
                    return
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            resolveJob?.cancel()
            noMetadataJob?.cancel()
            playbackRequested = false
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
            syncSession()
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
            syncSession()
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
            syncSession()
        }
    }

    private fun bitrateKbps(bitrate: Int): Int? {
        return bitrate.takeIf { it > 0 }?.let { it / 1000 }
    }

    private fun scheduleNoMetadataIfPlaybackStarted() {
        val current = state.value
        if (!player.isPlaying || current.resolving || current.buffering || current.trackStatus != PlaybackTrackStatus.WaitingMetadata) return
        if (noMetadataJob?.isActive == true) return
        noMetadataJob = scope.launch {
            delay(METADATA_WAIT_TIMEOUT)
            val latest = state.value
            if (
                player.isPlaying &&
                !latest.resolving &&
                !latest.buffering &&
                latest.trackStatus == PlaybackTrackStatus.WaitingMetadata
            ) {
                _state.update { it.withTrackStatus(PlaybackTrackStatus.NoMetadata) }
                syncSession()
            }
            noMetadataJob = null
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

    private fun syncSession() {
        sessionPlayer.syncState()
    }

    private fun buildSessionMetadata(station: Station, trackText: String?): MediaMetadata {
        val metadata = StationMediaLibrary.stationMetadata(this, station, trackText, showAndroidAutoArtwork)
        return if (state.value.previewing) metadata.buildUpon()
            .setArtworkUri(station.imageUrl?.takeIf { showAndroidAutoArtwork }?.let(Uri::parse)).build() else metadata
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

    private fun activityPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private inner class OmniBeatSessionPlayer : SimpleBasePlayer(mainLooper) {
        private var reportedError: PlaybackException? = null

        fun syncState() = invalidateState()

        override fun getState(): State {
            val current = PlaybackService.state.value
            val selected = current.selectedStation
            val queue = when {
                selected == null -> emptyList()
                current.previewing -> listOf(selected)
                else -> navigationStations().let { stations ->
                    if (stations.any { it.id == selected.id }) stations else listOf(selected)
                }
            }
            if (current.errorText != reportedError?.message) {
                reportedError = current.errorText?.let {
                    player.playerError ?: PlaybackException(it, null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
                }
            }
            val error = reportedError
            return State.Builder()
                .setAvailableCommands(Player.Commands.Builder().addAll(
                    COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP, COMMAND_RELEASE,
                    COMMAND_SET_MEDIA_ITEM, COMMAND_CHANGE_MEDIA_ITEMS,
                    COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_SEEK_TO_MEDIA_ITEM,
                    COMMAND_GET_TIMELINE, COMMAND_GET_METADATA,
                    COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                ).build())
                .setPlaylist(queue.map { station ->
                    MediaItemData.Builder(station.id)
                        .setMediaItem(StationMediaLibrary.stationItem(
                            this@PlaybackService, station, showArtwork = showAndroidAutoArtwork,
                        ))
                        .setMediaMetadata(buildSessionMetadata(
                            station, current.trackText.takeIf { station.id == selected?.id && current.trackStatus == null },
                        ))
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
                        .setIsDynamic(true).setIsSeekable(false).build()
                })
                .setCurrentMediaItemIndex(queue.indexOfFirst { it.id == selected?.id })
                .setRepeatMode(REPEAT_MODE_ALL)
                .setPlayWhenReady(
                    error == null && playbackRequested && (current.resolving || player.playWhenReady),
                    PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
                .setPlaybackState(when {
                    error != null || queue.isEmpty() -> STATE_IDLE
                    current.resolving -> STATE_BUFFERING
                    else -> player.playbackState
                })
                .setPlaybackSuppressionReason(player.playbackSuppressionReason)
                .setPlayerError(error)
                .setAudioAttributes(player.audioAttributes)
                .setVolume(player.volume)
                .setContentPositionMs(0)
                .build()
        }

        override fun handleSetMediaItems(
            mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long,
        ): ListenableFuture<*> {
            val selected = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex)
            val station = stations.find { it.id == selected?.mediaId?.let(StationMediaLibrary::stationId) }
            if (station == null) {
                stopPlayback()
                return Futures.immediateVoidFuture()
            }
            resolveJob?.cancel()
            stopAfterPauseJob?.cancel()
            noMetadataJob?.cancel()
            playbackRequested = false
            player.stop()
            player.clearMediaItems()
            navigationQueueIds = mediaItems.mapNotNull { StationMediaLibrary.stationId(it.mediaId) }
            _state.update {
                it.copy(
                    selectedStation = station, selectedIndex = stations.indexOfFirst { item -> item.id == station.id },
                    previewing = false, resolving = false, buffering = false, isPlaying = false,
                    errorText = null, streamInfo = PlaybackStreamInfo(),
                ).withTrackStatus(PlaybackTrackStatus.Paused)
            }
            return Futures.immediateVoidFuture()
        }

        override fun handlePrepare(): ListenableFuture<*> {
            val current = PlaybackService.state.value
            if (!current.resolving && player.playbackState == STATE_IDLE) {
                current.selectedStation?.let {
                    playStation(it, current.selectedIndex, current.previewing, startPlayback = playbackRequested)
                }
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            if (playWhenReady) {
                if (PlaybackService.state.value.resolving) {
                    playbackRequested = true
                } else {
                    playFromCurrentState()
                }
            } else {
                pausePlayback()
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            when (seekCommand) {
                COMMAND_SEEK_TO_PREVIOUS, COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> playAdjacentStation(-1)
                COMMAND_SEEK_TO_NEXT, COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> playAdjacentStation(1)
                COMMAND_SEEK_TO_MEDIA_ITEM -> navigationStations().getOrNull(mediaItemIndex)?.let { station ->
                    val index = stations.indexOfFirst { it.id == station.id }
                    if (index >= 0) playStationAt(index)
                }
                else -> Unit
            }
            return Futures.immediateVoidFuture()
        }

        override fun handleStop(): ListenableFuture<*> {
            stopPlayback()
            return Futures.immediateVoidFuture()
        }

        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }

    companion object {
        private val METADATA_WAIT_TIMEOUT = 15.seconds
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_QUEUE_IDS = "queue_ids"
        private const val EXTRA_PREVIEW_ID = "preview_id"
        private const val EXTRA_PREVIEW_TITLE = "preview_title"
        private const val EXTRA_PREVIEW_STREAM_URL = "preview_stream_url"
        private const val EXTRA_PREVIEW_TAGS = "preview_tags"
        private const val EXTRA_PREVIEW_IMAGE_URL = "preview_image_url"
        private const val ACTION_PLAY_STATION = "omnibeat.app.action.PLAY_STATION"
        private const val ACTION_PLAY_PREVIEW = "omnibeat.app.action.PLAY_PREVIEW"
        private const val ACTION_PLAY_PAUSE = "omnibeat.app.action.PLAY_PAUSE"
        private const val ACTION_STOP = "omnibeat.app.action.STOP"

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state.asStateFlow()

        fun playStation(
            context: Context,
            index: Int,
            queueIds: List<String> = emptyList(),
        ) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_STATION)
                    .putExtra(EXTRA_INDEX, index)
                    .putStringArrayListExtra(EXTRA_QUEUE_IDS, ArrayList(queueIds)),
            )
        }

        fun playPreview(context: Context, station: Station) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_PREVIEW)
                    .putExtra(EXTRA_PREVIEW_ID, station.id)
                    .putExtra(EXTRA_PREVIEW_TITLE, station.title)
                    .putExtra(EXTRA_PREVIEW_STREAM_URL, station.streamUrl)
                    .putExtra(EXTRA_PREVIEW_TAGS, station.tags.joinToString(","))
                    .putExtra(EXTRA_PREVIEW_IMAGE_URL, station.imageUrl.orEmpty()),
            )
        }

        fun playOrPause(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java)
                    .setAction(ACTION_PLAY_PAUSE),
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
