package vn.quangns.timeforbed.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightColors = darkColorScheme(
    primary = Color(0xFFFFD66B),
    onPrimary = Color(0xFF2A2100),
    primaryContainer = Color(0xFF3A3360),
    onPrimaryContainer = Color(0xFFE6E0FF),
    secondary = Color(0xFFB8B3FF),
    onSecondary = Color(0xFF1C1A40),
    secondaryContainer = Color(0xFF2B2952),
    onSecondaryContainer = Color(0xFFE2DFFF),
    background = Color(0xFF0F1226),
    onBackground = Color(0xFFE6E4F2),
    surface = Color(0xFF0F1226),
    onSurface = Color(0xFFE6E4F2),
    surfaceVariant = Color(0xFF1E2240),
    onSurfaceVariant = Color(0xFFC4C2D8),
    surfaceContainer = Color(0xFF171A33),
    surfaceContainerHigh = Color(0xFF1E2240),
    error = Color(0xFFFF8A80),
)

/** Giao diện tối, tông xanh đêm — dịu mắt khi dùng trước giờ ngủ. */
@Composable
fun TimeForBedTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}
