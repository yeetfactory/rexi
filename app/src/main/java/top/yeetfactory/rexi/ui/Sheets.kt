package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.AsNeeded
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.remind.DoseActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
private fun close(
    scope: CoroutineScope,
    state: SheetState,
    onDismiss: () -> Unit,
) {
    scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
}

@Composable
private fun Handle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .size(36.dp, 4.dp)
            .clip(CircleShape)
            .background(LocalPalette.current.line),
    )
}

/** The sheet for a medicine taken only when needed. It warns when it's too soon, and never stops anyone logging. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AsNeededSheet(
    app: RexiApp,
    medId: Long,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val now by rememberNow(15_000)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val medicine by remember(medId) { app.repository.dao.watchMedicine(medId) }.collectAsStateWithLifecycle(null)
    val since = remember { System.currentTimeMillis() - 2 * DAY_MS }
    val logs by remember(medId) { app.repository.dao.watchAsNeeded(medId, since) }.collectAsStateWithLifecycle(emptyList())
    var reason by remember { mutableStateOf<String?>(null) }
    var pickedMinutes by remember { mutableStateOf<Int?>(null) }
    var amount by remember { mutableDoubleStateOf(0.0) }
    var choosingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = palette.surface,
        dragHandle = { Handle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        val med = medicine ?: return@ModalBottomSheet
        val m = med.medicine
        val dose = if (amount == 0.0) m.asNeededAmount else amount
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val status = AsNeeded.status(logs.map { it.at }, now, startOfToday, m.minGapHours, m.maxPerDay)
        val takenAt =
            pickedMinutes?.let { minutes ->
                val picked =
                    today
                        .atStartOfDay(zone)
                        .plusMinutes(minutes.toLong())
                        .toInstant()
                        .toEpochMilli()
                if (picked > now) picked - DAY_MS else picked
            } ?: now
        val nextAllowed = status.nextAllowedAt
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedIcon(m.kind, m.color1, m.color2, size = 50.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title,
                        style = Type.title.copy(fontSize = Type.title.fontSize * 0.93f),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(m.strengthText, m.amount(m.asNeededAmount), "as needed").filter { it.isNotBlank() }.joinToString(", "),
                        style = Type.text,
                        color = palette.muted,
                    )
                }
                IconCircleButton(RexiIcons.Close, "Close", { close(scope, state, onDismiss) }, background = palette.well)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = ButtonGap) {
                StatTile(
                    label = "Last dose",
                    value = status.lastAt?.let { Format.time(it, zone, use24) } ?: "None yet",
                    detail = status.lastAt?.let { "${Format.duration((now - it) / 60_000)} ago" } ?: "in the last 2 days",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Today",
                    value = if (m.maxPerDay > 0) "${status.takenToday} of ${m.maxPerDay}" else "${status.takenToday}",
                    detail = if (m.maxPerDay > 0) "your daily limit" else "doses",
                    modifier = Modifier.weight(1f),
                )
            }
            if (status.tooSoon && nextAllowed != null) {
                Spacer(Modifier.height(10.dp))
                Banner(
                    title = "Too soon. Wait until ${Format.time(nextAllowed, zone, use24)}",
                    text = "You set at least ${m.minGapHours} hours between doses.",
                    icon = RexiIcons.Clock,
                )
            } else if (status.limitReached) {
                Spacer(Modifier.height(10.dp))
                Banner(title = "That's your limit for today", text = "You set at most ${m.maxPerDay} a day.", icon = RexiIcons.Clock)
            }
            Spacer(Modifier.height(20.dp))
            Text("What's it for? Optional", style = Type.label.copy(fontSize = Type.small.fontSize), color = palette.muted)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONS.forEach { option ->
                    Chip(option, reason == option, {
                        reason =
                            if (reason ==
                                option
                            ) {
                                null
                            } else {
                                option
                            }
                    }, bordered = true, unselected = palette.surface)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.well)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Amount", style = Type.text, color = palette.muted, modifier = Modifier.weight(1f))
                Stepper(
                    value = m.amount(dose),
                    onMinus = { amount = (dose - m.unit.step).coerceAtLeast(m.unit.step) },
                    onPlus = { amount = (dose + m.unit.step).coerceAtMost(MAX_AMOUNT) },
                    buttonColor = palette.surface,
                    buttonSize = 40.dp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.well)
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Taken at", style = Type.text, color = palette.muted)
                Spacer(Modifier.width(8.dp))
                Text(Format.time(takenAt, zone, use24), style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
                TextAction("Change", { choosingTime = true })
            }
            val todaysLogs = logs.filter { it.at >= startOfToday }
            if (todaysLogs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Logged today", style = Type.label, color = palette.muted)
                todaysLogs.sortedByDescending { it.at }.forEach { log ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bits = listOf(Format.time(log.at, zone, use24), m.amount(log.amount), log.reason).filter { it.isNotBlank() }
                        Text(bits.joinToString(", "), style = Type.text, color = palette.ink, modifier = Modifier.weight(1f))
                        TextAction("Remove", { scope.launch { app.repository.undoAsNeeded(log.id) } }, color = palette.muted)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            fun log() {
                scope.launch {
                    app.repository.logAsNeeded(medId, takenAt, dose, reason.orEmpty())
                    app.settings.setAsNeededReminder(medId, 0)
                    close(scope, state, onDismiss)
                }
            }
            if (status.tooSoon && nextAllowed != null) {
                PrimaryButton(
                    "Remind me at ${Format.time(nextAllowed, zone, use24)}",
                    {
                        app.settings.setAsNeededReminder(medId, nextAllowed)
                        app.changed()
                        close(scope, state, onDismiss)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlineButton("Log it anyway", ::log, modifier = Modifier.fillMaxWidth())
            } else if (status.limitReached) {
                OutlineButton("Log it anyway", ::log, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Log dose", ::log, icon = RexiIcons.Check, modifier = Modifier.fillMaxWidth())
            }
        }
        if (choosingTime) {
            TimeDialog(
                title = "When did you take it?",
                minutes = Format.minutesOfDay(takenAt, zone),
                onPick = {
                    pickedMinutes = it
                    choosingTime = false
                },
                onDismiss = { choosingTime = false },
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.well)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = Type.label, color = palette.muted)
        Text(value, style = Type.heading.copy(fontSize = Type.heading.fontSize * 1.1f), color = palette.ink)
        Text(detail, style = Type.label.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
    }
}

/** For a dose that was marked as missed, or nobody answered: say when it was really taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogLateSheet(
    app: RexiApp,
    day: LocalDate,
    part: DayPart,
    onDismiss: () -> Unit,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var book by remember { mutableStateOf<DoseActions.Book?>(null) }
    LaunchedEffect(day, part) { book = app.doses.book(day) }
    val now = remember { app.repository.now() }
    val dueAt =
        settings.clock
            .dueAt(day, part)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    val missedAt =
        settings.clock
            .missedAt(day, part)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    val latest = minOf(now, dueAt + DAY_MS - 60_000)
    var choice by remember { mutableIntStateOf(2) }
    var other by remember { mutableLongStateOf(dueAt + (latest - dueAt) / 2 / FIVE_MIN_MS * FIVE_MIN_MS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = palette.surface,
        dragHandle = { Handle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        val current = book ?: return@ModalBottomSheet
        val doses =
            current.view
                .part(part)
                ?.doses
                .orEmpty()
                .filter { it.state == DoseState.MISSED || it.pending }
        val meds = doses.mapNotNull { current.medicines[it.medId] }
        val wasMarked = doses.any { it.state == DoseState.MISSED && it.at != null }
        val times = listOf(dueAt, now, other)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                meds.firstOrNull()?.medicine?.let { MedIcon(it.kind, it.color1, it.color2, size = 48.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (meds.size == 1) meds.first().medicine.title else "${part.label}, ${meds.size} medicines",
                        style = Type.title.copy(fontSize = Type.title.fontSize * 0.86f),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${part.label} dose, due at ${Format.time(settings.minutes(part), use24)}",
                        style = Type.text,
                        color = palette.muted,
                    )
                }
                IconCircleButton(RexiIcons.Close, "Close", { close(scope, state, onDismiss) }, background = palette.well)
            }
            if (meds.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Nothing left to log for this dose.", style = Type.text, color = palette.muted)
                return@Column
            }
            if (wasMarked) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(palette.alertSoft)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Marked as missed at ${Format.time(missedAt, zone, use24)}", style = Type.label, color = palette.alertText)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("When did you take it?", style = Type.body, color = palette.ink)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("When it was due", "Just now", "Another time").forEachIndexed { index, label ->
                    val on = choice == index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) palette.surface else palette.well)
                            .border(2.dp, if (on) palette.ink else palette.well, RoundedCornerShape(18.dp))
                            .selectable(selected = on, role = Role.RadioButton) { choice = index }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
                        if (index < 2) Text(Format.time(times[index], zone, use24), style = Type.body, color = palette.muted)
                    }
                }
            }
            if (choice == 2) {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.well)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Stepper(
                        value = Format.time(other, zone, use24),
                        onMinus = { other = (other - FIVE_MIN_MS).coerceAtLeast(dueAt - DAY_MS / 2) },
                        onPlus = { other = (other + FIVE_MIN_MS).coerceAtMost(latest) },
                        minusLabel = "5 minutes earlier",
                        plusLabel = "5 minutes later",
                        valueStyle = Type.title.copy(fontSize = Type.title.fontSize * 1.25f),
                        buttonSize = 48.dp,
                        buttonColor = palette.surface,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                "Log it at ${Format.time(times[choice], zone, use24)}",
                {
                    scope.launch {
                        app.doses.take(day, part, at = times[choice], includeMissed = true)
                        close(scope, state, onDismiss)
                    }
                },
                icon = RexiIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            TextAction(
                "I didn't take it",
                {
                    scope.launch {
                        doses.filter { it.pending }.forEach { app.repository.markMissed(it.medId, day, part, it.amount) }
                        app.notifier.cancelMissed(day, part)
                        app.notifier.cancelDose(day, part)
                        close(scope, state, onDismiss)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val REASONS = listOf("Headache", "Pain", "Fever", "Other")
private const val DAY_MS = 86_400_000L
private const val FIVE_MIN_MS = 5 * 60_000L
private const val MAX_AMOUNT = 20.0
