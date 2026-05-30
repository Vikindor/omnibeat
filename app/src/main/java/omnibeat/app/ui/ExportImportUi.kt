package omnibeat.app.ui

import omnibeat.app.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExportImportPage(
    stationCount: Int,
    favoriteCount: Int,
    onExportStations: () -> Unit,
    onExportSimpleText: () -> Unit,
    onImportStations: () -> Unit,
    onDeleteLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDeleteLibrary by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 14.dp, bottom = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.export_import_library_title),
                    color = RadioText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.export_import_library_summary, stationCount, favoriteCount),
                    color = RadioTextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            IconButton(onClick = { confirmDeleteLibrary = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.settings_delete_entire_library_content_description),
                    tint = RadioDanger,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        )

        FormatDescription(
            title = stringResource(R.string.export_import_json_title),
            text = stringResource(R.string.export_import_json_description),
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_download,
            title = stringResource(R.string.export_import_json_export_title),
            subtitle = stringResource(R.string.export_import_json_export_subtitle),
            onClick = onExportStations,
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_upload,
            title = stringResource(R.string.export_import_json_import_title),
            subtitle = stringResource(R.string.export_import_json_import_subtitle),
            onClick = onImportStations,
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        FormatDescription(
            title = stringResource(R.string.export_import_txt_title),
            text = stringResource(R.string.export_import_txt_description),
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_download,
            title = stringResource(R.string.export_import_txt_export_title),
            subtitle = stringResource(R.string.export_import_txt_export_subtitle),
            onClick = onExportSimpleText,
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_upload,
            title = stringResource(R.string.export_import_txt_import_title),
            subtitle = stringResource(R.string.export_import_txt_import_subtitle),
            onClick = onImportStations,
        )
    }

    if (confirmDeleteLibrary) {
        OmniConfirmDialog(
            title = stringResource(R.string.dialog_delete_entire_library_title),
            text = stringResource(R.string.dialog_delete_entire_library_text),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onDismiss = { confirmDeleteLibrary = false },
            onConfirm = {
                confirmDeleteLibrary = false
                onDeleteLibrary()
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
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            color = RadioText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = text,
            color = RadioTextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ExportImportActionRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = RadioText,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = RadioText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = RadioTextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
