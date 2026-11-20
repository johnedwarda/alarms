package app.alarm.dusk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.alarm.dusk.data.Holidays
import app.alarm.dusk.data.Sound
import app.alarm.dusk.data.ThemeMode
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsPopup(vm: AlarmViewModel) {
    val t = T
    Box(Modifier.fillMaxSize()) {
        // Tapping outside closes it.
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { vm.toggleSettings() },
        )
        Box(Modifier.statusBarsPadding().padding(top = 70.dp, start = 10.dp, end = 10.dp).fillMaxWidth()) {
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 740.dp)
                    .shadow(16.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
                    .clip(shape).background(t.panel).border(1.dp, t.panelEdge, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                AnimatedContent(
                    vm.settingsView,
                    transitionSpec = {
                        val dir = if (targetState == SettingsView.HOLIDAYS) 1 else -1
                        (slideInHorizontally { dir * it / 14 } + fadeIn()) togetherWith fadeOut()
                    },
                    label = "settingsView",
                ) { view ->
                    if (view == SettingsView.MAIN) MainSettings(vm) else HolidaySettings(vm)
                }
                PillButton("Done", Icons.Check, { vm.toggleSettings() }, Modifier.padding(top = 16.dp).fillMaxWidth().height(44.dp), fill = t.fill, fg = t.onFill, fontSize = 15.sp)
            }
            Canvas(Modifier.align(Alignment.TopEnd).offset(x = (-12).dp, y = (-8).dp).size(16.dp).rotate(45f)) {
                drawRect(t.panel)
                val w = 1.dp.toPx()
                drawLine(t.panelEdge, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                drawLine(t.panelEdge, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
            }
        }
    }
}

@Composable
private fun MainSettings(vm: AlarmViewModel) {
    val t = T
    val s = vm.settings
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SETTINGS", fontFamily = Outfit, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = t.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("All alarms", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                DuskSwitch(s.allOn, "All alarms") { vm.updateSettings { it.copy(allOn = !it.allOn) } }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.line))
        Dropdown(
            "Theme", s.theme, listOf(ThemeMode.SYSTEM to "System default", ThemeMode.DUSK to "Dusk", ThemeMode.LIGHT to "Light"),
        ) { v -> vm.updateSettings { it.copy(theme = v) } }
        Dropdown("Time format", s.clock24, listOf(true to "24-hour", false to "12-hour")) { v -> vm.updateSettings { it.copy(clock24 = v) } }
        Dropdown("Sound", s.sound, Sound.entries.map { it to it.label }) { v -> vm.pickSound(v) }
        Dropdown("Snooze", s.snooze, listOf(5, 10, 15, 20).map { it to "$it minutes" }) { v -> vm.updateSettings { it.copy(snooze = v) } }
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Vibration", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.ink)
            DuskSwitch(s.vibrate, "Vibration") { vm.updateSettings { it.copy(vibrate = !it.vibrate) } }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2)
                .border(1.dp, t.line, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { vm.settingsView = SettingsView.HOLIDAYS }
                .padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Public holidays", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.ink)
                Text("Finland · ${s.holSkip.count { it }} of ${Holidays.list.size} skipped", fontFamily = Outfit, fontSize = 12.sp, color = t.muted)
            }
            Icon(Icons.ChevronRight, t.muted)
        }
        Row(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { vm.openIntro() }.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Help, t.accentText)
            Text("How it works", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = t.accentText)
        }
    }
}

@Composable
private fun <V> Dropdown(label: String, value: V, options: List<Pair<V, String>>, onPick: (V) -> Unit) {
    val t = T
    var open by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (open) 180f else 0f, label = "chev")
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.ink)
        Box {
            Row(
                Modifier.size(170.dp, 44.dp).clip(RoundedCornerShape(12.dp)).background(t.surface2)
                    .border(1.5.dp, if (open) t.accent else t.line, RoundedCornerShape(12.dp))
                    .clickable(role = Role.DropdownList) { open = !open }
                    .padding(start = 14.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(options.find { it.first == value }?.second ?: "", fontFamily = Outfit, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = t.ink, maxLines = 1)
                Icon(Icons.ChevronDown, t.muted, Modifier.size(16.dp).rotate(rot))
            }
            DropdownMenu(
                open, { open = false },
                Modifier.width(170.dp), offset = DpOffset(0.dp, 6.dp),
                shape = RoundedCornerShape(14.dp), containerColor = t.menu,
                border = androidx.compose.foundation.BorderStroke(1.dp, t.panelEdge),
            ) {
                options.forEach { (v, name) ->
                    val selected = v == value
                    DropdownMenuItem(
                        text = { Text(name, fontFamily = Outfit, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = t.ink) },
                        trailingIcon = if (selected) ({ Icon(Icons.Check, t.accentText, Modifier.size(16.dp)) }) else null,
                        onClick = { onPick(v); open = false },
                        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).then(if (selected) Modifier.background(t.optSel) else Modifier),
                        colors = MenuDefaults.itemColors(textColor = t.ink),
                    )
                }
            }
        }
    }
}

@Composable
private fun HolidaySettings(vm: AlarmViewModel) {
    val t = T
    val s = vm.settings
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.heightIn(min = 44.dp).offset(x = (-4).dp).clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { vm.settingsView = SettingsView.MAIN }.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(Icons.ChevronLeft, t.accentText, Modifier.size(20.dp))
                Text("Settings", fontFamily = Outfit, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.accentText)
            }
            Text("PUBLIC HOLIDAYS", fontFamily = Outfit, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = t.muted)
        }
        Text(
            "Finland. Alarms with “Skip holidays” turned on stay silent on the holidays marked Skip. Tap to let one ring.",
            fontFamily = Outfit, fontSize = 12.sp, lineHeight = 17.sp, color = t.muted,
        )
        Column {
            Holidays.list.forEachIndexed { i, h ->
                val on = s.holSkip.getOrElse(i) { true }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).drawBottomLine(t.line),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(h.date.format(fmt), Modifier.width(78.dp), fontFamily = Outfit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
                    Text(h.name, Modifier.weight(1f), fontFamily = Outfit, fontSize = 13.sp, color = t.ink)
                    Box(
                        Modifier.height(30.dp).clip(CircleShape)
                            .then(if (on) Modifier.background(t.fill) else Modifier.background(t.surface2).border(1.5.dp, t.line, CircleShape))
                            .clickable(role = Role.Switch) {
                                vm.updateSettings { st -> st.copy(holSkip = st.holSkip.toMutableList().also { it[i] = !it[i] }) }
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Skip", fontFamily = Outfit, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) t.onFill else t.muted) }
                }
            }
        }
    }
}

private fun Modifier.drawBottomLine(c: androidx.compose.ui.graphics.Color) = this.then(
    Modifier.drawBehind {
        drawLine(c, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
    },
)
