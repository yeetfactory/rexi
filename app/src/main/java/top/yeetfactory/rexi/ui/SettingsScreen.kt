package top.yeetfactory.rexi.ui

import android.app.UiModeManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yeetfactory.rexi.BuildConfig
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.data.AppSettings
import top.yeetfactory.rexi.data.ThemeMode
import java.time.Instant
import java.time.ZoneId

private sealed interface Editing {
    data class PartTime(
        val part: DayPart,
    ) : Editing

    data object Snooze : Editing

    data object QuietStart : Editing

    data object QuietEnd : Editing

    data object MissedAfter : Editing

    data object NoLock : Editing

    data class Restore(
        val text: String,
    ) : Editing

    data class Message(
        val title: String,
        val text: String,
    ) : Editing
}

@Composable
internal fun SettingsScreen(
    app: RexiApp,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var health by remember { mutableStateOf(ReminderHealth.read(app)) }
    var fullScreen by remember { mutableStateOf(app.notifier.canUseFullScreen()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        health = ReminderHealth.read(app)
        fullScreen = app.notifier.canUseFullScreen()
    }

    fun change(edit: (AppSettings) -> AppSettings) {
        app.settings.update(edit)
        app.changed()
    }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val result =
                    runCatching {
                        val now = app.repository.now()
                        val text = app.backup.export(now)
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                                ?: error("The file couldn't be opened.")
                        }
                        app.settings.update { it.copy(lastBackupAt = now) }
                    }
                editing =
                    if (result.isSuccess) {
                        Editing.Message("Backed up", "Keep the file somewhere safe. Restore it from here on a new phone.")
                    } else {
                        Editing.Message("Backup failed", result.exceptionOrNull()?.message ?: "Something went wrong.")
                    }
            }
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                    }
                editing = if (text == null) Editing.Message("Restore failed", "The file couldn't be read.") else Editing.Restore(text)
            }
        }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenTitle("Settings")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            SectionLabel("Your day")
            DayPart.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { part ->
                        PartTimeTile(
                            part = part,
                            time = Format.time(settings.minutes(part), use24),
                            onClick = { editing = Editing.PartTime(part) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "Doses are grouped into these times. Tap one to change it.",
                style = Type.label.copy(fontWeight = Type.text.fontWeight),
                color = palette.muted,
                modifier = Modifier.padding(start = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            if (!health.ok) {
                Banner(title = health.title, text = health.text, icon = RexiIcons.Bell, action = { SoftButton("Fix", { health.fix(app) }) })
                Spacer(Modifier.height(16.dp))
            }
            SectionLabel("Reminders")
            Panel(padding = 0.dp) {
                ToggleRow(
                    "Keep ringing until I answer",
                    settings.repeatUntilAnswered,
                    { on -> change { it.copy(repeatUntilAnswered = on) } },
                    subtitle = "Repeats every 10 minutes",
                )
                RowLine()
                ToggleRow(
                    "Show over the lock screen",
                    settings.overLockScreen && fullScreen,
                    { on ->
                        change { it.copy(overLockScreen = on) }
                        if (on && !fullScreen) context.startActivity(app.notifier.fullScreenSettings())
                    },
                    subtitle = if (fullScreen) "Fills the screen, like an alarm clock" else "Needs permission from Android",
                )
                RowLine()
                SettingRow("Snooze length", { editing = Editing.Snooze }, value = "${settings.snoozeMinutes} min")
                RowLine()
                ToggleRow(
                    "Quiet hours",
                    settings.quietHours,
                    { on -> change { it.copy(quietHours = on) } },
                    subtitle = "Reminders arrive without sound, and don't repeat",
                )
                if (settings.quietHours) {
                    RowLine()
                    SettingRow("Quiet from", { editing = Editing.QuietStart }, value = Format.time(settings.quietStart, use24))
                    RowLine()
                    SettingRow("Quiet until", { editing = Editing.QuietEnd }, value = Format.time(settings.quietEnd, use24))
                }
                RowLine()
                SettingRow(
                    "Mark as missed after",
                    { editing = Editing.MissedAfter },
                    subtitle = "If you haven't answered by then",
                    value = hours(settings.missedAfterHours),
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Privacy")
            Panel(padding = 0.dp) {
                ToggleRow(
                    "App lock",
                    settings.appLock,
                    { on ->
                        val canLock =
                            BiometricManager.from(context).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                            ) == BiometricManager.BIOMETRIC_SUCCESS
                        if (on && !canLock) editing = Editing.NoLock else change { it.copy(appLock = on) }
                    },
                    subtitle = "Fingerprint or PIN to open the app",
                )
                RowLine()
                ToggleRow(
                    "Hide names in notifications",
                    settings.hideNames,
                    { on -> change { it.copy(hideNames = on) } },
                    subtitle = "Shows \"Time for your dose\" instead",
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Appearance")
            val themes = ThemeMode.entries
            Segmented(
                themes.map { it.label },
                themes.indexOf(settings.theme),
                { index ->
                    val mode = themes[index]
                    app.settings.update { it.copy(theme = mode) }
                    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
                },
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Your data")
            Panel(padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RexiIcons.Lock, contentDescription = null, tint = palette.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Stored only on this phone. No account, nothing sent anywhere.", style = Type.small, color = palette.muted)
                }
                RowLine()
                SettingRow(
                    "Back up to a file",
                    { backupLauncher.launch("rexi-backup-${app.repository.today()}.json") },
                    subtitle =
                        if (settings.lastBackupAt > 0) {
                            "Last backup ${Format.day(
                                Instant.ofEpochMilli(settings.lastBackupAt).atZone(ZoneId.systemDefault()).toLocalDate(),
                            )}"
                        } else {
                            "Never backed up"
                        },
                )
                RowLine()
                SettingRow(
                    "Restore from a file",
                    { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                )
                RowLine()
                SettingRow(
                    "Notification settings",
                    {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    subtitle = "Sounds and channels for each kind of reminder",
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Rexi ${BuildConfig.VERSION_NAME}. Set in Figtree, under the SIL Open Font License.",
                style = Type.label.copy(fontWeight = Type.text.fontWeight),
                color = palette.faint,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    when (val current = editing) {
        is Editing.PartTime -> {
            TimeDialog(
                title = current.part.label,
                minutes = settings.minutes(current.part),
                onPick = { minutes ->
                    change { it.copy(partMinutes = it.partMinutes + (current.part to minutes)) }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        Editing.Snooze -> {
            ChoiceDialog(
                title = "Snooze length",
                options = listOf(5, 10, 15, 30, 60).map { it to "$it minutes" },
                selected = settings.snoozeMinutes,
                onPick = { minutes ->
                    change { it.copy(snoozeMinutes = minutes) }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        Editing.QuietStart -> {
            TimeDialog("Quiet from", settings.quietStart, { minutes ->
                change { it.copy(quietStart = minutes) }
                editing = null
            }, { editing = null })
        }

        Editing.QuietEnd -> {
            TimeDialog("Quiet until", settings.quietEnd, { minutes ->
                change { it.copy(quietEnd = minutes) }
                editing = null
            }, { editing = null })
        }

        Editing.MissedAfter -> {
            ChoiceDialog(
                title = "Mark as missed after",
                options = listOf(1, 2, 3, 4, 6, 8).map { it to hours(it) },
                selected = settings.missedAfterHours,
                onPick = { value ->
                    change { it.copy(missedAfterHours = value) }
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }

        Editing.NoLock -> {
            MessageDialog(
                "Set a screen lock first",
                "App lock uses your phone's own fingerprint, face or PIN. Set one in Android's Settings, then turn this on.",
            ) { editing = null }
        }

        is Editing.Restore -> {
            ConfirmDialog(
                title = "Replace everything?",
                text = "Restoring replaces every medicine and all history on this phone with what's in the backup.",
                confirm = "Restore",
                danger = true,
                onConfirm = {
                    editing = null
                    scope.launch {
                        editing =
                            runCatching { app.backup.restore(current.text) }.fold(
                                onSuccess = { count ->
                                    app.notifier.cancelAll()
                                    app.changed()
                                    Editing.Message("Restored", if (count == 1) "1 medicine is back." else "$count medicines are back.")
                                },
                                onFailure = { Editing.Message("Restore failed", it.message ?: "This file couldn't be restored.") },
                            )
                    }
                },
                onDismiss = { editing = null },
            )
        }

        is Editing.Message -> {
            MessageDialog(current.title, current.text) { editing = null }
        }

        null -> {}
    }
}

private fun hours(value: Int): String = if (value == 1) "1 hour" else "$value hours"

/** One part of the day and its time, two to a row so "10:00 PM" fits on one line. */
@Composable
private fun PartTimeTile(
    part: DayPart,
    time: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = LocalPalette.current.tint(part)
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(tint.background)
            .clickable(role = Role.Button, onClickLabel = "Change the time") { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(part.label, style = Type.body, color = tint.label, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(RexiIcons.Clock, contentDescription = null, tint = tint.label, modifier = Modifier.size(18.dp))
        }
        Text(time, style = Type.time, color = tint.ink, maxLines = 1)
    }
}
