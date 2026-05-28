package ski.wischnew.shield.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ski.wischnew.shield.settings.ThemeMode

@Composable
fun SmsShieldTheme(
    themeMode: ThemeMode,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val lightOnAccent = contentColorForLightAccent(accentColor)
    val darkOnAccent = darkAccentContent(accentColor)
    val colors = when (themeMode) {
        ThemeMode.LIGHT -> lightColorScheme(
            primary = accentColor,
            onPrimary = lightOnAccent,
            secondary = accentColor,
            onSecondary = lightOnAccent,
            background = Color(0xFFF6F8FB),
            surface = Color(0xFFFFFFFF),
            surfaceContainerHighest = Color(0xFFFFFFFF),
            onSurfaceVariant = Color(0xFF647083),
            outlineVariant = Color(0xFFE0E5EE)
        )
        ThemeMode.DARK -> darkColorScheme(
            primary = accentColor,
            onPrimary = darkOnAccent,
            secondary = accentColor,
            onSecondary = darkOnAccent,
            background = Color(0xFF101218),
            surface = Color(0xFF171B24),
            surfaceContainerHighest = Color(0xFF202735),
            onSurfaceVariant = Color(0xFFB0B8C7),
            outlineVariant = Color(0xFF2F3747)
        )
        ThemeMode.OLED -> darkColorScheme(
            primary = accentColor,
            onPrimary = darkOnAccent,
            secondary = accentColor,
            onSecondary = darkOnAccent,
            background = Color(0xFF000000),
            surface = Color(0xFF050505),
            surfaceContainerHighest = Color(0xFF0B0B0B),
            onSurfaceVariant = Color(0xFFA8A8A8),
            outlineVariant = Color(0xFF202020)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private fun contentColorForLightAccent(accent: Color): Color {
    return if (accent.luminance() > 0.35f) {
        darkAccentContent(accent)
    } else {
        Color.White
    }
}

private fun darkAccentContent(accent: Color): Color {
    if (accent.luminance() < 0.18f) {
        return Color.White
    }
    return Color(
        red = accent.red * 0.22f,
        green = accent.green * 0.22f,
        blue = accent.blue * 0.22f,
        alpha = 1f
    )
}
