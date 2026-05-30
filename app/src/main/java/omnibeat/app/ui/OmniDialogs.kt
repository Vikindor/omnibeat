package omnibeat.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import omnibeat.app.R
import omnibeat.app.data.StationImportMode

@Composable
fun OmniConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OmniSecondaryButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                if (destructive) {
                    OmniDangerButton(text = confirmText, onClick = onConfirm)
                } else {
                    OmniPrimaryButton(text = confirmText, onClick = onConfirm)
                }
            }
        },
        dismissButton = {},
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
        modifier = modifier,
    )
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val context = LocalContext.current
    val dialogTitle = title ?: stringResource(R.string.dialog_error_title)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
        title = {
            Text(
                text = dialogTitle,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = message,
                lineHeight = 20.sp,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OmniSecondaryButton(text = stringResource(R.string.action_close), onClick = onDismiss)
                Spacer(modifier = Modifier.weight(1f))
                OmniPrimaryButton(
                    text = stringResource(R.string.action_copy),
                    onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText(dialogTitle, message))
                        Toast.makeText(context, context.getString(R.string.toast_error_copied), Toast.LENGTH_SHORT).show()
                    },
                )
            }
        },
        dismissButton = {},
    )
}

@Composable
fun ImportStationsDialog(
    stationCount: Int,
    onImport: (StationImportMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = RadioSurface,
        title = {
            Text(
                text = stringResource(R.string.dialog_import_stations_title),
                color = RadioText,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_import_stations_text, stationCount),
                color = RadioTextMuted,
                lineHeight = 20.sp,
            )
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onImport(StationImportMode.Replace) }) {
                    Text(stringResource(R.string.action_replace))
                }
                Spacer(modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { onImport(StationImportMode.Merge) }) {
                    Text(stringResource(R.string.action_merge))
                }
            }
        },
        dismissButton = {},
    )
}
