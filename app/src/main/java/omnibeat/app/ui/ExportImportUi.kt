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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onImportStations: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 20.dp),
    ) {
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

        HorizontalDivider(
            color = RadioOutline.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        )

        ExportImportActionRow(
            icon = R.drawable.ic_file_download,
            title = "Export stations",
            subtitle = "Save an OmniBeat JSON backup",
            onClick = onExportStations,
        )
        ExportImportActionRow(
            icon = R.drawable.ic_file_upload,
            title = "Import stations",
            subtitle = "Merge or replace from an OmniBeat JSON backup",
            onClick = onImportStations,
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
