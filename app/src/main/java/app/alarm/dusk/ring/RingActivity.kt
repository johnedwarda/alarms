package app.alarm.dusk.ring

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec
import app.alarm.dusk.data.Repo
import app.alarm.dusk.ui.Dusk
import app.alarm.dusk.ui.Icon
import app.alarm.dusk.ui.Icons
import app.alarm.dusk.ui.Logo
import app.alarm.dusk.ui.Outfit
import app.alarm.dusk.ui.PillButton
import app.alarm.dusk.ui.duskBackground
import app.alarm.dusk.ui.fmtTime
import app.alarm.dusk.ui.recName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Full-screen ring, shown even over the lock screen. One tap snoozes; dismissing needs a swipe. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = Repo.get(this).data.value.settings
        setContent {
            val alarm by RingService.ringing.collectAsState()
            var shown by remember { mutableStateOf<Alarm?>(null) }
            var dismissed by remember { mutableStateOf(false) }
            LaunchedEffect(alarm) {
                if (alarm != null) shown = alarm
                else if (!dismissed) { delay(300); finish() }
            }
            val a = shown ?: alarm
            if (a != null) {
                RingScreen(
                    a, settings.clock24, settings.snooze, dismissed,
                    onSnooze = { RingService.send(this, RingService.ACTION_SNOOZE); finish() },
                    onDismiss = {
                        app.alarm.dusk.ui.Haptics.dismissed(this)
                        dismissed = true
                        RingService.send(this, RingService.ACTION_DISMISS)
                    },
                    onDismissShown = { finish() },
                )
            }
        }
    }

    @Deprecated("Back shouldn't silently leave a ringing alarm")
    override fun onBackPressed() { /* swallow: snooze or swipe instead */ }
}

@Composable
private fun RingScreen(
    a: Alarm, clock24: Boolean, snoozeMin: Int, dismissed: Boolean,
    onSnooze: () -> Unit, onDismiss: () -> Unit, onDismissShown: () -> Unit,
) {
    val t = Dusk
    val label = a.name.ifBlank { if (a.rec == Rec.MULTI && a.days.size == 7) "Every morning" else recName(a) }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    Box(
        Modifier.fillMaxSize().duskBackground(t, centerY = 0.38f, widthF = 1.3f, heightF = 0.45f, glow = Color(0x4DD61FCB)),
    ) {
        Column(
            Modifier.fillMaxSize().padding(bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Pulse()
            Text(
                fmtTime(a.time, clock24),
                style = TextStyle(
                    fontFamily = Outfit, fontSize = 76.sp, fontWeight = FontWeight.Medium, color = t.ink,
                    letterSpacing = (-0.76).sp, shadow = Shadow(Color(0x73F23FE4), blurRadius = 48f),
                ),
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(label, fontFamily = Outfit, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = t.accentText, modifier = Modifier.padding(top = 14.dp))
            Text(date, fontFamily = Outfit, fontSize = 14.sp, color = t.muted, modifier = Modifier.padding(top = 6.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth().height(128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!dismissed) {
                PillButton(
                    "Snooze $snoozeMin min", Icons.Snooze, onSnooze,
                    Modifier.fillMaxWidth().height(52.dp), outline = Color(0x73F23FE4), fg = t.accentText,
                )
                SwipeTrack(onDismiss)
            } else {
                LaunchedEffect(Unit) { delay(1200); onDismissShown() }
                Icon(Icons.CheckCircle, t.accentText, Modifier.size(40.dp))
                Text("Alarm dismissed", fontFamily = Outfit, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = t.ink)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Pulse() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Box(Modifier.size(168.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 88f / 168f)
            for (ph in listOf(p, (p + 0.5f) % 1f)) {
                val r = (40f + 40f * ph).dp.toPx()
                drawCircle(Color(0xFFF23FE4).copy(alpha = 0.6f * (1 - ph)), r, c, style = Stroke(2.dp.toPx()))
            }
        }
        Logo(Modifier.size(72.dp).offset(y = 4.dp))
    }
}

@Composable
private fun SwipeTrack(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val density = LocalDensity.current
    val inf = rememberInfiniteTransition(label = "shimmer")
    val sh by inf.animateFloat(1f, -1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "s")
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
            .padding(6.dp),
    ) {
        val maxPx = with(density) { (maxWidth - 50.dp).toPx() }
        val knobPx = with(density) { 50.dp.toPx() }
        // Fill trailing the knob.
        Box(
            Modifier.fillMaxHeight().width(with(density) { (x.value + knobPx).toDp() }).clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(Color(0x59C618C4), Color(0x269C12D6)))),
        )
        val hint = (1f - x.value / with(density) { 140.dp.toPx() }).coerceIn(0f, 1f)
        Row(Modifier.fillMaxSize().alpha(hint).padding(start = 40.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Swipe to dismiss",
                style = TextStyle(
                    fontFamily = Outfit, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    brush = Brush.linearGradient(
                        0f to Color(0xFFB396C0), 0.4f to Color(0xFFB396C0), 0.5f to Color.White, 0.6f to Color(0xFFB396C0), 1f to Color(0xFFB396C0),
                        start = Offset(sh * 400f, 0f), end = Offset(sh * 400f + 400f, 0f),
                    ),
                ),
            )
        }
        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), 0) }
                .size(50.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFC618C4), Color(0xFF9C12D6)), Offset.Zero, Offset.Infinite))
                .semantics {
                    contentDescription = "Swipe right to dismiss the alarm"
                    onClick("Dismiss") { onDismiss(); true }
                }
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (x.value > maxPx * 0.85f) { x.animateTo(maxPx); onDismiss() }
                                else x.animateTo(0f, tween(300))
                            }
                        },
                        onDragCancel = { scope.launch { x.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { x.snapTo((x.value + drag).coerceIn(0f, maxPx)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.ArrowRight, Color.White, Modifier.size(22.dp)) }
    }
}
