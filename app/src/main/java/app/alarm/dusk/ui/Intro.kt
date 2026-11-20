package app.alarm.dusk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class Slide(val title: String, val body: String, val art: @Composable () -> Unit)

/** The four-slide "How it works" walkthrough, shown on first launch. */
@Composable
fun Intro(vm: AlarmViewModel) {
    val t = T
    val slides = listOf(
        Slide("Tap to add", "Tap an empty spot to set an alarm there, then drag it to adjust the time.") { ArtTap() },
        Slide("Your week at a glance", "Every alarm sits on one grid: days across the top, hours down the side. A row of dots repeats, a single dot is weekly, a ring rings once.") { ArtWeek() },
        Slide("Repeat your way", "Choose once, weekly, daily or weekdays, then tap the dots on the grid to pick exactly which days.") { ArtRepeat() },
        Slide("Skip, don’t switch off", "Skip the next ring or public holidays. The skipped day fades, and the alarm turns itself back on afterwards.") { ArtSkip() },
    )
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().duskBackground(t)
            .clickable(remember { MutableInteractionSource() }, null) { }
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(68.dp).padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Logo(Modifier.size(40.dp))
            Box(
                Modifier.alpha(if (pager.currentPage < slides.lastIndex) 1f else 0f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = pager.currentPage < slides.lastIndex, role = Role.Button) { vm.finishIntro() }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Skip", fontFamily = Outfit, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = t.ink) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val sl = slides[i]
            Column(Modifier.fillMaxSize().padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(240.dp)) { sl.art() }
                Text(sl.title, Modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp), fontFamily = Outfit, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, color = t.ink, textAlign = TextAlign.Center)
                Text(sl.body, Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp).widthIn(max = 320.dp), fontFamily = Outfit, fontSize = 15.sp, lineHeight = 22.sp, color = t.muted, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            repeat(slides.size) { i ->
                val on = i == pager.currentPage
                val w by animateDpAsState(if (on) 20.dp else 6.dp, tween(250), label = "dotW")
                Box(Modifier.size(w, 6.dp).clip(CircleShape).background(if (on) t.accent else t.gridDot))
            }
        }
        val last = pager.currentPage == slides.lastIndex
        PillButton(
            if (last) "Get started" else "Next", Icons.ArrowNext,
            { if (last) vm.finishIntro() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().height(48.dp),
            fill = t.fill, fg = t.onFill, fontSize = 15.sp,
        )
    }
}

// ---------- slide illustrations: a slice of the real grid ----------

@Composable
private fun MiniGrid(hours: List<Pair<String, Float>>, draw: DrawScope.(xOf: (Int) -> Float, u: Float, p: Float) -> Unit) {
    val t = T
    val inf = rememberInfiniteTransition(label = "art")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "p")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value
        val colW = (w - 62f) / 7f
        val xOf = { d: Int -> 52f + d * colW + colW / 2 }
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 52.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            DAYS_SHORT.forEach { Text(it.uppercase(), Modifier.weight(1f), fontFamily = Outfit, fontSize = 12.sp, color = t.muted, textAlign = TextAlign.Center) }
        }
        hours.forEach { (label, y) ->
            Text(label, Modifier.offset(14.dp, (y - 8f).dp).width(44.dp), fontFamily = Outfit, fontSize = 12.sp, lineHeight = 16.sp, color = t.muted, textAlign = TextAlign.Center)
        }
        Canvas(Modifier.fillMaxSize()) {
            val u = density
            hours.forEach { (_, y) -> for (d in 0..6) drawCircle(t.gridDotFor(d / 6f), 2 * u, Offset(xOf(d) * u, y * u)) }
            draw(xOf, u, p)
        }
    }
}

private fun DrawScope.dot(c: Offset, r: Float, fill: Color, border: Color, bw: Float, dashed: Boolean = false) {
    drawCircle(fill, r, c)
    drawCircle(border, r - bw / 2, c, style = Stroke(bw, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(bw * 1.5f, bw * 1.25f)) else null))
}

@Composable
private fun ArtTap() {
    val t = T
    MiniGrid(listOf("07:00" to 64f, "08:00" to 112f, "09:00" to 160f)) { xOf, u, p ->
        val c = Offset(xOf(3) * u, 112f * u)
        // Press, ripple, then the alarm settles as a ring (rings once).
        val ph = (p * 1.6f).coerceAtMost(1f)
        drawCircle(t.accent.copy(alpha = 0.7f * (1 - ph)), 12 * u * (1 + 1.4f * ph), c, style = Stroke(2 * u))
        if (p < 0.55f) {
            drawCircle(t.ghost, 12 * u, c)
            drawCircle(t.accent, 11 * u, c, style = Stroke(2 * u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * u, 3 * u))))
            drawCircle(Color.White.copy(alpha = 0.28f), 15 * u, c)
        } else {
            dot(c, 9 * u, t.ground, t.accent, 3 * u)
        }
    }
}

@Composable
private fun ArtWeek() {
    val t = T
    MiniGrid(listOf("07:00" to 64f, "08:00" to 112f, "09:00" to 160f)) { xOf, u, p ->
        val y = 64f * u
        val shown = (p * 10).toInt().coerceAtMost(7)
        val n = shown.coerceAtMost(5)
        if (n > 1) drawLine(t.accent, Offset(xOf(0) * u, y), Offset(xOf(n - 1) * u, y), 2 * u)
        for (d in 0 until n) dot(Offset(xOf(d) * u, y), 9 * u, t.accent, t.accent, 2.5f * u)
        if (shown >= 6) dot(Offset(xOf(5) * u, 112f * u), 9 * u, t.accent, t.accent, 2.5f * u)
        if (shown >= 7) dot(Offset(xOf(3) * u, 160f * u), 9 * u, t.ground, t.accent, 3 * u)
    }
}

@Composable
private fun ArtRepeat() {
    val t = T
    MiniGrid(listOf("09:00" to 116f)) { xOf, u, p ->
        val y = 116f * u
        val satOn = p in 0.3f..0.75f
        val last = if (satOn) 5 else 4
        drawLine(t.accentText, Offset(xOf(0) * u, y), Offset(xOf(last) * u, y), 2 * u)
        for (d in 0..6) {
            val on = d <= last
            val c = Offset(xOf(d) * u, y)
            if (on) {
                drawCircle(t.halo, 15 * u, c)
                dot(c, 11 * u, t.accentText, t.accentText, 2.5f * u)
                drawLine(t.ground, Offset(c.x - 3 * u, c.y), Offset(c.x - 1 * u, c.y + 2 * u), 1.6f * u)
                drawLine(t.ground, Offset(c.x - 1 * u, c.y + 2 * u), Offset(c.x + 3.5f * u, c.y - 2.5f * u), 1.6f * u)
            } else dot(c, 9 * u, Color.Transparent, t.accentText, 2 * u, dashed = true)
        }
    }
}

@Composable
private fun ArtSkip() {
    val t = T
    MiniGrid(listOf("07:00" to 72f)) { xOf, u, p ->
        val y = 72f * u
        val skipped = p in 0.3f..0.75f
        drawLine(t.accentText, Offset(xOf(0) * u, y), Offset(xOf(4) * u, y), 2 * u)
        for (d in 0..4) {
            val c = Offset(xOf(d) * u, y)
            drawCircle(t.halo, 15 * u, c)
            if (d == 2 && skipped) dot(c, 11 * u, t.introSkipFill, t.accentText, 1 * u)
            else dot(c, 11 * u, t.accentText, t.accentText, 2.5f * u)
        }
        for (d in 5..6) dot(Offset(xOf(d) * u, y), 9 * u, Color.Transparent, t.accentText, 2 * u, dashed = true)
    }
}
