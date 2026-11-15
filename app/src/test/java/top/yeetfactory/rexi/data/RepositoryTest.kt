package top.yeetfactory.rexi.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Frequency
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

    @Test fun `changing when doses fall starts the schedule again, a new amount doesn't`() =
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
            clocked.save(db.dao().medicine(id)!!.medicine, mapOf(DayPart.MORNING to 2.0))
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
}
