package top.yeetfactory.rexi.remind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.net.toUri
import top.yeetfactory.rexi.MainActivity
import top.yeetfactory.rexi.R
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.CustomTime
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.SupplyInfo
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.ui.MedArt
import java.time.LocalDate
import java.time.ZoneId

/** Everything Rexi says through the notification shade. */
class Notifier(
    private val app: RexiApp,
) {
    private val manager = app.getSystemService(NotificationManager::class.java)

    private val hideNames: Boolean get() = app.settings.value.hideNames

    private val use24: Boolean get() = DateFormat.is24HourFormat(app)

    fun createChannels() {
        // The alarm stream still plays when the ringer is on silent, which is the point of a medicine reminder.
        val alarmSound =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_DOSES, "Dose reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Rings when a dose is due, and again until you answer."
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), alarmSound)
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_QUIET, "Reminders in quiet hours", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The same reminders, without sound, during your quiet hours."
                },
                NotificationChannel(CHANNEL_MISSED, "Missed doses", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Says when a dose wasn't logged in time."
                },
                NotificationChannel(CHANNEL_REFILLS, "Refills", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Warns before a medicine runs out."
                },
                NotificationChannel(CHANNEL_AS_NEEDED, "As-needed medicines", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Says when it's fine to take an as-needed medicine again."
                },
            ),
        )
    }

    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    fun canUseFullScreen(): Boolean = manager.canUseFullScreenIntent()

    fun fullScreenSettings(): Intent =
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${app.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The reminder for one compartment. One medicine gets Take, Snooze and Skip; several get Take all and Choose. */
    fun showDose(
        day: LocalDate,
        part: DoseSlot,
        doses: List<Pair<MedicineWithSlots, Double>>,
        dueAt: Long,
        quiet: Boolean,
    ) {
        if (doses.isEmpty() || !canNotify()) return
        val settings = app.settings.value
        val single = doses.singleOrNull()
        val swallowed = doses.all { it.first.medicine.unit.swallowed }
        val title = if (single != null || hideNames) "${part.label(use24)} dose" else "${part.label(use24)}, ${doses.size} medicines"
        val text =
            when {
                hideNames && single != null -> "Time for your dose"
                hideNames -> "Time for ${doses.size} doses"
                single != null -> doseLine(single.first, single.second)
                else -> doses.joinToString(", ") { it.first.medicine.title }
            }
        val key = doseId(day, part)
        val builder =
            Notification
                .Builder(app, if (quiet) CHANNEL_QUIET else CHANNEL_DOSES)
                .setSmallIcon(R.drawable.ic_stat_rexi)
                .setColor(INK)
                .setContentTitle(title)
                .setContentText(text)
                .setWhen(dueAt)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open("today", key))
                .setDeleteIntent(null)
                .setAutoCancel(true)
                .setVisibility(if (hideNames) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
                .setPublicVersion(publicVersion("${part.label(use24)} dose", "Time for your dose", quiet))
        if (!hideNames) {
            val first = doses.first().first.medicine
            builder.setLargeIcon(Icon.createWithBitmap(MedArt.bitmap(first.kind, first.color1, first.color2, ICON_PX)))
        }
        val takeLabel =
            when {
                single != null -> if (swallowed) "Take" else "Done"
                else -> if (swallowed) "Take all" else "All done"
            }
        builder.addAction(action(takeLabel, broadcast(RexiReceiver.ACTION_TAKE, key) { putDayPart(day, part) }))
        builder.addAction(
            // Three buttons share one row, so "Snooze 15 min" gets cut off. The length is set in Settings.
            action("Snooze", broadcast(RexiReceiver.ACTION_SNOOZE, key) { putDayPart(day, part) }),
        )
        if (single != null) {
            builder.addAction(action("Skip", broadcast(RexiReceiver.ACTION_SKIP, key) { putDayPart(day, part) }))
        } else {
            builder.addAction(action("Choose", open("today", key + CHOOSE_OFFSET)))
        }
        if (!quiet && settings.overLockScreen && canUseFullScreen()) {
            val fullScreen =
                PendingIntent.getActivity(
                    app,
                    key,
                    Intent(app, ReminderActivity::class.java)
                        .putDayPart(day, part)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            builder.setFullScreenIntent(fullScreen, true)
            builder.setCategory(Notification.CATEGORY_ALARM)
        }
        manager.notify(key, builder.build())
    }

    fun cancelDose(
        day: LocalDate,
        part: DoseSlot,
    ) = manager.cancel(doseId(day, part))

    fun showMissed(
        day: LocalDate,
        part: DoseSlot,
        medicines: List<MedicineWithSlots>,
    ) {
        if (medicines.isEmpty() || !canNotify()) return
        val settings = app.settings.value
        val by = Format.time(settings.minutes(part) + settings.missedAfterHours * 60, use24)
        val names = medicines.joinToString(", ") { it.medicine.title }
        val verb = if (medicines.size == 1) "wasn't" else "weren't"
        val text = if (hideNames) "A dose wasn't logged by $by. Took it after all?" else "$names $verb logged by $by. Took it after all?"
        val key = missedId(day, part)
        val notification =
            Notification
                .Builder(app, CHANNEL_MISSED)
                .setSmallIcon(R.drawable.ic_stat_rexi)
                .setColor(INK)
                .setContentTitle("${part.label(use24)} dose marked as missed")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open("late:${day.toEpochDay()}:${part.key}", key))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion("${part.label(use24)} dose marked as missed", "Took it after all?", quiet = false))
                .addAction(action("I took it", open("late:${day.toEpochDay()}:${part.key}", key + CHOOSE_OFFSET)))
                .addAction(action("OK", broadcast(RexiReceiver.ACTION_DISMISS_MISSED, key) { putDayPart(day, part) }))
                .build()
        manager.notify(key, notification)
    }

    fun cancelMissed(
        day: LocalDate,
        part: DoseSlot,
    ) = manager.cancel(missedId(day, part))

    fun showRefill(
        med: MedicineWithSlots,
        info: SupplyInfo,
    ) {
        if (!canNotify()) return
        val medicine = med.medicine
        val days = info.daysLeft ?: 0
        val dayWord = if (days == 1) "day" else "days"
        val text =
            when (medicine.supplyMode) {
                SupplyMode.COUNT -> "${Format.number(
                    medicine.supplyCount,
                )} ${medicine.unitWord(medicine.supplyCount)} left, about $days $dayWord"

                else -> "About $days $dayWord left in this pack"
            }
        val key = REFILL_BASE + med.id.toInt()
        val notification =
            Notification
                .Builder(app, CHANNEL_REFILLS)
                .setSmallIcon(R.drawable.ic_stat_rexi)
                .setColor(INK)
                .setContentTitle(if (hideNames) "A medicine is running low" else "${medicine.title} is running low")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open("med:${med.id}", key))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion("A medicine is running low", "Open Rexi to see which", quiet = false))
                .addAction(action(if (medicine.supplyMode == SupplyMode.COUNT) "Refilled" else "New pack", open("refill:${med.id}", key)))
                .addAction(
                    action(
                        "Remind me tomorrow",
                        broadcast(RexiReceiver.ACTION_REFILL_LATER, key) { putExtra(RexiReceiver.EXTRA_MED, med.id) },
                    ),
                ).build()
        manager.notify(key, notification)
    }

    fun cancelRefill(medId: Long) = manager.cancel(REFILL_BASE + medId.toInt())

    /** Clears everything Rexi shows. After a restore, what's there may belong to medicines that are gone. */
    fun cancelAll() = manager.cancelAll()

    fun showAsNeededOk(med: MedicineWithSlots) {
        if (!canNotify()) return
        val medicine = med.medicine
        val key = AS_NEEDED_BASE + med.id.toInt()
        val hours = medicine.minGapHours
        val notification =
            Notification
                .Builder(app, CHANNEL_AS_NEEDED)
                .setSmallIcon(R.drawable.ic_stat_rexi)
                .setColor(INK)
                .setContentTitle(if (hideNames) "Fine to take again" else "${medicine.title} is fine to take again")
                .setContentText("It's been $hours hours since your last dose.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open("prn:${med.id}", key))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion("Fine to take again", "Open Rexi to log a dose", quiet = false))
                .addAction(action("Log a dose", open("prn:${med.id}", key + CHOOSE_OFFSET)))
                .build()
        manager.notify(key, notification)
    }

    private fun doseLine(
        med: MedicineWithSlots,
        amount: Double,
    ): String {
        val medicine = med.medicine
        val name = listOf(medicine.title, medicine.strengthText).filter { it.isNotBlank() }.joinToString(" ")
        return "$name, ${medicine.amount(amount)}"
    }

    private fun publicVersion(
        title: String,
        text: String,
        quiet: Boolean,
    ): Notification =
        Notification
            .Builder(app, if (quiet) CHANNEL_QUIET else CHANNEL_DOSES)
            .setSmallIcon(R.drawable.ic_stat_rexi)
            .setColor(INK)
            .setContentTitle(title)
            .setContentText(text)
            .build()

    private fun action(
        label: String,
        intent: PendingIntent,
    ): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(app, R.drawable.ic_notification_action), label, intent).build()

    private fun open(
        route: String,
        requestCode: Int,
    ): PendingIntent =
        PendingIntent.getActivity(
            app,
            requestCode,
            Intent(app, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(
        action: String,
        key: Int,
        extras: Intent.() -> Unit,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            app,
            key,
            Intent(app, RexiReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_DOSES = "doses"
        const val CHANNEL_QUIET = "doses_quiet"
        const val CHANNEL_MISSED = "missed"
        const val CHANNEL_REFILLS = "refills"
        const val CHANNEL_AS_NEEDED = "as_needed"
        private const val INK = 0xFF12161C.toInt()
        private const val ICON_PX = 128
        private const val REFILL_BASE = 100_000
        private const val AS_NEEDED_BASE = 200_000
        private const val MISSED_BASE = 50_000
        private const val CUSTOM_BASE = 10_000_000
        private const val CUSTOM_MISSED_OFFSET = 15_000_000
        private const val CHOOSE_OFFSET = 7_000_000

        fun doseId(
            day: LocalDate,
            part: DoseSlot,
        ): Int {
            val dayIndex = Math.floorMod(day.toEpochDay(), 10_000L).toInt()
            return when (part) {
                is DayPart -> 1_000 + dayIndex * 4 + part.ordinal
                is CustomTime -> CUSTOM_BASE + dayIndex * CustomTime.MINUTES_PER_DAY + part.minutes
            }
        }

        fun missedId(
            day: LocalDate,
            part: DoseSlot,
        ): Int = doseId(day, part) + if (part is CustomTime) CUSTOM_MISSED_OFFSET else MISSED_BASE

        /** The zone the reminders were last worked out in. */
        fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
