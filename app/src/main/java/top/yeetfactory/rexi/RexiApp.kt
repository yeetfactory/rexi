package top.yeetfactory.rexi

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.data.Backup
import top.yeetfactory.rexi.data.Repository
import top.yeetfactory.rexi.data.RexiDatabase
import top.yeetfactory.rexi.data.SettingsStore
import top.yeetfactory.rexi.remind.DoseActions
import top.yeetfactory.rexi.remind.Notifier
import top.yeetfactory.rexi.remind.Reminders
import top.yeetfactory.rexi.widget.Widgets

class RexiApp : Application() {
    /** Work that must finish even when no screen is open, such as rescheduling after a dose is logged. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: RexiDatabase by lazy { RexiDatabase.create(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: Repository by lazy { Repository(database).also { it.onChange = ::changed } }
    val notifier: Notifier by lazy { Notifier(this) }
    val reminders: Reminders by lazy { Reminders(this) }
    val doses: DoseActions by lazy { DoseActions(this) }
    val backup: Backup by lazy { Backup(database, settings) }

    /** Many changes in a row only need one refresh, so requests are folded together. */
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        scope.launch {
            for (request in refreshes) {
                reminders.reschedule()
                Widgets.update(this@RexiApp)
            }
        }
        changed()
        scope.launch { Widgets.publishPreviews(this@RexiApp) }
    }

    /** Something about medicines, doses or settings changed: bring alarms and widgets up to date. */
    fun changed() {
        refreshes.trySend(Unit)
    }
}
