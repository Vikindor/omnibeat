package omnibeat.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import omnibeat.app.R
import omnibeat.app.data.StationRepository
import omnibeat.app.data.TranslationCodec
import omnibeat.app.data.TranslationLanguage
import omnibeat.app.data.appString

@Composable
fun TranslationPage(repository: StationRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val hasTranslation = TranslationLanguage.imported != null

    fun perform(success: Int, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                operation()
                Toast.makeText(context, resources.appString(success), Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = resources.appString(R.string.translation_failed, error.message.orEmpty())
            } finally {
                busy = false
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) perform(R.string.translation_exported) {
            withContext(Dispatchers.IO) {
                val text = TranslationCodec.export(resources)
                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Cannot open file" }
                output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) perform(R.string.translation_imported) {
            val translation = withContext(Dispatchers.IO) {
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open file" }
                TranslationCodec.decode(input.use(TranslationCodec::read), resources)
            }
            TranslationLanguage.install(context.applicationContext, repository, translation)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 20.dp),
        ) {
            Text(
                text = appStringResource(R.string.translation_description),
                color = RadioTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            HorizontalDivider(
                color = RadioOutline.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_export,
                title = appStringResource(R.string.translation_export),
                subtitle = appStringResource(R.string.translation_export_description),
                enabled = !busy,
                onClick = {
                    val tag = TranslationLanguage.activeTranslation(resources)?.languageTag
                        ?: resources.configuration.locales[0].toLanguageTag()
                    exportLauncher.launch("omnibeat-strings-$tag.txt")
                },
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_import,
                title = appStringResource(R.string.translation_import),
                subtitle = appStringResource(R.string.translation_import_description),
                enabled = !busy,
                onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
            )
            HorizontalDivider(
                color = RadioOutline.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ExportImportActionRow(
                icon = R.drawable.ic_delete,
                title = appStringResource(R.string.translation_reset),
                subtitle = appStringResource(if (hasTranslation) R.string.translation_active else R.string.translation_inactive),
                enabled = !busy && hasTranslation,
                onClick = {
                    perform(R.string.translation_reset_done) {
                        TranslationLanguage.reset(context.applicationContext, repository)
                    }
                },
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
        )
    }
    errorMessage?.let { ErrorDialog(message = it, onDismiss = { errorMessage = null }) }
}
