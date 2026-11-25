package top.yeetfactory.rexi.remind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.CustomTime
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.PartView
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.core.Supply
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.MedicineWithSlots
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Keeps one alarm per compartment for yesterday, today and tomorrow, plus one for when each would count as missed.
 * Everything is worked out again from the database each time, so a reboot, a time zone change or a new medicine
 * can never leave a stale alarm behind. Times are local clock times, so after travel reminders follow the new clock.
 */
class Reminders(
    private val app: RexiApp,
) {
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    /** Without the exact alarm permission, Android may deliver a reminder several minutes late. */
    fun canRingOnTime(): Boolean = alarms.canScheduleExactAlarms()

    fun exactAlarmSettings(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${app.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun reschedule() =
        lock.withLock {
            val zone = ZoneId.systemDefault()
            val repository = app.repository
            val settings = app.settings
            val now = repository.now()
            val nowLocal = LocalDateTime.now(zone)
            val today = repository.today()
            val values = settings.value
            val medicines = repository.dao.medicines()
            val byId = medicines.associateBy { it.id }
            val plans = medicines.map { it.plan() }
            val logs =
                repository.dao
                    .logs(today.minusDays(CATCH_UP_DAYS).toEpochDay(), today.plusDays(1).toEpochDay())
                    .map { it.logged() }
            settings.forgetSnoozesBefore(today.minusDays(1))

            // Doses nobody answered are written down as missed, so the record holds even if the phone was off.
            for (offset in -CATCH_UP_DAYS..0L) {
                val date = today.plusDays(offset)
                DayBook.build(date, plans, logs, nowLocal, values.clock).parts.forEach { compartment ->
                    val overdue = compartment.doses.filter { it.state == DoseState.MISSED && it.at == null }
                    if (overdue.isNotEmpty()) {
                        markMissed(date, compartment.part, overdue.map { it.medId to it.amount }, medicines, notify = offset >= -1)
                    }
                }
            }

            var customWake = Long.MAX_VALUE
            for (offset in -1..1) {
                val date = today.plusDays(offset.toLong())
                val view = DayBook.build(date, plans, logs, nowLocal, values.clock)
                for (part in DayPart.entries) {
                    val compartment = view.part(part)
                    val unanswered = compartment?.doses.orEmpty().filter { it.pending }
                    val remindCode = code(REMIND_BASE, offset, part)
                    val missCode = code(MISS_BASE, offset, part)
                    if (unanswered.isEmpty()) {
                        cancel(remindCode, RexiReceiver.ACTION_REMIND)
                        cancel(missCode, RexiReceiver.ACTION_MISS)
                        continue
                    }
                    val dueAt = millis(values.clock.dueAt(date, part), zone)
                    val missAt = millis(values.clock.missedAt(date, part), zone)
                    val ring = nextRing(date, part, dueAt, now, zone)
                    if (ring != null && ring < missAt) {
                        set(remindCode, ring, RexiReceiver.ACTION_REMIND) { putDayPart(date, part) }
                    } else {
                        cancel(remindCode, RexiReceiver.ACTION_REMIND)
                    }
                    set(missCode, missAt, RexiReceiver.ACTION_MISS) { putDayPart(date, part) }
                }
                view.parts.filter { it.part is CustomTime && it.pending.isNotEmpty() }.forEach { compartment ->
                    customWake = minOf(customWake, checkCustom(date, compartment, now, zone, byId))
                }
            }
            // One rolling alarm lets every custom time work without hitting Android's per-app alarm limit.
            if (customWake == Long.MAX_VALUE) {
                cancel(CUSTOM_WAKE_CODE, RexiReceiver.ACTION_MISS)
            } else {
                set(CUSTOM_WAKE_CODE, customWake, RexiReceiver.ACTION_MISS) {}
            }

            settings.asNeededReminders().forEach { (medId, at) ->
                if (at > now) {
                    set(AS_NEEDED_BASE + medId.toInt(), at, RexiReceiver.ACTION_AS_NEEDED_OK) { putExtra(RexiReceiver.EXTRA_MED, medId) }
                } else {
                    cancel(AS_NEEDED_BASE + medId.toInt(), RexiReceiver.ACTION_AS_NEEDED_OK)
                }
            }

            val refillTime = LocalTime.of(REFILL_HOUR, 0)
            val nextRefill =
                if (nowLocal.toLocalTime() < refillTime) today.atTime(refillTime) else today.plusDays(1).atTime(refillTime)
            set(REFILL_CODE, millis(nextRefill, zone), RexiReceiver.ACTION_REFILL_CHECK) {}
        }

    /** The time a compartment should ring next: its own time, a snooze, or a repeat while nobody answers. */
    private fun nextRing(
        date: LocalDate,
        part: DayPart,
        dueAt: Long,
        now: Long,
        zone: ZoneId,
    ): Long? {
        val settings = app.settings
        val values = settings.value
        val snoozed = settings.snoozedUntil(date, part)
        if (Schedule.snoozeHolds(snoozed, dueAt, now)) return snoozed
        if (dueAt > now) return dueAt
        if (!values.repeatUntilAnswered) return null
        val rang = settings.rangAt(date, part)
        val next = if (rang < dueAt) now + SOON_MS else maxOf(rang + REPEAT_MS, now + SOON_MS)
        return if (values.isQuiet(Format.minutesOfDay(next, zone))) null else next
    }

    private fun checkCustom(
        date: LocalDate,
        compartment: PartView,
        now: Long,
        zone: ZoneId,
        medicines: Map<Long, MedicineWithSlots>,
    ): Long {
        val settings = app.settings
        val values = settings.value
        val part = compartment.part
        val dueAt = millis(values.clock.dueAt(date, part), zone)
        val missAt = millis(values.clock.missedAt(date, part), zone)
        val rang = settings.rangAt(date, part)
        val snoozed = settings.snoozedUntil(date, part)
        if (now >= missAt) return Long.MAX_VALUE
        val repeatAfter = REPEAT_MS.takeIf { values.repeatUntilAnswered }
        val ringAt = Schedule.customRingAt(dueAt, rang, snoozed, repeatAfter) ?: return missAt
        if (ringAt > now) return minOf(ringAt, missAt)

        val quiet = values.isQuiet(Format.minutesOfDay(now, zone))
        if (quiet && rang >= dueAt && snoozed <= rang) {
            val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
            val end = local.toLocalDate().atStartOfDay().plusMinutes(values.quietEnd.toLong())
            val endAt = millis(if (end <= local) end.plusDays(1) else end, zone)
            return minOf(missAt, if (endAt > now) endAt else now + REPEAT_MS)
        }
        settings.setRang(date, part, now)
        app.notifier.showDose(
            date,
            part,
            compartment.pending.mapNotNull { dose ->
                medicines[dose.medId]?.let { it to dose.amount }
            },
            dueAt,
            quiet,
        )
        return repeatAfter?.let { minOf(missAt, now + it) } ?: missAt
    }

    private suspend fun markMissed(
        date: LocalDate,
        part: DoseSlot,
        doses: List<Pair<Long, Double>>,
        medicines: List<MedicineWithSlots>,
        notify: Boolean,
    ) {
        val written = doses.filter { (medId, amount) -> app.repository.markMissed(medId, date, part, amount, quietly = true) }
        if (written.isEmpty() || !notify) return
        app.notifier.cancelDose(date, part)
        val names = written.mapNotNull { (medId, _) -> medicines.firstOrNull { it.id == medId } }
        app.notifier.showMissed(date, part, names)
    }

    /** Refill warnings go out once a day at most, from a week before a medicine runs out (or whatever lead was set). */
    suspend fun checkRefills() {
        val today = app.repository.today()
        app.repository.dao.medicines().forEach { med ->
            val medicine = med.medicine
            if (medicine.paused || medicine.supplyMode == SupplyMode.NONE) return@forEach
            if (medicine.refillSnoozedUntil > today.toEpochDay()) return@forEach
            if (app.settings.refillWarnedOn(med.id) == today.toEpochDay()) return@forEach
            val info = supply(med, today)
            if (info.low) {
                app.notifier.showRefill(med, info)
                app.settings.setRefillWarnedOn(med.id, today)
            }
        }
    }

    private fun set(
        requestCode: Int,
        at: Long,
        action: String,
        extras: Intent.() -> Unit,
    ) {
        val intent = pending(requestCode, action, extras)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    private fun cancel(
        requestCode: Int,
        action: String,
    ) {
        alarms.cancel(pending(requestCode, action) {})
    }

    private fun pending(
        requestCode: Int,
        action: String,
        extras: Intent.() -> Unit,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            requestCode,
            Intent(app, RexiReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun millis(
        time: LocalDateTime,
        zone: ZoneId,
    ): Long = time.atZone(zone).toInstant().toEpochMilli()

    private fun code(
        base: Int,
        offset: Int,
        part: DayPart,
    ): Int = base + (offset + 1) * 10 + part.ordinal

    companion object {
        const val REPEAT_MS = 10 * 60_000L

        /** How far back unanswered doses are written down as missed after the phone was off. */
        private const val CATCH_UP_DAYS = 7L
        private const val SOON_MS = 5_000L
        private const val REFILL_HOUR = 10
        private const val REMIND_BASE = 1_000
        private const val MISS_BASE = 2_000
        private const val REFILL_CODE = 3_000
        private const val CUSTOM_WAKE_CODE = 4_000
        private const val AS_NEEDED_BASE = 1_000_000

        fun supply(
            med: MedicineWithSlots,
            today: LocalDate,
        ) = when (med.medicine.supplyMode) {
            SupplyMode.COUNT -> {
                Supply.counted(med.medicine.supplyCount, Schedule.dailyUse(med.plan()), today, med.medicine.refillLeadDays)
            }

            SupplyMode.DURATION -> {
                Supply.timed(LocalDate.ofEpochDay(med.medicine.packOpenedDay), med.medicine.packDays, today, med.medicine.refillLeadDays)
            }

            SupplyMode.NONE -> {
                Supply.UNKNOWN
            }
        }
    }
}

fun Intent.putDayPart(
    day: LocalDate,
    part: DoseSlot,
): Intent = putExtra(RexiReceiver.EXTRA_DAY, day.toEpochDay()).putExtra(RexiReceiver.EXTRA_PART, part.key)

fun Intent.dayExtra(): LocalDate? =
    if (hasExtra(RexiReceiver.EXTRA_DAY)) LocalDate.ofEpochDay(getLongExtra(RexiReceiver.EXTRA_DAY, 0)) else null

fun Intent.partExtra(): DoseSlot? = getStringExtra(RexiReceiver.EXTRA_PART)?.let(DoseSlot::fromKey)
