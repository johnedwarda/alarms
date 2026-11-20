package app.alarm.dusk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

/** The app's alarm-clock mark, drawn on a 40×40 grid like the design's SVG. */
@Composable
fun Logo(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 40f
        scale(s, s, pivot = Offset.Zero) {
            val grad = Brush.linearGradient(listOf(Color(0xFF6D3DF2), Color(0xFFE43BC6)), Offset.Zero, Offset(40f, 40f))
            // Bells: a chord across a circle of radius 7, from (4.6,12.6) to (14.6,4.8).
            drawPath(bell(Offset(4.6f, 12.6f), Offset(14.6f, 4.8f), clockwise = true), grad)
            drawPath(bell(Offset(35.4f, 12.6f), Offset(25.4f, 4.8f), clockwise = false), grad)
            val c = Offset(20f, 22f)
            drawCircle(Color.White, 13.6f, c)
            drawCircle(grad, 13.6f, c, style = Stroke(2.4f))
            drawCircle(Color(0xFFF3ECFA), 10.4f, c)
            drawCircle(Color.White, 7.6f, c)
            drawLine(Color(0xFF2B0C3F), c, Offset(15.6f, 18.6f), 1.6f, StrokeCap.Round)
            drawLine(Color(0xFF2B0C3F), c, Offset(25.4f, 17.4f), 1.3f, StrokeCap.Round)
            drawLine(Color(0xFFE43BC6), c, Offset(21.2f, 28.4f), 0.8f, StrokeCap.Round)
            drawCircle(Color(0xFF2B0C3F), 1.1f, c)
        }
    }
}

/** The minor arc of radius 7 between two points, closed by its chord. */
private fun bell(a: Offset, b: Offset, clockwise: Boolean): Path {
    val r = 7f
    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
    val dx = b.x - a.x; val dy = b.y - a.y
    val d = kotlin.math.sqrt(dx * dx + dy * dy)
    val h = kotlin.math.sqrt(r * r - (d / 2) * (d / 2))
    // The centre sits on the side that makes the arc bulge outward (up/out from the clock face).
    val sign = if (clockwise) -1f else 1f
    val center = Offset(mid.x + sign * h * (dy / d), mid.y - sign * h * (dx / d))
    val angA = Math.toDegrees(kotlin.math.atan2((a.y - center.y).toDouble(), (a.x - center.x).toDouble())).toFloat()
    val angB = Math.toDegrees(kotlin.math.atan2((b.y - center.y).toDouble(), (b.x - center.x).toDouble())).toFloat()
    var sweep = angB - angA
    if (clockwise) { while (sweep < 0) sweep += 360f } else { while (sweep > 0) sweep -= 360f }
    return Path().apply {
        moveTo(a.x, a.y)
        arcTo(Rect(center, r), angA, sweep, false)
        close()
    }
}
