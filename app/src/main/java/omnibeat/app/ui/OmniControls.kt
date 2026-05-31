package omnibeat.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState

@Composable
fun omniTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
    errorPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
)

@Composable
fun omniDisabledTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
    unfocusedPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
    errorPlaceholderColor = RadioTextMuted.copy(alpha = 0.55f),
    disabledTextColor = RadioText,
    disabledLabelColor = RadioTextMuted,
    disabledBorderColor = RadioOutline,
)

@Composable
fun OmniPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = RadioPrimary,
            contentColor = RadioText,
            disabledContainerColor = RadioSurfaceHigh,
            disabledContentColor = RadioTextMuted,
        ),
        modifier = modifier,
    ) {
        Text(text)
    }
}

@Composable
fun OmniSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = RadioSurfaceHigh,
            contentColor = RadioText,
            disabledContainerColor = RadioSurfaceHigh,
            disabledContentColor = RadioTextMuted,
        ),
        modifier = modifier,
    ) {
        Text(text)
    }
}

@Composable
fun OmniDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = RadioDanger,
            contentColor = RadioText,
            disabledContainerColor = RadioSurfaceHigh,
            disabledContentColor = RadioTextMuted,
        ),
        modifier = modifier,
    ) {
        Text(text)
    }
}

@Composable
fun OmniIconButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = RadioText,
    disabledTint: Color = RadioTextMuted,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (enabled) tint else disabledTint,
            modifier = iconModifier.size(24.dp),
        )
    }
}

@Composable
fun OmniFilledIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    disabledContainerColor: Color? = null,
    disabledContentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        enabled = enabled,
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor ?: RadioSurfaceHigh,
            contentColor = contentColor ?: RadioPrimary,
            disabledContainerColor = disabledContainerColor ?: RadioSurfaceHigh,
            disabledContentColor = disabledContentColor ?: RadioTextMuted,
        ),
        modifier = modifier,
    ) {
        content()
    }
}

@Composable
fun OmniVerticalScrollIndicator(
    thumbTopFraction: Float,
    thumbHeightFraction: Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = RadioOutline.copy(alpha = 0.55f)
    val thumbColor = RadioPrimary
    Canvas(modifier = modifier.width(3.dp)) {
        drawRect(color = trackColor, size = size)
        drawRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = 0f,
                y = size.height * thumbTopFraction.coerceIn(0f, 1f),
            ),
            size = androidx.compose.ui.geometry.Size(
                width = size.width,
                height = size.height * thumbHeightFraction.coerceIn(0.12f, 1f),
            ),
        )
    }
}

@Composable
fun OmniLazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    OmniScrollIndicator(
        scrollIndicatorState = listState.scrollIndicatorState,
        modifier = modifier,
    )
}

@Composable
@Suppress("FrequentlyChangedStateReadInComposition")
fun OmniScrollIndicator(
    scrollIndicatorState: ScrollIndicatorState?,
    modifier: Modifier = Modifier,
) {
    val state = scrollIndicatorState ?: return
    val viewportSize = state.viewportSize
    val contentSize = state.contentSize
    val scrollOffset = state.scrollOffset
    val values = listOf(viewportSize, contentSize, scrollOffset)
    if (
        viewportSize !in 1 until contentSize ||
        values.any { it == Int.MAX_VALUE }
    ) {
        return
    }

    val thumbHeightFraction = (viewportSize.toFloat() / contentSize).coerceIn(0.12f, 1f)
    val maxScrollOffset = (contentSize - viewportSize).coerceAtLeast(1)
    val thumbTopFraction = (scrollOffset / maxScrollOffset.toFloat() * (1f - thumbHeightFraction))
        .coerceIn(0f, 1f - thumbHeightFraction)

    OmniVerticalScrollIndicator(
        thumbTopFraction = thumbTopFraction,
        thumbHeightFraction = thumbHeightFraction,
        modifier = modifier.fillMaxHeight(),
    )
}
