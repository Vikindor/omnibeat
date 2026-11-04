package omnibeat.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import omnibeat.app.R
import omnibeat.app.data.StationRepository
import kotlin.time.Duration.Companion.milliseconds

object ReleaseWarning {
    private const val ENABLED = true
    private const val WARNING_ID = "library_backup_v2"
    private const val DISMISS_DELAY_MILLIS = 5_000L

    @Composable
    fun ShowIfNeeded(repository: StationRepository, canShow: Boolean) {
        if (!ENABLED) return

        val seenId by repository.releaseWarningSeenId.collectAsState(initial = null)
        val scope = rememberCoroutineScope()

        if (canShow && seenId != null && seenId != WARNING_ID) {
            val dismissDeadline = rememberSaveable(WARNING_ID) {
                SystemClock.elapsedRealtime() + DISMISS_DELAY_MILLIS
            }
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            val remainingMillis = (dismissDeadline - now).coerceAtLeast(0L)
            val remainingSeconds = ((remainingMillis + 999L) / 1_000L).toInt()

            LaunchedEffect(dismissDeadline) {
                while (now < dismissDeadline) {
                    delay(minOf(dismissDeadline - now, 1_000L).milliseconds)
                    now = SystemClock.elapsedRealtime()
                }
            }

            OmniInfoDialog(
                title = stringResource(R.string.release_warning_title),
                text = AnnotatedString.fromHtml(stringResource(R.string.release_warning_message)),
                onDismiss = {
                    scope.launch { repository.saveReleaseWarningSeenId(WARNING_ID) }
                },
                dismissEnabled = remainingSeconds == 0,
                dismissText = if (remainingSeconds > 0) {
                    stringResource(R.string.release_warning_close_countdown, remainingSeconds)
                } else {
                    appStringResource(R.string.action_close)
                },
            )
        }
    }
}
