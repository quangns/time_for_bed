package vn.quangns.timeforbed.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Bảng màu theo DESIGN.md — "Quiet midnight", một màu nhấn duy nhất (Sleep Green). */
object Night {
    val Ink = Color(0xFF0B0F1A)
    val Surface = Color(0xFF121826)
    val Raised = Color(0xFF1A2233)
    val Hairline = Color(0xFF232C3F)
    val Moonlight = Color(0xFFE7E9EE)
    val Mist = Color(0xFF8A93A6)
    val Green = Color(0xFF3DBE7A)
    val Ember = Color(0xFFE5866B)
}

private val NightColors = darkColorScheme(
    primary = Night.Green,
    onPrimary = Night.Ink,
    primaryContainer = Night.Green.copy(alpha = 0.16f),
    onPrimaryContainer = Night.Moonlight,
    secondary = Night.Mist,
    onSecondary = Night.Ink,
    secondaryContainer = Night.Green.copy(alpha = 0.16f),
    onSecondaryContainer = Night.Moonlight,
    background = Night.Ink,
    onBackground = Night.Moonlight,
    surface = Night.Ink,
    onSurface = Night.Moonlight,
    surfaceVariant = Night.Raised,
    onSurfaceVariant = Night.Mist,
    surfaceContainerLowest = Night.Ink,
    surfaceContainerLow = Night.Surface,
    surfaceContainer = Night.Surface,
    surfaceContainerHigh = Night.Raised,
    surfaceContainerHighest = Night.Raised,
    outline = Night.Hairline,
    outlineVariant = Night.Hairline,
    error = Night.Ember,
    onError = Night.Ink,
    errorContainer = Night.Ember.copy(alpha = 0.14f),
    onErrorContainer = Night.Ember,
)

private val NightTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 64.sp, letterSpacing = (-0.01).em, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 48.sp, letterSpacing = (-0.01).em, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontWeight = FontWeight.Light, fontSize = 40.sp, letterSpacing = (-0.01).em, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.08.em),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.04.em),
)

private val NightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Giao diện tối "nửa đêm tĩnh lặng" — dịu mắt khi dùng trước giờ ngủ. */
@Composable
fun TimeForBedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, typography = NightTypography, shapes = NightShapes, content = content)
}
