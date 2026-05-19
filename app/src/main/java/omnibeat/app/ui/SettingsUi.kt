package omnibeat.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnibeat.app.R
import omnibeat.app.model.ThemeMode

@Composable
fun SettingsPage(
    themeMode: ThemeMode,
    showStationArtwork: Boolean,
    addRadioBrowserTags: Boolean,
    removeTrackingParameters: Boolean,
    rememberLastStation: Boolean,
    showBitrateInControlPanel: Boolean,
    showUnavailableBitrate: Boolean,
    marqueeTrackTitle: Boolean,
    showEmptyFavoritesTab: Boolean,
    confirmStationDeletion: Boolean,
    syncingStationArtwork: Boolean,
    onShowStationArtworkChange: (Boolean) -> Unit,
    onAddRadioBrowserTagsChange: (Boolean) -> Unit,
    onRemoveTrackingParametersChange: (Boolean) -> Unit,
    onRememberLastStationChange: (Boolean) -> Unit,
    onShowBitrateInControlPanelChange: (Boolean) -> Unit,
    onShowUnavailableBitrateChange: (Boolean) -> Unit,
    onMarqueeTrackTitleChange: (Boolean) -> Unit,
    onShowEmptyFavoritesTabChange: (Boolean) -> Unit,
    onConfirmStationDeletionChange: (Boolean) -> Unit,
    onSyncStationArtwork: () -> Unit,
    onDeleteLibrary: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var confirmDeleteLibrary by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 20.dp),
        ) {
            SettingsSectionHeader(title = "Appearance")
            SettingsOptionRow(
                title = "Theme",
                subtitle = when (themeMode) {
                    ThemeMode.System -> "Follow system theme"
                    ThemeMode.Dark -> "Use dark theme"
                    ThemeMode.Light -> "Use light theme"
                },
                value = themeMode.label,
                options = ThemeMode.entries,
                optionText = { it.label },
                onOptionSelected = onThemeModeChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Artwork")
            SettingsSwitchRow(
                title = "Covers for stations",
                subtitle = if (showStationArtwork) {
                    "Station lists will show covers"
                } else {
                    "Station lists will hide covers"
                },
                checked = showStationArtwork,
                onCheckedChange = onShowStationArtworkChange,
            )
            SettingsActionRow(
                title = "Fetch artwork for stations",
                subtitle = "Look up covers for all stations online",
                syncing = syncingStationArtwork,
                onClick = onSyncStationArtwork,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Playback")
            SettingsSwitchRow(
                title = "Remember last station",
                subtitle = if (rememberLastStation) {
                    "Play will start the last played station"
                } else {
                    "Play will start the first station in the list"
                },
                checked = rememberLastStation,
                onCheckedChange = onRememberLastStationChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Library")
            SettingsSwitchRow(
                title = "Show empty Favorites tab",
                subtitle = if (showEmptyFavoritesTab) {
                    "Favorites tab will stay visible when empty"
                } else {
                    "Favorites tab will be hidden when empty"
                },
                checked = showEmptyFavoritesTab,
                onCheckedChange = onShowEmptyFavoritesTabChange,
            )
            SettingsSwitchRow(
                title = "Confirm station deletion",
                subtitle = if (confirmStationDeletion) {
                    "Deleting a station will ask for confirmation"
                } else {
                    "Stations will be deleted immediately"
                },
                checked = confirmStationDeletion,
                onCheckedChange = onConfirmStationDeletionChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Control panel")
            SettingsSwitchRow(
                title = "Show bitrate in control panel",
                subtitle = if (showBitrateInControlPanel) {
                    "Control panel will show stream bitrate"
                } else {
                    "Control panel will hide stream bitrate"
                },
                checked = showBitrateInControlPanel,
                onCheckedChange = onShowBitrateInControlPanelChange,
            )
            SettingsSwitchRow(
                title = "Show bitrate when unavailable",
                subtitle = if (showUnavailableBitrate) {
                    "Control panel will show N/A when bitrate is missing"
                } else {
                    "Control panel will hide missing bitrate"
                },
                checked = showUnavailableBitrate,
                onCheckedChange = onShowUnavailableBitrateChange,
                enabled = showBitrateInControlPanel,
            )
            SettingsSwitchRow(
                title = "Marquee track title",
                subtitle = if (marqueeTrackTitle) {
                    "Long track titles will scroll"
                } else {
                    "Long track titles will be truncated"
                },
                checked = marqueeTrackTitle,
                onCheckedChange = onMarqueeTrackTitleChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Online search")
            SettingsSwitchRow(
                title = "Save Radio Browser tags",
                subtitle = if (addRadioBrowserTags) {
                    "Online search tags will be saved with new stations"
                } else {
                    "Online search tags will be ignored"
                },
                checked = addRadioBrowserTags,
                onCheckedChange = onAddRadioBrowserTagsChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "URLs")
            SettingsSwitchRow(
                title = "Remove tracking from URLs",
                subtitle = if (removeTrackingParameters) {
                    "Tracking parameters will be cleaned for new stations"
                } else {
                    "New station URLs will be saved unchanged"
                },
                checked = removeTrackingParameters,
                onCheckedChange = onRemoveTrackingParametersChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = "Danger zone", color = RadioDanger)
            SettingsDangerRow(
                title = "Delete entire library",
                subtitle = "Remove all stations, favorites, and custom order",
                onClick = { confirmDeleteLibrary = true },
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
            .padding(end = 4.dp),
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
private fun SettingsSectionHeader(
    title: String,
    color: androidx.compose.ui.graphics.Color? = null,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        color = color ?: RadioTextMuted,
        fontSize = 14.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = RadioOutline.copy(alpha = 0.65f),
        modifier = modifier.padding(top = 14.dp),
    )
}

@Composable
private fun <T> SettingsOptionRow(
    title: String,
    subtitle: String,
    value: String,
    options: List<T>,
    optionText: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = RadioText,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = RadioTextMuted,
                fontSize = 14.sp,
            )
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = true },
            ) {
                Text(
                    text = value,
                    color = RadioText,
                    fontSize = 15.sp,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Choose $title",
                    tint = RadioTextMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = RadioSurface,
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = optionText(option),
                                color = RadioText,
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) RadioText else RadioTextMuted.copy(alpha = 0.55f)
    val subtitleColor = if (enabled) RadioTextMuted else RadioTextMuted.copy(alpha = 0.45f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = subtitleColor,
                fontSize = 14.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RadioText,
                checkedTrackColor = RadioPrimary,
                uncheckedThumbColor = RadioTextMuted,
                uncheckedTrackColor = RadioSurfaceHigh,
                uncheckedBorderColor = RadioOutline,
                disabledCheckedThumbColor = RadioTextMuted.copy(alpha = 0.45f),
                disabledCheckedTrackColor = RadioSurfaceHigh.copy(alpha = 0.45f),
                disabledUncheckedThumbColor = RadioTextMuted.copy(alpha = 0.35f),
                disabledUncheckedTrackColor = RadioSurfaceHigh.copy(alpha = 0.35f),
                disabledUncheckedBorderColor = RadioOutline.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun SettingsDangerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = RadioDanger,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = RadioTextMuted,
                fontSize = 14.sp,
            )
        }
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_outline),
                contentDescription = "Delete entire library",
                tint = RadioDanger,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    syncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "artwork sync")
    val rotation = if (syncing) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Restart,
            ),
            label = "sync rotation",
        ).value
    } else {
        0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = RadioText,
                fontSize = 16.sp,
            )
            Text(
                text = subtitle,
                color = RadioTextMuted,
                fontSize = 14.sp,
            )
        }
        IconButton(
            onClick = onClick,
            enabled = !syncing,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sync),
                contentDescription = "Sync station artwork",
                tint = if (syncing) RadioTextMuted else RadioPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation),
            )
        }
    }
}
