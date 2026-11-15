package top.yeetfactory.rexi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SupplyTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test fun `a counted pack lasts as many days as the doses allow`() {
        val info = Supply.counted(count = 23.0, dailyUse = 2.0, today = today, leadDays = 7)
        assertEquals(11, info.daysLeft)
        assertEquals(LocalDate.of(2026, 11, 26), info.runOut)
        assertEquals(LocalDate.of(2026, 11, 19), info.remindOn)
        assertFalse(info.low)
    }

    @Test fun `it's low once the days left reach the lead time`() {
        assertTrue(Supply.counted(count = 6.0, dailyUse = 1.0, today = today, leadDays = 7).low)
    }

    @Test fun `a timed pack counts down from the day it was opened`() {
        val info = Supply.timed(opened = LocalDate.of(2026, 11, 1), packDays = 30, today = today, leadDays = 7)
        assertEquals(16, info.daysLeft)
        assertEquals(LocalDate.of(2026, 12, 1), info.runOut)
        assertFalse(info.low)
    }

    @Test fun `without a daily rate there's no run-out date`() {
        assertEquals(Supply.UNKNOWN, Supply.counted(count = 10.0, dailyUse = null, today = today, leadDays = 7))
    }

    @Test fun `the bar never goes past full or below empty`() {
        assertEquals(1f, Supply.fraction(80.0, 60.0))
        assertEquals(0f, Supply.fraction(-1.0, 60.0))
        assertEquals(0f, Supply.fraction(5.0, 0.0))
    }
}
