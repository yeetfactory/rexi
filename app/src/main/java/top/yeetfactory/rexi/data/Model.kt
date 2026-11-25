package top.yeetfactory.rexi.data

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.parcelize.Parcelize
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.Logged
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.Plan
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.core.SupplyMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One medicine, of any kind. Nothing here assumes a pill: a dose is an amount of [unit], the drawing is one of
 * [MedKind], and the supply is either counted or timed. It's Parcelable because the edit page keeps the saved
 * medicine it started from while Android rebuilds the screen.
 */
@Parcelize
@Entity(tableName = "medicine")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Free text such as "500" or "5". Empty when the person skipped it. */
    val strength: String = "",
    val strengthUnit: String = "mg",
    val unit: DoseUnit = DoseUnit.TABLET,
    val customUnit: String = "",
    val purpose: String = "",
    val notes: String = "",
    val kind: MedKind = MedKind.TABLET,
    val otherKind: String = "",
    val color1: MedColor = MedColor.WHITE,
    val color2: MedColor = MedColor.WHITE,
    val frequency: Frequency = Frequency.EVERY_DAY,
    val daysMask: Int = Schedule.ALL_DAYS,
    val everyDays: Int = 2,
    val courseDays: Int = 7,
    /** Epoch day when the schedule starts. Courses and "every few days" count from here. */
    val startDay: Long,
    val food: Food = Food.ANY,
    val asNeededAmount: Double = 1.0,
    val minGapHours: Int = 6,
    val maxPerDay: Int = 3,
    val supplyMode: SupplyMode = SupplyMode.NONE,
    val supplyCount: Double = 0.0,
    /** The count after the last refill, so the bar on the medicine's page has something to measure against. */
    val supplyFull: Double = 0.0,
    val packDays: Int = 30,
    val packOpenedDay: Long = startDay,
    val refillLeadDays: Int = 7,
    val refillSnoozedUntil: Long = 0,
    /** When the current schedule took effect, in epoch millis. See [Plan.from]. */
    val scheduleFrom: Long = 0,
    val paused: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = 0,
) : Parcelable {
    val strengthText: String get() = if (strength.isBlank()) "" else "${strength.trim()} $strengthUnit"

    val title: String get() = name.ifBlank { "Untitled" }

    fun amount(value: Double): String = Format.amount(value, unit, customUnit)

    fun unitWord(count: Double = 2.0): String = Format.unitWord(unit, customUnit, count)

    val kindName: String get() = if (kind == MedKind.OTHER && otherKind.isNotBlank()) otherKind.trim() else kind.label
}

@Entity(
    tableName = "slot",
    primaryKeys = ["medicineId", "part"],
    foreignKeys = [
        ForeignKey(entity = Medicine::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class Slot(
    val medicineId: Long,
    val part: DoseSlot,
    val amount: Double,
)

/** One answered dose. A part identifies a scheduled dose; unscheduled logs can occur any number of times. */
@Entity(
    tableName = "dose_log",
    foreignKeys = [
        ForeignKey(entity = Medicine::class, parentColumns = ["id"], childColumns = ["medicineId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["medicineId", "day", "part"], unique = true), Index(value = ["day"])],
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val day: Long,
    val part: DoseSlot?,
    val status: DoseStatus,
    val at: Long,
    val amount: Double,
    val reason: String = "",
    /** The current supply count was entered after this dose was taken. */
    @ColumnInfo(defaultValue = "0") val historical: Boolean = false,
) {
    fun logged(): Logged = Logged(medicineId, LocalDate.ofEpochDay(day), part, status, at, amount, reason, historical)
}

data class MedicineWithSlots(
    @Embedded val medicine: Medicine,
    @Relation(parentColumn = "id", entityColumn = "medicineId") val slots: List<Slot>,
) {
    val id: Long get() = medicine.id

    val amounts: Map<DoseSlot, Double> get() = slots.associate { it.part to it.amount }

    val asNeeded: Boolean get() = medicine.frequency == Frequency.AS_NEEDED

    fun plan(zone: ZoneId = ZoneId.systemDefault()): Plan =
        Plan(
            id = medicine.id,
            frequency = medicine.frequency,
            daysMask = medicine.daysMask,
            everyDays = medicine.everyDays,
            startDay = LocalDate.ofEpochDay(medicine.startDay),
            courseDays = medicine.courseDays,
            slots = amounts,
            active = !medicine.paused && !medicine.archived,
            from =
                medicine.scheduleFrom
                    .takeIf { it > 0 }
                    ?.let { Instant.ofEpochMilli(it - SCHEDULE_GRACE_MS).atZone(zone).toLocalDateTime() },
        )

    companion object {
        /** A dose due up to an hour before a medicine was added still counts, since it may just have been taken. */
        const val SCHEDULE_GRACE_MS = 3_600_000L
    }
}
