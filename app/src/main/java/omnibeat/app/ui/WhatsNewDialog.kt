package omnibeat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import omnibeat.app.R
import omnibeat.app.ui.appStringResource as stringResource

@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    OmniInfoDialog(
        title = stringResource(R.string.whats_new_title),
        text = stringArrayResource(R.array.whats_new_entries).joinToString("\n\n"),
        onDismiss = onDismiss,
    )
}
