package omnibeat.app.playback

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import omnibeat.app.data.TRACK_HISTORY_DISABLED
import omnibeat.app.data.TrackHistoryRepository
import kotlin.time.Duration.Companion.seconds

private data class HistoryTrack(val stationId: String, val text: String)

private data class HistoryPlayback(
    val track: HistoryTrack?,
    val playing: Boolean,
    val stopped: Boolean,
    val recordingEnabled: Boolean,
)

internal suspend fun recordTrackHistory(
    state: StateFlow<PlaybackState>,
    repository: TrackHistoryRepository,
) {
    var currentTrack: HistoryTrack? = null
    var recorded = false

    combine(
        state,
        repository.limit.map { it != TRACK_HISTORY_DISABLED }.distinctUntilChanged(),
    ) { playback, recordingEnabled ->
        val station = playback.selectedStation
        val text = playback.trackText.trim()
        val track = if (
            station != null && playback.trackStatus == null &&
            text.isNotBlank() && !text.equals(station.title.trim(), ignoreCase = true)
        ) {
            HistoryTrack(station.id, text)
        } else {
            null
        }
        HistoryPlayback(
            track = track,
            playing = playback.isPlaying,
            stopped = station == null || playback.trackStatus == PlaybackTrackStatus.Stopped,
            recordingEnabled = recordingEnabled,
        )
    }.distinctUntilChanged().collectLatest { playback ->
        if (playback.stopped) {
            currentTrack = null
            recorded = false
        }
        val track = playback.track ?: return@collectLatest
        if (currentTrack != track) {
            currentTrack = track
            recorded = false
        }
        if (!playback.recordingEnabled || !playback.playing || recorded) return@collectLatest
        delay(5.seconds)
        val station = state.value.selectedStation ?: return@collectLatest
        try {
            recorded = repository.record(track.text, station.title)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("TrackHistory", "Cannot save track history", error)
        }
    }
}
