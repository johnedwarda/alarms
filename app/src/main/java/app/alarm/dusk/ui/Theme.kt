package app.alarm.dusk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.alarm.dusk.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun outfit(w: Int) = Font(R.font.outfit, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

val Outfit = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700), outfit(800))

/** Design tokens straight from the "Dusk" and "Light" palettes of the design. */
data class Tokens(
    val dark: Boolean,
    val ground: Color,
    val glowTop: Color,
    val surface2: Color,
    val line: Color,
    val panel: Color,
    val panelEdge: Color,
    val menu: Color,
    val optSel: Color,
    val tint: Color,
    val tintLine: Color,
    val soft: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentText: Color,
    val onFill: Color,
    val faded: Color,
    val halo: Color,
    val ghost: Color,
    val off: Color,
    val offSel: Color,
    val offHalo: Color,
    val switchOff: Color,
    val danger: Color,
    val dangerLine: Color,
    val shadow: Color,
    val toastBg: Color,
    val toastAction: Color,
    val introSkipFill: Color,
    val gridDot: Color,
) {
    val fill: Brush get() = Brush.linearGradient(listOf(Color(0xFFC618C4), Color(0xFF9C12D6)), Offset.Zero, Offset.Infinite)
    val fillSoft: Brush get() = Brush.linearGradient(listOf(Color(0x99C618C4), Color(0x999C12D6)), Offset.Zero, Offset.Infinite)

    /** Grid dot colour: today brightest, then each following day a little dimmer. */
    fun gridDotFor(f: Float): Color = if (dark) Color.White.copy(alpha = 0.26f - f * 0.18f)
    else lerpColor(Color(0xFFBBA6C6), Color(0xFFECE4F0), f)
}

fun lerpColor(a: Color, b: Color, f: Float) = Color(
    a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f,
)

val Dusk = Tokens(
    dark = true,
    ground = Color(0xFF14151C), glowTop = Color(0x6BD61FCB),
    surface2 = Color.White.copy(alpha = 0.07f), line = Color.White.copy(alpha = 0.10f),
    panel = Color(0xFF3E1256), panelEdge = Color.White.copy(alpha = 0.12f), menu = Color(0xFF4D1A6B),
    optSel = Color.White.copy(alpha = 0.08f), tint = Color(0x29F23FE4), tintLine = Color(0x66F23FE4),
    soft = Color(0xFFDCCAE4), ink = Color(0xFFF6ECF8), muted = Color(0xFFB396C0),
    accent = Color(0xFFF23FE4), accentText = Color(0xFFF7A8F1), onFill = Color.White,
    faded = Color(0xFF5E2A6B), halo = Color(0x59F23FE4), ghost = Color(0x40F23FE4),
    off = Color(0xFF4A3954), offSel = Color(0xFF8C7A96), offHalo = Color.White.copy(alpha = 0.12f),
    switchOff = Color.White.copy(alpha = 0.16f), danger = Color(0xFFF7A8F1), dangerLine = Color(0x73F23FE4),
    shadow = Color.Black.copy(alpha = 0.55f), toastBg = Color(0xFF341247), toastAction = Color(0xFFF7A8F1),
    introSkipFill = Color(0x52F7A8F1), gridDot = Color.White.copy(alpha = 0.13f),
)

val Light = Tokens(
    dark = false,
    ground = Color(0xFFFBF8FC), glowTop = Color(0x1FD61FCB),
    surface2 = Color.White, line = Color(0xFFE7DAEC),
    panel = Color(0xFFF3E8F6), panelEdge = Color(0xFFE2CFE8), menu = Color.White,
    optSel = Color(0xFFF3E8F6), tint = Color(0x1FD62FC8), tintLine = Color(0x59D62FC8),
    soft = Color(0xFF46334F), ink = Color(0xFF24102E), muted = Color(0xFF6E5A78),
    accent = Color(0xFFD62FC8), accentText = Color(0xFFA3169B), onFill = Color.White,
    faded = Color(0xFFEBC2EA), halo = Color(0x38D62FC8), ghost = Color(0x33D62FC8),
    off = Color(0xFFCFC4D4), offSel = Color(0xFF8E7F96), offHalo = Color.Black.copy(alpha = 0.1f),
    switchOff = Color(0xFFA898B0), danger = Color(0xFFA3169B), dangerLine = Color(0xFFE2B5E0),
    shadow = Color(0x293C0A50), toastBg = Color(0xFF2B0C3F), toastAction = Color(0xFFF7A8F1),
    introSkipFill = Color(0x52A3169B), gridDot = Color(0xFFDCCFE2),
)

val LocalTokens = staticCompositionLocalOf { Dusk }
val T: Tokens @Composable get() = LocalTokens.current

/** The design's background: an elliptical magenta glow (130% × 38%) above the top edge. */
fun Modifier.duskBackground(t: Tokens, centerY: Float = -0.04f, widthF: Float = 1.3f, heightF: Float = 0.38f, glow: Color = t.glowTop) =
    drawBehind {
        drawRect(t.ground)
        val rx = size.width * widthF
        val ry = size.height * heightF
        val c = Offset(size.width / 2, size.height * centerY)
        scale(1f, ry / rx, pivot = c) {
            drawCircle(Brush.radialGradient(0f to glow, 0.7f to glow.copy(alpha = 0f), center = c, radius = rx), rx, c)
        }
    }
