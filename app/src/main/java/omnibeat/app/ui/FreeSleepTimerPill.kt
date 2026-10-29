package omnibeat.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import omnibeat.app.model.SleepTimerFreePosition

@Composable
fun FreeSleepTimerPill(
    remainingSeconds: Long,
    position: SleepTimerFreePosition,
    onPositionChange: (SleepTimerFreePosition) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPosition by remember { mutableStateOf<SleepTimerFreePosition?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val savedPosition by rememberUpdatedState(position)
    val savePosition by rememberUpdatedState(onPositionChange)
    val openTimer by rememberUpdatedState(onClick)
    val horizontalRange = (containerSize.width - pillSize.width).coerceAtLeast(0).toFloat()
    val verticalRange = (containerSize.height - pillSize.height).coerceAtLeast(0).toFloat()
    val dragRange by rememberUpdatedState(Offset(horizontalRange, verticalRange))

    Box(modifier = modifier.onSizeChanged { containerSize = it }) {
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .graphicsLayer {
                    val displayedPosition = currentPosition ?: savedPosition
                    translationX = displayedPosition.xFraction * horizontalRange
                    translationY = displayedPosition.yFraction * verticalRange
                }
                .onSizeChanged { pillSize = it }
                .pointerInput(Unit) {
                    var gestureStartPosition = savedPosition
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            gestureStartPosition = currentPosition ?: savedPosition
                            currentPosition = gestureStartPosition
                            dragging = true
                        },
                        onDragEnd = {
                            currentPosition?.let(savePosition)
                            dragging = false
                        },
                        onDragCancel = {
                            currentPosition = gestureStartPosition
                            dragging = false
                        },
                    ) { change, amount ->
                        change.consume()
                        val current = currentPosition ?: savedPosition
                        val range = dragRange
                        currentPosition = SleepTimerFreePosition(
                            xFraction = if (range.x > 0f) {
                                (current.xFraction + amount.x / range.x).coerceIn(0f, 1f)
                            } else {
                                current.xFraction
                            },
                            yFraction = if (range.y > 0f) {
                                (current.yFraction + amount.y / range.y).coerceIn(0f, 1f)
                            } else {
                                current.yFraction
                            },
                        )
                    }
                },
        ) {
            SleepTimerPill(
                remainingSeconds = remainingSeconds,
                onClick = { if (!dragging) openTimer() },
            )
        }
    }
}
