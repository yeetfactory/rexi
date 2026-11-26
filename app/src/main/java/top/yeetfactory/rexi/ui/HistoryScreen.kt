package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.CellState
import top.yeetfactory.rexi.core.CustomTime
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.History
import top.yeetfactory.rexi.core.PartView
import top.yeetfactory.rexi.data.DoseLog
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.report.Report
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun HistoryScreen(
    app: RexiApp,
    onFix: (LocalDate, DoseSlot, Long) -> Unit,
    onLogDose: (Long, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val now by rememberNow(60_000)
    val today = app.repository.today()
    var weekStartDay by rememberSaveable { mutableLongStateOf(History.weekStart(today).toEpochDay()) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var making by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val weekStart = LocalDate.ofEpochDay(weekStartDay)
    val weekEnd = weekStart.plusDays(6)
    val medicines by remember { app.repository.dao.watchAllMedicines() }.collectAsStateWithLifecycle(emptyList())
    val logs by remember(weekStartDay) {
        app.repository.dao.watchLogs(weekStart.toEpochDay(), weekEnd.toEpochDay())
    }.collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val dates = (0..6).map { weekStart.plusDays(it.toLong()) }
    val nowLocal = LocalDateTime.now(zone)
    val views =
        remember(medicines, logs, settings, now, weekStartDay) {
            val plans = medicines.map { it.plan() }
            val logged = logs.map { it.logged() }
            dates.associateWith { DayBook.build(it, plans, logged, nowLocal, settings.clock) }
        }
    val byId = medicines.associateBy { it.id }
    val answered =
        views
            .filterKeys { it <= today }
            .values
            .flatMap { view -> view.parts.flatMap { it.doses } }
            .filter { !it.pending }
    val pastUnscheduled = logs.filter { it.part == null && it.historical }.groupBy { it.day }
    val pastCount = pastUnscheduled.values.sumOf { it.size }
    val answeredCount = answered.size + pastCount
    val takenCount = answered.count { it.state == DoseState.TAKEN } + pastCount

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenTitle("History") {
            SoftButton(if (making) "Making report" else "Report for doctor", {
                if (!making) {
                    making = true
                    scope.launch {
                        runCatching { Report.make(app) }
                            .onSuccess { Report.share(context, it) }
                            .onFailure { problem = "The report couldn't be made. ${it.message.orEmpty()}" }
                        making = false
                    }
                }
            })
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircleButton(RexiIcons.Back, "Previous week", { weekStartDay -= 7 })
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Format.range(weekStart, weekEnd),
                        style = Type.body,
                        color = palette.ink,
                        modifier = Modifier.semantics { heading() },
                    )
                    val soFar = if (weekEnd >= today) " so far" else ""
                    Text(
                        if (answeredCount == 0) "Nothing logged yet" else "$takenCount of $answeredCount taken$soFar",
                        style = Type.label.copy(fontWeight = Type.text.fontWeight),
                        color = palette.muted,
                    )
                }
                val canGoForward = weekEnd < today
                IconCircleButton(
                    RexiIcons.Forward,
                    "Next week",
                    { if (canGoForward) weekStartDay += 7 },
                    background = if (canGoForward) palette.surface else palette.ground,
                    tint = if (canGoForward) palette.ink else palette.faint,
                )
            }
            Spacer(Modifier.height(10.dp))
            WeekGrid(
                dates = dates,
                views = views,
                pastDays = pastUnscheduled.keys,
                today = today,
                selected = LocalDate.ofEpochDay(selectedDay),
                onSelect = { selectedDay = it.toEpochDay() },
            )
            Spacer(Modifier.height(10.dp))
            Legend()
            Spacer(Modifier.height(12.dp))
            val day = LocalDate.ofEpochDay(selectedDay).let { if (it in dates) it else dates.first() }
            val unscheduled = logs.filter { it.part == null && it.day == day.toEpochDay() }
            DayDetail(
                date = day,
                today = today,
                parts = views[day]?.parts.orEmpty(),
                unscheduled = unscheduled,
                asNeeded =
                    medicines.filter { med ->
                        val started = med.medicine.startDay <= day.toEpochDay()
                        med.asNeeded && (unscheduled.any { it.medicineId == med.id } || (!med.medicine.archived && started && day <= today))
                    },
                medicines = byId,
                use24 = use24,
                onFix = { part, medId -> onFix(day, part, medId) },
                onLogDose = { medId -> onLogDose(medId, day) },
            )
        }
    }
    problem?.let { MessageDialog("Report", it) { problem = null } }
}

@Composable
private fun WeekGrid(
    dates: List<LocalDate>,
    views: Map<LocalDate, top.yeetfactory.rexi.core.DayView>,
    pastDays: Set<Long>,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(palette.surface)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Spacer(Modifier.width(LABEL_WIDTH))
            dates.forEach { date ->
                val on = date == selected
                Column(
                    Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                on -> palette.selected
                                date == today -> palette.well
                                else -> palette.surface
                            },
                        ).selectable(selected = on, role = Role.Tab) { onSelect(date) }
                        .semantics { contentDescription = Format.longDay(date) + if (date == today) ", today" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val color = if (on) palette.onSelected else palette.ink
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        style = Type.micro,
                        color = color.copy(alpha = 0.8f),
                    )
                    Text("${date.dayOfMonth}", style = Type.body, color = color)
                }
            }
        }
        DayPart.entries.forEach { part ->
            WeekRow(part.label, part, dates.map { History.cell(views[it]?.part(part)) })
        }
        val custom =
            dates.map { date ->
                History.cell(
                    views[date]
                        ?.parts
                        .orEmpty()
                        .filter { it.part is CustomTime }
                        .flatMap { it.doses },
                )
            }
        if (custom.any { it != CellState.NONE }) WeekRow("Custom", DayPart.AFTERNOON, custom)
        if (pastDays.isNotEmpty()) {
            WeekRow("Past", DayPart.AFTERNOON, dates.map { if (it.toEpochDay() in pastDays) CellState.TAKEN else CellState.NONE })
        }
    }
}

@Composable
private fun WeekRow(
    label: String,
    colorPart: DayPart,
    cells: List<CellState>,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = Type.micro.copy(fontWeight = Type.label.fontWeight),
            color = if (colorPart == DayPart.NIGHT) palette.ink else palette.tint(colorPart).label,
            modifier = Modifier.width(LABEL_WIDTH),
            maxLines = 1,
        )
        cells.forEach { state ->
            HistoryCell(
                state,
                colorPart,
                Modifier.weight(1f).height(36.dp),
                showIcon = true,
            )
        }
    }
}

@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            CellState.TAKEN to "Taken",
            CellState.MISSED to "Missed",
            CellState.LATER to "Still to come",
            CellState.NONE to "Nothing due",
        ).forEach { (state, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state == CellState.TAKEN) {
                    TakenSwatch(Modifier.size(14.dp))
                } else {
                    // On the page's own grey, the empty cell needs an outline to show at all.
                    val outline = if (state == CellState.NONE) Modifier.border(1.dp, palette.line, RoundedCornerShape(4.dp)) else Modifier
                    HistoryCell(
                        state,
                        DayPart.AFTERNOON,
                        Modifier.size(14.dp).then(outline),
                        radius = 4,
                        showIcon = state == CellState.NONE,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(label, style = Type.micro, color = palette.muted)
            }
        }
    }
}

/** Taken cells take their part of the day's colour, so the key shows all four rather than pretending it's one. */
@Composable
private fun TakenSwatch(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Column(modifier.clip(RoundedCornerShape(4.dp))) {
        DayPart.entries.chunked(2).forEach { pair ->
            Row(Modifier.weight(1f)) {
                pair.forEach { part -> Box(Modifier.weight(1f).fillMaxHeight().background(palette.tint(part).strong)) }
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    today: LocalDate,
    parts: List<PartView>,
    unscheduled: List<DoseLog>,
    asNeeded: List<MedicineWithSlots>,
    medicines: Map<Long, MedicineWithSlots>,
    use24: Boolean,
    onFix: (DoseSlot, Long) -> Unit,
    onLogDose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val doses = parts.flatMap { it.doses }
    val pastUnscheduled = unscheduled.count { it.historical }
    val taken = doses.count { it.state == DoseState.TAKEN } + pastUnscheduled
    val resolved = doses.count { !it.pending } + pastUnscheduled
    val editable = date <= today
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .padding(horizontal = 20.dp, vertical = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (date == today) "Today, " else "") + Format.day(date),
                style = Type.body,
                color = palette.ink,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                when {
                    doses.isEmpty() && pastUnscheduled == 0 -> "Nothing scheduled"
                    date == today -> "$taken taken so far"
                    else -> "$taken of $resolved taken"
                },
                style = Type.small,
                color = palette.muted,
            )
        }
        parts.forEach { part ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
            PartLine(part, medicines, use24, zone, editable, onFix = { medId -> onFix(part.part, medId) })
        }
        val past = unscheduled.filter { it.historical && medicines[it.medicineId]?.asNeeded != true }
        if (past.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("Past doses", style = Type.label, color = palette.muted)
                Spacer(Modifier.height(4.dp))
                past.sortedBy { it.at }.forEach { log ->
                    val medicine = medicines[log.medicineId]?.medicine
                    val name = medicine?.title ?: "A medicine"
                    val amount = if (medicine != null) ", ${medicine.amount(log.amount)}" else ""
                    Text("$name$amount at ${Format.time(log.at, zone, use24)}", style = Type.text, color = palette.ink)
                }
            }
        }
        if (asNeeded.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("As needed", style = Type.label, color = palette.muted)
                asNeeded.forEach { med ->
                    val entries = unscheduled.filter { it.medicineId == med.id }.sortedBy { it.at }
                    val times =
                        entries.joinToString("; ") { log ->
                            Format.time(log.at, zone, use24) + if (log.reason.isNotBlank()) ", ${log.reason.lowercase()}" else ""
                        }
                    ChangeRow(
                        title = med.medicine.title,
                        detail = times.ifEmpty { "None logged" },
                        onClick = if (editable) ({ onLogDose(med.id) }) else null,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun PartLine(
    part: PartView,
    medicines: Map<Long, MedicineWithSlots>,
    use24: Boolean,
    zone: ZoneId,
    editable: Boolean,
    onFix: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val tint = palette.tint(part.part)
    val states = part.doses.map { it.state }
    val (icon, status, color) =
        when {
            DoseState.MISSED in states -> {
                Triple(RexiIcons.Cross, "Missed", palette.alert)
            }

            DoseState.SKIPPED in states -> {
                Triple(RexiIcons.Cross, "Skipped", palette.alert)
            }

            DoseState.DUE in states -> {
                Triple(null, "Due now", palette.ink)
            }

            DoseState.LATER in states -> {
                Triple(
                    null,
                    if (part.part is CustomTime) "Later" else "At ${Format.time(part.minutes, use24)}",
                    palette.muted,
                )
            }

            else -> {
                Triple(RexiIcons.Check, "Taken ${part.takenAt?.let { Format.time(it, zone, use24) }.orEmpty()}", palette.muted)
            }
        }
    // Each dose says its own answer only when they differ; otherwise the line above already says it.
    val mixed = states.distinct().size > 1
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                part.part.label(use24),
                style = Type.label,
                color = if (part.part == DayPart.NIGHT) palette.ink else tint.label,
                modifier = Modifier.weight(1f),
            )
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                status.trim(),
                style = Type.label.copy(fontWeight = if (color == palette.alert) Type.label.fontWeight else Type.text.fontWeight),
                color = color,
            )
        }
        part.doses.forEach { dose ->
            val med = medicines[dose.medId]?.medicine ?: return@forEach
            val time = if (dose.state == DoseState.TAKEN && dose.at != null) " at ${Format.time(dose.at, zone, use24)}" else ""
            val reason = if (dose.reason.isNotBlank()) ", ${dose.reason.lowercase()}" else ""
            ChangeRow(
                title = "${med.title}, ${med.amount(dose.amount)}",
                detail =
                    if (mixed) {
                        doseStatus(
                            dose.state,
                        ) + time + reason
                    } else {
                        reason.removePrefix(", ").replaceFirstChar { it.uppercaseChar() }
                    },
                onClick = if (editable && History.canChange(dose)) ({ onFix(dose.medId) }) else null,
            )
        }
    }
}

private fun doseStatus(state: DoseState): String =
    when (state) {
        DoseState.TAKEN -> "Taken"
        DoseState.SKIPPED -> "Skipped"
        DoseState.MISSED -> "Missed"
        DoseState.DUE -> "Due now"
        DoseState.LATER -> "Later"
    }

/** A medicine's line in a day. When it can be corrected, the whole line opens the sheet that does it. */
@Composable
private fun ChangeRow(
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (onClick != null) 48.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Change", onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = Type.text, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) Text(detail, style = Type.label.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
        }
        if (onClick != null) {
            Icon(RexiIcons.Forward, contentDescription = null, tint = palette.faint, modifier = Modifier.size(18.dp))
        }
    }
}

private val LABEL_WIDTH = 62.dp
