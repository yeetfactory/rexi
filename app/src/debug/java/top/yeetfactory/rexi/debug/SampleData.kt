package top.yeetfactory.rexi.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.DoseLog
import top.yeetfactory.rexi.data.Medicine
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Debug builds only: replaces everything with a realistic set of medicines and two weeks of history.
 * Send it with `adb shell am broadcast -n top.yeetfactory.rexi/.debug.SampleData`.
 */
class SampleData : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as RexiApp
        val pending = goAsync()
        app.scope.launch {
            try {
                fill(app)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fill(app: RexiApp) {
        val repository = app.repository
        val dao = repository.dao
        val zone = ZoneId.systemDefault()
        val today = repository.today()
        val start = today.minusDays(HISTORY_DAYS)
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        dao.deleteEverything()
        app.settings.update { it.copy(onboarded = true) }

        suspend fun add(
            medicine: Medicine,
            parts: Map<DayPart, Double>,
        ): Long {
            val id = repository.save(medicine, parts)
            dao.update(dao.medicine(id)!!.medicine.copy(scheduleFrom = startMillis))
            return id
        }

        val base = Medicine(name = "", startDay = start.toEpochDay())
        add(
            base.copy(
                name = "Metformin",
                strength = "500",
                purpose = "Blood sugar",
                notes = "Take with the biggest meal of the day.",
                food = Food.WITH_FOOD,
                supplyMode = SupplyMode.COUNT,
                supplyCount = 23.0,
                supplyFull = 60.0,
            ),
            mapOf(DayPart.MORNING to 1.0, DayPart.EVENING to 1.0),
        )
        add(
            base.copy(
                name = "Lisinopril",
                strength = "10",
                kind = MedKind.OVAL,
                color1 = MedColor.PINK,
                supplyMode = SupplyMode.COUNT,
                supplyCount = 6.0,
                supplyFull = 30.0,
            ),
            mapOf(DayPart.MORNING to 1.0),
        )
        add(
            base.copy(
                name = "Vitamin D3",
                strength = "1000",
                strengthUnit = "IU",
                unit = DoseUnit.CAPSULE,
                kind = MedKind.SOFTGEL,
                color1 = MedColor.AMBER,
                food = Food.WITH_FOOD,
                supplyMode = SupplyMode.COUNT,
                supplyCount = 41.0,
                supplyFull = 60.0,
            ),
            mapOf(DayPart.MORNING to 1.0),
        )
        add(
            base.copy(
                name = "Atorvastatin",
                strength = "20",
                kind = MedKind.OVAL,
                supplyMode = SupplyMode.COUNT,
                supplyCount = 18.0,
                supplyFull = 30.0,
            ),
            mapOf(DayPart.NIGHT to 1.0),
        )
        add(
            base.copy(
                name = "Hair growth spray",
                purpose = "Hair growth",
                unit = DoseUnit.SPRAY,
                kind = MedKind.SPRAY,
                color1 = MedColor.TEAL,
                supplyMode = SupplyMode.DURATION,
                packDays = 30,
                packOpenedDay = today.minusDays(14).toEpochDay(),
            ),
            mapOf(DayPart.MORNING to 6.0, DayPart.NIGHT to 6.0),
        )
        add(
            base.copy(
                name = "Azithromycin",
                strength = "500",
                unit = DoseUnit.CAPSULE,
                kind = MedKind.CAPSULE,
                color1 = MedColor.BLUE,
                frequency = Frequency.COURSE,
                courseDays = 3,
                startDay = today.minusDays(1).toEpochDay(),
            ),
            mapOf(DayPart.AFTERNOON to 1.0),
        )
        val ibuprofen =
            add(
                base.copy(
                    name = "Ibuprofen",
                    strength = "400",
                    kind = MedKind.TABLET,
                    color1 = MedColor.ORANGE,
                    frequency = Frequency.AS_NEEDED,
                    supplyMode = SupplyMode.COUNT,
                    supplyCount = 20.0,
                    supplyFull = 24.0,
                ),
                emptyMap(),
            )

        // Two weeks of answers: nearly everything taken, a couple of misses, and a skip with a reason.
        val medicines = dao.medicines()
        val plans = medicines.map { it.plan() }
        val clock = app.settings.value.clock
        val now = LocalDateTime.now(zone)
        val logs = mutableListOf<DoseLog>()
        for (offset in HISTORY_DAYS downTo 0) {
            val date = today.minusDays(offset)
            DayBook.build(date, plans, emptyList(), now, clock).parts.forEach { part ->
                part.doses.filter { !it.pending }.forEach { dose ->
                    val due =
                        clock
                            .dueAt(date, part.part)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                    val name = medicines.first { it.id == dose.medId }.medicine.name
                    val status =
                        when {
                            offset == 3L && part.part == DayPart.EVENING && name == "Metformin" -> DoseStatus.MISSED
                            offset == 1L && part.part == DayPart.NIGHT && name == "Atorvastatin" -> DoseStatus.SKIPPED
                            offset == 7L && part.part == DayPart.NIGHT && name == "Hair growth spray" -> DoseStatus.MISSED
                            else -> DoseStatus.TAKEN
                        }
                    val reason = if (status == DoseStatus.SKIPPED) "Felt unwell" else ""
                    logs +=
                        DoseLog(0, dose.medId, date.toEpochDay(), part.part, status, due + (offset * 37 % 11) * 60_000, dose.amount, reason)
                }
            }
        }
        val morning =
            today
                .atStartOfDay(zone)
                .plusHours(9)
                .plusMinutes(10)
                .toInstant()
                .toEpochMilli()
        logs += DoseLog(0, ibuprofen, today.toEpochDay(), null, DoseStatus.TAKEN, morning, 1.0, "Headache")
        logs += DoseLog(0, ibuprofen, today.minusDays(4).toEpochDay(), null, DoseStatus.TAKEN, morning - 4 * DAY_MS, 1.0, "Back pain")
        // Upsert, because the reminder engine may already have written some of these days down as missed.
        logs.forEach { log ->
            val existing = log.part?.let { dao.log(log.medicineId, log.day, it) }
            dao.upsert(log.copy(id = existing?.id ?: 0))
        }
        // The engine ran while medicines were going in without their history, and announced those days as missed.
        // Clear what it showed, then let it look again at the finished data.
        app.notifier.cancelAll()
        app.changed()
    }

    companion object {
        private const val HISTORY_DAYS = 14L
        private const val DAY_MS = 86_400_000L
    }
}
