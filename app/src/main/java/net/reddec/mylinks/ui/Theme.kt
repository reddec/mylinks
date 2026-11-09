package net.reddec.mylinks.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Editorial "reading room" palette: warm paper, ink, a single oxblood accent.
private val Paper = Color(0xFFFAF7F1)
private val PaperDeep = Color(0xFFF1EBE0)
private val Ink = Color(0xFF17140F)
private val Muted = Color(0xFF6B635A)
private val Hairline = Color(0xFFE2D9CB)
private val Oxblood = Color(0xFF93331F)
private val OxbloodSoft = Color(0xFFF3E5DF)

private val Night = Color(0xFF121110)
private val NightDeep = Color(0xFF1C1A17)
private val NightInk = Color(0xFFF0EAE0)
private val NightMuted = Color(0xFF9E9488)
private val NightHairline = Color(0xFF3C3833)
private val NightAccent = Color(0xFFE08A6B)
private val NightAccentSoft = Color(0xFF33241E)

private val LightScheme = lightColorScheme(
    primary = Oxblood,
    onPrimary = Paper,
    primaryContainer = OxbloodSoft,
    onPrimaryContainer = Oxblood,
    secondary = Ink,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = PaperDeep,
    surfaceContainerHigh = PaperDeep,
    surfaceContainerHighest = PaperDeep,
    outline = Muted,
    outlineVariant = Hairline,
    error = Oxblood,
    onError = Paper,
    scrim = Color(0x6617140F),
)

private val DarkScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color(0xFF1A0F0A),
    primaryContainer = NightAccentSoft,
    onPrimaryContainer = NightAccent,
    secondary = NightInk,
    onSecondary = Night,
    background = Night,
    onBackground = NightInk,
    surface = Night,
    onSurface = NightInk,
    surfaceVariant = NightDeep,
    onSurfaceVariant = NightMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Night,
    surfaceContainer = NightDeep,
    surfaceContainerHigh = NightDeep,
    surfaceContainerHighest = NightDeep,
    outline = NightMuted,
    outlineVariant = NightHairline,
    error = NightAccent,
    onError = Color(0xFF1A0F0A),
    scrim = Color(0xCC000000),
)

/** Serif display + sans body: the platform serif keeps the APK small and works offline. */
private val EditorialTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 25.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
    ),
)

/** Extra roles the editorial layout needs that Material's scale has no slot for. */
object Editorial {
    val kicker = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.8.sp,
    )
    val meta = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
    )
    val brand = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 4.5.sp,
    )
}

@Composable
fun MyLinksTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = EditorialTypography,
        content = content,
    )
}
