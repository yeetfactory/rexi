package top.yeetfactory.rexi.core

/**
 * Whether the app lock is up, and when it comes back. A new one starts locked, so opening Rexi afresh always asks,
 * and so does coming back after Android closed it in the background. Times come from the phone's uptime clock, so
 * changing the clock or the time zone can't skip the lock.
 */
class AppLock(
    enabled: Boolean,
) {
    var locked: Boolean = enabled
        private set

    /**
     * When the person left Rexi while it was open. Leaving while it's locked doesn't count, so stepping out to
     * Android's own PIN screen to unlock can't lock it again on the way back.
     */
    private var leftAt: Long? = null

    /** The person left Rexi. Turning the phone rebuilds the screen without leaving, so the screen doesn't report that. */
    fun leave(now: Long) {
        if (!locked) leftAt = now
    }

    /** The person came back. It locks if they were away for [afterMs] or longer, so zero locks every time. */
    fun comeBack(
        now: Long,
        enabled: Boolean,
        afterMs: Long,
    ) {
        val left = leftAt ?: return
        leftAt = null
        if (enabled && now - left >= afterMs) locked = true
    }

    fun unlock() {
        locked = false
    }
}
