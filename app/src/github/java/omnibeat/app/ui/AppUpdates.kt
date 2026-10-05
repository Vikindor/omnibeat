package omnibeat.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import omnibeat.app.R

@Composable
fun rememberAppUpdates(): AppUpdateActions {
    val activity = checkNotNull(LocalActivity.current) as androidx.activity.ComponentActivity
    val context = LocalContext.current
    val model = remember(activity) { ViewModelProvider(activity)[AppUpdateViewModel::class.java] }
    val state by model.state.collectAsState()
    var pendingInstall by rememberSaveable { mutableStateOf<String?>(null) }

    fun install(uri: Uri) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            model.dismissDownloadedApk()
        } catch (exception: Exception) {
            model.showError(exception)
        }
    }

    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val uri = pendingInstall
        pendingInstall = null
        if (uri != null && context.packageManager.canRequestPackageInstalls()) install(Uri.parse(uri))
    }

    val message = state.message?.let { appStringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            model.dismissMessage()
        }
    }

    state.release?.takeIf { state.error == null }?.let { release ->
        OmniConfirmDialog(
            title = appStringResource(R.string.update_available_title),
            text = appStringResource(R.string.update_available_text, release.version),
            confirmText = appStringResource(R.string.action_download),
            onConfirm = model::download,
            onDismiss = model::dismissRelease,
        )
    }
    if (state.downloading && state.showDownloadProgress && state.error == null) {
        UpdateDownloadDialog(state.downloadProgress, model::dismissDownloadProgress)
    }
    state.downloadedApk?.takeIf { state.error == null }?.let { uri ->
        OmniConfirmDialog(
            title = appStringResource(R.string.update_downloaded_title),
            text = appStringResource(R.string.update_downloaded_text),
            confirmText = appStringResource(R.string.action_open),
            onConfirm = {
                if (context.packageManager.canRequestPackageInstalls()) {
                    install(uri)
                } else {
                    pendingInstall = uri.toString()
                    try {
                        installPermission.launch(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    } catch (exception: Exception) {
                        pendingInstall = null
                        model.showError(exception)
                    }
                }
            },
            onDismiss = model::dismissDownloadedApk,
        )
    }
    state.error?.let { ErrorDialog(message = it, onDismiss = model::dismissError) }
    val statusText = when {
        state.checking -> appStringResource(R.string.update_checking)
        state.downloading -> appStringResource(R.string.update_downloading)
        else -> null
    }
    return AppUpdateActions(statusText = statusText, onCheck = { model.check() })
}

@Composable
private fun UpdateDownloadDialog(progress: Float?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appStringResource(R.string.update_downloading_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RadioSpacing.medium)) {
                Text(appStringResource(R.string.update_downloading))
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(appStringResource(R.string.update_download_progress, (progress * 100).toInt()))
                }
            }
        },
        confirmButton = {
            OmniSecondaryButton(text = appStringResource(R.string.action_hide), onClick = onDismiss)
        },
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
    )
}
