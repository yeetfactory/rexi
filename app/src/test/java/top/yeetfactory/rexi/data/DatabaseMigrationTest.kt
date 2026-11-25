package top.yeetfactory.rexi.data

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseStatus
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseMigrationTest {
    @Test fun `upgrading version one preserves preset slots and answered doses`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val path = context.getDatabasePath("rexi.db")
            path.parentFile?.mkdirs()
            val schema = File("schemas/top.yeetfactory.rexi.data.RexiDatabase/1.json")
            val database = JSONObject(schema.readText()).getJSONObject("database")

            SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
                database.getJSONArray("entities").objects().forEach { entity ->
                    val name = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                    entity.optJSONArray("indices")?.objects()?.forEach { index ->
                        old.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", name))
                    }
                    if (name == "medicine") {
                        val values = ContentValues()
                        entity.getJSONArray("fields").objects().forEach { field ->
                            val column = field.getString("columnName")
                            when (field.getString("affinity")) {
                                "INTEGER" -> values.put(column, 0L)
                                "REAL" -> values.put(column, 0.0)
                                else -> values.put(column, "")
                            }
                        }
                        values.put("id", 4L)
                        values.put("name", "Example")
                        values.put("unit", "TABLET")
                        values.put("kind", "TABLET")
                        values.put("color1", "WHITE")
                        values.put("color2", "WHITE")
                        values.put("frequency", "EVERY_DAY")
                        values.put("food", "WITH_FOOD")
                        values.put("supplyMode", "NONE")
                        old.insertOrThrow("medicine", null, values)
                    }
                }
                val setup = database.getJSONArray("setupQueries")
                (0 until setup.length()).forEach { old.execSQL(setup.getString(it)) }
                old.execSQL("INSERT INTO slot (medicineId, part, amount) VALUES (4, 'EVENING', 2.0)")
                old.execSQL("INSERT INTO dose_log VALUES (8, 4, 5, 'EVENING', 'TAKEN', 1000, 2.0, '')")
                old.version = 1
            }

            val upgraded = RexiDatabase.create(context)
            try {
                assertEquals(
                    "Example",
                    upgraded
                        .dao()
                        .medicine(4)
                        ?.medicine
                        ?.name,
                )
                assertEquals(listOf(Slot(4, DayPart.EVENING, 2.0)), upgraded.dao().allSlots())
                val log = upgraded.dao().log(4, 5, DayPart.EVENING)!!
                assertEquals(8L, log.id)
                assertEquals(DoseStatus.TAKEN, log.status)
                assertFalse(log.historical)
            } finally {
                upgraded.close()
            }
        }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}
