package top.yeetfactory.rexi.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** What scheduling needs to know about a medicine, without Room or Android. */
data class Plan(
    val id: Long,
    val frequency: Frequency,
    /** Bit 0 is Monday, bit 6 is Sunday. Only read for [Frequency.SOME_DAYS]. */
    val daysMask: Int,
    val everyDays: Int,
    val startDay: LocalDate,
    val courseDays: Int,
    val slots: Map<DayPart, Double>,
    /** False while reminders are paused or after the person stopped taking it. */
    val active: Boolean,
    /**
     * When this schedule took effect. Doses due before it never count, so a medicine added in the afternoon
     * doesn't show its morning dose as missed, and changing a schedule doesn't rewrite the past.
     */
    val from: LocalDateTime? = null,
)

data class ScheduledDose(
    val medId: Long,
    val date: LocalDate,
    val part: DayPart,
    val amount: Double,
)

object Schedule {
    const val ALL_DAYS = 0b111_1111
    private const val DAYS_AHEAD = 366

    fun isDueOn(
        plan: Plan,
        date: LocalDate,
    ): Boolean {
        if (!plan.active || plan.slots.isEmpty() || date < plan.startDay) return false
        val offset = ChronoUnit.DAYS.between(plan.startDay, date)
        return when (plan.frequency) {
            Frequency.EVERY_DAY -> true
            Frequency.SOME_DAYS -> plan.daysMask and (1 shl (date.dayOfWeek.value - 1)) != 0
            Frequency.EVERY_FEW_DAYS -> offset % plan.everyDays.coerceAtLeast(1) == 0L
            Frequency.COURSE -> offset < plan.courseDays
            Frequency.AS_NEEDED -> false
        }
    }

    /** "Day 2 of 3" for a course, counted from 1. Null outside a course. */
    fun courseDay(
        plan: Plan,
        date: LocalDate,
    ): Int? =
        if (plan.frequency == Frequency.COURSE && isDueOn(plan, date)) {
            ChronoUnit.DAYS.between(plan.startDay, date).toInt() + 1
        } else {
            null
        }

    fun courseEnd(plan: Plan): LocalDate = plan.startDay.plusDays((plan.courseDays - 1).coerceAtLeast(0).toLong())

    /**
     * Whether a snooze still decides when a compartment rings. It has to end after the compartment's own time, so a
     * snooze left over from before its time moved later, or from before a restore, can't ring hours early.
     */
    fun snoozeHolds(
        snoozedUntil: Long,
        dueAt: Long,
        now: Long,
    ): Boolean = snoozedUntil > now && snoozedUntil > dueAt

    /** The first day from [from] on that has a dose, looking up to a year ahead. Null when nothing is scheduled. */
    fun nextDue(
        plan: Plan,
        from: LocalDate,
    ): LocalDate? =
        generateSequence(from) { it.plusDays(1) }
            .take(DAYS_AHEAD)
            .firstOrNull { isDueOn(plan, it) }

    /**
     * The start day that puts a dose on [next] and every [everyDays] days around it. It steps back in whole cycles
     * until it's no later than [notAfter], so choosing tomorrow doesn't hide the medicine from the days before.
     */
    fun startFor(
        next: LocalDate,
        everyDays: Int,
        notAfter: LocalDate,
    ): LocalDate {
        if (next <= notAfter) return next
        val cycle = everyDays.coerceAtLeast(1).toLong()
        val cycles = (ChronoUnit.DAYS.between(notAfter, next) + cycle - 1) / cycle
        return next.minusDays(cycles * cycle)
    }

    fun dosesOn(
        plans: List<Plan>,
        date: LocalDate,
    ): List<ScheduledDose> =
        plans
            .filter { isDueOn(it, date) }
            .flatMap { plan -> plan.slots.map { (part, amount) -> ScheduledDose(plan.id, date, part, amount) } }

    /** The average amount used per day, for supply estimates. Null for medicines taken only when needed. */
    fun dailyUse(plan: Plan): Double? {
        val perDay = plan.slots.values.sum()
        if (perDay <= 0.0) return null
        return when (plan.frequency) {
            Frequency.EVERY_DAY, Frequency.COURSE -> perDay
            Frequency.SOME_DAYS -> perDay * Integer.bitCount(plan.daysMask and ALL_DAYS) / 7.0
            Frequency.EVERY_FEW_DAYS -> perDay / plan.everyDays.coerceAtLeast(1)
            Frequency.AS_NEEDED -> null
        }
    }
}
