package top.yeetfactory.rexi.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.SupplyMode

/**
 * A backup is one JSON file the person keeps wherever they like. Nothing is sent anywhere: the system's file
 * picker decides where it goes. A restore replaces everything, because merging two histories can't be done honestly.
 */
class Backup(
    private val db: RexiDatabase,
    private val settings: SettingsStore,
) {
    private val dao = db.dao()

    suspend fun export(exportedAt: Long): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        root.put("settings", JSONObject(settings.export()))
        root.put("medicines", JSONArray(dao.allMedicines().map(::medicineJson)))
        root.put("slots", JSONArray(dao.allSlots().map(::slotJson)))
        root.put("logs", JSONArray(dao.allLogs().map(::logJson)))
        return root.toString(2)
    }

    /** Returns how many medicines came back. Throws [IllegalArgumentException] for a file that isn't a Rexi backup. */
    suspend fun restore(text: String): Int {
        val root = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("This file isn't a Rexi backup.") }
        require(root.optString("format") == FORMAT) { "This file isn't a Rexi backup." }
        require(root.optInt("version") <= VERSION) { "This backup comes from a newer version of Rexi." }
        val medicines = root.getJSONArray("medicines").objects().map(::medicineFrom)
        val slots = root.getJSONArray("slots").objects().map(::slotFrom)
        val logs = root.getJSONArray("logs").objects().map(::logFrom)
        db.withTransaction {
            dao.deleteEverything()
            dao.insertMedicines(medicines)
            dao.insertSlots(slots)
            dao.insertLogs(logs)
        }
        val saved = root.optJSONObject("settings")
        if (saved != null) {
            settings.import(saved.keys().asSequence().associateWith { key -> saved.get(key).let { if (it is Number) it.toInt() else it } })
        }
        return medicines.size
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun medicineJson(m: Medicine) =
        JSONObject()
            .put("id", m.id)
            .put("name", m.name)
            .put("strength", m.strength)
            .put("strengthUnit", m.strengthUnit)
            .put("unit", m.unit.name)
            .put("customUnit", m.customUnit)
            .put("purpose", m.purpose)
            .put("notes", m.notes)
            .put("kind", m.kind.name)
            .put("otherKind", m.otherKind)
            .put("color1", m.color1.name)
            .put("color2", m.color2.name)
            .put("frequency", m.frequency.name)
            .put("daysMask", m.daysMask)
            .put("everyDays", m.everyDays)
            .put("courseDays", m.courseDays)
            .put("startDay", m.startDay)
            .put("food", m.food.name)
            .put("asNeededAmount", m.asNeededAmount)
            .put("minGapHours", m.minGapHours)
            .put("maxPerDay", m.maxPerDay)
            .put("supplyMode", m.supplyMode.name)
            .put("supplyCount", m.supplyCount)
            .put("supplyFull", m.supplyFull)
            .put("packDays", m.packDays)
            .put("packOpenedDay", m.packOpenedDay)
            .put("refillLeadDays", m.refillLeadDays)
            .put("refillSnoozedUntil", m.refillSnoozedUntil)
            .put("scheduleFrom", m.scheduleFrom)
            .put("paused", m.paused)
            .put("archived", m.archived)
            .put("createdAt", m.createdAt)

    private fun medicineFrom(o: JSONObject) =
        Medicine(
            id = o.getLong("id"),
            name = o.getString("name"),
            strength = o.optString("strength"),
            strengthUnit = o.optString("strengthUnit", "mg"),
            unit = enumOf(o.optString("unit"), DoseUnit.TABLET),
            customUnit = o.optString("customUnit"),
            purpose = o.optString("purpose"),
            notes = o.optString("notes"),
            kind = enumOf(o.optString("kind"), MedKind.TABLET),
            otherKind = o.optString("otherKind"),
            color1 = enumOf(o.optString("color1"), MedColor.WHITE),
            color2 = enumOf(o.optString("color2"), MedColor.WHITE),
            frequency = enumOf(o.optString("frequency"), Frequency.EVERY_DAY),
            daysMask = o.optInt("daysMask", 0b111_1111),
            everyDays = o.optInt("everyDays", 2),
            courseDays = o.optInt("courseDays", 7),
            startDay = o.getLong("startDay"),
            food = enumOf(o.optString("food"), Food.ANY),
            asNeededAmount = o.optDouble("asNeededAmount", 1.0),
            minGapHours = o.optInt("minGapHours", 6),
            maxPerDay = o.optInt("maxPerDay", 3),
            supplyMode = enumOf(o.optString("supplyMode"), SupplyMode.NONE),
            supplyCount = o.optDouble("supplyCount", 0.0),
            supplyFull = o.optDouble("supplyFull", 0.0),
            packDays = o.optInt("packDays", 30),
            packOpenedDay = o.optLong("packOpenedDay", o.getLong("startDay")),
            refillLeadDays = o.optInt("refillLeadDays", 7),
            refillSnoozedUntil = o.optLong("refillSnoozedUntil", 0),
            scheduleFrom = o.optLong("scheduleFrom", 0),
            paused = o.optBoolean("paused"),
            archived = o.optBoolean("archived"),
            createdAt = o.optLong("createdAt"),
        )

    private fun slotJson(s: Slot) =
        JSONObject()
            .put("medicineId", s.medicineId)
            .put("part", s.part.name)
            .put("amount", s.amount)

    private fun slotFrom(o: JSONObject) = Slot(o.getLong("medicineId"), enumOf(o.getString("part"), DayPart.MORNING), o.getDouble("amount"))

    private fun logJson(l: DoseLog) =
        JSONObject()
            .put("id", l.id)
            .put("medicineId", l.medicineId)
            .put("day", l.day)
            .put("part", l.part?.name ?: JSONObject.NULL)
            .put("status", l.status.name)
            .put("at", l.at)
            .put("amount", l.amount)
            .put("reason", l.reason)

    private fun logFrom(o: JSONObject) =
        DoseLog(
            id = o.getLong("id"),
            medicineId = o.getLong("medicineId"),
            day = o.getLong("day"),
            part = if (o.isNull("part")) null else enumOf(o.getString("part"), DayPart.MORNING),
            status = enumOf(o.getString("status"), DoseStatus.TAKEN),
            at = o.getLong("at"),
            amount = o.optDouble("amount", 1.0),
            reason = o.optString("reason"),
        )

    private inline fun <reified T : Enum<T>> enumOf(
        name: String,
        fallback: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: fallback

    companion object {
        private const val FORMAT = "rexi-backup"
        private const val VERSION = 1
    }
}
