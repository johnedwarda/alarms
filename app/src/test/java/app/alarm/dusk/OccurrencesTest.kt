package app.alarm.dusk

import app.alarm.dusk.data.Alarm
import app.alarm.dusk.data.Rec
import app.alarm.dusk.data.Settings
import app.alarm.dusk.schedule.Occurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OccurrencesTest {
    private val s = Settings()
    // Sunday 15 Nov 2026, 10:00.
    private val now = LocalDateTime.of(2026, 11, 15, 10, 0)

    @Test fun onceLaterTodayRingsToday() {
        val a = Alarm(1, 11 * 60, Rec.ONCE, day = 6) // Sunday 11:00
        assertEquals(LocalDateTime.of(2026, 11, 15, 11, 0), Occurrences.next(a, s, now))
    }

    @Test fun onceEarlierTodayRingsNextWeek() {
        val a = Alarm(1, 9 * 60, Rec.ONCE, day = 6) // Sunday 09:00 already passed
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 0), Occurrences.next(a, s, now))
    }

    @Test fun exactlyNowIsNotNext() {
        val a = Alarm(1, 10 * 60, Rec.WEEKLY, day = 6)
        assertEquals(LocalDateTime.of(2026, 11, 22, 10, 0), Occurrences.next(a, s, now))
    }

    @Test fun weekdaysFromSundayIsMonday() {
        val a = Alarm(1, 6 * 60 + 30, Rec.MULTI, day = 0, days = setOf(0, 1, 2, 3, 4))
        assertEquals(LocalDateTime.of(2026, 11, 16, 6, 30), Occurrences.next(a, s, now))
    }

    @Test fun skipNextMovesToFollowingDay() {
        val a = Alarm(1, 6 * 60, Rec.MULTI, day = 0, days = (0..6).toSet())
        val skip = Occurrences.nextUnskipped(a, now)
        assertEquals(LocalDate.of(2026, 11, 16), skip)
        assertEquals(LocalDateTime.of(2026, 11, 17, 6, 0), Occurrences.next(a.copy(skipDate = skip), s, now))
    }

    @Test fun skipHolidaysSkipsIndependenceDay() {
        // Sunday 6 Dec 2026 is Independence Day.
        val sat = LocalDateTime.of(2026, 12, 5, 12, 0)
        val a = Alarm(1, 8 * 60, Rec.WEEKLY, day = 6, skipHol = true)
        assertEquals(LocalDateTime.of(2026, 12, 13, 8, 0), Occurrences.next(a, s, sat))
        // Holiday set to ring: back to the 6th.
        val ring = s.copy(holSkip = s.holSkip.toMutableList().also { it[0] = false })
        assertEquals(LocalDateTime.of(2026, 12, 6, 8, 0), Occurrences.next(a, ring, sat))
        // Not opted in: rings on the holiday.
        assertEquals(LocalDateTime.of(2026, 12, 6, 8, 0), Occurrences.next(a.copy(skipHol = false), s, sat))
    }

    @Test fun onceIgnoresHolidaySkip() {
        val sat = LocalDateTime.of(2026, 12, 5, 12, 0)
        val a = Alarm(1, 8 * 60, Rec.ONCE, day = 6, skipHol = true)
        assertEquals(LocalDateTime.of(2026, 12, 6, 8, 0), Occurrences.next(a, s, sat))
    }

    @Test fun emptyMultiNeverRings() {
        assertNull(Occurrences.next(Alarm(1, 0, Rec.MULTI, day = 0, days = emptySet()), s, now))
    }
}
