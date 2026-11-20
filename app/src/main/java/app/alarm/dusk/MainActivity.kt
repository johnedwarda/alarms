package app.alarm.dusk

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.alarm.dusk.data.ThemeMode
import app.alarm.dusk.ring.RingService
import app.alarm.dusk.schedule.Scheduler
import app.alarm.dusk.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RingService.ensureChannel(this)
        Scheduler.rescheduleAll(this)
        setContent {
            val sysDark = isSystemInDarkTheme()
            val tokens = when (vm.settings.theme) {
                ThemeMode.SYSTEM -> if (sysDark) Dusk else Light
                ThemeMode.DUSK -> Dusk
                ThemeMode.LIGHT -> Light
            }
            LaunchedEffect(tokens.dark) {
                val style = if (tokens.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            // Keep "next in …" honest when coming back to the app.
            val owner = LocalLifecycleOwner.current
            LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshNow() } }

            CompositionLocalProvider(LocalTokens provides tokens) { AlarmApp(vm) }
        }
    }
}

@Composable
fun AlarmApp(vm: AlarmViewModel) {
    val t = T
    BackHandler(vm.sel != null || vm.settingsOpen) {
        if (vm.settingsOpen && vm.settingsView == SettingsView.HOLIDAYS) vm.settingsView = SettingsView.MAIN else vm.closeAll()
    }
    // Entrance: header drops in, the day row follows, then the grid rises with a slight zoom.
    val clock = remember { androidx.compose.animation.core.Animatable(10_000f) }
    LaunchedEffect(vm.enterKey) {
        if (vm.enterKey > 0) {
            clock.snapTo(0f)
            clock.animateTo(2400f, androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    val ease = remember { androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    fun phase(delay: Float, dur: Float) = ease.transform(((clock.value - delay) / dur).coerceIn(0f, 1f))
    Box(Modifier.fillMaxSize().duskBackground(t)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.graphicsLayer { val p = phase(150f, 700f); alpha = p; translationY = (1 - p) * (-28).dp.toPx() }) { Header(vm) }
            PermissionBanner()
            Box(Modifier.graphicsLayer { val p = phase(290f, 700f); alpha = p; translationY = (1 - p) * (-18).dp.toPx() }) { DayRow(vm) }
            Box(
                Modifier.weight(1f).fillMaxWidth().graphicsLayer {
                    val p = phase(420f, 900f)
                    alpha = p; translationY = (1 - p) * 90.dp.toPx()
                    scaleX = 0.94f + 0.06f * p; scaleY = scaleX
                },
            ) { WeekGrid(vm, starClock = if (vm.enterStars && clock.value < 2400f) ({ clock.value }) else null) }
        }
        if (vm.settingsOpen) SettingsPopup(vm)
        if (vm.introOpen) {
            BackHandler { vm.finishIntro() }
            Intro(vm)
        }
    }
}

/** Alarms can only wake you if notifications (and, on Android 14+, full-screen alerts) are allowed. */
@Composable
private fun PermissionBanner() {
    val t = T
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    val needNotif = remember(tick) {
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val needFull = remember(tick) {
        Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(needNotif) {
        if (needNotif && !asked && Build.VERSION.SDK_INT >= 33) { asked = true; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (!needNotif && !needFull) return
    val (text, action) = if (needNotif) {
        "Allow notifications so alarms can ring" to {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    } else {
        "Allow full-screen alerts so alarms show on the lock screen" to {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)))
        }
    }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(t.tint).border(1.dp, t.tintLine, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { runCatching { action() } }.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, Modifier.weight(1f), fontFamily = Outfit, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.ink)
        Icon(Icons.ChevronRight, t.accentText, Modifier.size(16.dp))
    }
}
