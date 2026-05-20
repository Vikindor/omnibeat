package omnibeat.app.ui

import omnibeat.app.model.ThemeMode

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RadioColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val primary: Color,
    val danger: Color,
    val text: Color,
    val textMuted: Color,
)

private val DarkRadioColors = RadioColors(
    background = Color(0xFF0D0D10),
    surface = Color(0xFF18181D),
    surfaceHigh = Color(0xFF24242B),
    outline = Color(0xFF353540),
    primary = Color(0xFF8F5CFF),
    danger = Color(0xFFFF4D5E),
    text = Color(0xFFF4F1FA),
    textMuted = Color(0xFFB7B2C3),
)

private val LightRadioColors = RadioColors(
    background = Color(0xFFF2F0F6),
    surface = Color(0xFFE7E5E9),
    surfaceHigh = Color(0xFFDBD9DD),
    outline = Color(0xFFC6C1CE),
    primary = Color(0xFF7650E8),
    danger = Color(0xFFD83A4A),
    text = Color(0xFF16131D),
    textMuted = Color(0xFF6D667A),
)

private val LocalRadioColors = staticCompositionLocalOf { DarkRadioColors }

val RadioBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.background

val RadioSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.surface

val RadioSurfaceHigh: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.surfaceHigh

val RadioOutline: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.outline

val RadioPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.primary

val RadioDanger: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.danger

val RadioText: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.text

val RadioTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalRadioColors.current.textMuted

@Composable
fun OmniBeatTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = shouldUseDarkTheme(themeMode)
    val radioColors = if (useDarkTheme) DarkRadioColors else LightRadioColors
    CompositionLocalProvider(LocalRadioColors provides radioColors) {
        MaterialTheme(
            colorScheme = radioColors.toMaterialColorScheme(useDarkTheme),
            content = content,
        )
    }
}

@Composable
fun shouldUseDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
}

private fun RadioColors.toMaterialColorScheme(useDarkTheme: Boolean): ColorScheme {
    return if (useDarkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = primary,
            onSecondary = Color.White,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = textMuted,
            outline = outline,
            error = danger,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            secondary = primary,
            onSecondary = Color.White,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = textMuted,
            outline = outline,
            error = danger,
            onError = Color.White,
        )
    }
}
