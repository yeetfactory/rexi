package top.yeetfactory.rexi.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind

@Composable
internal fun Onboarding(
    app: RexiApp,
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page-- }
    AnimatedContent(page, transitionSpec = {
        fadeIn() togetherWith fadeOut()
    }, label = "onboarding", modifier = modifier.fillMaxSize()) { shown ->
        when (shown) {
            0 -> Welcome(onNext = { page = 1 })
            1 -> YourDay(app, onBack = { page = 0 }, onNext = { page = 2 })
            else -> Permissions(app, onBack = { page = 1 }, onFinish = onFinish)
        }
    }
}

@Composable
private fun Welcome(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 20.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(palette.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val contents =
                mapOf(
                    DayPart.MORNING to
                        listOf(
                            Triple(MedKind.TABLET, MedColor.WHITE, MedColor.WHITE),
                            Triple(MedKind.OVAL, MedColor.PINK, MedColor.PINK),
                            Triple(MedKind.SOFTGEL, MedColor.AMBER, MedColor.AMBER),
                            Triple(MedKind.SPRAY, MedColor.WHITE, MedColor.WHITE),
                        ),
                    DayPart.AFTERNOON to listOf(Triple(MedKind.CAPSULE, MedColor.BLUE, MedColor.WHITE)),
                    DayPart.EVENING to
                        listOf(
                            Triple(MedKind.TABLET, MedColor.WHITE, MedColor.WHITE),
                            Triple(MedKind.DROPS, MedColor.GREEN, MedColor.GREEN),
                        ),
                    DayPart.NIGHT to
                        listOf(Triple(MedKind.OVAL, MedColor.WHITE, MedColor.WHITE), Triple(MedKind.INHALER, MedColor.BLUE, MedColor.BLUE)),
                )
            DayPart.entries.forEach { part ->
                val tint = palette.tint(part)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(tint.background)
                        .padding(start = 18.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(part.label, style = Type.body, color = tint.label, modifier = Modifier.weight(1f))
                    contents[part].orEmpty().forEach { (kind, c1, c2) -> MedIcon(kind, c1, c2, size = 34.dp) }
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            "Never wonder if you took it.",
            style =
                Type.display.copy(
                    fontSize = Type.display.fontSize * 1.06f,
                    fontWeight = Type.title.fontWeight,
                ),
            color = palette.ink,
            modifier =
                Modifier.semantics {
                    heading()
                },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Reminders that don't give up, a warning before you run out, and a simple record of every dose. Everything stays on this phone.",
            style = Type.text.copy(fontSize = Type.body.fontSize),
            color = palette.muted,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Get started", onNext, modifier = Modifier.fillMaxWidth(), height = 58.dp)
    }
}

@Composable
private fun YourDay(
    app: RexiApp,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val use24 = rememberUse24h()
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(RexiIcons.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            StepBar(1, 2)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp),
        ) {
            Text("When does your day happen?", style = Type.title, color = palette.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Doses are grouped into these four times. Set them to fit your routine. You can change them later.",
                style = Type.text,
                color = palette.muted,
            )
            Spacer(Modifier.height(22.dp))
            val notes =
                mapOf(
                    DayPart.MORNING to "After you wake up",
                    DayPart.AFTERNOON to "Around lunch",
                    DayPart.EVENING to "Around dinner",
                    DayPart.NIGHT to "Before bed",
                )
            DayPart.entries.forEach { part ->
                val tint = palette.tint(part)
                val minutes = settings.minutes(part)

                fun set(value: Int) {
                    app.settings.update { it.copy(partMinutes = it.partMinutes + (part to value.coerceIn(0, LAST_QUARTER))) }
                    app.changed()
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 76.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(tint.background)
                        .padding(start = 18.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(part.label, style = Type.body, color = tint.label)
                        Text(notes.getValue(part), style = Type.label.copy(fontWeight = Type.text.fontWeight), color = tint.text)
                    }
                    Stepper(
                        value = Format.time(minutes, use24),
                        onMinus = { set(minutes - QUARTER) },
                        onPlus = { set(minutes + QUARTER) },
                        minusLabel = "15 minutes earlier",
                        plusLabel = "15 minutes later",
                        valueStyle = Type.heading,
                        buttonColor = tint.ink.copy(alpha = 0.12f),
                        tint = tint.ink,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        PrimaryButton(
            "Continue",
            onNext,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            height = 58.dp,
        )
    }
}

@Composable
private fun Permissions(
    app: RexiApp,
    onBack: () -> Unit,
    onFinish: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current

    fun notificationsAllowed() =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && app.notifier.canNotify()
    var notifications by remember { mutableStateOf(notificationsAllowed()) }
    var exact by remember { mutableStateOf(app.reminders.canRingOnTime()) }
    var fullScreen by remember { mutableStateOf(app.notifier.canUseFullScreen()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notifications = notificationsAllowed()
        exact = app.reminders.canRingOnTime()
        fullScreen = app.notifier.canUseFullScreen()
        app.changed()
    }
    val askNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            app.settings.markAsked(Manifest.permission.POST_NOTIFICATIONS)
            notifications = notificationsAllowed()
        }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(RexiIcons.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            StepBar(2, 2)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp),
        ) {
            Text("Let reminders reach you", style = Type.title, color = palette.ink, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(6.dp))
            Text(
                "Android asks before an app can remind you properly. Here's what each one is for.",
                style = Type.text,
                color = palette.muted,
            )
            Spacer(Modifier.height(22.dp))
            PermissionRow("Notifications", "So reminders can show up at all.", notifications, onAllow = {
                if (app.settings.wasAsked(Manifest.permission.POST_NOTIFICATIONS) && !notifications) {
                    ReminderHealth(notifications = false, exact = exact).fix(app)
                } else {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            })
            Spacer(Modifier.height(8.dp))
            PermissionRow("Alarms and reminders", "So they ring at the exact minute, not roughly around it.", exact, onAllow = {
                context.startActivity(app.reminders.exactAlarmSettings())
            })
            Spacer(Modifier.height(8.dp))
            PermissionRow("Full-screen alerts", "So a reminder can show over the lock screen.", fullScreen, onAllow = {
                context.startActivity(app.notifier.fullScreenSettings())
            })
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            PrimaryButton("Add your first medicine", { onFinish(true) }, modifier = Modifier.fillMaxWidth(), height = 58.dp)
            TextAction("I'll do it later", { onFinish(false) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    why: String,
    granted: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body, color = palette.ink)
            Spacer(Modifier.height(3.dp))
            Text(why, style = Type.small, color = palette.muted)
        }
        Spacer(Modifier.width(12.dp))
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 6.dp)) {
                Icon(RexiIcons.Check, contentDescription = null, tint = palette.ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Allowed", style = Type.label.copy(fontSize = Type.small.fontSize), color = palette.ink)
            }
        } else {
            PrimaryButton("Allow", onAllow, height = 44.dp)
        }
    }
}

/** Shown over everything while the app is locked. The fingerprint prompt itself is drawn by Android. */
@Composable
internal fun LockScreen(
    onUnlock: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxSize()
            .background(palette.ground)
            .padding(horizontal = 28.dp)
            .padding(bottom = 24.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(palette.surface),
                contentAlignment = Alignment.Center,
            ) { Icon(RexiIcons.Lock, contentDescription = null, tint = palette.ink, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(18.dp))
            Text(
                "Locked",
                style =
                    Type.title.copy(
                        fontSize = Type.title.fontSize * 1.07f,
                    ),
                color = palette.ink,
                modifier =
                    Modifier.semantics {
                        heading()
                    },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Unlock to see your medicines. Reminders still come through while it's locked.",
                style = Type.text.copy(fontSize = Type.body.fontSize),
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(290.dp),
            )
        }
        PrimaryButton("Unlock", onUnlock, icon = RexiIcons.Fingerprint, modifier = Modifier.fillMaxWidth(), height = 58.dp)
        TextAction("Use PIN instead", onPin, modifier = Modifier.fillMaxWidth())
    }
}

private const val QUARTER = 15
private const val LAST_QUARTER = 23 * 60 + 45
