package top.yeetfactory.rexi.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Every piece of text that turns numbers and dates into words. The clock follows the phone's 12 or 24 hour setting. */
object Format {
    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val longDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)
    private val shortDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private val weekdayFormat = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
    private val monthFormat = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)

    fun time(
        minutes: Int,
        use24: Boolean,
    ): String {
        val wrapped = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val hour = wrapped / 60
        val minute = wrapped % 60
        if (use24) return "%02d:%02d".format(Locale.ENGLISH, hour, minute)
        val twelve = if (hour % 12 == 0) 12 else hour % 12
        val half = if (hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(Locale.ENGLISH, twelve, minute, half)
    }

    fun time(
        epochMillis: Long,
        zone: ZoneId,
        use24: Boolean,
    ): String {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return time(local.hour * 60 + local.minute, use24)
    }

    fun minutesOfDay(
        epochMillis: Long,
        zone: ZoneId,
    ): Int {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        return local.hour * 60 + local.minute
    }

    /** "1", "½", "1½" or "2.5". Halves read better as fractions on a tablet, but not on a measure like ml. */
    fun number(
        value: Double,
        fractions: Boolean = true,
    ): String {
        val whole = floor(value)
        val rest = value - whole
        return when {
            abs(rest) < EPSILON -> whole.toLong().toString()
            fractions && abs(rest - 0.5) < EPSILON -> if (whole == 0.0) "½" else "${whole.toLong()}½"
            else -> "%.2f".format(Locale.ENGLISH, value).trimEnd('0').trimEnd('.')
        }
    }

    /** "1 tablet", "½ tablet", "6 sprays", "2.5 ml" or "1 scoop". */
    fun amount(
        value: Double,
        unit: DoseUnit,
        custom: String,
    ): String {
        val word =
            when {
                unit == DoseUnit.CUSTOM && custom.isNotBlank() -> custom.trim()
                value <= 1.0 -> unit.one
                else -> unit.many
            }
        return "${number(value, fractions = unit != DoseUnit.ML && unit != DoseUnit.UNIT)} $word"
    }

    /** The plural word alone, for "tablets left" or "sprays". */
    fun unitWord(
        unit: DoseUnit,
        custom: String,
        count: Double = 2.0,
    ): String =
        when {
            unit == DoseUnit.CUSTOM && custom.isNotBlank() -> custom.trim()
            count == 1.0 -> unit.one
            else -> unit.many
        }

    /** "4 h 55 min", "1 h", or "25 min". */
    fun duration(minutes: Long): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0L -> "$rest min"
            rest == 0L -> "$hours h"
            else -> "$hours h $rest min"
        }
    }

    /** "in the morning", but "at night": English doesn't say "in the night" for a time of day. */
    fun inPart(part: DayPart): String = if (part == DayPart.NIGHT) "at night" else "in the ${part.label.lowercase()}"

    /** "in the morning and at night", for a dose taken at more than one part of the day. */
    fun inParts(parts: Collection<DayPart>): String {
        val phrases = parts.sortedBy { it.ordinal }.map(::inPart)
        return if (phrases.size < 2) phrases.joinToString() else phrases.dropLast(1).joinToString(", ") + " and " + phrases.last()
    }

    /** "Every other day" reads better than "Every 2 days", the most common gap by far. */
    fun everyDays(days: Int): String = if (days == 2) "Every other day" else "Every $days days"

    fun day(date: LocalDate): String = dayFormat.format(date)

    fun longDay(date: LocalDate): String = longDayFormat.format(date)

    fun shortDate(date: LocalDate): String = shortDateFormat.format(date)

    fun fullDate(date: LocalDate): String = fullDateFormat.format(date)

    fun weekday(date: LocalDate): String = weekdayFormat.format(date)

    fun month(date: LocalDate): String = monthFormat.format(date)

    /** "9 to 15 November", or "28 October to 3 November" across a month. */
    fun range(
        from: LocalDate,
        to: LocalDate,
    ): String =
        if (from.month == to.month) {
            "${from.dayOfMonth} to ${to.dayOfMonth} ${month(to)}"
        } else {
            "${from.dayOfMonth} ${month(from)} to ${to.dayOfMonth} ${month(to)}"
        }

    /** Two letters for a medicine's tag, used where there's no room for its drawing. */
    fun initials(name: String): String =
        name
            .trim()
            .take(2)
            .replaceFirstChar { it.uppercaseChar() }
            .ifEmpty { "?" }

    private const val MINUTES_PER_DAY = 24 * 60
    private const val EPSILON = 0.0001
}
