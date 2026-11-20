package app.alarm.dusk.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec

// ---------- Icons: the design's inline stroke SVGs on a 24×24 grid ----------

class IconDef(val paths: List<String>, val stroke: Float = 2f, val filled: Boolean = false) {
    val parsed: List<Path> by lazy { paths.map { PathParser().parsePathString(it).toPath() } }
}

object Icons {
    val Minus = IconDef(listOf("M5 12h14"), 2.4f)
    val Plus = IconDef(listOf("M5 12h14M12 5v14"), 2.4f)
    val Check = IconDef(listOf("M5 12.5l4.5 4.5L19 7.5"), 2.6f)
    val CheckBold = IconDef(listOf("M5 12.5l4.5 4.5L19 7.5"), 3.8f)
    val CheckCircle = IconDef(listOf("M22 12a10 10 0 1 1-20 0a10 10 0 1 1 20 0", "M7.5 12.5l3 3 6-6.5"), 2.2f)
    val Pencil = IconDef(listOf("M12 20h9", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"))
    val Trash = IconDef(listOf("M4 7h16", "M10 11v6M14 11v6", "M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12", "M9 7V4h6v3"))
    val CalendarCheck = IconDef(listOf("M6.5 5h11a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3v-9a3 3 0 0 1 3-3z", "M3.5 10h17", "M8 3v4M16 3v4", "M9.5 15.2l1.8 1.8 3.4-3.6"))
    val SkipNext = IconDef(listOf("M5 5l9 7-9 7V5z", "M19 5v14"))
    val ChevronDown = IconDef(listOf("M6 9l6 6 6-6"), 2.4f)
    val ChevronRight = IconDef(listOf("M9 6l6 6-6 6"), 2.2f)
    val ChevronLeft = IconDef(listOf("M15 6l-6 6 6 6"), 2.2f)
    val Help = IconDef(listOf("M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M9.6 9.3a2.5 2.5 0 0 1 4.8 1c0 1.7-2.4 2.2-2.4 3.7", "M12 17.2v.01"))
    val ArrowRight = IconDef(listOf("M5 12h12M12 6l6 6-6 6"), 2.4f)
    val ArrowNext = IconDef(listOf("M5 12h14M13 6l6 6-6 6"), 2.4f)
    val Snooze = IconDef(listOf("M9 4h6l-6 7h6", "M13 13h7l-7 7h7"), 2.2f)
    val Hand = IconDef(listOf("M9 11V5.5a1.5 1.5 0 0 1 3 0V11", "M12 10.5V9a1.5 1.5 0 0 1 3 0v2", "M15 10.5a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-.6a6 6 0 0 1-4.6-2.2L4.6 16a1.6 1.6 0 0 1 2.4-2l2 1.9"))
    val More = IconDef(listOf("M12 3a2 2 0 1 1 0 4a2 2 0 1 1 0-4z", "M12 10a2 2 0 1 1 0 4a2 2 0 1 1 0-4z", "M12 17a2 2 0 1 1 0 4a2 2 0 1 1 0-4z"), filled = true)
}

@Composable
fun Icon(def: IconDef, color: Color, modifier: Modifier = Modifier.size(18.dp), strokeOverride: Float? = null) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            def.parsed.forEach { p ->
                if (def.filled) drawPath(p, color, style = Fill)
                else drawPath(p, color, style = Stroke(strokeOverride ?: def.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ---------- Buttons ----------

/** A fully rounded button: gradient fill (primary) or an outline. */
@Composable
fun PillButton(
    text: String,
    icon: IconDef?,
    onClick: () -> Unit,
    modifier: Modifier,
    fill: Brush? = null,
    outline: Color? = null,
    fg: Color,
    fontSize: TextUnit = 16.sp,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (outline != null) Modifier.border(1.5.dp, outline, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, fg, Modifier.size(18.dp))
        Text(text, fontFamily = Outfit, fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun IconButton(def: IconDef, desc: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier.size(44.dp), iconSize: Dp = 18.dp) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Icon(def, color, Modifier.size(iconSize)) }
}

/** The design's 52×32 switch with a white knob. */
@Composable
fun DuskSwitch(checked: Boolean, label: String, onToggle: () -> Unit) {
    val t = T
    val knob by animateDpAsState(if (checked) 23.dp else 3.dp, tween(200), label = "knob")
    Box(
        Modifier.size(52.dp, 32.dp).clip(CircleShape)
            .background(if (checked) t.fill else SolidColor(t.switchOff))
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = ripple(),
                role = Role.Switch, onClick = onToggle,
            )
            .semantics { contentDescription = label; stateDescription = if (checked) "On" else "Off" },
    ) {
        Box(Modifier.offset(x = knob, y = 3.dp).size(26.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

// ---------- Text helpers ----------

val DAYS_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
val DAYS_LONG = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun pad(n: Int) = n.toString().padStart(2, '0')

fun fmtTime(t: Int, h24: Boolean): String {
    val h = t / 60; val m = t % 60
    return if (h24) pad(h) + ":" + pad(m) else ((h % 12).takeIf { it != 0 } ?: 12).toString() + ":" + pad(m) + if (h < 12) " AM" else " PM"
}

fun hourLabel(h: Int, h24: Boolean) = if (h24) pad(h) + ":00" else ((h % 12).takeIf { it != 0 } ?: 12).toString() + if (h < 12) " AM" else " PM"

fun recName(a: Alarm): String = when (a.rec) {
    Rec.ONCE -> "Once · " + DAYS_LONG[a.day]
    Rec.WEEKLY -> "Every " + DAYS_LONG[a.day]
    Rec.MULTI -> {
        val d = a.days.sorted()
        when {
            d.size == 7 -> "Daily"
            d == listOf(0, 1, 2, 3, 4) -> "Weekdays"
            else -> d.joinToString(", ") { DAYS_SHORT[it] }
        }
    }
}

/** A name from the time of day and how it repeats, e.g. "Weekday mornings". */
fun autoName(a: Alarm): String {
    val h = a.time / 60
    val w = when (h) {
        in 4..5 -> "early morning"; in 6..10 -> "morning"; in 11..13 -> "midday"
        in 14..17 -> "afternoon"; in 18..21 -> "evening"; else -> "night"
    }
    val n = when (a.rec) {
        Rec.ONCE -> DAYS_LONG[a.day] + " " + w
        Rec.WEEKLY -> DAYS_LONG[a.day] + " " + w + "s"
        Rec.MULTI -> {
            val d = a.days.sorted()
            when {
                d.size == 7 -> "Every $w"
                d == listOf(0, 1, 2, 3, 4) -> "Weekday ${w}s"
                else -> d.joinToString("/") { DAYS_SHORT[it] } + " " + w + "s"
            }
        }
    }
    return n.replaceFirstChar { it.uppercase() }
}

fun leftText(min: Long): String {
    val d = min / 1440; val h = (min % 1440) / 60; val m = min % 60
    return when {
        d > 0 -> "$d ${if (d == 1L) "day" else "days"} $h h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}

fun shortLeft(min: Long): String {
    val d = min / 1440; val h = (min % 1440) / 60; val m = min % 60
    return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${m}m"; else -> "${m}m" }
}
