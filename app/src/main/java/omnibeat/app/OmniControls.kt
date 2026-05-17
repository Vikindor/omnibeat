package omnibeat.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
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
fun OmniDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                Text(
                    text = text,
                    color = RadioText,
                    fontSize = 14.sp,
                    modifier = if (leadingIcon == null) Modifier else Modifier.padding(start = 12.dp),
                )
                trailingIcon?.invoke()
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = modifier.height(40.dp),
        onClick = onClick,
    )
}

@Composable
fun OmniTopBarIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = RadioText,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.height(28.dp),
        )
    }
}

@Composable
fun OmniListActionIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = RadioText,
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun OmniFilledIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = RadioSurfaceHigh,
    contentColor: Color = RadioPrimary,
    disabledContainerColor: Color = RadioSurfaceHigh,
    disabledContentColor: Color = RadioTextMuted,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(
        enabled = enabled,
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor,
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
    Canvas(modifier = modifier.width(3.dp)) {
        drawRect(color = RadioOutline.copy(alpha = 0.55f), size = size)
        drawRect(
            color = RadioPrimary,
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
fun OmniScrollIndicator(
    scrollIndicatorState: ScrollIndicatorState?,
    modifier: Modifier = Modifier,
) {
    val state = scrollIndicatorState ?: return
    val viewportSize = state.viewportSize
    val contentSize = state.contentSize
    val scrollOffset = state.scrollOffset
    if (
        viewportSize <= 0 ||
        contentSize <= viewportSize ||
        scrollOffset == Int.MAX_VALUE ||
        contentSize == Int.MAX_VALUE ||
        viewportSize == Int.MAX_VALUE
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
