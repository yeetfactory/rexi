package top.yeetfactory.rexi.core

data class AsNeededStatus(
    val lastAt: Long?,
    val takenToday: Int,
    val maxPerDay: Int,
    /** The first moment the person's own gap rule allows another dose. Null if there's no earlier dose. */
    val nextAllowedAt: Long?,
    val tooSoon: Boolean,
    val limitReached: Boolean,
)

/** The guard for medicines taken only when needed. It warns, and never blocks: the person decides. */
object AsNeeded {
    const val HOUR_MS = 3_600_000L

    fun status(
        takenAt: List<Long>,
        now: Long,
        startOfToday: Long,
        minGapHours: Int,
        maxPerDay: Int,
    ): AsNeededStatus {
        val last = takenAt.filter { it <= now }.maxOrNull()
        val next = if (last != null && minGapHours > 0) last + minGapHours * HOUR_MS else null
        val today = takenAt.count { it in startOfToday..now }
        return AsNeededStatus(
            lastAt = last,
            takenToday = today,
            maxPerDay = maxPerDay,
            nextAllowedAt = next,
            tooSoon = next != null && now < next,
            limitReached = maxPerDay > 0 && today >= maxPerDay,
        )
    }
}
