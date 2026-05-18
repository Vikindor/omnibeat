package omnibeat.app.ui

import omnibeat.app.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import omnibeat.app.model.Station
import omnibeat.app.playback.PlaybackStreamInfo
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerPanel(
    station: Station?,
    trackText: String,
    errorText: String?,
    streamInfo: PlaybackStreamInfo,
    loading: Boolean,
    resolving: Boolean,
    isPlaying: Boolean,
    canNavigateStations: Boolean,
    appVolume: Float,
    canPlay: Boolean,
    onPlayStop: () -> Unit,
    onPreviousStation: () -> Unit,
    onNextStation: () -> Unit,
    onRandomStation: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    var showStreamInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val displayTrackText = errorText ?: trackText
    val bitrateText = streamInfo.bitrateText

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioSurface)
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DividerDefaults.Thickness)
                .background(RadioOutline),
        )
        Column(
            modifier = Modifier
                .combinedClickable(
                    enabled = station != null,
                    onClick = {
                        copyTextToClipboard(context, label = "Track", text = trackText)
                        android.widget.Toast.makeText(context, "Track name copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    onLongClick = { showStreamInfo = true },
                )
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station?.title ?: "Choose a station",
                    color = RadioText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                        color = RadioPrimary,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            ) {
                Text(
                    text = displayTrackText,
                    color = RadioTextMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1_500,
                        ),
                )
                bitrateText?.let { bitrate ->
                    Text(
                        text = bitrate,
                        color = RadioTextMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            PlayerIconButton(
                enabled = canNavigateStations && !resolving,
                onClick = onRandomStation,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shuffle),
                    contentDescription = "Random station",
                    modifier = Modifier.size(24.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.align(Alignment.Center),
            ) {
                PlayerIconButton(
                    enabled = canNavigateStations && !resolving,
                    onClick = onPreviousStation,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_previous),
                        contentDescription = "Previous station",
                        modifier = Modifier.size(24.dp),
                    )
                }
                PlayerIconButton(
                    enabled = canPlay && !resolving,
                    onClick = onPlayStop,
                    modifier = Modifier.size(62.dp),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play_arrow),
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        modifier = Modifier.size(32.dp),
                    )
                }
                PlayerIconButton(
                    enabled = canNavigateStations && !resolving,
                    onClick = onNextStation,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next station",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            VolumeButton(
                volume = appVolume,
                onVolumeChange = onVolumeChange,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }

    if (showStreamInfo) {
        StreamInfoDialog(
            stationTitle = station?.title.orEmpty(),
            trackText = trackText,
            streamInfo = streamInfo,
            onDismiss = { showStreamInfo = false },
            onCopyInfo = { info ->
                copyTextToClipboard(context, label = "Stream info", text = info)
                android.widget.Toast.makeText(context, "Stream info copied", android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun StreamInfoDialog(
    stationTitle: String,
    trackText: String,
    streamInfo: PlaybackStreamInfo,
    onDismiss: () -> Unit,
    onCopyInfo: (String) -> Unit,
) {
    val textFieldColors = omniDisabledTextFieldColors()
    val bitrate = streamInfo.bitrateText ?: "Not available"
    val sampleRate = streamInfo.sampleRateText ?: "Not available"
    val format = streamInfo.formatLabel ?: "Not available"
    val copyText = listOf(
        "Station: ${stationTitle.ifBlank { "Not available" }}",
        "Track: ${trackText.ifBlank { "Not available" }}",
        "Bitrate: $bitrate",
        "Sample rate: $sampleRate",
        "Format: $format",
    ).joinToString(" | ")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Stream info") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = stationTitle,
                    onValueChange = {},
                    enabled = false,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 3,
                    label = { Text("Station") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = trackText,
                    onValueChange = {},
                    enabled = false,
                    singleLine = false,
                    minLines = 1,
                    maxLines = 5,
                    label = { Text("Track") },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                StreamInfoRow(label = "Bitrate", value = bitrate)
                StreamInfoRow(label = "Sample rate", value = sampleRate)
                StreamInfoRow(label = "Format", value = format)
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OmniSecondaryButton(text = "Close", onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                OmniPrimaryButton(
                    text = "Copy info",
                    onClick = { onCopyInfo(copyText) },
                )
            }
        },
        dismissButton = {},
        containerColor = RadioSurface,
        titleContentColor = RadioText,
        textContentColor = RadioTextMuted,
    )
}

@Composable
private fun StreamInfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = label, color = RadioTextMuted, fontSize = 14.sp)
        Text(text = value, color = RadioText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun PlayerIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    OmniFilledIconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
private fun VolumeButton(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sliderHeight = 320.dp
    val sliderTrackBottomGap = 14.dp
    val sliderTrackBottomInset = 12.dp
    val popupOffsetY = with(LocalDensity.current) {
        -(sliderHeight + sliderTrackBottomGap - sliderTrackBottomInset).roundToPx()
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        OmniFilledIconButton(
            enabled = true,
            onClick = { expanded = !expanded },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_volume_up),
                contentDescription = "Volume",
                modifier = Modifier.size(24.dp),
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(x = 0, y = popupOffsetY),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                VerticalVolumeSlider(
                    volume = volume,
                    onVolumeChange = onVolumeChange,
                    modifier = Modifier
                        .width(72.dp)
                        .height(sliderHeight),
                )
            }
        }
    }
}

@Composable
private fun VerticalVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = RadioOutline,
    activeTrackColor: Color = RadioPrimary,
    thumbColor: Color = RadioText,
) {
    val coercedVolume = volume.coerceIn(0f, 1f)

    fun positionToVolume(y: Float, height: Float, thumbHeight: Float, verticalPadding: Float): Float {
        val trackTop = verticalPadding + thumbHeight / 2f
        val trackBottom = height - verticalPadding - thumbHeight / 2f
        if (trackBottom <= trackTop) return coercedVolume
        return ((trackBottom - y) / (trackBottom - trackTop)).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val thumbHeight = 8.dp.toPx()
            val verticalPadding = 8.dp.toPx()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                onVolumeChange(positionToVolume(down.position.y, size.height.toFloat(), thumbHeight, verticalPadding))
                down.consume()

                drag(down.id) { change ->
                    onVolumeChange(positionToVolume(change.position.y, size.height.toFloat(), thumbHeight, verticalPadding))
                    change.consume()
                }
            }
        },
    ) {
        val trackWidth = 18.dp.toPx()
        val thumbWidth = 40.dp.toPx()
        val thumbHeight = 8.dp.toPx()
        val verticalPadding = 8.dp.toPx()
        val centerX = size.width / 2f
        val trackTop = verticalPadding + thumbHeight / 2f
        val trackBottom = size.height - verticalPadding - thumbHeight / 2f
        val trackHeight = trackBottom - trackTop
        val thumbCenterY = trackBottom - trackHeight * coercedVolume
        val trackRadius = trackWidth / 2f
        val thumbRadius = thumbWidth / 2f
        val thumbTrackGap = 4.dp.toPx()
        val inactiveTrackBottom = (thumbCenterY - thumbHeight / 2f - thumbTrackGap).coerceAtLeast(trackTop)
        val activeTrackTop = (thumbCenterY + thumbHeight / 2f + thumbTrackGap).coerceAtMost(trackBottom)

        fun drawTrackSegment(
            color: Color,
            top: Float,
            bottom: Float,
            roundTop: Boolean,
            roundBottom: Boolean,
        ) {
            if (bottom <= top) return

            val left = centerX - trackWidth / 2f
            val rectTop = if (roundTop) top + trackRadius else top
            val rectBottom = if (roundBottom) bottom - trackRadius else bottom

            if (rectBottom > rectTop) {
                drawRect(
                    color = color,
                    topLeft = Offset(left, rectTop),
                    size = Size(trackWidth, rectBottom - rectTop),
                )
            }
            if (roundTop) {
                drawCircle(
                    color = color,
                    radius = trackRadius,
                    center = Offset(centerX, top + trackRadius),
                )
            }
            if (roundBottom) {
                drawCircle(
                    color = color,
                    radius = trackRadius,
                    center = Offset(centerX, bottom - trackRadius),
                )
            }
        }
        drawTrackSegment(
            color = trackColor,
            top = trackTop,
            bottom = inactiveTrackBottom,
            roundTop = true,
            roundBottom = false,
        )
        drawTrackSegment(
            color = activeTrackColor,
            top = activeTrackTop,
            bottom = trackBottom,
            roundTop = false,
            roundBottom = true,
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(centerX - thumbWidth / 2f, thumbCenterY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbRadius, thumbRadius),
        )
    }
}
