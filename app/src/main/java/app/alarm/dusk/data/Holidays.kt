package app.alarm.dusk.data

import java.time.LocalDate

data class Holiday(val date: LocalDate, val name: String)

/** Finnish public holidays, as in the design. */
object Holidays {
    val list: List<Holiday> = listOf(
        Holiday(LocalDate.of(2026, 12, 6), "Independence Day"),
        Holiday(LocalDate.of(2026, 12, 24), "Christmas Eve"),
        Holiday(LocalDate.of(2026, 12, 25), "Christmas Day"),
        Holiday(LocalDate.of(2026, 12, 26), "St Stephen’s Day"),
        Holiday(LocalDate.of(2027, 1, 1), "New Year’s Day"),
        Holiday(LocalDate.of(2027, 1, 6), "Epiphany"),
        Holiday(LocalDate.of(2027, 3, 26), "Good Friday"),
        Holiday(LocalDate.of(2027, 3, 29), "Easter Monday"),
    )

    fun skippedOn(date: LocalDate, holSkip: List<Boolean>): Boolean =
        list.withIndex().any { (i, h) -> h.date == date && holSkip.getOrElse(i) { true } }
}
