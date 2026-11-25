package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DayBookTest {
    private val day = LocalDate.of(2026, 11, 15)
    private val clock = DayClock(DayPart.entries.associateWith { it.defaultMinutes }, missedAfterMinutes = 180)
    private val metformin =
        Plan(
            1,
            Frequency.EVERY_DAY,
            Schedule.ALL_DAYS,
            2,
            day.minusDays(30),
            7,
            mapOf(
                DayPart.MORNING to 1.0,
                DayPart.EVENING to 1.0,
            ),
            true,
        )
    private val azithromycin =
        Plan(2, Frequency.EVERY_DAY, Schedule.ALL_DAYS, 2, day.minusDays(1), 3, mapOf(DayPart.AFTERNOON to 1.0), true)
    private val plans = listOf(metformin, azithromycin)

    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = day.atTime(hour, minute)

    @Test fun `a dose waits until its time, then is due, then is missed`() {
        assertEquals(
            DoseState.LATER,
            DayBook
                .build(day, plans, emptyList(), at(13, 59), clock)
                .part(DayPart.AFTERNOON)!!
                .doses
                .single()
                .state,
        )
        assertEquals(
            DoseState.DUE,
            DayBook
                .build(day, plans, emptyList(), at(14, 0), clock)
                .part(DayPart.AFTERNOON)!!
                .doses
                .single()
                .state,
        )
        assertEquals(
            DoseState.MISSED,
            DayBook
                .build(day, plans, emptyList(), at(17, 0), clock)
                .part(DayPart.AFTERNOON)!!
                .doses
                .single()
                .state,
        )
    }

    @Test fun `an answer always wins over the clock`() {
        val logs = listOf(Logged(2, day, DayPart.AFTERNOON, DoseStatus.TAKEN, 0, 1.0))
        val view = DayBook.build(day, plans, logs, at(20), clock)
        assertEquals(
            DoseState.TAKEN,
            view
                .part(DayPart.AFTERNOON)!!
                .doses
                .single()
                .state,
        )
    }

    @Test fun `the open compartment is the earliest one still waiting`() {
        val logs = listOf(Logged(1, day, DayPart.MORNING, DoseStatus.TAKEN, 0, 1.0))
        val view = DayBook.build(day, plans, logs, at(14, 5), clock)
        assertEquals(DayPart.AFTERNOON, view.due?.part)
        assertEquals(1, view.taken)
        assertEquals(3, view.total)
    }

    @Test fun `with nothing due, the next compartment is the one coming up`() {
        val logs =
            listOf(Logged(1, day, DayPart.MORNING, DoseStatus.TAKEN, 0, 1.0), Logged(2, day, DayPart.AFTERNOON, DoseStatus.TAKEN, 0, 1.0))
        val view = DayBook.build(day, plans, logs, at(15), clock)
        assertNull(view.due)
        assertEquals(DayPart.EVENING, view.next?.part)
    }

    @Test fun `compartments follow the person's own times`() {
        val early = DayClock(mapOf(DayPart.EVENING to 12 * 60, DayPart.AFTERNOON to 15 * 60), missedAfterMinutes = 180)
        val view = DayBook.build(day, plans, emptyList(), at(6), early)
        assertEquals(listOf(DayPart.MORNING, DayPart.EVENING, DayPart.AFTERNOON), view.parts.map { it.part })
    }

    @Test fun `a logged dose that is no longer scheduled still shows on its day`() {
        val logs = listOf(Logged(9, day, DayPart.NIGHT, DoseStatus.SKIPPED, 0, 1.0, "Felt unwell"))
        val night = DayBook.build(day, plans, logs, at(23), clock).part(DayPart.NIGHT)!!
        assertEquals(DoseState.SKIPPED, night.doses.single().state)
        assertEquals("Felt unwell", night.doses.single().reason)
    }

    @Test fun `history marks a compartment with any missed or skipped dose`() {
        val logs = listOf(Logged(1, day, DayPart.MORNING, DoseStatus.SKIPPED, 0, 1.0))
        val view = DayBook.build(day, plans, logs, at(23, 30), clock)
        assertEquals(CellState.MISSED, History.cell(view.part(DayPart.MORNING)))
        assertEquals(CellState.NONE, History.cell(view.part(DayPart.NIGHT)))
    }

    @Test fun `weeks start on Monday`() {
        assertEquals(LocalDate.of(2026, 11, 9), History.weekStart(day))
        assertEquals(LocalDate.of(2026, 11, 9), History.weekStart(LocalDate.of(2026, 11, 9)))
    }

    @Test fun `a medicine added in the afternoon doesn't count its morning dose`() {
        val added = metformin.copy(startDay = day, from = at(14, 10).minusHours(1))
        val view = DayBook.build(day, listOf(added), emptyList(), at(14, 12), clock)
        assertNull(view.part(DayPart.MORNING))
        assertEquals(
            DoseState.LATER,
            view
                .part(DayPart.EVENING)!!
                .doses
                .single()
                .state,
        )
    }

    @Test fun `a dose due just before the medicine was added still counts`() {
        val added = metformin.copy(startDay = day, from = at(8, 30).minusHours(1))
        val view = DayBook.build(day, listOf(added), emptyList(), at(8, 31), clock)
        assertEquals(
            DoseState.DUE,
            view
                .part(DayPart.MORNING)!!
                .doses
                .single()
                .state,
        )
    }

    @Test fun `custom times have separate answers and stay fixed when preset times change`() {
        val dawn = CustomTime(6 * 60 + 30)
        val midday = CustomTime(12 * 60)
        val late = CustomTime(23 * 60 + 45)
        val plan = metformin.copy(slots = mapOf(late to 3.0, dawn to 1.0, midday to 2.0))
        val logs = listOf(Logged(plan.id, day, dawn, DoseStatus.TAKEN, 1, 1.0))
        val changed = clock.copy(partMinutes = mapOf(DayPart.MORNING to midday.minutes))
        val view = DayBook.build(day, listOf(plan), logs, at(12), changed)

        assertEquals(listOf(dawn, midday, late), view.parts.map { it.part })
        assertEquals(
            DoseState.TAKEN,
            view
                .part(dawn)
                ?.doses
                ?.single()
                ?.state,
        )
        assertEquals(
            DoseState.DUE,
            view
                .part(midday)
                ?.doses
                ?.single()
                ?.state,
        )
        assertEquals(
            DoseState.LATER,
            view
                .part(late)
                ?.doses
                ?.single()
                ?.state,
        )
        assertEquals(6.0, Schedule.dailyUse(plan)!!, 0.0)
    }
}
