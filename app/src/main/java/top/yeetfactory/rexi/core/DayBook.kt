package top.yeetfactory.rexi.core

import java.time.LocalDate
import java.time.LocalDateTime

/** A dose someone answered, or one the app marked as missed. A null part means an unscheduled log. */
data class Logged(
    val medId: Long,
    val date: LocalDate,
    val part: DoseSlot?,
    val status: DoseStatus,
    val at: Long,
    val amount: Double,
    val reason: String = "",
    val historical: Boolean = false,
)

enum class DoseState {
    TAKEN,
    SKIPPED,
    MISSED,

    /** Its time has come and nobody has answered yet. */
    DUE,

    /** Its time hasn't come yet. */
    LATER,
}

data class DoseView(
    val medId: Long,
    val amount: Double,
    val state: DoseState,
    val at: Long?,
    val reason: String,
    val historical: Boolean = false,
) {
    val pending: Boolean get() = state == DoseState.DUE || state == DoseState.LATER
}

enum class PartState {
    /** Everything in it was answered. */
    DONE,
    DUE,
    LATER,

    /** Nothing is pending, and at least one dose was missed. */
    MISSED,
}

/** One compartment of one day. */
data class PartView(
    val part: DoseSlot,
    val minutes: Int,
    val doses: List<DoseView>,
) {
    val state: PartState
        get() =
            when {
                doses.any { it.state == DoseState.DUE } -> PartState.DUE
                doses.any { it.state == DoseState.LATER } -> PartState.LATER
                doses.any { it.state == DoseState.MISSED } -> PartState.MISSED
                else -> PartState.DONE
            }

    val pending: List<DoseView> get() = doses.filter { it.pending }

    /** When the last dose in it was taken, for "Taken 08:04". */
    val takenAt: Long? get() = doses.filter { it.state == DoseState.TAKEN }.mapNotNull { it.at }.maxOrNull()
}

data class DayView(
    val date: LocalDate,
    val parts: List<PartView>,
) {
    val total: Int get() = parts.sumOf { it.doses.size }
    val taken: Int get() = parts.sumOf { part -> part.doses.count { it.state == DoseState.TAKEN } }

    /** The earliest compartment waiting for an answer. Today opens it. */
    val due: PartView? get() = parts.firstOrNull { it.state == PartState.DUE }

    /** The next compartment whose time hasn't come. */
    val next: PartView? get() = parts.firstOrNull { it.state == PartState.LATER }

    fun part(part: DoseSlot): PartView? = parts.firstOrNull { it.part == part }
}

/** The person's own times for each part of the day, and how long a dose waits before it counts as missed. */
data class DayClock(
    val partMinutes: Map<DayPart, Int>,
    val missedAfterMinutes: Int,
) {
    fun minutes(part: DoseSlot): Int =
        when (part) {
            is DayPart -> partMinutes[part] ?: part.defaultMinutes
            is CustomTime -> part.minutes
        }

    fun dueAt(
        date: LocalDate,
        part: DoseSlot,
    ): LocalDateTime = date.atStartOfDay().plusMinutes(minutes(part).toLong())

    fun missedAt(
        date: LocalDate,
        part: DoseSlot,
    ): LocalDateTime = dueAt(date, part).plusMinutes(missedAfterMinutes.toLong())

    /** Whether a dose nobody answered counts as missed by [now]. */
    fun isOverdue(
        date: LocalDate,
        part: DoseSlot,
        now: LocalDateTime,
    ): Boolean = now >= missedAt(date, part)
}

object DayBook {
    /**
     * Puts a day's doses into compartments. Answered doses come from [logs]. A dose nobody answered becomes due
     * at its time, and missed once [DayClock.missedAfterMinutes] have passed, even before the app writes that down.
     * A dose that was logged but is no longer on the schedule still shows, so the record stays true to the day.
     */
    fun build(
        date: LocalDate,
        plans: List<Plan>,
        logs: List<Logged>,
        now: LocalDateTime,
        clock: DayClock,
    ): DayView {
        val startsAt = plans.associate { it.id to it.from }
        val scheduled =
            Schedule.dosesOn(plans, date).filter { dose ->
                val from = startsAt[dose.medId]
                from == null || clock.dueAt(date, dose.part) >= from
            }
        val bySlot = scheduled.groupBy { it.part }
        val dayLogs = logs.filter { it.date == date && it.part != null }.groupBy { it.part }
        val parts =
            (bySlot.keys + dayLogs.keys.filterNotNull()).map { part ->
                val answers = dayLogs[part].orEmpty().associateBy { it.medId }
                val views =
                    bySlot[part].orEmpty().map { dose ->
                        val log = answers[dose.medId]
                        val state =
                            when {
                                log != null -> log.status.state
                                clock.isOverdue(date, part, now) -> DoseState.MISSED
                                now >= clock.dueAt(date, part) -> DoseState.DUE
                                else -> DoseState.LATER
                            }
                        if (log == null) DoseView(dose.medId, dose.amount, state, null, "") else log.view()
                    }
                val extra =
                    answers.values
                        .filter { log -> views.none { it.medId == log.medId } }
                        .map { it.view() }
                PartView(part, clock.minutes(part), views + extra)
            }
        return DayView(date, parts.sortedWith(compareBy({ it.minutes }, { (it.part as? DayPart)?.ordinal ?: DayPart.entries.size })))
    }

    private fun Logged.view() = DoseView(medId, amount, status.state, at, reason, historical)

    private val DoseStatus.state: DoseState
        get() =
            when (this) {
                DoseStatus.TAKEN -> DoseState.TAKEN
                DoseStatus.SKIPPED -> DoseState.SKIPPED
                DoseStatus.MISSED -> DoseState.MISSED
            }
}

/** One cell of the weekly pillbox in History. */
enum class CellState {
    NONE,
    TAKEN,
    MISSED,
    DUE,
    LATER,
}

object History {
    fun cell(part: PartView?): CellState = cell(part?.doses.orEmpty())

    fun cell(doses: List<DoseView>): CellState {
        if (doses.isEmpty()) return CellState.NONE
        val states = doses.map { it.state }
        return when {
            DoseState.MISSED in states || DoseState.SKIPPED in states -> CellState.MISSED
            DoseState.DUE in states -> CellState.DUE
            DoseState.LATER in states -> CellState.LATER
            else -> CellState.TAKEN
        }
    }

    /** A dose can be corrected from History once its time has come, whatever its answer was. */
    fun canChange(dose: DoseView): Boolean = dose.state != DoseState.LATER

    /** Monday of the week that holds [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
