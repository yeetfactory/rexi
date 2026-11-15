package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.CellState
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.History
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.core.Supply
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.remind.Reminders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
internal fun MedDetailScreen(
    app: RexiApp,
    medId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onKeepTrack: (Long) -> Unit,
    modifier: Modifier = Modifier,
    askRefill: Boolean = false,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val today = app.repository.today()
    val medicine by remember(medId) { app.repository.dao.watchMedicine(medId) }.collectAsStateWithLifecycle(null)
    val from = today.minusDays(HISTORY_DAYS - 1L)
    val logs by remember(
        medId,
    ) { app.repository.dao.watchLogs(from.toEpochDay(), today.toEpochDay()) }.collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var refilling by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(askRefill, medicine != null) { if (askRefill && medicine != null) refilling = true }

    val med = medicine
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        TopBar(onBack, backLabel = "Medicines") {
            if (med != null) SoftButton("Edit", { onEdit(med.id) })
        }
        if (med == null) return@Column
        val m = med.medicine
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MedIcon(m.kind, m.color1, m.color2, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        m.title,
                        style =
                            Type.display.copy(
                                fontWeight = Type.title.fontWeight,
                            ),
                        color = palette.ink,
                        modifier =
                            Modifier.semantics {
                                heading()
                            },
                    )
                    val subtitle =
                        listOf(
                            listOf(m.strengthText, m.kindName.lowercase()).filter { it.isNotBlank() }.joinToString(" "),
                            m.purpose.takeIf { it.isNotBlank() }?.let { "for ${it.lowercase()}" },
                        ).filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle.replaceFirstChar { it.uppercaseChar() },
                            style = Type.text,
                            color = palette.muted,
                        )
                    }
                }
            }
            if (m.paused) {
                Spacer(Modifier.height(14.dp))
                Banner(
                    title = "Reminders are paused",
                    text = "It's not on Today, and nothing will ring for it.",
                    action = { SoftButton("Resume", { scope.launch { app.repository.setPaused(med.id, false) } }) },
                )
            }
            Spacer(Modifier.height(18.dp))
            WhenSection(med, today, settings.partMinutes, use24)
            Spacer(Modifier.height(14.dp))
            SupplySection(med, today, onRefill = { refilling = true }, onKeepTrack = { onKeepTrack(med.id) })
            Spacer(Modifier.height(14.dp))
            RecentSection(
                med = med,
                days = (0 until HISTORY_DAYS).map { from.plusDays(it.toLong()) },
                logs = logs.filter { it.medicineId == med.id }.map { it.logged() },
                clock = settings.clock,
            )
            Spacer(Modifier.height(14.dp))
            Panel(padding = 0.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Notes", style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
                    TextAction(if (m.notes.isBlank()) "Add" else "Edit", { editingNotes = true })
                }
                Text(
                    m.notes.ifBlank { "Anything worth remembering, such as who prescribed it or what to ask at the next check-up." },
                    style = Type.text,
                    color = if (m.notes.isBlank()) palette.faint else palette.ink,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = ButtonGap) {
                SoftButton(
                    if (m.paused) "Resume reminders" else "Pause reminders",
                    { scope.launch { app.repository.setPaused(med.id, !m.paused) } },
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
                SoftButton("Stop taking", { stopping = true }, height = 48.dp, color = palette.alert, modifier = Modifier.weight(1f))
            }
        }
        if (refilling) {
            if (m.supplyMode == SupplyMode.DURATION) {
                ConfirmDialog(
                    title = "Start a new pack today?",
                    text = "Rexi counts about ${m.packDays} days from today before it reminds you again.",
                    confirm = "New pack",
                    onConfirm = {
                        scope.launch { app.repository.newPack(med.id) }
                        refilling = false
                    },
                    onDismiss = { refilling = false },
                )
            } else {
                NumberDialog(
                    title = "How many do you have now?",
                    initial = if (m.supplyFull > 0) m.supplyFull else m.supplyCount,
                    unit = m.unitWord(),
                    onSave = { count ->
                        scope.launch { app.repository.refill(med.id, count) }
                        refilling = false
                    },
                    onDismiss = { refilling = false },
                )
            }
        }
        if (editingNotes) {
            TextDialog(
                title = "Notes",
                initial = m.notes,
                placeholder = "e.g. Take with the biggest meal of the day",
                onSave = { text ->
                    scope.launch { app.repository.setNotes(med.id, text) }
                    editingNotes = false
                },
                onDismiss = { editingNotes = false },
            )
        }
        if (stopping) {
            ConfirmDialog(
                title = "Stop taking ${m.title}?",
                text = "It comes off Today and Medicines, and nothing rings for it again. Its history stays in History and in reports.",
                confirm = "Stop taking",
                danger = true,
                onConfirm = {
                    stopping = false
                    scope.launch { app.repository.archive(med.id) }
                    onBack()
                },
                onDismiss = { stopping = false },
            )
        }
    }
}

@Composable
private fun WhenSection(
    med: MedicineWithSlots,
    today: LocalDate,
    partMinutes: Map<DayPart, Int>,
    use24: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val m = med.medicine
    Column(modifier) {
        SectionLabel("When")
        if (med.asNeeded) {
            Panel {
                Text("Only when needed", style = Type.body, color = palette.ink)
                Spacer(Modifier.height(4.dp))
                val rules =
                    listOfNotNull(
                        "${m.amount(m.asNeededAmount)} each time",
                        if (m.minGapHours > 0) "at least ${m.minGapHours} hours apart" else null,
                        if (m.maxPerDay > 0) "up to ${m.maxPerDay} a day" else null,
                    )
                Text(rules.joinToString(", ").replaceFirstChar { it.uppercaseChar() } + ".", style = Type.small, color = palette.muted)
            }
            return
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            med.slots.sortedBy { partMinutes[it.part] ?: it.part.defaultMinutes }.forEach { slot ->
                val tint = palette.tint(slot.part)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(tint.background)
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.part.label, style = Type.body.copy(fontWeight = Type.name.fontWeight), color = tint.label)
                        val food = if (m.food != Food.ANY) ", ${m.food.label.lowercase()}" else ""
                        Text(m.amount(slot.amount) + food, style = Type.small, color = tint.text)
                    }
                    Text(Format.time(partMinutes[slot.part] ?: slot.part.defaultMinutes, use24), style = Type.time, color = tint.ink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val since = LocalDate.ofEpochDay(m.startDay)
        val text =
            when (m.frequency) {
                Frequency.EVERY_DAY -> {
                    "Every day since ${Format.fullDate(since)}"
                }

                Frequency.SOME_DAYS -> {
                    weekdays(m.daysMask)
                }

                Frequency.EVERY_FEW_DAYS -> {
                    val next = Schedule.nextDue(med.plan(), today)
                    Format.everyDays(m.everyDays) + next?.let { ". Next dose ${Format.day(it)}" }.orEmpty()
                }

                Frequency.COURSE -> {
                    val day = Schedule.courseDay(med.plan(), today)
                    val end = Format.day(Schedule.courseEnd(med.plan()))
                    if (day != null) "Day $day of ${m.courseDays}, ends $end" else "A ${m.courseDays} day course, ending $end"
                }

                Frequency.AS_NEEDED -> {
                    ""
                }
            }
        Text(
            text,
            style = Type.label.copy(fontWeight = Type.text.fontWeight),
            color = palette.muted,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SupplySection(
    med: MedicineWithSlots,
    today: LocalDate,
    onRefill: () -> Unit,
    onKeepTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val m = med.medicine
    val info = Reminders.supply(med, today)
    Panel(modifier) {
        when (m.supplyMode) {
            SupplyMode.NONE -> {
                Text("Not keeping count", style = Type.body, color = palette.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Count what's left, or say how long a pack lasts, to get a warning before you run out.",
                    style = Type.small,
                    color = palette.muted,
                )
                Spacer(Modifier.height(12.dp))
                SoftButton("Keep track", onKeepTrack, background = palette.well)
            }

            else -> {
                val count = m.supplyMode == SupplyMode.COUNT
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Text(
                            if (count) Format.number(m.supplyCount) else "${info.daysLeft ?: 0}",
                            style = Type.display.copy(fontSize = Type.display.fontSize * 1.12f, fontWeight = Type.title.fontWeight),
                            color = if (info.low) palette.alert else palette.ink,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (count) "${m.unitWord(m.supplyCount)} left" else "days left",
                            style = Type.text,
                            color = palette.muted,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    OutlineButton(if (count) "Refilled" else "New pack", onRefill, height = 44.dp)
                }
                Spacer(Modifier.height(14.dp))
                val fraction =
                    if (count) {
                        Supply.fraction(m.supplyCount, maxOf(m.supplyFull, m.supplyCount))
                    } else {
                        Supply.fraction((info.daysLeft ?: 0).toDouble(), m.packDays.toDouble())
                    }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(palette.well)
                        .semantics { contentDescription = "${(fraction * 100).toInt()} percent left" },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(if (info.low) palette.alert else palette.ink),
                    )
                }
                Spacer(Modifier.height(12.dp))
                val runOut = info.runOut
                val remind = info.remindOn
                val text =
                    when {
                        runOut == null -> {
                            "Taken only when needed, so there's no date to run out."
                        }

                        count -> {
                            "Runs out around ${Format.day(runOut)}. Refill reminder on ${Format.day(maxOf(remind ?: today, today))}."
                        }

                        else -> {
                            "Opened ${Format.day(LocalDate.ofEpochDay(m.packOpenedDay))}. A pack lasts about ${m.packDays} days, " +
                                "so it runs out around ${Format.day(
                                    runOut,
                                )}. Refill reminder on ${Format.day(maxOf(remind ?: today, today))}."
                        }
                    }
                Text(text, style = Type.small, color = palette.muted)
            }
        }
    }
}

@Composable
private fun RecentSection(
    med: MedicineWithSlots,
    days: List<LocalDate>,
    logs: List<top.yeetfactory.rexi.core.Logged>,
    clock: top.yeetfactory.rexi.core.DayClock,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val now = LocalDateTime.now(ZoneId.systemDefault())
    Panel(modifier) {
        if (med.asNeeded) {
            val count = logs.count { it.part == null }
            Text("Last 2 weeks", style = Type.body, color = palette.ink)
            Spacer(Modifier.height(4.dp))
            Text(if (count == 1) "Taken once." else "Taken $count times.", style = Type.small, color = palette.muted)
            return@Panel
        }
        val views = days.associateWith { DayBook.build(it, listOf(med.plan()), logs, now, clock) }
        val doses = views.values.flatMap { view -> view.parts.flatMap { it.doses } }
        val due = doses.count { !it.pending }
        val taken = doses.count { it.state == DoseState.TAKEN }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 2 weeks", style = Type.body, color = palette.ink, modifier = Modifier.weight(1f))
            Text("$taken of $due taken", style = Type.small, color = palette.muted)
        }
        Spacer(Modifier.height(12.dp))
        val parts = med.slots.map { it.part }.sortedBy { clock.minutes(it) }
        parts.forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(part.label, style = Type.micro, color = palette.muted, modifier = Modifier.width(66.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    days.forEach { date ->
                        HistoryCell(History.cell(views[date]?.part(part)), part, Modifier.weight(1f).height(20.dp), radius = 5)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 66.dp, top = 6.dp)) {
            Text(Format.shortDate(days.first()), style = Type.micro, color = palette.muted, modifier = Modifier.weight(1f))
            Text("Today", style = Type.micro, color = palette.muted)
        }
        val missed =
            views.entries.flatMap { (date, view) ->
                view.parts.filter { p -> p.doses.any { it.state == DoseState.MISSED || it.state == DoseState.SKIPPED } }.map {
                    date to
                        it.part
                }
            }
        Spacer(Modifier.height(12.dp))
        val text =
            when {
                missed.isEmpty() -> "Nothing missed."
                missed.size == 1 -> "Missed one: ${Format.day(missed.first().first)}, ${missed.first().second.label.lowercase()}."
                else -> "Missed ${missed.size}. The last: ${Format.day(missed.last().first)}, ${missed.last().second.label.lowercase()}."
            }
        Text(text, style = Type.small, color = if (missed.isEmpty()) palette.muted else palette.alert)
    }
}

/** One cell of a pillbox chart. Taken is filled with a check, missed is outlined with a cross, so it isn't colour alone. */
@Composable
internal fun HistoryCell(
    state: CellState,
    part: DayPart,
    modifier: Modifier = Modifier,
    radius: Int = 9,
    showIcon: Boolean = false,
) {
    val palette = LocalPalette.current
    val tint = palette.tint(part)
    val shape = RoundedCornerShape(radius.dp)
    val base = modifier.clip(shape)
    val styled =
        when (state) {
            CellState.TAKEN -> base.background(tint.strong)
            CellState.MISSED -> base.background(palette.surface).border(2.dp, palette.alert, shape)
            CellState.DUE -> base.background(palette.surface).border(2.dp, palette.ink, shape)
            CellState.LATER -> base.background(palette.surface).border(1.5.dp, palette.faint, shape)
            CellState.NONE -> base.background(palette.well)
        }
    Box(styled, contentAlignment = Alignment.Center) {
        if (showIcon) {
            when (state) {
                CellState.TAKEN -> {
                    androidx.compose.material3.Icon(
                        RexiIcons.Check,
                        contentDescription = null,
                        tint = palette.surface,
                        modifier = Modifier.fillMaxSize(0.45f),
                    )
                }

                CellState.MISSED -> {
                    androidx.compose.material3.Icon(
                        RexiIcons.Cross,
                        contentDescription = null,
                        tint = palette.alert,
                        modifier = Modifier.fillMaxSize(0.45f),
                    )
                }

                // A short bar says "nothing here", so an empty day can't be mistaken for a dose still to come.
                CellState.NONE -> {
                    Box(
                        Modifier
                            .fillMaxWidth(0.3f)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(palette.faint.copy(alpha = 0.6f)),
                    )
                }

                else -> {}
            }
        }
    }
}

private const val HISTORY_DAYS = 14
