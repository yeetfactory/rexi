package top.yeetfactory.rexi.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

data class SupplyInfo(
    /** Null when it can't be worked out, such as a count for something taken only when needed. */
    val daysLeft: Int?,
    val runOut: LocalDate?,
    val remindOn: LocalDate?,
    val low: Boolean,
)

object Supply {
    val UNKNOWN = SupplyInfo(daysLeft = null, runOut = null, remindOn = null, low = false)

    /** A pack that's counted, like tablets: how long what's left lasts at the usual rate. */
    fun counted(
        count: Double,
        dailyUse: Double?,
        today: LocalDate,
        leadDays: Int,
    ): SupplyInfo {
        if (dailyUse == null || dailyUse <= 0.0) return UNKNOWN
        val days = floor(count / dailyUse).toInt().coerceAtLeast(0)
        val runOut = today.plusDays(days.toLong())
        return SupplyInfo(days, runOut, runOut.minusDays(leadDays.toLong()), days <= leadDays)
    }

    /** A pack that's timed, like a spray: it lasts about [packDays] from the day it was opened. */
    fun timed(
        opened: LocalDate,
        packDays: Int,
        today: LocalDate,
        leadDays: Int,
    ): SupplyInfo {
        val used = ChronoUnit.DAYS.between(opened, today).toInt()
        val left = (packDays - used).coerceAtLeast(0)
        val runOut = opened.plusDays(packDays.toLong())
        return SupplyInfo(left, runOut, runOut.minusDays(leadDays.toLong()), left <= leadDays)
    }

    /** How much of the pack is left, from 0 to 1, for the bar on a medicine's page. */
    fun fraction(
        left: Double,
        full: Double,
    ): Float = if (full <= 0.0) 0f else (left / full).coerceIn(0.0, 1.0).toFloat()
}
