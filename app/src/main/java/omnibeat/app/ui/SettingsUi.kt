package omnibeat.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import omnibeat.app.R
import omnibeat.app.data.STOP_SERVICE_AFTER_PAUSE_NEVER
import omnibeat.app.model.AppLanguage
import omnibeat.app.model.ThemeMode

@Composable
fun ThemeModeSegmentedControl(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
    val selectedIndex = options.indexOf(themeMode).coerceAtLeast(0)
    val segmentWidth = 52.dp
    val controlHeight = 32.dp
    val selectedOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = tween(durationMillis = 220),
        label = "theme selector",
    )

    Box(
        modifier = modifier
            .width(segmentWidth * options.size)
            .height(controlHeight)
            .background(RadioSurfaceHigh, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .offset(x = selectedOffset)
                .width(segmentWidth)
                .height(controlHeight)
                .background(RadioPrimary, CircleShape),
        )
        Row(modifier = Modifier.matchParentSize()) {
            options.forEach { option ->
                val selected = themeMode == option
                val interactionSource = remember(option) { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(controlHeight)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onThemeModeChange(option) },
                        ),
                ) {
                    Icon(
                        painter = painterResource(themeModeIcon(option)),
                        contentDescription = null,
                        tint = if (selected) RadioText else RadioTextMuted,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

private fun themeModeIcon(themeMode: ThemeMode): Int {
    return when (themeMode) {
        ThemeMode.System -> R.drawable.ic_theme_system
        ThemeMode.Light -> R.drawable.ic_theme_light
        ThemeMode.Dark -> R.drawable.ic_theme_dark
    }
}

@Composable
private fun appLanguageLabel(appLanguage: AppLanguage): String {
    return when (appLanguage) {
        AppLanguage.System -> stringResource(R.string.language_system)
        else -> appLanguage.displayName.orEmpty()
    }
}

@Composable
private fun pauseTimeoutLabel(minutes: Int): String {
    return if (minutes == STOP_SERVICE_AFTER_PAUSE_NEVER) {
        stringResource(R.string.settings_stop_service_after_pause_never)
    } else {
        stringResource(R.string.settings_stop_service_after_pause_minutes, minutes)
    }
}

@Composable
fun SettingsPage(
    themeMode: ThemeMode,
    appLanguage: AppLanguage,
    showStationArtwork: Boolean,
    addRadioBrowserTags: Boolean,
    removeTrackingParameters: Boolean,
    rememberLastStation: Boolean,
    showBitrateInControlPanel: Boolean,
    showUnavailableBitrate: Boolean,
    marqueeTrackTitle: Boolean,
    stopServiceAfterPauseMinutes: Int,
    autoExpandPlayerPanelOnPlayback: Boolean,
    collapsePlayerPanelInSearch: Boolean,
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
    onStopServiceAfterPauseMinutesChange: (Int) -> Unit,
    onAutoExpandPlayerPanelOnPlaybackChange: (Boolean) -> Unit,
    onCollapsePlayerPanelInSearchChange: (Boolean) -> Unit,
    onShowEmptyFavoritesTabChange: (Boolean) -> Unit,
    onConfirmStationDeletionChange: (Boolean) -> Unit,
    onSyncStationArtwork: () -> Unit,
    onClearLibrary: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAppLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var confirmClearLibrary by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp)
                .padding(bottom = 20.dp),
        ) {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance))
            SettingsThemeRow(
                title = stringResource(R.string.settings_theme_title),
                subtitle = when (themeMode) {
                    ThemeMode.System -> stringResource(R.string.settings_theme_system)
                    ThemeMode.Dark -> stringResource(R.string.settings_theme_dark)
                    ThemeMode.Light -> stringResource(R.string.settings_theme_light)
                },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
            SettingsLanguageRow(
                title = stringResource(R.string.settings_language_title),
                subtitle = when (appLanguage) {
                    AppLanguage.System -> stringResource(R.string.settings_language_system)
                    else -> stringResource(R.string.settings_language_selected)
                },
                appLanguage = appLanguage,
                onAppLanguageChange = onAppLanguageChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_playback))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_remember_last_station_title),
                subtitle = if (rememberLastStation) {
                    stringResource(R.string.settings_remember_last_station_on)
                } else {
                    stringResource(R.string.settings_remember_last_station_off)
                },
                checked = rememberLastStation,
                onCheckedChange = onRememberLastStationChange,
            )
            SettingsPauseTimeoutRow(
                title = stringResource(R.string.settings_stop_service_after_pause_title),
                subtitle = if (stopServiceAfterPauseMinutes == STOP_SERVICE_AFTER_PAUSE_NEVER) {
                    stringResource(R.string.settings_stop_service_after_pause_never_subtitle)
                } else {
                    stringResource(R.string.settings_stop_service_after_pause_subtitle, stopServiceAfterPauseMinutes)
                },
                selectedMinutes = stopServiceAfterPauseMinutes,
                onSelectedMinutesChange = onStopServiceAfterPauseMinutesChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_artwork))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_station_covers_title),
                subtitle = if (showStationArtwork) {
                    stringResource(R.string.settings_station_covers_on)
                } else {
                    stringResource(R.string.settings_station_covers_off)
                },
                checked = showStationArtwork,
                onCheckedChange = onShowStationArtworkChange,
            )
            SettingsActionRow(
                title = stringResource(R.string.settings_sync_artwork_title),
                subtitle = stringResource(R.string.settings_sync_artwork_subtitle),
                syncing = syncingStationArtwork,
                onClick = onSyncStationArtwork,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_library))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_empty_favorites_title),
                subtitle = if (showEmptyFavoritesTab) {
                    stringResource(R.string.settings_show_empty_favorites_on)
                } else {
                    stringResource(R.string.settings_show_empty_favorites_off)
                },
                checked = showEmptyFavoritesTab,
                onCheckedChange = onShowEmptyFavoritesTabChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_confirm_station_deletion_title),
                subtitle = if (confirmStationDeletion) {
                    stringResource(R.string.settings_confirm_station_deletion_on)
                } else {
                    stringResource(R.string.settings_confirm_station_deletion_off)
                },
                checked = confirmStationDeletion,
                onCheckedChange = onConfirmStationDeletionChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_control_panel))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_bitrate_title),
                subtitle = if (showBitrateInControlPanel) {
                    stringResource(R.string.settings_show_bitrate_on)
                } else {
                    stringResource(R.string.settings_show_bitrate_off)
                },
                checked = showBitrateInControlPanel,
                onCheckedChange = onShowBitrateInControlPanelChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_show_unavailable_bitrate_title),
                subtitle = if (showUnavailableBitrate) {
                    stringResource(R.string.settings_show_unavailable_bitrate_on)
                } else {
                    stringResource(R.string.settings_show_unavailable_bitrate_off)
                },
                checked = showUnavailableBitrate,
                onCheckedChange = onShowUnavailableBitrateChange,
                enabled = showBitrateInControlPanel,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_marquee_track_title_title),
                subtitle = if (marqueeTrackTitle) {
                    stringResource(R.string.settings_marquee_track_title_on)
                } else {
                    stringResource(R.string.settings_marquee_track_title_off)
                },
                checked = marqueeTrackTitle,
                onCheckedChange = onMarqueeTrackTitleChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_auto_expand_player_panel_title),
                subtitle = if (autoExpandPlayerPanelOnPlayback) {
                    stringResource(R.string.settings_auto_expand_player_panel_on)
                } else {
                    stringResource(R.string.settings_auto_expand_player_panel_off)
                },
                checked = autoExpandPlayerPanelOnPlayback,
                onCheckedChange = onAutoExpandPlayerPanelOnPlaybackChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_collapse_player_panel_search_title),
                subtitle = if (collapsePlayerPanelInSearch) {
                    stringResource(R.string.settings_collapse_player_panel_search_on)
                } else {
                    stringResource(R.string.settings_collapse_player_panel_search_off)
                },
                checked = collapsePlayerPanelInSearch,
                onCheckedChange = onCollapsePlayerPanelInSearchChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_online_search))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_save_radio_browser_tags_title),
                subtitle = if (addRadioBrowserTags) {
                    stringResource(R.string.settings_save_radio_browser_tags_on)
                } else {
                    stringResource(R.string.settings_save_radio_browser_tags_off)
                },
                checked = addRadioBrowserTags,
                onCheckedChange = onAddRadioBrowserTagsChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_urls))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_remove_tracking_title),
                subtitle = if (removeTrackingParameters) {
                    stringResource(R.string.settings_remove_tracking_on)
                } else {
                    stringResource(R.string.settings_remove_tracking_off)
                },
                checked = removeTrackingParameters,
                onCheckedChange = onRemoveTrackingParametersChange,
            )
            SettingsDivider()

            SettingsSectionHeader(title = stringResource(R.string.settings_section_danger_zone), color = RadioDanger)
            SettingsDangerRow(
                title = stringResource(R.string.settings_clear_library_title),
                onClick = { confirmClearLibrary = true },
            )
        }
        OmniScrollIndicator(
            scrollIndicatorState = scrollState.scrollIndicatorState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
            .padding(end = 4.dp),
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
private fun SettingsLanguageRow(
    title: String,
    subtitle: String,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = appLanguageLabel(appLanguage),
                    color = RadioPrimary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = RadioSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                AppLanguage.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = appLanguageLabel(option),
                                color = RadioText,
                            )
                        },
                        onClick = {
                            expanded = false
                            onAppLanguageChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPauseTimeoutRow(
    title: String,
    subtitle: String,
    selectedMinutes: Int,
    onSelectedMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(STOP_SERVICE_AFTER_PAUSE_NEVER, 1, 5, 10, 30)
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
            TextButton(onClick = { expanded = true }) {
                Text(
                    text = pauseTimeoutLabel(selectedMinutes),
                    color = RadioPrimary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = RadioSurface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                options.forEach { minutes ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = pauseTimeoutLabel(minutes),
                                color = RadioText,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelectedMinutesChange(minutes)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    Text(
        text = title,
        color = color ?: RadioTextMuted,
        fontSize = 14.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = RadioOutline.copy(alpha = 0.65f),
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsThemeRow(
    title: String,
    subtitle: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
        ThemeModeSegmentedControl(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
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
            .padding(vertical = 10.dp),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
        }
        OmniIconButton(
            painter = painterResource(R.drawable.ic_delete),
            onClick = onClick,
            tint = RadioDanger,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    syncing: Boolean = false,
    enabled: Boolean = true,
    actionText: String? = null,
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
            .padding(vertical = 10.dp),
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
        if (actionText == null) {
            OmniIconButton(
                painter = painterResource(R.drawable.ic_sync),
                onClick = onClick,
                enabled = enabled && !syncing,
                tint = if (syncing || !enabled) RadioTextMuted else RadioPrimary,
                iconModifier = Modifier.rotate(rotation),
            )
        } else {
            TextButton(
                onClick = onClick,
                enabled = enabled,
            ) {
                Text(
                    text = actionText,
                    color = if (enabled) RadioPrimary else RadioTextMuted,
                )
            }
        }
    }
}
