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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import omnibeat.app.model.Station
import omnibeat.app.playback.PlaybackTrackStatus
import omnibeat.app.playback.PlaybackStreamInfo
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerPanel(
    station: Station?,
    trackText: String,
    trackStatus: PlaybackTrackStatus?,
    errorText: String?,
    streamInfo: PlaybackStreamInfo,
    loading: Boolean,
    resolving: Boolean,
    isPlaying: Boolean,
    canNavigateStations: Boolean,
    appVolume: Float,
    canPlay: Boolean,
    showBitrate: Boolean,
    showUnavailableBitrate: Boolean,
    marqueeTrackTitle: Boolean,
    onPlayStop: () -> Unit,
    onPreviousStation: () -> Unit,
    onNextStation: () -> Unit,
    onRandomStation: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    var showStreamInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val displayTrackText = errorText ?: trackStatus?.let { trackStatusText(it) } ?: trackText
    val bitrateText = if (showBitrate) {
        streamInfo.bitrateKbps?.let { bitrate ->
            stringResource(R.string.player_bitrate_format, bitrate)
        } ?: stringResource(R.string.player_bitrate_unavailable).takeIf { showUnavailableBitrate }
    } else {
        null
    }
    val hasActivePlaybackRequest = isPlaying || loading

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
                        if (trackStatus == null && trackText.isNotBlank()) {
                            copyTextToClipboard(
                                context = context,
                                label = context.getString(R.string.player_clipboard_track_label),
                                text = trackText,
                            )
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.player_track_copied),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onLongClick = { showStreamInfo = true },
                )
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = station?.title ?: stringResource(R.string.player_choose_station),
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
                    overflow = if (marqueeTrackTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (marqueeTrackTitle) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    repeatDelayMillis = 1_500,
                                )
                            } else {
                                Modifier
                            },
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
                    contentDescription = null,
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
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
                PlayerIconButton(
                    enabled = canPlay,
                    onClick = onPlayStop,
                    modifier = Modifier.size(62.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (hasActivePlaybackRequest) R.drawable.ic_stop else R.drawable.ic_play_arrow,
                        ),
                        contentDescription = null,
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
                        contentDescription = null,
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
            trackText = displayTrackText,
            streamInfo = streamInfo,
            onDismiss = { showStreamInfo = false },
            onCopyInfo = { info ->
                copyTextToClipboard(context, label = context.getString(R.string.player_clipboard_stream_info_label), text = info)
                android.widget.Toast.makeText(context, context.getString(R.string.player_stream_info_copied), android.widget.Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun trackStatusText(trackStatus: PlaybackTrackStatus): String {
    return when (trackStatus) {
        PlaybackTrackStatus.Stopped -> stringResource(R.string.track_text_stopped)
        PlaybackTrackStatus.LoadingStations -> stringResource(R.string.track_text_loading_stations)
        PlaybackTrackStatus.Resolving -> stringResource(R.string.track_text_resolving)
        PlaybackTrackStatus.WaitingMetadata -> stringResource(R.string.track_text_waiting_metadata)
        PlaybackTrackStatus.NoMetadata -> stringResource(R.string.track_text_no_metadata)
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
    val notAvailable = stringResource(R.string.player_not_available)
    val bitrate = streamInfo.bitrateKbps?.let { stringResource(R.string.player_bitrate_format, it) } ?: notAvailable
    val sampleRate = streamInfo.sampleRateText ?: notAvailable
    val format = streamInfo.formatLabel ?: notAvailable
    val copyText = listOf(
        stringResource(R.string.player_stream_info_copy_station, stationTitle.ifBlank { notAvailable }),
        stringResource(R.string.player_stream_info_copy_track, trackText.ifBlank { notAvailable }),
        stringResource(R.string.player_stream_info_copy_bitrate, bitrate),
        stringResource(R.string.player_stream_info_copy_sample_rate, sampleRate),
        stringResource(R.string.player_stream_info_copy_format, format),
    ).joinToString(" | ")

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text(stringResource(R.string.player_stream_info_title)) },
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
                    label = { Text(stringResource(R.string.player_stream_info_station)) },
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
                    label = { Text(stringResource(R.string.player_stream_info_track_metadata)) },
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                StreamInfoRow(label = stringResource(R.string.player_stream_info_bitrate), value = bitrate)
                StreamInfoRow(label = stringResource(R.string.player_stream_info_sample_rate), value = sampleRate)
                StreamInfoRow(label = stringResource(R.string.player_stream_info_format), value = format)
            }
        },
        confirmButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OmniSecondaryButton(text = stringResource(R.string.action_close), onClick = onDismiss)
                Spacer(Modifier.weight(1f))
                OmniPrimaryButton(
                    text = stringResource(R.string.action_copy),
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
                contentDescription = null,
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
    trackColor: Color? = null,
    activeTrackColor: Color? = null,
    thumbColor: Color? = null,
) {
    val coercedVolume = volume.coerceIn(0f, 1f)
    val resolvedTrackColor = trackColor ?: RadioOutline
    val resolvedActiveTrackColor = activeTrackColor ?: RadioPrimary
    val resolvedThumbColor = thumbColor ?: RadioText

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
            color = resolvedTrackColor,
            top = trackTop,
            bottom = inactiveTrackBottom,
            roundTop = true,
            roundBottom = false,
        )
        drawTrackSegment(
            color = resolvedActiveTrackColor,
            top = activeTrackTop,
            bottom = trackBottom,
            roundTop = false,
            roundBottom = true,
        )
        drawRoundRect(
            color = resolvedThumbColor,
            topLeft = Offset(centerX - thumbWidth / 2f, thumbCenterY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbRadius, thumbRadius),
        )
    }
}
