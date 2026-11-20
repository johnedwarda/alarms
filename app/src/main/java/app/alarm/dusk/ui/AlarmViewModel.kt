package app.alarm.dusk.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec
import app.alarm.dusk.data.Repo
import app.alarm.dusk.data.Settings
import app.alarm.dusk.data.Sound
import app.alarm.dusk.ring.ToneEngine
import app.alarm.dusk.schedule.Occurrences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class Ghost(val d: Int, val t: Int)
data class Ripple(val d: Int, val t: Int, val key: Long = System.nanoTime())
data class Toast(val text: String, val undo: (() -> Unit)?, val key: Long = System.nanoTime())

enum class SettingsView { MAIN, HOLIDAYS }

class AlarmViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo.get(app)

    var data by mutableStateOf(repo.data.value); private set
    /** A new alarm that only becomes real when "Done" is pressed. */
    var draft by mutableStateOf<Alarm?>(null); private set
    var sel by mutableStateOf<Int?>(null); private set
    var anchorDay by mutableStateOf<Int?>(null); private set
    var settingsOpen by mutableStateOf(false); private set
    var settingsView by mutableStateOf(SettingsView.MAIN)
    var ghost by mutableStateOf<Ghost?>(null)
    var dragging by mutableStateOf(false)
    var ripple by mutableStateOf<Ripple?>(null); private set
    var toast by mutableStateOf<Toast?>(null); private set
    var editingName by mutableStateOf(false)
    var introOpen by mutableStateOf(!repo.data.value.settings.introSeen); private set
    var now by mutableStateOf(LocalDateTime.now()); private set
    /** Bumped each time the app's entrance animation should play (every open, after the intro). */
    var enterKey by mutableStateOf(0); private set
    /** Whether this entrance also lights the grid dots up like stars (first entrance only). */
    var enterStars by mutableStateOf(false); private set

    /** Bumped when the callout should scroll into view. */
    var ensureTick by mutableStateOf(0); private set

    private var toastJob: Job? = null
    private var preview: ToneEngine? = null

    init {
        repo.clearStaleSkips()
        if (!introOpen) playEnter()
        viewModelScope.launch { repo.data.collectLatest { data = it } }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                now = LocalDateTime.now()
            }
        }
    }

    fun refreshNow() { now = LocalDateTime.now() }

    val settings: Settings get() = data.settings
    val alarms: List<Alarm> get() = data.alarms + listOfNotNull(draft)
    val selected: Alarm? get() = sel?.let { id -> alarms.find { it.id == id } }
    val today: Int get() = Occurrences.dow(now.toLocalDate())
    val nowMin: Int get() = now.hour * 60 + now.minute

    // ---------- selection ----------

    fun createAt(d: Int, t: Int) {
        discardDraft()
        val a = Alarm(id = data.nextId, time = t, rec = Rec.ONCE, day = d)
        draft = a
        sel = a.id; anchorDay = d; editingName = false; ghost = null
        rippleAt(d, t)
        ensureTick++
    }

    fun select(id: Int, day: Int) {
        if (sel == id) return
        discardDraft()
        sel = id; anchorDay = day; editingName = false; settingsOpen = false
        ensureTick++
    }

    fun closeAll() {
        discardDraft()
        sel = null; settingsOpen = false; editingName = false
    }

    fun saveAndClose() {
        val cur = selected
        if (cur != null) {
            Haptics.saved(getApplication())
            val days = cur.dayList()
            rippleAt(if (today in days) today else days.first(), cur.time)
        }
        draft?.let { d ->
            repo.update { it.copy(alarms = it.alarms + d, nextId = maxOf(it.nextId, d.id + 1)) }
            draft = null
        }
        sel = null; editingName = false
    }

    private fun discardDraft() {
        val d = draft ?: return
        draft = null
        if (sel == d.id) sel = null
        showToast("New alarm discarded") {
            draft = d; sel = d.id; anchorDay = d.day; settingsOpen = false; ensureTick++
        }
    }

    fun deleteSelected() {
        val a = selected ?: return
        Haptics.deleted(getApplication())
        sel = null; editingName = false
        if (draft?.id == a.id) {
            draft = null
            showToast("Alarm deleted") { draft = a; sel = a.id; anchorDay = a.day; ensureTick++ }
            return
        }
        val idx = data.alarms.indexOfFirst { it.id == a.id }
        repo.update { it.copy(alarms = it.alarms.filterNot { x -> x.id == a.id }) }
        showToast("Alarm deleted") {
            repo.update { d ->
                val l = d.alarms.toMutableList(); l.add(idx.coerceIn(0, l.size), a); d.copy(alarms = l)
            }
        }
    }

    /** Opens the alarm shown in the header. */
    fun openNext(id: Int, day: Int) {
        if (settingsOpen) settingsOpen = false
        if (sel == id) { anchorDay = day; ensureTick++; return }
        select(id, day)
    }

    // ---------- editing the selected alarm ----------

    fun edit(fn: (Alarm) -> Alarm) {
        val id = sel ?: return
        val d = draft
        if (d != null && d.id == id) { draft = fn(d); return }
        repo.update { data -> data.copy(alarms = data.alarms.map { if (it.id == id) fn(it) else it }) }
    }

    fun step(dm: Int) = edit { it.copy(time = (it.time + dm + 1440) % 1440) }

    fun setTime(t: Int) = edit { if (it.time == t) it else it.copy(time = t) }

    fun setOnce() = edit { it.copy(rec = Rec.ONCE, day = singleDay(it), skipDate = null, skipHol = false) }
    fun setWeekly() = edit { it.copy(rec = Rec.WEEKLY, day = singleDay(it), skipDate = null) }
    fun setDays(days: Set<Int>) = edit { it.copy(rec = Rec.MULTI, days = days, skipDate = null) }

    private fun singleDay(a: Alarm) = if (a.rec == Rec.MULTI) (anchorDay?.takeIf { it in a.days } ?: a.days.minOrNull() ?: a.day) else a.day

    /** Tapping a day's dot while the alarm is selected. */
    fun toggleDay(d: Int) {
        val a = selected ?: return
        val onDay = d in a.dayList()
        val single = a.rec != Rec.MULTI
        if (!settings.dotsTipDone && !(single && onDay)) updateSettings { it.copy(dotsTipDone = true) }
        if (single) {
            if (a.day != d) { edit { it.copy(day = d, skipDate = null) }; anchorDay = d; rippleAt(d, a.time) }
            return
        }
        val has = d in a.days
        if (has && a.days.size == 1) return
        if (!has) { rippleAt(d, a.time); anchorDay = d }
        else if (anchorDay == d) anchorDay = null
        edit { it.copy(days = if (has) it.days - d else it.days + d, skipDate = null) }
    }

    fun toggleSkipNext() = edit { a ->
        if (!a.isRepeating) a
        else if (a.skipDate != null) a.copy(skipDate = null)
        else a.copy(skipDate = Occurrences.nextUnskipped(a, now))
    }

    fun toggleSkipHol() = edit { if (it.isRepeating) it.copy(skipHol = !it.skipHol) else it }

    fun rename(name: String) = edit { it.copy(name = name) }

    // ---------- settings ----------

    fun toggleSettings() {
        if (!settingsOpen) { discardDraft(); sel = null; editingName = false; settingsView = SettingsView.MAIN }
        settingsOpen = !settingsOpen
    }

    fun updateSettings(fn: (Settings) -> Settings) = repo.update { it.copy(settings = fn(it.settings)) }

    fun pickSound(s: Sound) {
        updateSettings { it.copy(sound = s) }
        preview?.stop()
        preview = ToneEngine(s).also { it.start(rampMs = 0, limitMs = 2600) }
    }

    fun openIntro() { settingsOpen = false; introOpen = true; updateSettings { it.copy(dotsTipDone = false) } }
    fun finishIntro() { introOpen = false; updateSettings { it.copy(introSeen = true) }; playEnter() }

    private fun playEnter() {
        // Stars only on the very first entrance; replays from Settings › How it works skip them.
        enterStars = !starsShown
        starsShown = true
        enterKey++
    }

    companion object { private var starsShown = false }

    // ---------- feedback ----------

    fun rippleAt(d: Int, t: Int) { ripple = Ripple(d, t) }

    fun showToast(text: String, undo: (() -> Unit)?) {
        toastJob?.cancel()
        val tst = Toast(text, undo)
        toast = tst
        toastJob = viewModelScope.launch { delay(4000); if (toast?.key == tst.key) toast = null }
    }

    fun undoToast() {
        val u = toast?.undo
        toast = null
        u?.invoke()
    }

    override fun onCleared() { preview?.stop() }
}
