package omnibeat.app.ui

import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import omnibeat.app.R
import omnibeat.app.model.SleepTimerPlacement
import omnibeat.app.ui.appStringResource as stringResource
import java.util.Locale

private val SleepTimerPickerHeight = 180.dp

@Composable
fun SleepTimerPage(
    remainingSeconds: Long,
    closeApp: Boolean,
    onCloseAppChange: (Boolean) -> Unit,
    placement: SleepTimerPlacement,
    onPlacementChange: (SleepTimerPlacement) -> Unit,
    onStart: (Long) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var durationSeconds by rememberSaveable { mutableIntStateOf(0) }
    val active = remainingSeconds > 0L
    val displayedSeconds = if (active) remainingSeconds.toInt() else durationSeconds
    val pickerCenter = with(LocalDensity.current) { (SleepTimerPickerHeight / 2).roundToPx() }

    Surface(color = RadioBackground, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RadioSpacing.large),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(RadioSpacing.page),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SleepTimerNumberField(
                    value = displayedSeconds / 3600,
                    editable = !active,
                    maximum = 23,
                    label = stringResource(R.string.sleep_timer_hours),
                    onValueChange = { durationSeconds = it * 3600 + durationSeconds % 3600 },
                    modifier = Modifier.weight(1f).alignBy { pickerCenter },
                )
                Text(
                    text = ":",
                    color = RadioText,
                    fontSize = RadioTextSizes.appName,
                    modifier = Modifier.alignBy { it.measuredHeight / 2 },
                )
                SleepTimerNumberField(
                    value = displayedSeconds / 60 % 60,
                    editable = !active,
                    maximum = 59,
                    label = stringResource(R.string.sleep_timer_minutes),
                    onValueChange = { durationSeconds = durationSeconds / 3600 * 3600 + it * 60 + durationSeconds % 60 },
                    modifier = Modifier.weight(1f).alignBy { pickerCenter },
                )
                Text(
                    text = ":",
                    color = RadioText,
                    fontSize = RadioTextSizes.appName,
                    modifier = Modifier.alignBy { it.measuredHeight / 2 },
                )
                SleepTimerNumberField(
                    value = displayedSeconds % 60,
                    editable = !active,
                    maximum = 59,
                    label = stringResource(R.string.sleep_timer_seconds),
                    onValueChange = { durationSeconds = durationSeconds / 60 * 60 + it },
                    modifier = Modifier.weight(1f).alignBy { pickerCenter },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(RadioSpacing.small, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(RadioSpacing.small),
            ) {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    OmniPrimaryButton(
                        text = stringResource(R.string.sleep_timer_preset_minutes, minutes),
                        enabled = !active,
                        onClick = { durationSeconds = minutes * 60 },
                    )
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.sleep_timer_close_app_title),
                subtitle = stringResource(
                    if (closeApp) R.string.sleep_timer_close_app_on else R.string.sleep_timer_close_app_off,
                ),
                checked = closeApp,
                onCheckedChange = onCloseAppChange,
            )
            SettingsDropdownRow(
                title = stringResource(R.string.sleep_timer_position_title),
                subtitle = stringResource(
                    when (placement) {
                        SleepTimerPlacement.PanelLeft -> R.string.sleep_timer_position_panel_left_description
                        SleepTimerPlacement.PanelRight -> R.string.sleep_timer_position_panel_right_description
                        SleepTimerPlacement.FreePlacement -> R.string.sleep_timer_position_free_description
                    },
                ),
                selectedOption = placement,
                options = SleepTimerPlacement.entries,
                optionLabel = {
                    stringResource(
                        when (it) {
                            SleepTimerPlacement.PanelLeft -> R.string.sleep_timer_position_panel_left
                            SleepTimerPlacement.PanelRight -> R.string.sleep_timer_position_panel_right
                            SleepTimerPlacement.FreePlacement -> R.string.sleep_timer_position_free
                        },
                    )
                },
                onOptionSelected = onPlacementChange,
            )
            OmniPrimaryButton(
                text = stringResource(if (active) R.string.sleep_timer_stop else R.string.sleep_timer_start),
                icon = painterResource(if (active) R.drawable.ic_stop else R.drawable.ic_play_arrow),
                enabled = active || durationSeconds > 0,
                onClick = { if (active) onStop() else onStart(durationSeconds.toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SleepTimerNumberField(
    value: Int,
    editable: Boolean,
    maximum: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val textColor = RadioText.toArgb()
    val textSize = with(LocalDensity.current) { RadioTextSizes.appName.toPx() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (editable) {
            AndroidView(
                factory = { context ->
                    NumberPicker(context).apply {
                        minValue = 0
                        maxValue = maximum
                        wrapSelectorWheel = true
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setFormatter { String.format(locale, "%02d", it) }
                    }
                },
                update = { picker ->
                    picker.setOnValueChangedListener { _, _, selected -> onValueChange(selected) }
                    if (picker.value != value) picker.value = value
                    picker.contentDescription = label
                    picker.textColor = textColor
                    picker.textSize = textSize
                },
                modifier = Modifier.fillMaxWidth().height(SleepTimerPickerHeight),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().height(SleepTimerPickerHeight),
            ) {
                Text(
                    text = String.format(locale, "%02d", value),
                    color = RadioText,
                    fontSize = RadioTextSizes.appName,
                )
            }
        }
        Text(text = label, color = RadioTextMuted, fontSize = RadioTextSizes.bodySmall)
    }
}

@Composable
fun SleepTimerPill(
    remainingSeconds: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.page_sleep_timer)
    OmniPrimaryButton(
        text = formatSleepTimer(remainingSeconds),
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = description },
    )
}

private fun formatSleepTimer(seconds: Long): String = String.format(
    Locale.getDefault(),
    "%02d:%02d:%02d",
    seconds / 3600,
    seconds / 60 % 60,
    seconds % 60,
)
