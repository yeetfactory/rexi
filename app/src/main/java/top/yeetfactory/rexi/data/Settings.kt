package top.yeetfactory.rexi.data

import android.app.UiModeManager
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.yeetfactory.rexi.core.DayClock
import top.yeetfactory.rexi.core.DayPart
import java.time.LocalDate

enum class ThemeMode(
    val label: String,
    val nightMode: Int,
) {
    SYSTEM("System", UiModeManager.MODE_NIGHT_AUTO),
    LIGHT("Light", UiModeManager.MODE_NIGHT_NO),
    DARK("Dark", UiModeManager.MODE_NIGHT_YES),
}

data class AppSettings(
    val partMinutes: Map<DayPart, Int> = DayPart.entries.associateWith { it.defaultMinutes },
    val snoozeMinutes: Int = 15,
    val repeatUntilAnswered: Boolean = true,
    val overLockScreen: Boolean = true,
    val quietHours: Boolean = true,
    val quietStart: Int = 23 * 60,
    val quietEnd: Int = 7 * 60,
    val missedAfterHours: Int = 3,
    val appLock: Boolean = false,
    /**
     * How long someone can be away before the app lock asks again. A minute by default, so stepping out to a file
     * picker or the share sheet doesn't lock it. Zero asks every time.
     */
    val lockAfterSeconds: Int = 60,
    val hideNames: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboarded: Boolean = false,
    val lastBackupAt: Long = 0,
    /** Set when the phone changed time zone, so Today can say once that reminders follow the local clock. */
    val zoneNotice: Boolean = false,
) {
    val clock: DayClock get() = DayClock(partMinutes, missedAfterHours * 60)

    fun minutes(part: DayPart): Int = partMinutes[part] ?: part.defaultMinutes

    /** Quiet hours can wrap past midnight, such as 23:00 to 07:00. */
    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!quietHours || quietStart == quietEnd) return false
        return if (quietStart < quietEnd) {
            minuteOfDay in quietStart until quietEnd
        } else {
            minuteOfDay >= quietStart || minuteOfDay < quietEnd
        }
    }
}

/** Settings and small bits of reminder state that must outlive the process. */
class SettingsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("rexi", Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val value: AppSettings get() = state.value

    private fun read() =
        AppSettings(
            partMinutes = DayPart.entries.associateWith { prefs.getInt("part:${it.name}", it.defaultMinutes) },
            snoozeMinutes = prefs.getInt("snoozeMinutes", 15),
            repeatUntilAnswered = prefs.getBoolean("repeat", true),
            overLockScreen = prefs.getBoolean("overLockScreen", true),
            quietHours = prefs.getBoolean("quietHours", true),
            quietStart = prefs.getInt("quietStart", 23 * 60),
            quietEnd = prefs.getInt("quietEnd", 7 * 60),
            missedAfterHours = prefs.getInt("missedAfterHours", 3),
            appLock = prefs.getBoolean("appLock", false),
            lockAfterSeconds = prefs.getInt("lockAfterSeconds", 60),
            hideNames = prefs.getBoolean("hideNames", false),
            theme =
                prefs
                    .getString("theme", null)
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            onboarded = prefs.getBoolean("onboarded", false),
            lastBackupAt = prefs.getLong("lastBackupAt", 0),
            zoneNotice = prefs.getBoolean("zoneNotice", false),
        )

    fun update(change: (AppSettings) -> AppSettings) {
        state.update(change)
        val value = state.value
        prefs.edit {
            value.partMinutes.forEach { (part, minutes) -> putInt("part:${part.name}", minutes) }
            putInt("snoozeMinutes", value.snoozeMinutes)
            putBoolean("repeat", value.repeatUntilAnswered)
            putBoolean("overLockScreen", value.overLockScreen)
            putBoolean("quietHours", value.quietHours)
            putInt("quietStart", value.quietStart)
            putInt("quietEnd", value.quietEnd)
            putInt("missedAfterHours", value.missedAfterHours)
            putBoolean("appLock", value.appLock)
            putInt("lockAfterSeconds", value.lockAfterSeconds)
            putBoolean("hideNames", value.hideNames)
            putString("theme", value.theme.name)
            putBoolean("onboarded", value.onboarded)
            putLong("lastBackupAt", value.lastBackupAt)
            putBoolean("zoneNotice", value.zoneNotice)
        }
    }

    /** When a snoozed compartment should ring again, or 0 if it isn't snoozed. */
    fun snoozedUntil(
        day: LocalDate,
        part: DayPart,
    ): Long = prefs.getLong(snoozeKey(day, part), 0)

    fun snooze(
        day: LocalDate,
        part: DayPart,
        until: Long,
    ) = prefs.edit { putLong(snoozeKey(day, part), until) }

    fun clearSnooze(
        day: LocalDate,
        part: DayPart,
    ) = prefs.edit { remove(snoozeKey(day, part)) }

    /** When a compartment last rang, so a repeat comes ten minutes after it rather than straight away. */
    fun rangAt(
        day: LocalDate,
        part: DayPart,
    ): Long = prefs.getLong("rang:${day.toEpochDay()}:${part.name}", 0)

    fun setRang(
        day: LocalDate,
        part: DayPart,
        at: Long,
    ) = prefs.edit { putLong("rang:${day.toEpochDay()}:${part.name}", at) }

    /** Old snoozes and rings are only clutter. Anything before [day] is dropped. */
    fun forgetSnoozesBefore(day: LocalDate) {
        val stale =
            prefs.all.keys.filter { key ->
                (key.startsWith("snooze:") || key.startsWith("rang:")) &&
                    (key.split(":").getOrNull(1)?.toLongOrNull() ?: 0) < day.toEpochDay()
            }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
    }

    /** A reminder the person asked for on the as-needed sheet: "Remind me when it's fine again". */
    fun asNeededReminder(medId: Long): Long = prefs.getLong("prn:$medId", 0)

    fun setAsNeededReminder(
        medId: Long,
        at: Long,
    ) = prefs.edit { if (at > 0) putLong("prn:$medId", at) else remove("prn:$medId") }

    fun asNeededReminders(): Map<Long, Long> =
        prefs.all
            .filterKeys { it.startsWith("prn:") }
            .mapNotNull { (key, value) -> key.removePrefix("prn:").toLongOrNull()?.let { id -> id to (value as? Long ?: 0L) } }
            .toMap()

    /** The day a refill warning last went out for a medicine, so it comes once a day at most. */
    fun refillWarnedOn(medId: Long): Long = prefs.getLong("refill:$medId", Long.MIN_VALUE)

    fun setRefillWarnedOn(
        medId: Long,
        day: LocalDate,
    ) = prefs.edit { putLong("refill:$medId", day.toEpochDay()) }

    var lastZone: String
        get() = prefs.getString("lastZone", "").orEmpty()
        set(value) = prefs.edit { putString("lastZone", value) }

    /** Android tells a refused permission from a permanently refused one only through the asking, so remember it. */
    fun wasAsked(permission: String) = prefs.getBoolean("asked:$permission", false)

    fun markAsked(permission: String) = prefs.edit { putBoolean("asked:$permission", true) }

    /** Everything a backup needs to bring the settings back. */
    fun export(): Map<String, Any?> = prefs.all.filterKeys { key -> BACKED_UP.any { key == it || key.startsWith("part:") } }

    fun import(values: Map<String, Any?>) {
        prefs.edit {
            values.forEach { (key, value) ->
                when (value) {
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                }
            }
        }
        state.value = read()
    }

    private fun snoozeKey(
        day: LocalDate,
        part: DayPart,
    ) = "snooze:${day.toEpochDay()}:${part.name}"

    companion object {
        private val BACKED_UP =
            setOf(
                "snoozeMinutes",
                "repeat",
                "overLockScreen",
                "quietHours",
                "quietStart",
                "quietEnd",
                "missedAfterHours",
                "hideNames",
                "theme",
            )
    }
}
