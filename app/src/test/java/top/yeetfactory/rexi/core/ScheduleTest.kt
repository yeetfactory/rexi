package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private val start = LocalDate.of(2026, 11, 9) // A Monday.

    private fun plan(
        frequency: Frequency,
        daysMask: Int = Schedule.ALL_DAYS,
        everyDays: Int = 2,
        courseDays: Int = 3,
        slots: Map<DayPart, Double> = mapOf(DayPart.MORNING to 1.0, DayPart.EVENING to 1.0),
        active: Boolean = true,
    ) = Plan(1, frequency, daysMask, everyDays, start, courseDays, slots, active)

    @Test fun `an everyday medicine is due every day from its start, and not before`() {
        val plan = plan(Frequency.EVERY_DAY)
        assertFalse(Schedule.isDueOn(plan, start.minusDays(1)))
        assertTrue(Schedule.isDueOn(plan, start))
        assertTrue(Schedule.isDueOn(plan, start.plusDays(40)))
    }

    @Test fun `some days follows the chosen weekdays`() {
        val mondayWednesdayFriday = 0b001_0101
        val plan = plan(Frequency.SOME_DAYS, daysMask = mondayWednesdayFriday)
        val due = (0L..6L).map { Schedule.isDueOn(plan, start.plusDays(it)) }
        assertEquals(listOf(true, false, true, false, true, false, false), due)
    }

    @Test fun `every few days counts from the start day`() {
        val plan = plan(Frequency.EVERY_FEW_DAYS, everyDays = 3)
        val due = (0L..6L).filter { Schedule.isDueOn(plan, start.plusDays(it)) }
        assertEquals(listOf(0L, 3L, 6L), due)
    }

    @Test fun `the next dose is the first due day from the given day on`() {
        val plan = plan(Frequency.EVERY_FEW_DAYS, everyDays = 3)
        assertEquals(start, Schedule.nextDue(plan, start.minusDays(5)))
        assertEquals(start.plusDays(3), Schedule.nextDue(plan, start.plusDays(1)))
        assertNull(Schedule.nextDue(plan(Frequency.AS_NEEDED), start))
    }

    @Test fun `starting every other day from tomorrow skips today and keeps the start in the past`() {
        val today = start.plusDays(10)
        val begin = Schedule.startFor(next = today.plusDays(1), everyDays = 2, notAfter = today)
        assertTrue(begin <= today)
        val due = (0L..4L).map { Schedule.isDueOn(plan(Frequency.EVERY_FEW_DAYS).copy(startDay = begin), today.plusDays(it)) }
        assertEquals(listOf(false, true, false, true, false), due)
    }

    @Test fun `a next dose that's already in range becomes the start day itself`() {
        assertEquals(start, Schedule.startFor(next = start, everyDays = 3, notAfter = start.plusDays(4)))
    }

    @Test fun `a snooze from before the time moved later can't ring early`() {
        val now = 1_000L
        assertTrue(Schedule.snoozeHolds(snoozedUntil = 1_500, dueAt = 900, now = now))
        assertFalse(Schedule.snoozeHolds(snoozedUntil = 1_500, dueAt = 5_000, now = now))
        assertFalse(Schedule.snoozeHolds(snoozedUntil = 950, dueAt = 900, now = now))
    }

    @Test fun `a course stops after its last day and knows which day it's on`() {
        val plan = plan(Frequency.COURSE, courseDays = 3)
        assertEquals(2, Schedule.courseDay(plan, start.plusDays(1)))
        assertTrue(Schedule.isDueOn(plan, start.plusDays(2)))
        assertFalse(Schedule.isDueOn(plan, start.plusDays(3)))
        assertNull(Schedule.courseDay(plan, start.plusDays(3)))
        assertEquals(start.plusDays(2), Schedule.courseEnd(plan))
    }

    @Test fun `as-needed and paused medicines are never scheduled`() {
        assertFalse(Schedule.isDueOn(plan(Frequency.AS_NEEDED), start))
        assertFalse(Schedule.isDueOn(plan(Frequency.EVERY_DAY, active = false), start))
    }

    @Test fun `daily use averages out over the week`() {
        assertEquals(2.0, Schedule.dailyUse(plan(Frequency.EVERY_DAY))!!, 0.0001)
        assertEquals(2.0 * 3 / 7, Schedule.dailyUse(plan(Frequency.SOME_DAYS, daysMask = 0b001_0101))!!, 0.0001)
        assertEquals(1.0, Schedule.dailyUse(plan(Frequency.EVERY_FEW_DAYS, everyDays = 2))!!, 0.0001)
        assertNull(Schedule.dailyUse(plan(Frequency.AS_NEEDED)))
    }

    @Test fun `each scheduled part becomes one dose`() {
        val doses = Schedule.dosesOn(listOf(plan(Frequency.EVERY_DAY)), start)
        assertEquals(setOf(DayPart.MORNING, DayPart.EVENING), doses.map { it.part }.toSet())
    }
}
