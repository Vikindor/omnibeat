package omnibeat.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsPage(
    showStationArtwork: Boolean,
    syncingStationArtwork: Boolean,
    onShowStationArtworkChange: (Boolean) -> Unit,
    onSyncStationArtwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 20.dp),
    ) {
        SettingsSwitchRow(
            title = "Show station artwork",
            checked = showStationArtwork,
            onCheckedChange = onShowStationArtworkChange,
        )
        SettingsActionRow(
            title = "Sync station artwork",
            buttonText = if (syncingStationArtwork) "Syncing..." else "Sync",
            enabled = !syncingStationArtwork,
            onClick = onSyncStationArtwork,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = RadioText,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RadioText,
                checkedTrackColor = RadioPrimary,
                uncheckedThumbColor = RadioTextMuted,
                uncheckedTrackColor = RadioSurfaceHigh,
                uncheckedBorderColor = RadioOutline,
            ),
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = RadioText,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f),
        )
        OmniPrimaryButton(
            text = buttonText,
            onClick = onClick,
            enabled = enabled,
        )
    }
}
