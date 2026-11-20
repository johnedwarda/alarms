package app.alarm.dusk.data

import android.content.Context
import app.alarm.dusk.schedule.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Single source of truth for alarms and settings, persisted as JSON in SharedPreferences. */
class Repo private constructor(private val app: Context) {

    private val prefs = app.getSharedPreferences("alarm_store", Context.MODE_PRIVATE)
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    @Synchronized
    fun update(fn: (AppData) -> AppData) {
        val next = fn(_data.value)
        if (next == _data.value) return
        _data.value = next
        save(next)
        Scheduler.rescheduleAll(app, next)
    }

    fun alarm(id: Int): Alarm? = _data.value.alarms.find { it.id == id }

    /** Drops "skip next" dates that are already in the past. */
    fun clearStaleSkips() = update { d ->
        val today = LocalDate.now()
        d.copy(alarms = d.alarms.map { a ->
            if (a.skipDate != null && a.skipDate.isBefore(today)) a.copy(skipDate = null) else a
        })
    }

    private fun load(): AppData {
        val raw = prefs.getString("data", null) ?: return AppData()
        return try {
            val o = JSONObject(raw)
            val alarms = o.getJSONArray("alarms").let { arr ->
                (0 until arr.length()).map { i -> alarmFrom(arr.getJSONObject(i)) }
            }
            AppData(alarms, settingsFrom(o.getJSONObject("settings")), o.optInt("nextId", 1))
        } catch (e: Exception) {
            AppData()
        }
    }

    private fun save(d: AppData) {
        val o = JSONObject()
        o.put("nextId", d.nextId)
        o.put("alarms", JSONArray().apply { d.alarms.forEach { put(alarmTo(it)) } })
        o.put("settings", settingsTo(d.settings))
        prefs.edit().putString("data", o.toString()).apply()
    }

    private fun alarmTo(a: Alarm) = JSONObject().apply {
        put("id", a.id); put("time", a.time); put("rec", a.rec.name); put("day", a.day)
        put("days", JSONArray(a.days.sorted())); put("name", a.name); put("skipHol", a.skipHol)
        a.skipDate?.let { put("skipDate", it.toString()) }
    }

    private fun alarmFrom(o: JSONObject): Alarm {
        val days = o.optJSONArray("days")?.let { arr -> (0 until arr.length()).map { arr.getInt(it) }.toSet() } ?: emptySet()
        return Alarm(
            id = o.getInt("id"), time = o.getInt("time"), rec = Rec.valueOf(o.getString("rec")),
            day = o.getInt("day"), days = days, name = o.optString("name", ""),
            skipHol = o.optBoolean("skipHol", false),
            skipDate = o.optString("skipDate", "").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
        )
    }

    private fun settingsTo(s: Settings) = JSONObject().apply {
        put("allOn", s.allOn); put("theme", s.theme.name); put("clock24", s.clock24)
        put("sound", s.sound.name); put("snooze", s.snooze); put("vibrate", s.vibrate)
        put("holSkip", JSONArray(s.holSkip)); put("introSeen", s.introSeen); put("dotsTipDone", s.dotsTipDone)
    }

    private fun settingsFrom(o: JSONObject): Settings {
        val def = Settings()
        val hs = o.optJSONArray("holSkip")?.let { arr -> (0 until arr.length()).map { arr.getBoolean(it) } }
        return Settings(
            allOn = o.optBoolean("allOn", def.allOn),
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(def.theme),
            clock24 = o.optBoolean("clock24", def.clock24),
            sound = runCatching { Sound.valueOf(o.getString("sound")) }.getOrDefault(def.sound),
            snooze = o.optInt("snooze", def.snooze),
            vibrate = o.optBoolean("vibrate", def.vibrate),
            holSkip = if (hs != null && hs.size == Holidays.list.size) hs else def.holSkip,
            introSeen = o.optBoolean("introSeen", false),
            dotsTipDone = o.optBoolean("dotsTipDone", false),
        )
    }

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(context: Context): Repo = instance ?: synchronized(this) {
            instance ?: Repo(context.applicationContext).also { instance = it }
        }
    }
}
