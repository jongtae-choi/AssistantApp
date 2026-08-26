package com.jongtae.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// PortfolioApp과 동일한 다크 팔레트(디자인 캔버스에서 확정한 색) — 두 앱의 톤을 맞춘다.
val DarkBg = Color(0xFF0A0F1E)
val DarkSurface = Color(0xFF111827)
val DarkCard = Color(0xFF1A2234)
val DarkCard2 = Color(0xFF1E2A42)
val DarkBorder = Color(0xFF2A3A55)
val DarkBorder2 = Color(0xFF3A4F70)
val TxtPrimary = Color(0xFFE8EEF8)
val TxtSecondary = Color(0xFF8FA4C8)
val TxtTertiary = Color(0xFF4A6080)
val AccentBlue = Color(0xFF3B82F6)
val AccentBlue2 = Color(0xFF2563EB)
val AccentAmber = Color(0xFFF59E0B)
val AccentEmerald = Color(0xFF10B981)
val AccentRose = Color(0xFFF43F5E)
val BadgeBg = Color(0xFF0B3D5E)
val BadgeTxt = Color(0xFF7DD3FC)
val MonoFont = FontFamily.Monospace

private val DarkColors = darkColorScheme(
    primary = AccentBlue2, secondary = AccentAmber, tertiary = AccentEmerald,
    background = DarkBg, surface = DarkCard, surfaceVariant = DarkCard2,
    onBackground = TxtPrimary, onSurface = TxtPrimary, onSurfaceVariant = TxtSecondary,
    outline = DarkBorder, error = AccentRose,
)

@Composable
fun AssistantAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Typography(), content = content)
}
