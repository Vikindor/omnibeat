package omnibeat.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RadioBackground = Color(0xFF0D0D10)
val RadioSurface = Color(0xFF18181D)
val RadioSurfaceHigh = Color(0xFF24242B)
val RadioOutline = Color(0xFF353540)
val RadioPrimary = Color(0xFF8F5CFF)
val RadioDanger = Color(0xFFFF4D5E)
val RadioText = Color(0xFFF4F1FA)
val RadioTextMuted = Color(0xFFB7B2C3)

private val RadioColorScheme = darkColorScheme(
    primary = RadioPrimary,
    onPrimary = Color.White,
    secondary = RadioPrimary,
    onSecondary = Color.White,
    background = RadioBackground,
    onBackground = RadioText,
    surface = RadioSurface,
    onSurface = RadioText,
    surfaceVariant = RadioSurfaceHigh,
    onSurfaceVariant = RadioTextMuted,
    outline = RadioOutline,
)

@Composable
fun OmniBeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RadioColorScheme, content = content)
}
