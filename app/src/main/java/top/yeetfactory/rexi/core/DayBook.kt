package top.yeetfactory.rexi.core

import java.time.LocalDate
import java.time.LocalDateTime

/** A dose someone answered, or one the app marked as missed. A null part means an as-needed dose. */
data class Logged(
    val medId: Long,
    val date: LocalDate,
    val part: DayPart?,
    val status: DoseStatus,
    val at: Long,
    val amount: Double,
    val reason: String = "",
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
    val part: DayPart,
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

    fun part(part: DayPart): PartView? = parts.firstOrNull { it.part == part }
}

/** The person's own times for each part of the day, and how long a dose waits before it counts as missed. */
data class DayClock(
    val partMinutes: Map<DayPart, Int>,
    val missedAfterMinutes: Int,
) {
    fun minutes(part: DayPart): Int = partMinutes[part] ?: part.defaultMinutes

    fun dueAt(
        date: LocalDate,
        part: DayPart,
    ): LocalDateTime = date.atStartOfDay().plusMinutes(minutes(part).toLong())

    fun missedAt(
        date: LocalDate,
        part: DayPart,
    ): LocalDateTime = dueAt(date, part).plusMinutes(missedAfterMinutes.toLong())
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
        val dayLogs = logs.filter { it.date == date && it.part != null }
        val parts =
            DayPart.entries.mapNotNull { part ->
                val views =
                    scheduled.filter { it.part == part }.map { dose ->
                        val log = dayLogs.firstOrNull { it.medId == dose.medId && it.part == part }
                        val state =
                            when {
                                log != null -> log.status.state
                                now >= clock.missedAt(date, part) -> DoseState.MISSED
                                now >= clock.dueAt(date, part) -> DoseState.DUE
                                else -> DoseState.LATER
                            }
                        DoseView(dose.medId, dose.amount, state, log?.at, log?.reason.orEmpty())
                    }
                val extra =
                    dayLogs
                        .filter { log -> log.part == part && views.none { it.medId == log.medId } }
                        .map { DoseView(it.medId, it.amount, it.status.state, it.at, it.reason) }
                val all = views + extra
                if (all.isEmpty()) null else PartView(part, clock.minutes(part), all)
            }
        return DayView(date, parts.sortedBy { it.minutes })
    }

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
    fun cell(part: PartView?): CellState {
        if (part == null) return CellState.NONE
        val states = part.doses.map { it.state }
        return when {
            DoseState.MISSED in states || DoseState.SKIPPED in states -> CellState.MISSED
            DoseState.DUE in states -> CellState.DUE
            DoseState.LATER in states -> CellState.LATER
            else -> CellState.TAKEN
        }
    }

    /** Monday of the week that holds [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
