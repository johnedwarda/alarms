package app.alarm.dusk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec
import app.alarm.dusk.schedule.Occurrences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

// Grid geometry (dp), as in the design.
private const val HOUR = 48f
private const val TOP = 8f
private const val GUT = 52f
private const val RIGHT = 10f
private const val GRID_H = 1168f

// ---------------------------------------------------------------------------------------------
// Header

data class NextInfo(val id: Int, val at: LocalDateTime, val day: Int)

fun nextAlarm(vm: AlarmViewModel): NextInfo? {
    if (!vm.settings.allOn) return null
    return vm.data.alarms.mapNotNull { a -> Occurrences.next(a, vm.settings, vm.now)?.let { NextInfo(a.id, it, Occurrences.dow(it.toLocalDate())) } }
        .minByOrNull { it.at }
}

@Composable
fun Header(vm: AlarmViewModel) {
    val t = T
    val best = nextAlarm(vm)
    val s = vm.settings
    val noneYet = vm.data.alarms.isEmpty()
    val k = best?.let { ChronoUnit.DAYS.between(vm.now.toLocalDate(), it.at.toLocalDate()) }
    val prefix = when {
        best != null -> when (k) { 0L -> "next"; 1L -> "next · tomorrow"; else -> "next · " + DAYS_SHORT[best.day] }
        !s.allOn -> "all alarms off"
        else -> "no alarms"
    }
    val left = when {
        best != null -> "in " + leftText(Duration.between(vm.now.withSecond(0).withNano(0), best.at).toMinutes())
        noneYet && s.allOn -> "tap the grid to add one"
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(Modifier.size(40.dp).semantics { contentDescription = "Alarms" })
        Spacer(Modifier.width(5.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = best != null, role = Role.Button) { best?.let { vm.openNext(it.id, it.day) } }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics { contentDescription = if (best != null) "Next alarm ${fmtTime(best.at.hour * 60 + best.at.minute, s.clock24)}. Open it" else "No upcoming alarm" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.alignBy(LastBaseline), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(prefix, fontFamily = Outfit, fontSize = 16.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = t.accentText)
                    if (left != null) Text(
                        left, fontFamily = Outfit, fontSize = if (best != null) 12.sp else 16.sp, lineHeight = if (best != null) 13.sp else 17.sp,
                        fontWeight = FontWeight.Medium, color = if (best != null) t.muted else t.ink, maxLines = 1,
                    )
                }
                if (best != null) {
                    Text(
                        fmtTime(best.at.hour * 60 + best.at.minute, s.clock24),
                        modifier = Modifier.alignBy(LastBaseline),
                        style = TextStyle(
                            fontFamily = Outfit, fontSize = 36.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.36).sp, color = t.accentText,
                            shadow = if (t.dark) Shadow(Color(0x8CF23FE4), blurRadius = 36f) else null,
                        ),
                    )
                }
            }
            IconButton(Icons.More, "Settings", t.muted, { vm.toggleSettings() }, Modifier.size(40.dp, 44.dp), iconSize = 22.dp)
        }
    }
}

@Composable
fun DayRow(vm: AlarmViewModel) {
    val t = T
    Row(Modifier.fillMaxWidth().height(40.dp).padding(start = GUT.dp, end = RIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        DAYS_SHORT.forEachIndexed { i, n ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Canvas(Modifier.size(10.dp, 6.dp).alpha(if (i == vm.today) 1f else 0f)) {
                    drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close()
                    }, t.accent)
                }
                Text(n.uppercase(), fontFamily = Outfit, fontSize = 12.sp, color = t.muted)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Week grid

private data class DotUi(
    val aid: Int, val d: Int, val x: Float, val y: Float, val on: Boolean,
    val size: Float, val fill: Color, val borderW: Float, val border: Color, val dashed: Boolean,
    val halo: Color?, val check: Boolean, val checkColor: Color, val label: String, val draggable: Boolean,
)

private data class SegUi(val aid: Int, val x0: Float, val x1: Float, val y: Float, val color: Color)

@Composable
fun WeekGrid(vm: AlarmViewModel, starClock: (() -> Float)? = null) {
    val t = T
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val s = vm.settings
    val setOff = !s.allOn

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val colW = (width - GUT - RIGHT) / 7f
        fun xOf(d: Int) = GUT + d * colW + colW / 2
        fun yOf(t: Int) = TOP + t / 60f * HOUR

        val sel = vm.selected
        val dots = mutableListOf<DotUi>()
        val segs = mutableListOf<SegUi>()
        for (a in vm.alarms) {
            val days = a.dayList()
            val isSel = a.id == vm.sel
            val faded = vm.sel != null && !isSel
            val y = yOf(a.time)
            var ink = if (isSel) t.accentText else if (faded) t.faded else t.accent
            if (setOff) ink = if (isSel) t.offSel else t.off
            if (a.rec == Rec.MULTI && days.size > 1) segs += SegUi(a.id, xOf(days.first()), xOf(days.last()), y, ink)
            val multiSel = isSel && !setOff
            val slots = if (multiSel) (0..6).toList() else days
            for (d in slots) {
                val on = d in days
                val skipped = on && Occurrences.silentOn(a, Occurrences.nextDateOf(d, a.time, vm.now), s)
                var fill = ink; var bw = 2.5f; var border = ink; var dashed = false
                if (a.rec == Rec.ONCE) { fill = t.ground; bw = 3f }
                if (skipped) { fill = ink.copy(alpha = 0.32f); bw = 1f }
                if (!on) { fill = Color.Transparent; bw = 2f; dashed = true }
                var label = fmtTime(a.time, s.clock24) + (if (a.name.isNotBlank()) " " + a.name else "") + ", " + recName(a) + ", " + DAYS_LONG[d] + (if (skipped) " (skipped)" else "")
                if (multiSel && a.rec != Rec.MULTI) label = DAYS_LONG[d] + if (on) " selected. Drag to change the time" else ". Tap to ring on this day instead"
                else if (multiSel) label = DAYS_LONG[d] + if (on) " included. Tap to remove it, drag to change the time" else " not included. Tap to add it"
                dots += DotUi(
                    a.id, d, xOf(d), y, on,
                    size = if (isSel) (if (on) 22f else 18f) else 18f,
                    fill = fill, borderW = bw, border = border, dashed = dashed,
                    halo = if (isSel && on) (if (setOff) t.offHalo else t.halo) else null,
                    check = multiSel && on, checkColor = if (a.rec == Rec.ONCE) ink else t.ground,
                    label = label, draggable = isSel && on,
                )
            }
        }
        val dotsState = rememberUpdatedState(dots.toList())

        // Callout geometry.
        var calloutH by remember { mutableFloatStateOf(300f) }
        val anchor = sel?.let { a ->
            val sd = a.dayList()
            vm.anchorDay?.takeIf { it in sd } ?: if (vm.today in sd) vm.today else sd.first()
        }
        val calloutTop = sel?.let { yOf(it.time) + 27f } ?: 0f
        val contentH = if (sel != null) maxOf(GRID_H, calloutTop + maxOf(calloutH, 420f) + 16f) else GRID_H

        // First open: start around the earliest alarm (or 04:00).
        LaunchedEffect(Unit) {
            val first = vm.alarms.minOfOrNull { it.time }
            val target = if (first != null) yOf(first) - 1.5f * HOUR else 4 * HOUR - 8f
            scroll.scrollTo(with(density) { target.coerceAtLeast(0f).dp.roundToPx() })
        }
        // Keep the open callout (and its dot) on screen.
        LaunchedEffect(vm.ensureTick, calloutH, vm.dragging, sel?.time) {
            if (sel == null || vm.dragging) return@LaunchedEffect
            delay(30)
            val px = { v: Float -> with(density) { v.dp.toPx() } }
            val lo = px(calloutTop - 27f - 22f - 10f)
            val hi = px(calloutTop + calloutH + 12f)
            val vh = scroll.viewportSize.toFloat()
            val cur = scroll.value.toFloat()
            val to = when {
                lo < cur -> lo
                hi > cur + vh -> minOf(lo, hi - vh)
                else -> return@LaunchedEffect
            }
            scroll.animateScrollTo(to.roundToInt().coerceIn(0, scroll.maxValue))
        }

        Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !vm.dragging)) {
            Box(
                Modifier.fillMaxWidth().height(contentH.dp)
                    .pointerInput(colW) {
                        val hourPx = HOUR.dp.toPx()
                        fun point(o: Offset, snapMin: Int): Ghost {
                            val xd = o.x / density.density
                            val yd = o.y / density.density - TOP
                            val d = ((xd - GUT) / colW).toInt().coerceIn(0, 6)
                            var tm = ((yd * density.density / hourPx * 60f) / snapMin).roundToInt() * snapMin
                            tm = tm.coerceIn(0, 1440 - snapMin)
                            return Ghost(d, tm)
                        }
                        fun tapDot(dot: DotUi) {
                            if (vm.sel == dot.aid) { if (vm.settings.allOn) vm.toggleDay(dot.d) }
                            else vm.select(dot.aid, dot.d)
                        }
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val p = down.position
                            val hitR = 24.dp.toPx()
                            val hit = dotsState.value
                                .map { it to hypot(it.x.dp.toPx() - p.x, it.y.dp.toPx() - p.y) }
                                .filter { it.second <= hitR }
                                .minByOrNull { it.second }?.first
                            if (hit != null) {
                                if (hit.draggable) {
                                    // Drag the selected alarm's dot up or down to change its time (5-minute steps).
                                    var dragged = false
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) {
                                            if (!dragged) tapDot(hit)
                                            break
                                        }
                                        ch.consume()
                                        if (!dragged && abs(ch.position.y - p.y) > viewConfiguration.touchSlop) {
                                            dragged = true; vm.dragging = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        if (dragged) {
                                            val g = point(ch.position, 5)
                                            if (g.t != vm.selected?.time) {
                                                vm.setTime(g.t)
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }
                                    }
                                    if (dragged) vm.dragging = false
                                } else if (waitForUpOrCancellation() != null) tapDot(hit)
                                return@awaitEachGesture
                            }
                            // Empty space.
                            if (vm.sel != null || vm.settingsOpen) {
                                if (waitForUpOrCancellation() != null) vm.closeAll()
                                return@awaitEachGesture
                            }
                            var long = false
                            val up = try {
                                withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                            } catch (e: PointerEventTimeoutCancellationException) { long = true; null }
                            if (!long) {
                                if (up != null) { val g = point(p, 15); vm.createAt(g.d, g.t) }
                                return@awaitEachGesture
                            }
                            // Press and hold, then drag, to pick the exact day and time.
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.ghost = point(p, 15); vm.dragging = true
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                ch.consume()
                                if (!ch.pressed) break
                                val g = point(ch.position, 15)
                                if (g != vm.ghost) { vm.ghost = g; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            }
                            vm.dragging = false
                            vm.ghost?.let { vm.createAt(it.d, it.t) }
                        }
                    }
                    .semantics { contentDescription = "Week grid. Tap to add an alarm" },
            ) {
                // Grid dots, now marker, repeat lines, press-and-hold ghost.
                val nowMin = vm.nowMin
                val today = vm.today
                val ghost = vm.ghost
                val starDelays = remember { FloatArray(24 * 7) { 500f + kotlin.random.Random.nextFloat() * 1400f } }
                Canvas(Modifier.matchParentSize()) {
                    val u = density.density
                    val clock = starClock?.invoke()
                    for (h in 0 until 24) for (d in 0..6) {
                        val f = ((d - today + 7) % 7) / 6f
                        var a = 1f; var sc = 1f
                        if (clock != null) {
                            // Each dot lights up at its own random moment, like stars coming out.
                            val k = (clock - starDelays[h * 7 + d]) / 650f
                            if (k <= 0f) continue
                            if (k < 0.45f) { a = k / 0.45f; sc = 0.2f + 2.0f * (k / 0.45f) }
                            else if (k < 1f) sc = 2.2f - 1.2f * ((k - 0.45f) / 0.55f)
                        }
                        val c = t.gridDotFor(f)
                        drawCircle(c.copy(alpha = c.alpha * a), 2 * u * sc, Offset(xOf(d) * u, yOf(h * 60) * u))
                    }
                    val ny = yOf(nowMin) * u
                    drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(4 * u, ny - 5 * u); lineTo(10 * u, ny); lineTo(4 * u, ny + 5 * u); close()
                    }, t.accent)
                    if (ghost != null) {
                        val c = Offset(xOf(ghost.d) * u, yOf(ghost.t) * u)
                        drawCircle(t.ghost, 12 * u, c)
                        drawCircle(t.accent, 11 * u, c, style = Stroke(2 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 3 * u))))
                    }
                }
                segs.forEach { sg ->
                    key("seg", sg.aid) {
                        val y by animateDpAsState(sg.y.dp, if (vm.dragging) snap() else tween(180), label = "segY")
                        Box(
                            Modifier.offset(sg.x0.dp, y - 1.dp).size((sg.x1 - sg.x0).dp, 2.dp)
                                .background(sg.color, RoundedCornerShape(1.dp)),
                        )
                    }
                }
                for (h in 0 until 24) {
                    Text(
                        hourLabel(h, s.clock24),
                        modifier = Modifier.offset(14.dp, (yOf(h * 60) - 8f).dp).width(44.dp),
                        fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp, color = t.muted, textAlign = TextAlign.Center,
                    )
                }
                dots.forEach { dot ->
                    key(dot.aid, dot.d) {
                        Dot(dot, vm.dragging) {
                            if (vm.sel == dot.aid) { if (vm.settings.allOn) vm.toggleDay(dot.d) } else vm.select(dot.aid, dot.d)
                        }
                    }
                }

                vm.ripple?.let { r -> key(r.key) { RippleRing(xOf(r.d), yOf(r.t), t.accent) } }

                if (sel != null && !s.dotsTipDone && !vm.dragging && !setOff) {
                    Row(
                        Modifier.fillMaxWidth().offset(y = (yOf(sel.time) - 52f).dp).wrapContentWidth(Alignment.CenterHorizontally).height(26.dp)
                            .clip(CircleShape).background(t.tint).border(1.dp, t.tintLine, CircleShape).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Hand, t.ink, Modifier.size(13.dp))
                        Text((if (sel.rec == Rec.MULTI) "Tap dots to pick days" else "Tap a dot to pick the day") + " · drag to adjust time", maxLines = 1, fontFamily = Outfit, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.ink)
                    }
                }

                if (sel != null && anchor != null) {
                    key(sel.id) {
                        Callout(
                            vm, sel, top = calloutTop, width = width - 20f,
                            arrowX = (xOf(anchor) - 10f - 8f).coerceIn(16f, width - 20f - 32f),
                            onHeight = { h -> calloutH = h / density.density },
                        )
                    }
                }
            }
        }

        // Undo toast.
        AnimatedVisibility(
            vm.toast != null,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            enter = slideInVertically { it / 2 } + fadeIn(), exit = fadeOut(),
        ) {
            val tst = vm.toast
            var shown by remember { mutableStateOf(tst) }
            if (tst != null) shown = tst
            Row(
                Modifier.fillMaxWidth().height(48.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(t.toastBg)
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(shown?.text ?: "", fontFamily = Outfit, fontSize = 14.sp, color = Color.White)
                if (shown?.undo != null) {
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(CircleShape).clickable(role = Role.Button) { scope.launch { vm.undoToast() } }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Undo", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.toastAction) }
                }
            }
        }
    }
}

@Composable
private fun Dot(dot: DotUi, dragging: Boolean, onTap: () -> Unit) {
    val y by animateDpAsState(dot.y.dp, if (dragging) snap() else tween(180, easing = FastOutSlowInEasing), label = "dotY")
    val size by animateDpAsState(dot.size.dp, tween(150), label = "dotSize")
    Box(
        Modifier.offset(dot.x.dp - 22.dp, y - 22.dp).size(44.dp)
            .semantics { contentDescription = dot.label; onClick { onTap(); true } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size + 8.dp)) {
            val c = center
            val r = size.toPx() / 2
            dot.halo?.let { drawCircle(it, r + 4.dp.toPx(), c) }
            drawCircle(dot.fill, r, c)
            val bw = dot.borderW.dp.toPx()
            drawCircle(
                dot.border, r - bw / 2, c,
                style = Stroke(bw, pathEffect = if (dot.dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx())) else null),
            )
        }
        if (dot.check) Icon(Icons.CheckBold, dot.checkColor, Modifier.size(10.dp))
    }
}

@Composable
private fun RippleRing(x: Float, y: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(600)) }
    val p = anim.value
    Canvas(Modifier.offset((x - 30f).dp, (y - 30f).dp).size(60.dp)) {
        val r = 11.dp.toPx() * (1f + 1.6f * p)
        drawCircle(color.copy(alpha = 0.7f * (1 - p)), r, center, style = Stroke(2.dp.toPx()))
    }
}

// ---------------------------------------------------------------------------------------------
// Edit callout

@Composable
private fun Callout(vm: AlarmViewModel, a: Alarm, top: Float, width: Float, arrowX: Float, onHeight: (Float) -> Unit) {
    val t = T
    val shape = RoundedCornerShape(22.dp)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(220)) }
    Box(
        Modifier.offset(10.dp, top.dp).width(width.dp)
            .onSizeChanged { onHeight(it.height.toFloat()) }
            .alpha(appear.value)
            .offset(y = ((1 - appear.value) * -8f).dp)
            // Swallow touches so they don't reach the grid underneath.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } },
    ) {
        Column(
            Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
                .clip(shape).background(t.panel).border(1.dp, t.panelEdge, shape).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TimeAndName(vm, a)
            RepeatControls(vm, a)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Delete", Icons.Trash, { vm.deleteSelected() }, Modifier.weight(1f).height(52.dp), outline = t.dangerLine, fg = t.danger, fontSize = 15.sp)
                PillButton("Done", Icons.Check, { vm.saveAndClose() }, Modifier.weight(2f).height(52.dp), fill = t.fill, fg = t.onFill)
            }
        }
        // Pointer toward the alarm's dot.
        Canvas(Modifier.offset(arrowX.dp, (-8).dp).size(16.dp).rotate(45f)) {
            drawRect(t.panel)
            val w = 1.dp.toPx()
            drawLine(t.panelEdge, Offset(0f, w / 2), Offset(size.width, w / 2), w)
            drawLine(t.panelEdge, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
        }
    }
}

@Composable
private fun TimeAndName(vm: AlarmViewModel, a: Alarm) {
    val t = T
    val s = vm.settings
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(t.surface2).border(1.dp, t.line, RoundedCornerShape(14.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatStep(Icons.Minus, "1 minute earlier") { vm.step(-1) }
            Text(
                fmtTime(a.time, s.clock24), fontFamily = Outfit, fontSize = if (s.clock24) 30.sp else 24.sp, fontWeight = FontWeight.Medium,
                color = t.ink, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp),
            )
            RepeatStep(Icons.Plus, "1 minute later") { vm.step(1) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (vm.editingName) {
                val focus = remember { FocusRequester() }
                var text by remember { mutableStateOf(a.name) }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(t.surface2)
                        .border(1.5.dp, t.line, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (text.isEmpty()) Text(autoName(a), fontFamily = Outfit, fontSize = 15.sp, color = t.muted, maxLines = 1)
                    var hadFocus by remember { mutableStateOf(false) }
                    BasicTextField(
                        text, { text = it; vm.rename(it) },
                        Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                            if (it.isFocused) hadFocus = true else if (hadFocus) { vm.rename(text.trim()); vm.editingName = false }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Outfit, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.accentText),
                        cursorBrush = SolidColor(t.accentText),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.rename(text.trim()); vm.editingName = false }),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.heightIn(min = 28.dp)) {
                    Text(
                        a.name.ifBlank { autoName(a) }, Modifier.weight(1f, fill = false).padding(top = 4.dp),
                        fontFamily = Outfit, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = t.accentText,
                    )
                    IconButton(Icons.Pencil, "Rename alarm", t.muted, { vm.editingName = true }, Modifier.size(36.dp).offset(y = (-4).dp), iconSize = 16.dp)
                }
            }
            Text(subLine(vm, a), fontFamily = Outfit, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun subLine(vm: AlarmViewModel, a: Alarm): String {
    if (!vm.settings.allOn) return "All alarms are off"
    val next = Occurrences.next(a, vm.settings, vm.now) ?: return ""
    val until = Duration.between(vm.now.withSecond(0).withNano(0), next).toMinutes()
    val k = ChronoUnit.DAYS.between(vm.now.toLocalDate(), next.toLocalDate())
    val day = when (k) { 0L -> "Today"; 1L -> "Tomorrow"; else -> DAYS_SHORT[Occurrences.dow(next.toLocalDate())] }
    val sk = a.skipDate
    return if (sk != null) DAYS_SHORT[Occurrences.dow(sk)] + " skipped · next in " + shortLeft(until)
    else "Next: $day · in " + shortLeft(until)
}

/** A ± button that repeats while held, speeding up. */
@Composable
private fun RepeatStep(icon: IconDef, desc: String, onStep: () -> Unit) {
    val t = T
    val haptic = LocalHapticFeedback.current
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier.size(40.dp, 48.dp)
            .semantics { contentDescription = desc; onClick { step(); true } }
            .pointerInput(Unit) {
                coroutineScopeGesture { pressed ->
                    step(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var wait = 420L; var n = 0
                    while (pressed()) {
                        delay(wait)
                        if (!pressed()) break
                        step(); n++
                        wait = if (n > 12) 40L else if (n > 4) 80L else 140L
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, t.ink, Modifier.size(18.dp)) }
}

/** Runs [block] on press; `pressed()` reports whether the finger is still down. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.coroutineScopeGesture(block: suspend (pressed: () -> Boolean) -> Unit) {
    kotlinx.coroutines.coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            var isDown = true
            val job = launch { block { isDown } }
            waitForUpOrCancellation()
            isDown = false
            job.cancel()
        }
    }
}

@Composable
private fun RepeatControls(vm: AlarmViewModel, a: Alarm) {
    val t = T
    val days = a.days.sorted()
    val segSel = when {
        a.rec == Rec.ONCE -> 0
        a.rec == Rec.WEEKLY -> 1
        days.size == 7 -> 2
        days == listOf(0, 1, 2, 3, 4) -> 3
        else -> -1
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(40.dp).clip(CircleShape).border(1.dp, t.line, CircleShape).padding(3.dp)
                .semantics { contentDescription = "Repeat" },
        ) {
            val segW = maxWidth / 4
            val x by animateDpAsState(segW * maxOf(0, segSel), tween(250, easing = FastOutSlowInEasing), label = "seg")
            if (segSel >= 0) Box(Modifier.offset(x = x).width(segW).fillMaxHeight().clip(CircleShape).background(t.fillSoft))
            Row(Modifier.fillMaxSize()) {
                listOf(
                    "Once" to { vm.setOnce() }, "Weekly" to { vm.setWeekly() },
                    "Daily" to { vm.setDays((0..6).toSet()) }, "Weekdays" to { vm.setDays((0..4).toSet()) },
                ).forEachIndexed { i, (label, pick) ->
                    val on = i == segSel
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable(role = Role.RadioButton) { pick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, fontFamily = Outfit, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) t.onFill else t.soft, maxLines = 1)
                    }
                }
            }
        }
        val rep = a.isRepeating
        val nextSkip = a.skipDate ?: Occurrences.nextUnskipped(a, vm.now)
        Row(Modifier.fillMaxWidth().alpha(if (rep) 1f else 0.5f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TogglePill("Skip holidays", Icons.CalendarCheck, rep && a.skipHol, rep, Modifier.weight(1f)) { vm.toggleSkipHol() }
            TogglePill(
                if (rep && nextSkip != null) "Skip next · " + DAYS_SHORT[Occurrences.dow(nextSkip)] else "Skip next",
                Icons.SkipNext, a.skipDate != null, rep, Modifier.weight(1f),
            ) { vm.toggleSkipNext() }
        }
    }
}

@Composable
private fun TogglePill(text: String, icon: IconDef, on: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = T
    Row(
        modifier.height(36.dp).clip(CircleShape)
            .then(if (on) Modifier.background(t.fillSoft) else Modifier.border(1.5.dp, t.line, CircleShape))
            .clickable(enabled = enabled, role = Role.Switch, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (on) t.onFill else t.soft
        Icon(icon, fg, Modifier.size(16.dp))
        Text(text, fontFamily = Outfit, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
