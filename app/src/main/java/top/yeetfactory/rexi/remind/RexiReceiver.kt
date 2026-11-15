package top.yeetfactory.rexi.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.widget.Widgets
import java.time.LocalDate
import java.time.ZoneId

/** Alarms, notification buttons, and the system telling Rexi the phone restarted or changed time zone. */
class RexiReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        // Only actions Rexi knows. A broadcast with any other action, or none, is ignored.
        if (intent.action !in HANDLED) return
        val app = context.applicationContext as RexiApp
        val pending = goAsync()
        app.scope.launch {
            try {
                handle(app, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        app: RexiApp,
        intent: Intent,
    ) {
        val day = intent.dayExtra()
        val part = intent.partExtra()
        val medId = intent.getLongExtra(EXTRA_MED, 0)
        when (intent.action) {
            ACTION_REMIND -> {
                if (day != null && part != null) remind(app, day, part)
            }

            ACTION_TAKE -> {
                if (day != null && part != null) app.doses.take(day, part)
            }

            ACTION_SKIP -> {
                if (day != null && part != null) app.doses.skip(day, part)
            }

            ACTION_SNOOZE -> {
                if (day != null && part != null) app.doses.snooze(day, part, app.settings.value.snoozeMinutes)
            }

            ACTION_DISMISS_MISSED -> {
                if (day != null && part != null) app.notifier.cancelMissed(day, part)
            }

            ACTION_REFILL_CHECK -> {
                app.reminders.checkRefills()
            }

            ACTION_REFILL_LATER -> {
                app.repository.snoozeRefill(medId, app.repository.today().plusDays(1))
                app.notifier.cancelRefill(medId)
            }

            ACTION_AS_NEEDED_OK -> {
                app.settings.setAsNeededReminder(medId, 0)
                app.repository.dao
                    .medicine(medId)
                    ?.let { app.notifier.showAsNeededOk(it) }
            }

            Intent.ACTION_TIMEZONE_CHANGED -> {
                noteZone(app)
            }
        }
        // ACTION_MISS needs nothing of its own: rescheduling writes down whatever is now overdue.
        app.reminders.reschedule()
        Widgets.update(app)
    }

    private suspend fun remind(
        app: RexiApp,
        day: LocalDate,
        part: DayPart,
    ) {
        val book = app.doses.book(day)
        val compartment = book.view.part(part) ?: return
        val unanswered = compartment.pending
        if (unanswered.isEmpty()) return
        val now = app.repository.now()
        val zone = ZoneId.systemDefault()
        val dueAt =
            app.settings.value.clock
                .dueAt(day, part)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        // An alarm left over from before the time moved later must not ring hours early. A minute's slack
        // covers an alarm that Android delivers a moment before its time.
        if (now < dueAt - EARLY_SLACK_MS) return
        app.settings.setRang(day, part, now)
        val quiet = app.settings.value.isQuiet(Format.minutesOfDay(now, zone))
        app.notifier.showDose(
            day,
            part,
            unanswered.mapNotNull { dose ->
                book.medicines[dose.medId]?.let { it to dose.amount }
            },
            dueAt,
            quiet,
        )
    }

    companion object {
        private const val EARLY_SLACK_MS = 60_000L
        const val ACTION_REMIND = "top.yeetfactory.rexi.REMIND"
        const val ACTION_MISS = "top.yeetfactory.rexi.MISS"
        const val ACTION_TAKE = "top.yeetfactory.rexi.TAKE"
        const val ACTION_SKIP = "top.yeetfactory.rexi.SKIP"
        const val ACTION_SNOOZE = "top.yeetfactory.rexi.SNOOZE"
        const val ACTION_DISMISS_MISSED = "top.yeetfactory.rexi.DISMISS_MISSED"
        const val ACTION_REFILL_CHECK = "top.yeetfactory.rexi.REFILL_CHECK"
        const val ACTION_REFILL_LATER = "top.yeetfactory.rexi.REFILL_LATER"
        const val ACTION_AS_NEEDED_OK = "top.yeetfactory.rexi.AS_NEEDED_OK"
        const val EXTRA_DAY = "day"
        const val EXTRA_PART = "part"
        const val EXTRA_MED = "med"

        private val HANDLED =
            setOf(
                ACTION_REMIND,
                ACTION_MISS,
                ACTION_TAKE,
                ACTION_SKIP,
                ACTION_SNOOZE,
                ACTION_DISMISS_MISSED,
                ACTION_REFILL_CHECK,
                ACTION_REFILL_LATER,
                ACTION_AS_NEEDED_OK,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
            )

        /** Reminders follow the local clock. When the zone changes, Today says so once. */
        fun noteZone(app: RexiApp) {
            val zone = ZoneId.systemDefault().id
            val last = app.settings.lastZone
            if (last.isNotEmpty() && last != zone) app.settings.update { it.copy(zoneNotice = true) }
            app.settings.lastZone = zone
        }
    }
}
