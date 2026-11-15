package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test fun `times follow the phone's 12 or 24 hour clock`() {
        assertEquals("14:05", Format.time(14 * 60 + 5, use24 = true))
        assertEquals("2:05 PM", Format.time(14 * 60 + 5, use24 = false))
        assertEquals("12:00 AM", Format.time(0, use24 = false))
        assertEquals("12:30 PM", Format.time(12 * 60 + 30, use24 = false))
        assertEquals("00:15", Format.time(24 * 60 + 15, use24 = true))
    }

    @Test fun `amounts read naturally for any kind of medicine`() {
        assertEquals("1 tablet", Format.amount(1.0, DoseUnit.TABLET, ""))
        assertEquals("½ tablet", Format.amount(0.5, DoseUnit.TABLET, ""))
        assertEquals("1½ tablets", Format.amount(1.5, DoseUnit.TABLET, ""))
        assertEquals("6 sprays", Format.amount(6.0, DoseUnit.SPRAY, ""))
        assertEquals("2.5 ml", Format.amount(2.5, DoseUnit.ML, ""))
        assertEquals("1 scoop", Format.amount(1.0, DoseUnit.CUSTOM, "scoop"))
    }

    @Test fun `parts of the day read as natural English, night included`() {
        assertEquals("in the morning", Format.inPart(DayPart.MORNING))
        assertEquals("at night", Format.inPart(DayPart.NIGHT))
        assertEquals("in the morning and at night", Format.inParts(setOf(DayPart.NIGHT, DayPart.MORNING)))
        assertEquals("in the morning, in the evening and at night", Format.inParts(listOf(DayPart.MORNING, DayPart.EVENING, DayPart.NIGHT)))
    }

    @Test fun `a two day gap reads as every other day`() {
        assertEquals("Every other day", Format.everyDays(2))
        assertEquals("Every 3 days", Format.everyDays(3))
    }

    @Test fun `durations drop the parts that are zero`() {
        assertEquals("4 h 55 min", Format.duration(295))
        assertEquals("1 h", Format.duration(60))
        assertEquals("25 min", Format.duration(25))
    }

    @Test fun `dates are short and plain`() {
        assertEquals("Sun 15 Nov", Format.day(LocalDate.of(2026, 11, 15)))
        assertEquals("9 to 15 November", Format.range(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)))
        assertEquals("28 October to 3 November", Format.range(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 3)))
    }
}
