package omnibeat.app.ui

import androidx.compose.runtime.Composable
import omnibeat.app.model.Station
import omnibeat.app.playback.PlaybackState

@Composable
fun PlaybackPanelHost(
    playbackState: PlaybackState,
    pageStations: List<Station>,
    allStations: List<Station>,
    rememberLastStation: Boolean,
    lastPlayedStationId: String?,
    appVolume: Float,
    showBitrate: Boolean,
    showUnavailableBitrate: Boolean,
    marqueeTrackTitle: Boolean,
    onPlayStop: () -> Unit,
    onPreviousStation: () -> Unit,
    onNextStation: () -> Unit,
    onRandomStation: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val canStartPlayback = pageStations.isNotEmpty() ||
        (
            rememberLastStation &&
                lastPlayedStationId?.let { stationId -> allStations.any { it.id == stationId } } == true
        )
    PlayerPanel(
        station = playbackState.selectedStation,
        trackText = playbackState.trackText,
        errorText = playbackState.errorText,
        streamInfo = playbackState.streamInfo,
        loading = playbackState.resolving || playbackState.buffering,
        resolving = playbackState.resolving,
        isPlaying = playbackState.isPlaying,
        canNavigateStations = pageStations.isNotEmpty(),
        appVolume = appVolume,
        canPlay = canStartPlayback,
        showBitrate = showBitrate,
        showUnavailableBitrate = showUnavailableBitrate,
        marqueeTrackTitle = marqueeTrackTitle,
        onPlayStop = onPlayStop,
        onPreviousStation = onPreviousStation,
        onNextStation = onNextStation,
        onRandomStation = onRandomStation,
        onVolumeChange = onVolumeChange,
    )
}
