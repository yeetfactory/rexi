package top.yeetfactory.rexi

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.remind.RexiReceiver
import top.yeetfactory.rexi.ui.AddFlow
import top.yeetfactory.rexi.ui.AsNeededSheet
import top.yeetfactory.rexi.ui.HistoryScreen
import top.yeetfactory.rexi.ui.LocalPalette
import top.yeetfactory.rexi.ui.LockScreen
import top.yeetfactory.rexi.ui.LogLateSheet
import top.yeetfactory.rexi.ui.MedDetailScreen
import top.yeetfactory.rexi.ui.MedsScreen
import top.yeetfactory.rexi.ui.Onboarding
import top.yeetfactory.rexi.ui.RexiIcons
import top.yeetfactory.rexi.ui.RexiTheme
import top.yeetfactory.rexi.ui.SettingsScreen
import top.yeetfactory.rexi.ui.TodayScreen
import top.yeetfactory.rexi.ui.Type
import java.time.LocalDate

/** FragmentActivity, because the fingerprint prompt for the app lock needs one. */
class MainActivity : FragmentActivity() {
    /** A screen a notification asked for, waiting for the navigation to pick it up. */
    private val requestedRoute = mutableStateOf<String?>(null)
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L

    private val app: RexiApp get() = application as RexiApp

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android remembers this itself. Setting it again keeps it right after a restore.
        getSystemService(UiModeManager::class.java).setApplicationNightMode(app.settings.value.theme.nightMode)
        refreshBars()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handle(intent)
            locked.value = app.settings.value.appLock
        }
        RexiReceiver.noteZone(app)
        setContent {
            RexiTheme {
                Root(
                    app = app,
                    requested = requestedRoute.value,
                    locked = locked.value,
                    onClearRequest = { requestedRoute.value = null },
                    onUnlock = ::unlock,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (app.settings.value.appLock && stoppedAt > 0 && away > LOCK_AFTER_MS) locked.value = true
        app.changed()
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    private fun handle(intent: Intent) {
        intent.getStringExtra(EXTRA_ROUTE)?.let { requestedRoute.value = it }
    }

    /** The activity handles theme changes itself so colours can fade, so the bars must follow by hand. */
    fun refreshBars() {
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }

    /** Opens Android's own prompt. With [pinOnly], it skips the fingerprint and asks for the screen lock. */
    private fun unlock(pinOnly: Boolean) {
        val prompt =
            BiometricPrompt(
                this,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        locked.value = false
                    }
                },
            )
        val info =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle("Unlock Rexi")
                .setAllowedAuthenticators(
                    if (pinOnly) Authenticators.DEVICE_CREDENTIAL else Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL,
                ).build()
        prompt.authenticate(info)
    }

    companion object {
        const val EXTRA_ROUTE = "route"

        /** Leaving the app for a share sheet or a file picker shouldn't lock it, so it locks after a minute away. */
        private const val LOCK_AFTER_MS = 60_000L
    }
}

private enum class Tab(
    val label: String,
    val icon: ImageVector,
) {
    TODAY("Today", RexiIcons.Today),
    MEDS("Medicines", RexiIcons.Medicines),
    HISTORY("History", RexiIcons.History),
    SETTINGS("Settings", RexiIcons.Settings),
}

@Composable
private fun Root(
    app: RexiApp,
    requested: String?,
    locked: Boolean,
    onClearRequest: () -> Unit,
    onUnlock: (Boolean) -> Unit,
) {
    val palette = LocalPalette.current
    val activity = LocalActivity.current as MainActivity
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    LaunchedEffect(dark) { activity.refreshBars() }
    var startWithAdd by rememberSaveable { mutableStateOf(false) }
    val kept = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(palette.ground).safeDrawingPadding()) {
        // While locked, the app isn't drawn under the lock screen at all. Covering it wasn't enough: taps went
        // through to the screen below, and a screen reader could still read it. The holder keeps where the person
        // was, and a file picked while locked still arrives once they unlock.
        if (locked) {
            LockScreen(onUnlock = { onUnlock(false) }, onPin = { onUnlock(true) })
        } else {
            kept.SaveableStateProvider("app") {
                AppContent(app, requested, onClearRequest, settings.onboarded, startWithAdd, { startWithAdd = it })
            }
        }
    }
    // With the app lock on, the recent apps screen shows a blank card instead of the person's medicines.
    LaunchedEffect(settings.appLock) { activity.setRecentsScreenshotEnabled(!settings.appLock) }
    val unlock by rememberUpdatedState(onUnlock)
    LaunchedEffect(locked) { if (locked) unlock(false) }
}

@Composable
private fun AppContent(
    app: RexiApp,
    requested: String?,
    onClearRequest: () -> Unit,
    onboarded: Boolean,
    startWithAdd: Boolean,
    onStartWithAdd: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        onboarded,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "root",
        modifier = modifier,
    ) { ready ->
        if (ready) {
            Home(
                app = app,
                requested = requested,
                onClearRequest = onClearRequest,
                startWithAdd = startWithAdd,
                onStartedAdd = { onStartWithAdd(false) },
            )
        } else {
            Onboarding(
                app = app,
                onFinish = { addFirst ->
                    onStartWithAdd(addFirst)
                    app.settings.update { it.copy(onboarded = true) }
                    app.changed()
                },
            )
        }
    }
}

@Composable
private fun Home(
    app: RexiApp,
    requested: String?,
    onClearRequest: () -> Unit,
    startWithAdd: Boolean,
    onStartedAdd: () -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(Tab.TODAY.name) }
    val tab = Tab.valueOf(tabName)
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()

    fun push(route: String) {
        stack = stack + route
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun go(route: String) {
        val bits = route.split(":")
        when (bits[0]) {
            "today" -> {
                tabName = Tab.TODAY.name
                stack = emptyList()
            }

            "prn", "late" -> {
                sheet = route
            }

            "med" -> {
                push(route)
            }

            "refill" -> {
                push("med:${bits.getOrNull(1)}:refill")
            }
        }
    }

    val clearRequest by rememberUpdatedState(onClearRequest)
    LaunchedEffect(requested) {
        if (requested != null) {
            go(requested)
            clearRequest()
        }
    }
    val startedAdd by rememberUpdatedState(onStartedAdd)
    LaunchedEffect(startWithAdd) {
        if (startWithAdd) {
            push("add")
            startedAdd()
        }
    }

    val top = stack.lastOrNull()
    if (top != null) {
        BackHandler { pop() }
        holder.SaveableStateProvider(top) {
            val bits = top.split(":")
            val id = bits.getOrNull(1)?.toLongOrNull() ?: 0L
            when (bits[0]) {
                "med" -> {
                    MedDetailScreen(
                        app = app,
                        medId = id,
                        onBack = ::pop,
                        onEdit = { push("edit:$it") },
                        askRefill = bits.getOrNull(2) == "refill",
                    )
                }

                "edit" -> {
                    AddFlow(app = app, editId = id, onSave = { pop() }, onCancel = ::pop)
                }

                else -> {
                    AddFlow(app = app, editId = null, onSave = { pop() }, onCancel = ::pop)
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                holder.SaveableStateProvider(tab.name) {
                    when (tab) {
                        Tab.TODAY -> {
                            TodayScreen(
                                app = app,
                                onLogDose = { sheet = "prn:$it" },
                                onLate = { day, part -> sheet = "late:${day.toEpochDay()}:${part.name}" },
                                onAdd = { push("add") },
                                onOpen = { push("med:$it") },
                            )
                        }

                        Tab.MEDS -> {
                            MedsScreen(
                                app = app,
                                onOpen = { push("med:$it") },
                                onAdd = { push("add") },
                                onLogDose = { sheet = "prn:$it" },
                                onRefill = { push("med:$it:refill") },
                            )
                        }

                        Tab.HISTORY -> {
                            HistoryScreen(app)
                        }

                        Tab.SETTINGS -> {
                            SettingsScreen(app)
                        }
                    }
                }
            }
            NavBar(tab, onSelect = { tabName = it.name })
        }
    }

    val current = sheet
    if (current != null) {
        val bits = current.split(":")
        when (bits[0]) {
            "prn" -> {
                AsNeededSheet(app, bits.getOrNull(1)?.toLongOrNull() ?: 0L, onDismiss = { sheet = null })
            }

            "late" -> {
                val day = bits.getOrNull(1)?.toLongOrNull()?.let(LocalDate::ofEpochDay)
                val part = bits.getOrNull(2)?.let { name -> DayPart.entries.firstOrNull { it.name == name } }
                if (day != null && part != null) LogLateSheet(app, day, part, onDismiss = { sheet = null }) else sheet = null
            }

            else -> {
                sheet = null
            }
        }
    }
}

@Composable
private fun NavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxWidth().background(palette.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.line))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup()) {
            Tab.entries.forEach { entry ->
                val on = entry == selected
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(CircleShape)
                        .selectable(selected = on, role = Role.Tab) { onSelect(entry) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(60.dp, 32.dp)
                            .clip(CircleShape)
                            .background(if (on) palette.selected else palette.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            tint = if (on) palette.onSelected else palette.muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(entry.label, style = tabLabel(), color = if (on) palette.ink else palette.muted, maxLines = 1)
                }
            }
        }
    }
}

/** Four labels share one row, so they stop growing at 130%. TalkBack still reads them in full. */
@Composable
private fun tabLabel(): TextStyle {
    val scale = LocalDensity.current.fontScale
    val base = Type.micro.copy(fontWeight = Type.label.fontWeight)
    return base.copy(fontSize = base.fontSize * (minOf(scale, MAX_TAB_SCALE) / scale))
}

private const val MAX_TAB_SCALE = 1.3f
