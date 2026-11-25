package top.yeetfactory.rexi.remind

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.ui.ReminderScreen
import top.yeetfactory.rexi.ui.RexiTheme
import java.time.LocalDate

/** Opens over the lock screen when a dose is due, the way an alarm clock does. */
class ReminderActivity : ComponentActivity() {
    private val target = mutableStateOf<Pair<LocalDate, DoseSlot>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        read(intent)
        val app = application as RexiApp
        setContent {
            RexiTheme {
                val current = target.value
                if (current != null) ReminderScreen(app, current.first, current.second, onFinish = ::finish)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        val day = intent.dayExtra()
        val part = intent.partExtra()
        if (day == null || part == null) finish() else target.value = day to part
    }
}
