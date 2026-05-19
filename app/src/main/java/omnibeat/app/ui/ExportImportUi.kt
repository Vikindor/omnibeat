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
import androidx.compose.material3.AlertDialog
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
                    text = "Library",
                    color = RadioText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "$stationCount stations, $favoriteCount favorites",
                    color = RadioTextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            IconButton(onClick = { confirmDeleteLibrary = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_outline),
                    contentDescription = "Delete entire library",
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
            title = "OmniBeat JSON",
            text = "Complete OmniBeat backup with stations, favorites, sorting, and custom order.",
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_download,
            title = "Export OmniBeat JSON",
            subtitle = "Save an OmniBeat JSON backup",
            onClick = onExportStations,
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_upload,
            title = "Import OmniBeat JSON",
            subtitle = "Merge or replace from an OmniBeat JSON backup",
            onClick = onImportStations,
        )

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )

        FormatDescription(
            title = "Simple TXT",
            text = "Plain-text format for manual editing. Write each station as separate lines: title, stream URL, and optional comma-separated tags on the third line. Separate stations with an empty line. Export TXT to get a sample file.",
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_download,
            title = "Export simple TXT",
            subtitle = "Save title, stream URL and tags",
            onClick = onExportSimpleText,
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_upload,
            title = "Import simple TXT",
            subtitle = "Read blocks with title, URL and optional tags",
            onClick = onImportStations,
        )
    }

    if (confirmDeleteLibrary) {
        AlertDialog(
            onDismissRequest = { confirmDeleteLibrary = false },
            title = { Text("Delete entire library?") },
            text = { Text("All stations, favorites, and custom order will be removed") },
            confirmButton = {
                OmniDangerButton(
                    text = "Delete",
                    onClick = {
                        confirmDeleteLibrary = false
                        onDeleteLibrary()
                    },
                )
            },
            dismissButton = {
                OmniSecondaryButton(text = "Cancel", onClick = { confirmDeleteLibrary = false })
            },
            containerColor = RadioSurface,
            titleContentColor = RadioText,
            textContentColor = RadioTextMuted,
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
