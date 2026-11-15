package top.yeetfactory.rexi.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import top.yeetfactory.rexi.core.DayPart

@Dao
interface RexiDao {
    @Transaction
    @Query("SELECT * FROM medicine WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun watchMedicines(): Flow<List<MedicineWithSlots>>

    @Transaction
    @Query("SELECT * FROM medicine WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun medicines(): List<MedicineWithSlots>

    /** Including medicines the person stopped taking, so their history still has names. */
    @Transaction
    @Query("SELECT * FROM medicine ORDER BY name COLLATE NOCASE")
    fun watchAllMedicines(): Flow<List<MedicineWithSlots>>

    @Transaction
    @Query("SELECT * FROM medicine ORDER BY name COLLATE NOCASE")
    suspend fun allMedicinesWithSlots(): List<MedicineWithSlots>

    @Transaction
    @Query("SELECT * FROM medicine WHERE id = :id")
    fun watchMedicine(id: Long): Flow<MedicineWithSlots?>

    @Transaction
    @Query("SELECT * FROM medicine WHERE id = :id")
    suspend fun medicine(id: Long): MedicineWithSlots?

    @Insert
    suspend fun insert(medicine: Medicine): Long

    @Update
    suspend fun update(medicine: Medicine)

    @Query("DELETE FROM slot WHERE medicineId = :id")
    suspend fun clearSlots(id: Long)

    @Insert
    suspend fun insertSlots(slots: List<Slot>)

    @Transaction
    suspend fun replaceSlots(
        id: Long,
        slots: List<Slot>,
    ) {
        clearSlots(id)
        insertSlots(slots)
    }

    @Query("SELECT * FROM dose_log WHERE day BETWEEN :from AND :to")
    fun watchLogs(
        from: Long,
        to: Long,
    ): Flow<List<DoseLog>>

    @Query("SELECT * FROM dose_log WHERE day BETWEEN :from AND :to")
    suspend fun logs(
        from: Long,
        to: Long,
    ): List<DoseLog>

    @Query("SELECT * FROM dose_log WHERE medicineId = :id AND day = :day AND part = :part")
    suspend fun log(
        id: Long,
        day: Long,
        part: DayPart,
    ): DoseLog?

    @Query("SELECT * FROM dose_log WHERE id = :id")
    suspend fun logById(id: Long): DoseLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DoseLog): Long

    @Query("DELETE FROM dose_log WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM dose_log WHERE medicineId = :id AND part IS NULL AND status = 'TAKEN' AND at >= :since ORDER BY at")
    fun watchAsNeeded(
        id: Long,
        since: Long,
    ): Flow<List<DoseLog>>

    @Query("SELECT at FROM dose_log WHERE medicineId = :id AND part IS NULL AND status = 'TAKEN' AND at >= :since ORDER BY at")
    suspend fun asNeededTimes(
        id: Long,
        since: Long,
    ): List<Long>

    @Query("SELECT * FROM medicine")
    suspend fun allMedicines(): List<Medicine>

    @Query("SELECT * FROM slot")
    suspend fun allSlots(): List<Slot>

    @Query("SELECT * FROM dose_log")
    suspend fun allLogs(): List<DoseLog>

    @Query("DELETE FROM medicine")
    suspend fun deleteEverything()

    @Insert
    suspend fun insertMedicines(medicines: List<Medicine>)

    @Insert
    suspend fun insertLogs(logs: List<DoseLog>)
}

@Database(entities = [Medicine::class, Slot::class, DoseLog::class], version = 1, exportSchema = true)
abstract class RexiDatabase : RoomDatabase() {
    abstract fun dao(): RexiDao

    companion object {
        fun create(context: Context): RexiDatabase = Room.databaseBuilder(context, RexiDatabase::class.java, "rexi.db").build()
    }
}
