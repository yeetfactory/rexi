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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.AsNeeded
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.History
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

/**
 * The sheet for a medicine taken only when needed. It warns when it's too soon, and never stops anyone logging. Given
 * an earlier [day], it logs or removes that day's doses instead, and the warnings, which are about now, stay away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AsNeededSheet(
    app: RexiApp,
    medId: Long,
    onDismiss: () -> Unit,
    day: LocalDate? = null,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val now by rememberNow(15_000)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val medicine by remember(medId) { app.repository.dao.watchMedicine(medId) }.collectAsStateWithLifecycle(null)
    val since = remember { System.currentTimeMillis() - 2 * DAY_MS }
    val earlier = day?.takeIf { it < Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
    val logs by remember(medId, earlier) {
        if (earlier == null) {
            app.repository.dao.watchAsNeeded(medId, since)
        } else {
            app.repository.dao
                .watchLogs(earlier.toEpochDay(), earlier.toEpochDay())
                .map { all -> all.filter { it.medicineId == medId && it.part == null && it.status == DoseStatus.TAKEN } }
        }
    }.collectAsStateWithLifecycle(emptyList())
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
                    (earlier ?: today)
                        .atStartOfDay(zone)
                        .plusMinutes(minutes.toLong())
                        .toInstant()
                        .toEpochMilli()
                if (earlier == null && picked > now) picked - DAY_MS else picked
            } ?: now
        val nextAllowed = status.nextAllowedAt
        // The warnings are about taking a dose now, so an earlier day doesn't show them.
        val tooSoon = earlier == null && status.tooSoon
        // On an earlier day there's no sensible default, so the person picks the time.
        val needsTime = earlier != null && pickedMinutes == null
        val limitReached = earlier == null && status.limitReached
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
                        listOf(m.strengthText, m.amount(m.asNeededAmount), "as needed", earlier?.let(Format::day).orEmpty())
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        style = Type.text,
                        color = palette.muted,
                    )
                }
                IconCircleButton(RexiIcons.Close, "Close", { close(scope, state, onDismiss) }, background = palette.well)
            }
            if (earlier == null) {
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
            }
            if (tooSoon && nextAllowed != null) {
                Spacer(Modifier.height(10.dp))
                Banner(
                    title = "Too soon. Wait until ${Format.time(nextAllowed, zone, use24)}",
                    text = "You set at least ${m.minGapHours} hours between doses.",
                    icon = RexiIcons.Clock,
                )
            } else if (limitReached) {
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
                Text(
                    if (needsTime) "Choose a time" else Format.time(takenAt, zone, use24),
                    style = Type.body,
                    color = if (needsTime) palette.muted else palette.ink,
                    modifier = Modifier.weight(1f),
                )
                TextAction(if (needsTime) "Choose" else "Change", { choosingTime = true })
            }
            val todaysLogs = if (earlier == null) logs.filter { it.at >= startOfToday } else logs
            if (todaysLogs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(if (earlier == null) "Logged today" else "Logged that day", style = Type.label, color = palette.muted)
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
                    if (earlier == null) app.settings.setAsNeededReminder(medId, 0)
                    close(scope, state, onDismiss)
                }
            }
            if (needsTime) {
                PrimaryButton(
                    "Choose when you took it",
                    { choosingTime = true },
                    icon = RexiIcons.Clock,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (tooSoon && nextAllowed != null) {
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
            } else if (limitReached) {
                OutlineButton("Log it anyway", ::log, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Log dose", ::log, icon = RexiIcons.Check, modifier = Modifier.fillMaxWidth())
            }
        }
        if (choosingTime) {
            TimeDialog(
                title = "When did you take it?",
                minutes = if (needsTime) NOON_MINUTES else Format.minutesOfDay(takenAt, zone),
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

/** The ways to say when a dose was taken, offered only when they make sense for its day. */
private enum class When {
    DUE,
    NOW,
    OTHER,
}

/**
 * Answers a dose after the fact, on any day. Without [medId] it covers every dose in the compartment that is missed or
 * still waiting, which is what Today and the missed notification ask about. With it, it covers that medicine's dose
 * whatever its answer was, so History can correct the record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FixDoseSheet(
    app: RexiApp,
    day: LocalDate,
    part: DoseSlot,
    onDismiss: () -> Unit,
    medId: Long? = null,
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
    val today = remember { Instant.ofEpochMilli(now).atZone(zone).toLocalDate() }
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
    val earliest = dueAt - DAY_MS / 2
    val latest = minOf(now, dueAt + DAY_MS - 60_000).coerceAtLeast(earliest)
    var choice by remember { mutableStateOf(When.OTHER) }
    // Within a day of the dose, halfway to now is a fair guess. Past that, halfway would land on another day, so it
    // starts from when the dose was due.
    val guess = if (now < dueAt + DAY_MS) dueAt + (latest - dueAt) / 2 / FIVE_MIN_MS * FIVE_MIN_MS else dueAt
    var other by remember { mutableLongStateOf(guess.coerceIn(earliest, latest)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = palette.surface,
        dragHandle = { Handle() },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        val current = book ?: return@ModalBottomSheet
        val all =
            current.view
                .part(part)
                ?.doses
                .orEmpty()
        val doses =
            if (medId != null) {
                all.filter { it.medId == medId && History.canChange(it) }
            } else {
                all.filter { it.state == DoseState.MISSED || it.pending }
            }
        val ids = doses.map { it.medId }
        val meds = doses.mapNotNull { current.medicines[it.medId] }
        val single = doses.singleOrNull()
        LaunchedEffect(single?.state, single?.at) {
            val takenAt = single?.at
            if (single?.state == DoseState.TAKEN && takenAt != null) other = takenAt.coerceIn(earliest, latest)
        }
        val choices =
            When.entries.filter {
                when (it) {
                    When.DUE -> dueAt <= now
                    When.NOW -> now < dueAt + DAY_MS
                    When.OTHER -> true
                }
            }
        val picked = if (choice in choices) choice else When.OTHER
        val times = mapOf(When.DUE to dueAt, When.NOW to now, When.OTHER to other)

        fun answer(action: suspend () -> Unit) {
            scope.launch {
                action()
                close(scope, state, onDismiss)
            }
        }
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
                        if (meds.size == 1) meds.first().medicine.title else "${part.label(use24)}, ${meds.size} medicines",
                        style = Type.title.copy(fontSize = Type.title.fontSize * 0.86f),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val label = if (part is DayPart) part.label else "Scheduled"
                    val on = if (day == today) "" else " ${Format.day(day)}"
                    Text(
                        "$label dose, due$on at ${Format.time(settings.minutes(part), use24)}",
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
            val takenAt = single?.at
            val badge =
                when {
                    single?.state == DoseState.TAKEN && takenAt != null -> {
                        "Taken at ${Format.time(takenAt, zone, use24)}" to false
                    }

                    single?.state == DoseState.SKIPPED -> {
                        "Skipped" + single.reason.let { if (it.isBlank()) "" else ", ${it.lowercase()}" } to
                            true
                    }

                    doses.any { it.state == DoseState.MISSED && it.at != null } -> {
                        "Marked as missed at ${Format.time(missedAt, zone, use24)}" to
                            true
                    }

                    else -> {
                        null
                    }
                }
            if (badge != null) {
                val (text, alert) = badge
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .height(28.dp)
                        .clip(CircleShape)
                        .background(if (alert) palette.alertSoft else palette.well)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = Type.label, color = if (alert) palette.alertText else palette.ink)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("When did you take it?", style = Type.body, color = palette.ink)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { option ->
                    val on = picked == option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (on) palette.surface else palette.well)
                            .border(2.dp, if (on) palette.ink else palette.well, RoundedCornerShape(18.dp))
                            .selectable(selected = on, role = Role.RadioButton) { choice = option }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val text =
                            when (option) {
                                When.DUE -> "When it was due"
                                When.NOW -> "Just now"
                                When.OTHER -> "Another time"
                            }
                        Text(text, style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
                        if (option !=
                            When.OTHER
                        ) {
                            Text(Format.time(times.getValue(option), zone, use24), style = Type.body, color = palette.muted)
                        }
                    }
                }
            }
            if (picked == When.OTHER) {
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
                        onMinus = { other = (other - FIVE_MIN_MS).coerceAtLeast(earliest) },
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
            val at = times.getValue(picked)
            val atDay = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            PrimaryButton(
                "Log it at ${Format.time(at, zone, use24)}" + if (atDay == day) "" else ", ${Format.day(atDay)}",
                { answer { app.doses.takeAt(day, part, ids, at) } },
                icon = RexiIcons.Check,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (doses.any { it.state != DoseState.SKIPPED }) {
                    TextAction("I skipped it", { answer { app.doses.skipThese(day, part, ids) } })
                }
                TextAction("I didn't take it", { answer { app.doses.notTaken(day, part, ids) } })
            }
        }
    }
}

private val REASONS = listOf("Headache", "Pain", "Fever", "Other")
private const val DAY_MS = 86_400_000L
private const val FIVE_MIN_MS = 5 * 60_000L
private const val NOON_MINUTES = 12 * 60
private const val MAX_AMOUNT = 20.0
