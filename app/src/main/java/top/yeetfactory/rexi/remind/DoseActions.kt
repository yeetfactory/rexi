package top.yeetfactory.rexi.remind

import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayView
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.data.MedicineWithSlots
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Take, skip and snooze, the same way from every place they can happen: Today, a notification, the full screen
 * reminder and the widget. Each one also clears the reminder that asked, so nothing keeps ringing after an answer.
 */
class DoseActions(
    private val app: RexiApp,
) {
    private val repository get() = app.repository

    suspend fun book(day: LocalDate): Book {
        val medicines = repository.dao.medicines()
        val logs = repository.dao.logs(day.toEpochDay(), day.toEpochDay()).map { it.logged() }
        val view =
            DayBook.build(
                day,
                medicines.map { it.plan() },
                logs,
                LocalDateTime.now(ZoneId.systemDefault()),
                app.settings.value.clock,
            )
        return Book(view, medicines.associateBy { it.id })
    }

    /** Takes every dose in a compartment that isn't answered yet, or only [only] when given. Returns what was taken. */
    suspend fun take(
        day: LocalDate,
        part: DoseSlot,
        at: Long = repository.now(),
        only: Set<Long>? = null,
        includeMissed: Boolean = false,
    ): List<MedicineWithSlots> {
        val book = book(day)
        val doses =
            book.view
                .part(part)
                ?.doses
                .orEmpty()
                .filter { it.pending || (includeMissed && it.state == DoseState.MISSED) }
                .filter { only == null || it.medId in only }
        doses.forEach { repository.take(it.medId, day, part, at, it.amount) }
        settle(day, part)
        return doses.mapNotNull { book.medicines[it.medId] }
    }

    /** Takes one dose whatever its state, for correcting the record: a skip that was really taken, or taking early. */
    suspend fun takeOne(
        day: LocalDate,
        part: DoseSlot,
        medId: Long,
        amount: Double,
    ) {
        repository.take(medId, day, part, repository.now(), amount)
        if (book(day)
                .view
                .part(part)
                ?.pending
                .isNullOrEmpty()
        ) {
            settle(day, part)
        }
    }

    suspend fun skip(
        day: LocalDate,
        part: DoseSlot,
        reason: String = "",
        only: Set<Long>? = null,
    ) {
        book(day)
            .view
            .part(part)
            ?.pending
            .orEmpty()
            .filter { only == null || it.medId in only }
            .forEach { repository.skip(it.medId, day, part, it.amount, reason) }
        settle(day, part)
    }

    suspend fun snooze(
        day: LocalDate,
        part: DoseSlot,
        minutes: Int,
    ): Long {
        val until = repository.now() + minutes * MINUTE_MS
        app.settings.snooze(day, part, until)
        app.notifier.cancelDose(day, part)
        app.reminders.reschedule()
        return until
    }

    suspend fun undo(
        day: LocalDate,
        part: DoseSlot,
        medIds: Collection<Long>,
    ) {
        medIds.forEach { repository.undo(it, day, part) }
    }

    private fun settle(
        day: LocalDate,
        part: DoseSlot,
    ) {
        app.settings.clearSnooze(day, part)
        app.notifier.cancelDose(day, part)
        app.notifier.cancelMissed(day, part)
    }

    data class Book(
        val view: DayView,
        val medicines: Map<Long, MedicineWithSlots>,
    )

    companion object {
        const val MINUTE_MS = 60_000L
    }
}
