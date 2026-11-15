package top.yeetfactory.rexi.data

import androidx.room.withTransaction
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.SupplyMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Every change to medicines and doses goes through here, so the count of what's left always moves with the doses,
 * and [onChange] can bring reminders and widgets up to date afterwards.
 */
class Repository(
    private val db: RexiDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    val dao: RexiDao = db.dao()

    /** Called after every write. The app sets it to reschedule alarms and refresh widgets. */
    var onChange: () -> Unit = {}

    fun now(): Long = clock()

    fun today(): LocalDate = LocalDate.now(zone())

    fun dayOf(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    suspend fun save(
        medicine: Medicine,
        amounts: Map<DayPart, Double>,
    ): Long {
        val id =
            db.withTransaction {
                val id =
                    if (medicine.id == 0L) {
                        dao.insert(medicine.copy(createdAt = clock(), scheduleFrom = clock()))
                    } else {
                        val before = dao.medicine(medicine.id)
                        val rescheduled = before == null || scheduleChanged(before, medicine, amounts)
                        dao.update(if (rescheduled) medicine.copy(scheduleFrom = clock()) else medicine)
                        medicine.id
                    }
                dao.replaceSlots(id, amounts.filterValues { it > 0.0 }.map { (part, amount) -> Slot(id, part, amount) })
                id
            }
        onChange()
        return id
    }

    /** Only a change to when doses fall resets [Medicine.scheduleFrom]. A new amount or colour doesn't. */
    private fun scheduleChanged(
        before: MedicineWithSlots,
        after: Medicine,
        amounts: Map<DayPart, Double>,
    ): Boolean {
        val old = before.medicine
        return old.frequency != after.frequency ||
            old.daysMask != after.daysMask ||
            old.everyDays != after.everyDays ||
            old.courseDays != after.courseDays ||
            old.startDay != after.startDay ||
            before.amounts.keys != amounts.filterValues { it > 0.0 }.keys
    }

    suspend fun take(
        medId: Long,
        day: LocalDate,
        part: DayPart,
        at: Long,
        amount: Double,
    ) {
        db.withTransaction {
            val previous = dao.log(medId, day.toEpochDay(), part)
            dao.upsert(
                DoseLog(
                    id = previous?.id ?: 0,
                    medicineId = medId,
                    day = day.toEpochDay(),
                    part = part,
                    status = DoseStatus.TAKEN,
                    at = at,
                    amount = amount,
                ),
            )
            if (previous?.status != DoseStatus.TAKEN) adjustCount(medId, -amount)
        }
        onChange()
    }

    suspend fun skip(
        medId: Long,
        day: LocalDate,
        part: DayPart,
        amount: Double,
        reason: String = "",
    ) {
        db.withTransaction {
            val previous = dao.log(medId, day.toEpochDay(), part)
            dao.upsert(
                DoseLog(
                    id = previous?.id ?: 0,
                    medicineId = medId,
                    day = day.toEpochDay(),
                    part = part,
                    status = DoseStatus.SKIPPED,
                    at = clock(),
                    amount = amount,
                    reason = reason,
                ),
            )
            if (previous?.status == DoseStatus.TAKEN) adjustCount(medId, previous.amount)
        }
        onChange()
    }

    /**
     * Only marks doses nobody answered. An answer always wins over the app's own guess. The reminder engine passes
     * [quietly], since it's already in the middle of rescheduling and mustn't ask for another round.
     */
    suspend fun markMissed(
        medId: Long,
        day: LocalDate,
        part: DayPart,
        amount: Double,
        quietly: Boolean = false,
    ): Boolean {
        val written =
            db.withTransaction {
                if (dao.log(medId, day.toEpochDay(), part) != null) return@withTransaction false
                dao.upsert(
                    DoseLog(
                        medicineId = medId,
                        day = day.toEpochDay(),
                        part = part,
                        status = DoseStatus.MISSED,
                        at = clock(),
                        amount = amount,
                    ),
                )
                true
            }
        if (written && !quietly) onChange()
        return written
    }

    suspend fun undo(
        medId: Long,
        day: LocalDate,
        part: DayPart,
    ) {
        db.withTransaction {
            val previous = dao.log(medId, day.toEpochDay(), part) ?: return@withTransaction
            dao.deleteLog(previous.id)
            if (previous.status == DoseStatus.TAKEN) adjustCount(medId, previous.amount)
        }
        onChange()
    }

    suspend fun logAsNeeded(
        medId: Long,
        at: Long,
        amount: Double,
        reason: String,
    ): Long {
        val id =
            db.withTransaction {
                val id =
                    dao.upsert(
                        DoseLog(
                            medicineId = medId,
                            day = dayOf(at).toEpochDay(),
                            part = null,
                            status = DoseStatus.TAKEN,
                            at = at,
                            amount = amount,
                            reason = reason,
                        ),
                    )
                adjustCount(medId, -amount)
                id
            }
        onChange()
        return id
    }

    suspend fun undoAsNeeded(logId: Long) {
        db.withTransaction {
            val log = dao.logById(logId) ?: return@withTransaction
            dao.deleteLog(log.id)
            adjustCount(log.medicineId, log.amount)
        }
        onChange()
    }

    suspend fun refill(
        medId: Long,
        count: Double,
    ) = change(medId) { it.copy(supplyCount = count, supplyFull = count, refillSnoozedUntil = 0) }

    suspend fun newPack(medId: Long) = change(medId) { it.copy(packOpenedDay = today().toEpochDay(), refillSnoozedUntil = 0) }

    suspend fun snoozeRefill(
        medId: Long,
        until: LocalDate,
    ) = change(medId) { it.copy(refillSnoozedUntil = until.toEpochDay()) }

    suspend fun setPaused(
        medId: Long,
        paused: Boolean,
    ) = change(medId) { it.copy(paused = paused) }

    suspend fun archive(medId: Long) = change(medId) { it.copy(archived = true) }

    suspend fun setNotes(
        medId: Long,
        notes: String,
    ) = change(medId) { it.copy(notes = notes.trim()) }

    private suspend fun change(
        medId: Long,
        edit: (Medicine) -> Medicine,
    ) {
        val current = dao.medicine(medId)?.medicine ?: return
        dao.update(edit(current))
        onChange()
    }

    private suspend fun adjustCount(
        medId: Long,
        delta: Double,
    ) {
        val medicine = dao.medicine(medId)?.medicine ?: return
        if (medicine.supplyMode != SupplyMode.COUNT) return
        dao.update(medicine.copy(supplyCount = (medicine.supplyCount + delta).coerceAtLeast(0.0)))
    }
}
