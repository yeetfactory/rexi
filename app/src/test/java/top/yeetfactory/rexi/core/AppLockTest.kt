package top.yeetfactory.rexi.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    private val minute = 60_000L

    private fun opened(): AppLock = AppLock(enabled = true).apply { unlock() }

    @Test fun `a fresh start is locked when the lock is on, and open when it's off`() {
        assertTrue(AppLock(enabled = true).locked)
        assertFalse(AppLock(enabled = false).locked)
    }

    @Test fun `it locks once the person has been away as long as they chose`() {
        val lock = opened()
        lock.leave(now = 0)
        lock.comeBack(now = minute - 1, enabled = true, afterMs = minute)
        assertFalse(lock.locked)

        lock.leave(now = minute)
        lock.comeBack(now = 2 * minute, enabled = true, afterMs = minute)
        assertTrue(lock.locked)
    }

    @Test fun `immediately locks on any return, however quick`() {
        val lock = opened()
        lock.leave(now = 5_000)
        lock.comeBack(now = 5_000, enabled = true, afterMs = 0)
        assertTrue(lock.locked)
    }

    @Test fun `unlocking through Android's PIN screen doesn't lock it again on the way back`() {
        val lock = AppLock(enabled = true)
        lock.leave(now = 0)
        lock.unlock()
        lock.comeBack(now = 10_000, enabled = true, afterMs = 0)
        assertFalse(lock.locked)
    }

    @Test fun `with the lock turned off, coming back never locks`() {
        val lock = opened()
        lock.leave(now = 0)
        lock.comeBack(now = 10 * minute, enabled = false, afterMs = 0)
        assertFalse(lock.locked)
    }
}
