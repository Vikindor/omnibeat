package omnibeat.app.widget

import omnibeat.app.playback.PlaybackState
import omnibeat.app.playback.PlaybackTrackStatus

internal data class WidgetPlaybackState(
    val stationTitle: String?,
    val imageUrl: String?,
    val trackText: String,
    val trackStatus: PlaybackTrackStatus?,
    val active: Boolean,
    val canSkip: Boolean,
) {
    companion object {
        fun from(state: PlaybackState) = WidgetPlaybackState(
            stationTitle = state.selectedStation?.title,
            imageUrl = state.selectedStation?.imageUrl,
            trackText = state.trackText,
            trackStatus = state.trackStatus,
            active = state.isPlaying || state.resolving || state.buffering,
            canSkip = state.selectedStation != null && !state.previewing,
        )
    }
}
