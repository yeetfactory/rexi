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
import top.yeetfactory.rexi.core.CustomTime
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.PastDose
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

    @Test fun `custom times and after food survive a backup with past doses`() =
        runTest {
            val now = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val at = now - 3_600_000
            val late = CustomTime(22 * 60 + 55)
            val repository = Repository(source, clock = { now }, zone = { ZoneOffset.UTC })
            val id =
                repository.save(
                    Medicine(name = "Example", startDay = day.toEpochDay(), food = Food.AFTER_FOOD),
                    mapOf(late to 1.0),
                    listOf(PastDose(at, 1.0, late)),
                )
            Backup(target, settings).restore(Backup(source, settings).export(now))
            assertEquals(source.dao().medicine(id), target.dao().medicine(id))
            assertEquals(source.dao().allLogs(), target.dao().allLogs())
        }

    @Test fun `version one backups still read named slots`() =
        runTest {
            val text =
                """
                {
                  "format": "rexi-backup", "version": 1,
                  "medicines": [{"id": 9, "name": "Example", "startDay": 100}],
                  "slots": [{"medicineId": 9, "part": "MORNING", "amount": 1}],
                  "logs": [{"id": 1, "medicineId": 9, "day": 100, "part": "MORNING", "status": "TAKEN", "at": 8640000000}]
                }
                """.trimIndent()
            assertEquals(1, Backup(target, settings).restore(text))
            assertEquals(
                DayPart.MORNING,
                target
                    .dao()
                    .allSlots()
                    .single()
                    .part,
            )
            assertEquals(
                false,
                target
                    .dao()
                    .allLogs()
                    .single()
                    .historical,
            )
        }
}
