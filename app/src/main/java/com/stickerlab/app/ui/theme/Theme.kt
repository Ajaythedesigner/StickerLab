package com.stickerlab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0A0D14)
val SurfaceCard = Color(0xFF131824)
val SurfaceElevated = Color(0xFF1B2232)
val BorderSubtle = Color(0xFF252E42)
val WhatsAppGreen = Color(0xFF25D366)
val TelegramSky = Color(0xFF38BDF8)
val TextPrimary = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

private val StickerLabColors = darkColorScheme(
    primary = WhatsAppGreen,
    onPrimary = Color(0xFF05180D),
    secondary = TelegramSky,
    onSecondary = Color(0xFF061A26),
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextMuted,
    outline = BorderSubtle
)

@Composable
fun StickerLabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StickerLabColors,
        content = content
    )
}
