package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsNeededTest {
    private val hour = AsNeeded.HOUR_MS
    private val startOfToday = 100 * hour
    private val now = startOfToday + 14 * hour

    @Test fun `too soon until the gap has passed`() {
        val status = AsNeeded.status(listOf(now - 5 * hour), now, startOfToday, minGapHours = 6, maxPerDay = 3)
        assertTrue(status.tooSoon)
        assertEquals(now + hour, status.nextAllowedAt)
    }

    @Test fun `fine once the gap has passed`() {
        assertFalse(AsNeeded.status(listOf(now - 6 * hour), now, startOfToday, minGapHours = 6, maxPerDay = 3).tooSoon)
    }

    @Test fun `only today's doses count towards the limit`() {
        val times = listOf(startOfToday - hour, startOfToday + hour, startOfToday + 7 * hour, startOfToday + 13 * hour)
        val status = AsNeeded.status(times, now, startOfToday, minGapHours = 0, maxPerDay = 3)
        assertEquals(3, status.takenToday)
        assertTrue(status.limitReached)
    }

    @Test fun `no earlier dose means nothing to wait for`() {
        val status = AsNeeded.status(emptyList(), now, startOfToday, minGapHours = 6, maxPerDay = 0)
        assertNull(status.nextAllowedAt)
        assertFalse(status.tooSoon)
        assertFalse(status.limitReached)
    }
}
