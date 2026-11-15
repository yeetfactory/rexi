package top.yeetfactory.rexi.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `the app lock waits a minute until the person picks another time, and keeps their pick`() {
        assertEquals(60, SettingsStore(context).value.lockAfterSeconds)

        SettingsStore(context).update { it.copy(lockAfterSeconds = 0) }

        assertEquals(0, SettingsStore(context).value.lockAfterSeconds)
    }
}
