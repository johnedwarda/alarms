package app.alarm.dusk.data

import java.time.LocalDate

/** How an alarm repeats. Days are indexed 0 = Monday … 6 = Sunday. */
enum class Rec { ONCE, WEEKLY, MULTI }

data class Alarm(
    val id: Int,
    /** Minutes after midnight, 0..1439. */
    val time: Int,
    val rec: Rec,
    /** The day for ONCE and WEEKLY alarms. */
    val day: Int,
    /** The days for MULTI alarms (daily, weekdays or any chosen set). */
    val days: Set<Int> = emptySet(),
    val name: String = "",
    /** Skip public holidays marked "Skip" in settings. */
    val skipHol: Boolean = false,
    /** A single upcoming date to stay silent on ("Skip next"). */
    val skipDate: LocalDate? = null,
) {
    fun dayList(): List<Int> = when (rec) {
        Rec.MULTI -> days.sorted()
        else -> listOf(day)
    }

    val isRepeating get() = rec != Rec.ONCE
}

enum class ThemeMode { SYSTEM, DUSK, LIGHT }

enum class Sound(val label: String) {
    SUNRISE("Sunrise"),
    BRIGHT("Bright morning"),
    CHIMES("Soft chimes"),
    BELL("Classic bell"),
    SILENT("Silent"),
}

data class Settings(
    val allOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.DUSK,
    val clock24: Boolean = true,
    val sound: Sound = Sound.SUNRISE,
    val snooze: Int = 10,
    val vibrate: Boolean = true,
    /** Parallel to [Holidays.list]: true = alarms with skipHol stay silent that day. */
    val holSkip: List<Boolean> = List(Holidays.list.size) { true },
    val introSeen: Boolean = false,
    val dotsTipDone: Boolean = false,
)

data class AppData(
    val alarms: List<Alarm> = emptyList(),
    val settings: Settings = Settings(),
    val nextId: Int = 1,
)
