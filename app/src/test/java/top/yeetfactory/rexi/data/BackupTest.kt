package top.yeetfactory.rexi.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source = inMemoryDatabase()
    private val target = inMemoryDatabase()
    private val settings = SettingsStore(context)
    private val day = LocalDate.of(2026, 11, 15)

    @After fun close() {
        source.close()
        target.close()
    }

    @Test fun `a backup brings back every medicine, part of the day and dose`() =
        runTest {
            val repository = Repository(source, clock = { 5_000L }, zone = { ZoneOffset.UTC })
            val id =
                repository.save(
                    Medicine(
                        name = "Hair growth spray",
                        unit = DoseUnit.SPRAY,
                        kind = MedKind.SPRAY,
                        color1 = MedColor.TEAL,
                        notes = "On a dry scalp",
                        startDay = day.toEpochDay(),
                    ),
                    mapOf(DayPart.MORNING to 6.0, DayPart.NIGHT to 6.0),
                )
            repository.take(id, day, DayPart.MORNING, 5_000, 6.0)
            repository.skip(id, day, DayPart.NIGHT, 6.0, "Away")
            settings.update { it.copy(snoozeMinutes = 30, partMinutes = it.partMinutes + (DayPart.NIGHT to 23 * 60)) }

            val text = Backup(source, settings).export(exportedAt = 5_000)
            settings.update { it.copy(snoozeMinutes = 5, partMinutes = it.partMinutes + (DayPart.NIGHT to 21 * 60)) }
            val restored = Backup(target, settings).restore(text)

            assertEquals(1, restored)
            val med = target.dao().medicine(id)!!
            assertEquals("Hair growth spray", med.medicine.name)
            assertEquals(MedColor.TEAL, med.medicine.color1)
            assertEquals("On a dry scalp", med.medicine.notes)
            assertEquals(mapOf(DayPart.MORNING to 6.0, DayPart.NIGHT to 6.0), med.amounts)
            assertEquals(2, target.dao().allLogs().size)
            assertEquals("Away", target.dao().log(id, day.toEpochDay(), DayPart.NIGHT)!!.reason)
            assertEquals(30, settings.value.snoozeMinutes)
            assertEquals(23 * 60, settings.value.minutes(DayPart.NIGHT))
        }

    @Test fun `any other file is refused, and nothing is touched`() =
        runTest {
            val error = runCatching { Backup(target, settings).restore("""{"hello": 1}""") }.exceptionOrNull()
            assertEquals("This file isn't a Rexi backup.", error?.message)
            assertEquals(0, target.dao().allMedicines().size)
        }
}
