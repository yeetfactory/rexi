package top.yeetfactory.rexi.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test fun `quiet hours can wrap past midnight`() {
        val settings = AppSettings(quietHours = true, quietStart = 23 * 60, quietEnd = 7 * 60)
        assertTrue(settings.isQuiet(23 * 60 + 30))
        assertTrue(settings.isQuiet(3 * 60))
        assertFalse(settings.isQuiet(7 * 60))
        assertFalse(settings.isQuiet(22 * 60))
    }

    @Test fun `quiet hours within a day`() {
        val settings = AppSettings(quietHours = true, quietStart = 13 * 60, quietEnd = 14 * 60)
        assertTrue(settings.isQuiet(13 * 60 + 30))
        assertFalse(settings.isQuiet(14 * 60))
    }

    @Test fun `turned off means never quiet`() {
        assertFalse(AppSettings(quietHours = false).isQuiet(3 * 60))
    }
}
