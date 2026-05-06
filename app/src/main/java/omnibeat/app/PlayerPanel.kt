package omnibeat.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun PlayerPanel(
    station: Station?,
    playableUrl: String,
    trackText: String,
    loading: Boolean,
    resolving: Boolean,
    isPlaying: Boolean,
    appVolume: Float,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RadioSurface)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DividerDefaults.Thickness)
                .background(RadioOutline),
        )
        Text(
            text = station?.name ?: "Choose a station",
            color = RadioText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = trackText,
            color = RadioTextMuted,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (playableUrl.isNotBlank()) {
            Text(
                text = playableUrl,
                color = RadioTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            FilledTonalButton(
                enabled = station != null && !resolving,
                onClick = onPlayPause,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = RadioSurfaceHigh,
                    contentColor = RadioText,
                    disabledContainerColor = RadioSurfaceHigh,
                    disabledContentColor = RadioTextMuted,
                ),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .width(132.dp)
                    .height(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = RadioPrimary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play")
            }
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = RadioPrimary,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            VolumeButton(volume = appVolume, onVolumeChange = onVolumeChange)
        }
    }
}

@Composable
private fun VolumeButton(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sliderHeight = 320.dp
    val sliderTrackBottomGap = 10.dp
    val sliderTrackBottomInset = 12.dp
    val popupOffsetY = with(LocalDensity.current) {
        -(sliderHeight + sliderTrackBottomGap - sliderTrackBottomInset).roundToPx()
    }

    Box(contentAlignment = Alignment.Center) {
        FilledTonalIconButton(
            onClick = { expanded = !expanded },
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = RadioSurfaceHigh,
                contentColor = RadioPrimary,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
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
