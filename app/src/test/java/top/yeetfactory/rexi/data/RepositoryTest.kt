package top.yeetfactory.rexi.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yeetfactory.rexi.core.CustomTime
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayClock
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.PastDose
import top.yeetfactory.rexi.core.SupplyMode
import java.time.LocalDate
import java.time.ZoneOffset

fun inMemoryDatabase(): RexiDatabase =
    Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), RexiDatabase::class.java)
        .allowMainThreadQueries()
        .build()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryTest {
    private val db = inMemoryDatabase()
    private val repository = Repository(db, clock = { 1_000L }, zone = { ZoneOffset.UTC })
    private val day = LocalDate.of(2026, 11, 15)

    @After fun close() = db.close()

    private suspend fun tablets(count: Double) =
        repository.save(
            Medicine(
                name = "Metformin",
                startDay = day.toEpochDay(),
                supplyMode = SupplyMode.COUNT,
                supplyCount = count,
                supplyFull = count,
            ),
            mapOf(DayPart.MORNING to 1.0, DayPart.EVENING to 1.0),
        )

    private suspend fun count(id: Long) =
        db
            .dao()
            .medicine(id)!!
            .medicine.supplyCount

    @Test fun `taking a dose takes it off the count, and undo puts it back`() =
        runTest {
            val id = tablets(23.0)
            repository.take(id, day, DayPart.MORNING, 1_000, 1.0)
            assertEquals(22.0, count(id), 0.0)
            repository.undo(id, day, DayPart.MORNING)
            assertEquals(23.0, count(id), 0.0)
        }

    @Test fun `taking the same dose twice only counts once`() =
        runTest {
            val id = tablets(10.0)
            repository.take(id, day, DayPart.MORNING, 1_000, 1.0)
            repository.take(id, day, DayPart.MORNING, 2_000, 1.0)
            assertEquals(9.0, count(id), 0.0)
        }

    @Test fun `skipping a dose that was taken gives the tablet back`() =
        runTest {
            val id = tablets(10.0)
            repository.take(id, day, DayPart.MORNING, 1_000, 1.0)
            repository.skip(id, day, DayPart.MORNING, 1.0, "Felt unwell")
            assertEquals(10.0, count(id), 0.0)
            assertEquals(DoseStatus.SKIPPED, db.dao().log(id, day.toEpochDay(), DayPart.MORNING)!!.status)
        }

    @Test fun `the app never marks a dose missed once someone answered it`() =
        runTest {
            val id = tablets(10.0)
            repository.take(id, day, DayPart.MORNING, 1_000, 1.0)
            assertFalse(repository.markMissed(id, day, DayPart.MORNING, 1.0))
            assertTrue(repository.markMissed(id, day, DayPart.EVENING, 1.0))
            assertEquals(DoseStatus.TAKEN, db.dao().log(id, day.toEpochDay(), DayPart.MORNING)!!.status)
        }

    @Test fun `a missed dose from an earlier day can be logged as taken later, and comes off the count`() =
        runTest {
            val id = tablets(10.0)
            val earlier = day.minusDays(2)
            repository.markMissed(id, earlier, DayPart.MORNING, 1.0)
            repository.take(id, earlier, DayPart.MORNING, 500, 1.0)
            val log = db.dao().log(id, earlier.toEpochDay(), DayPart.MORNING)!!
            assertEquals(DoseStatus.TAKEN, log.status)
            assertEquals(500L, log.at)
            assertEquals(9.0, count(id), 0.0)
        }

    @Test fun `saying a taken dose wasn't taken keeps it as missed and gives the tablet back`() =
        runTest {
            val id = tablets(10.0)
            repository.take(id, day, DayPart.MORNING, 500, 1.0)
            repository.notTaken(id, day, DayPart.MORNING, 1.0)
            repository.notTaken(id, day, DayPart.MORNING, 1.0)
            assertEquals(DoseStatus.MISSED, db.dao().log(id, day.toEpochDay(), DayPart.MORNING)!!.status)
            assertEquals(10.0, count(id), 0.0)
            assertFalse(repository.markMissed(id, day, DayPart.MORNING, 1.0))
        }

    @Test fun `a skipped dose can be said not taken, and an unanswered one is written down as missed`() =
        runTest {
            val id = tablets(10.0)
            repository.skip(id, day, DayPart.MORNING, 1.0)
            repository.notTaken(id, day, DayPart.MORNING, 1.0)
            repository.notTaken(id, day, DayPart.EVENING, 1.0)
            assertEquals(DoseStatus.MISSED, db.dao().log(id, day.toEpochDay(), DayPart.MORNING)!!.status)
            assertEquals(DoseStatus.MISSED, db.dao().log(id, day.toEpochDay(), DayPart.EVENING)!!.status)
            assertEquals(10.0, count(id), 0.0)
        }

    @Test fun `a spray tracked by time keeps no count`() =
        runTest {
            val id =
                repository.save(
                    Medicine(
                        name = "Hair growth spray",
                        unit = DoseUnit.SPRAY,
                        startDay = day.toEpochDay(),
                        supplyMode = SupplyMode.DURATION,
                    ),
                    mapOf(DayPart.MORNING to 6.0),
                )
            repository.take(id, day, DayPart.MORNING, 1_000, 6.0)
            assertEquals(0.0, count(id), 0.0)
        }

    @Test fun `as-needed doses can be logged many times a day and removed`() =
        runTest {
            val id =
                repository.save(
                    Medicine(
                        name = "Ibuprofen",
                        frequency = Frequency.AS_NEEDED,
                        startDay = day.toEpochDay(),
                        supplyMode = SupplyMode.COUNT,
                        supplyCount = 20.0,
                    ),
                    emptyMap(),
                )
            val first = repository.logAsNeeded(id, 1_000, 1.0, "Headache")
            repository.logAsNeeded(id, 2_000, 1.0, "")
            assertEquals(listOf(1_000L, 2_000L), db.dao().asNeededTimes(id, 0))
            assertEquals(18.0, count(id), 0.0)
            repository.undoAsNeeded(first)
            assertEquals(19.0, count(id), 0.0)
        }

    @Test fun `saving replaces the parts of the day`() =
        runTest {
            val id = tablets(10.0)
            val saved = db.dao().medicine(id)!!.medicine
            repository.save(saved, mapOf(DayPart.NIGHT to 2.0))
            assertEquals(mapOf(DayPart.NIGHT to 2.0), db.dao().medicine(id)!!.amounts)
        }

    @Test fun `changing when doses fall starts the schedule again, a new amount or food instruction doesn't`() =
        runTest {
            var now = 1_000L
            val clocked = Repository(db, clock = { now }, zone = { ZoneOffset.UTC })
            val id = clocked.save(Medicine(name = "Metformin", startDay = day.toEpochDay()), mapOf(DayPart.MORNING to 1.0))
            assertEquals(
                1_000L,
                db
                    .dao()
                    .medicine(id)!!
                    .medicine.scheduleFrom,
            )
            now = 2_000L
            val changed =
                db
                    .dao()
                    .medicine(id)!!
                    .medicine
                    .copy(food = Food.AFTER_FOOD, daysMask = 1)
            clocked.save(changed, mapOf(DayPart.MORNING to 2.0))
            assertEquals(
                1_000L,
                db
                    .dao()
                    .medicine(id)!!
                    .medicine.scheduleFrom,
            )
            now = 3_000L
            clocked.save(db.dao().medicine(id)!!.medicine, mapOf(DayPart.MORNING to 2.0, DayPart.NIGHT to 1.0))
            assertEquals(
                3_000L,
                db
                    .dao()
                    .medicine(id)!!
                    .medicine.scheduleFrom,
            )
        }

    @Test fun `past doses are saved at the entered time without creating misses or using current supply`() =
        runTest {
            val past = day.minusDays(30)
            val takenAt = past.atTime(6, 41).toInstant(ZoneOffset.UTC).toEpochMilli()
            val now = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val clocked = Repository(db, clock = { now }, zone = { ZoneOffset.UTC })
            val dawn = CustomTime(6 * 60 + 30)
            val afternoon = CustomTime(14 * 60)
            val id =
                clocked.save(
                    Medicine(name = "Example", startDay = day.toEpochDay(), supplyMode = SupplyMode.COUNT, supplyCount = 30.0),
                    mapOf(dawn to 1.0, afternoon to 1.0),
                    listOf(PastDose(takenAt, 2.0, dawn), PastDose(takenAt + 60_000, 1.0)),
                )
            val saved = db.dao().medicine(id)!!
            assertEquals(mapOf(dawn to 1.0, afternoon to 1.0), saved.amounts)
            assertEquals(now, saved.medicine.scheduleFrom)
            val logs = db.dao().logs(past.toEpochDay(), day.toEpochDay())
            assertEquals(2, logs.size)
            val clock = DayClock(emptyMap(), 180)
            val view = DayBook.build(past, listOf(saved.plan(ZoneOffset.UTC)), logs.map { it.logged() }, day.atTime(12, 0), clock)
            assertNull(view.part(afternoon))
            assertEquals(takenAt, view.part(dawn)?.takenAt)
            assertEquals(
                2.0,
                view
                    .part(dawn)!!
                    .doses
                    .single()
                    .amount,
                0.0,
            )
            assertFalse(clocked.markMissed(id, past, dawn, 1.0))
            assertEquals(30.0, count(id), 0.0)
            clocked.undo(id, past, dawn)
            clocked.undoAsNeeded(logs.single { it.part == null }.id)
            assertEquals(30.0, count(id), 0.0)
        }

    @Test fun `a confirmed range excludes earlier hours and leaves later doses due`() =
        runTest {
            fun time(
                hour: Int,
                minute: Int,
            ): Long = day.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()
            val through = time(21, 0)
            val now = time(21, 20)
            val clocked = Repository(db, clock = { now }, zone = { ZoneOffset.UTC })
            val before = CustomTime(20 * 60 + 15)
            val first = CustomTime(20 * 60 + 45)
            val later = CustomTime(21 * 60 + 5)
            val id =
                clocked.save(
                    Medicine(name = "Example", startDay = day.toEpochDay(), supplyMode = SupplyMode.COUNT, supplyCount = 14.0),
                    mapOf(before to 1.0, first to 1.0, later to 1.0),
                    listOf(PastDose(time(20, 45), 1.0, first)),
                    pastDosesThrough = through,
                )
            val saved = db.dao().medicine(id)!!
            val logs = db.dao().allLogs().map { it.logged() }
            val view = DayBook.build(day, listOf(saved.plan(ZoneOffset.UTC)), logs, day.atTime(21, 20), DayClock(emptyMap(), 180))
            assertNull(view.part(before))
            assertEquals(
                DoseState.TAKEN,
                view
                    .part(first)
                    ?.doses
                    ?.single()
                    ?.state,
            )
            assertEquals(
                DoseState.DUE,
                view
                    .part(later)
                    ?.doses
                    ?.single()
                    ?.state,
            )
            assertEquals(14.0, count(id), 0.0)
        }

    @Test fun `a future past dose leaves no partial medicine behind`() =
        runTest {
            val now = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val clocked = Repository(db, clock = { now }, zone = { ZoneOffset.UTC })
            val result =
                runCatching {
                    clocked.save(Medicine(name = "Example", startDay = day.toEpochDay()), emptyMap(), listOf(PastDose(now + 1, 1.0)))
                }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(db.dao().allMedicines().isEmpty())
            assertTrue(db.dao().allLogs().isEmpty())
        }
}
