package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

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

    @Test fun `custom slots ring once and honor snoozes when repeating is off`() {
        assertEquals(100L, Schedule.customRingAt(dueAt = 100, rangAt = 0, snoozedUntil = 0, repeatAfter = null))
        assertEquals(300L, Schedule.customRingAt(dueAt = 100, rangAt = 100, snoozedUntil = 300, repeatAfter = null))
        assertNull(Schedule.customRingAt(dueAt = 100, rangAt = 300, snoozedUntil = 300, repeatAfter = null))
        assertEquals(360L, Schedule.customRingAt(dueAt = 100, rangAt = 300, snoozedUntil = 300, repeatAfter = 60))
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

    @Test fun `past doses start at the chosen time and use each slot amount`() {
        val early = CustomTime(6 * 60 + 30)
        val late = CustomTime(18 * 60 + 30)
        val plan = plan(Frequency.EVERY_DAY).copy(slots = mapOf(early to 1.0, late to 2.0))
        val through =
            start
                .plusDays(1)
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        val clock = DayClock(emptyMap(), 180)
        val doses = Schedule.pastDoses(plan, late, through, clock, ZoneOffset.UTC)

        val expected =
            listOf(
                PastDose(start.atTime(18, 30).toInstant(ZoneOffset.UTC).toEpochMilli(), 2.0, late),
                PastDose(
                    start
                        .plusDays(1)
                        .atTime(6, 30)
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli(),
                    1.0,
                    early,
                ),
            )
        assertEquals(expected, doses)
    }

    @Test fun `past doses respect weekdays and the rhythm from the selected start date`() {
        val clock = DayClock(emptyMap(), 180)
        val through =
            start
                .plusDays(6)
                .atTime(23, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        val selected = start.plusDays(2)
        val weekdays = plan(Frequency.SOME_DAYS, daysMask = 0b001_0101).copy(startDay = selected)
        val intervals = plan(Frequency.EVERY_FEW_DAYS, everyDays = 3).copy(startDay = selected)
        val morning = DayPart.MORNING

        fun days(plan: Plan): Set<LocalDate> =
            Schedule
                .pastDoses(plan, morning, through, clock, ZoneOffset.UTC)
                .map {
                    java.time.Instant
                        .ofEpochMilli(it.at)
                        .atOffset(ZoneOffset.UTC)
                        .toLocalDate()
                }.toSet()

        assertEquals(setOf(selected, selected.plusDays(2)), days(weekdays))
        assertEquals(setOf(selected, selected.plusDays(3)), days(intervals))
        assertTrue(days(weekdays.copy(startDay = selected.plusDays(1))).isEmpty())
    }

    @Test fun `past doses end with the course even if the confirmation comes later`() {
        val clock = DayClock(emptyMap(), 180)
        val through =
            start
                .plusDays(4)
                .atTime(23, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        val plan = plan(Frequency.COURSE, courseDays = 2)
        val doses = Schedule.pastDoses(plan, DayPart.EVENING, through, clock, ZoneOffset.UTC)
        assertEquals(listOf(DayPart.EVENING, DayPart.MORNING, DayPart.EVENING), doses.map { it.part })
        assertTrue(Schedule.pastDoses(plan(Frequency.AS_NEEDED), DayPart.MORNING, through, clock, ZoneOffset.UTC).isEmpty())
    }
}
