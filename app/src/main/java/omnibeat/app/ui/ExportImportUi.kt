package omnibeat.app.ui

import omnibeat.app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import omnibeat.app.ui.appStringResource as stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExportImportPage(
    stationCount: Int,
    favoriteCount: Int,
    onExportStations: () -> Unit,
    onExportSimpleText: () -> Unit,
    onImportStations: () -> Unit,
    onClearLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClearLibrary by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = RadioSpacing.page)
                .padding(top = RadioSpacing.rowVertical, bottom = RadioSpacing.page),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RadioSpacing.large),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.export_import_library_title),
                        color = RadioText,
                        fontSize = RadioTextSizes.headline,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.export_import_library_summary, stationCount, favoriteCount),
                        color = RadioTextMuted,
                        fontSize = RadioTextSizes.bodySmall,
                        modifier = Modifier.padding(top = RadioSpacing.compact),
                    )
                }
                OmniIconButton(
                    painter = painterResource(R.drawable.ic_delete),
                    onClick = { confirmClearLibrary = true },
                    tint = RadioDanger,
                )
            }

            HorizontalDivider(
                color = RadioOutline.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = RadioSpacing.section, bottom = RadioSpacing.small),
            )

            FormatDescription(
                title = stringResource(R.string.export_import_json_title),
                text = stringResource(R.string.export_import_json_description),
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_export,
                title = stringResource(R.string.export_import_json_export_title),
                subtitle = stringResource(R.string.export_import_json_export_subtitle),
                onClick = onExportStations,
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_import,
                title = stringResource(R.string.export_import_json_import_title),
                subtitle = stringResource(R.string.export_import_json_import_subtitle),
                onClick = onImportStations,
            )

            HorizontalDivider(
                color = RadioOutline.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = RadioSpacing.small, bottom = RadioSpacing.small),
            )

            FormatDescription(
                title = stringResource(R.string.export_import_txt_title),
                text = stringResource(R.string.export_import_txt_description),
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_export,
                title = stringResource(R.string.export_import_txt_export_title),
                subtitle = stringResource(R.string.export_import_txt_export_subtitle),
                onClick = onExportSimpleText,
            )
            ExportImportActionRow(
                icon = R.drawable.ic_file_import,
                title = stringResource(R.string.export_import_txt_import_title),
                subtitle = stringResource(R.string.export_import_txt_import_subtitle),
                onClick = onImportStations,
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = RadioSpacing.extraSmall),
        )
    }

    if (confirmClearLibrary) {
        OmniConfirmDialog(
            title = stringResource(R.string.dialog_clear_library_title),
            text = stringResource(R.string.dialog_clear_library_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { confirmClearLibrary = false },
            onConfirm = {
                confirmClearLibrary = false
                onClearLibrary()
            },
        )
    }
}

@Composable
private fun FormatDescription(
    title: String,
    text: String,
) {
    Column(
        modifier = Modifier.padding(top = RadioSpacing.fieldGap, bottom = RadioSpacing.compact),
    ) {
        Text(
            text = title,
            color = RadioText,
            fontSize = RadioTextSizes.headline,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            color = RadioTextMuted,
            fontSize = RadioTextSizes.caption,
            lineHeight = RadioTextSizes.compactLineHeight,
            modifier = Modifier.padding(top = RadioSpacing.extraSmall),
        )
    }
}

@Composable
internal fun ExportImportActionRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RadioSpacing.large),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = RadioSpacing.rowVertical),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (enabled) RadioText else RadioTextMuted,
            modifier = Modifier.size(RadioSizes.iconLarge),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) RadioText else RadioTextMuted,
                fontSize = RadioTextSizes.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = RadioTextMuted,
                fontSize = RadioTextSizes.caption,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
