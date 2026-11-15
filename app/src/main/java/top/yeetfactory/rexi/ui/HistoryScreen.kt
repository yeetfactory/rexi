package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.History
import top.yeetfactory.rexi.core.PartView
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
    val takenCount = answered.count { it.state == DoseState.TAKEN }

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
                        if (answered.isEmpty()) "Nothing logged yet" else "$takenCount of ${answered.size} taken$soFar",
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
                today = today,
                selected = LocalDate.ofEpochDay(selectedDay),
                onSelect = { selectedDay = it.toEpochDay() },
            )
            Spacer(Modifier.height(10.dp))
            Legend()
            Spacer(Modifier.height(12.dp))
            val day = LocalDate.ofEpochDay(selectedDay).let { if (it in dates) it else dates.first() }
            DayDetail(
                date = day,
                today = today,
                parts = views[day]?.parts.orEmpty(),
                asNeeded = logs.filter { it.part == null && it.day == day.toEpochDay() },
                medicines = byId,
                use24 = use24,
            )
        }
    }
    problem?.let { MessageDialog("Report", it) { problem = null } }
}

@Composable
private fun WeekGrid(
    dates: List<LocalDate>,
    views: Map<LocalDate, top.yeetfactory.rexi.core.DayView>,
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
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    part.label,
                    style = Type.micro.copy(fontWeight = Type.label.fontWeight),
                    color = palette.tint(part).let { if (part == DayPart.NIGHT) palette.ink else it.label },
                    modifier = Modifier.width(LABEL_WIDTH),
                    maxLines = 1,
                )
                dates.forEach { date ->
                    HistoryCell(
                        History.cell(views[date]?.part(part)),
                        part,
                        Modifier.weight(1f).height(36.dp),
                        showIcon = true,
                    )
                }
            }
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
    asNeeded: List<top.yeetfactory.rexi.data.DoseLog>,
    medicines: Map<Long, MedicineWithSlots>,
    use24: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val zone = ZoneId.systemDefault()
    val doses = parts.flatMap { it.doses }
    val taken = doses.count { it.state == DoseState.TAKEN }
    val resolved = doses.count { !it.pending }
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
                    doses.isEmpty() -> "Nothing scheduled"
                    date == today -> "$taken taken so far"
                    else -> "$taken of $resolved taken"
                },
                style = Type.small,
                color = palette.muted,
            )
        }
        parts.forEach { part ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
            PartLine(part, medicines, use24, zone)
        }
        if (asNeeded.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
            Column(Modifier.padding(vertical = 10.dp)) {
                Text("As needed", style = Type.label, color = palette.muted)
                Spacer(Modifier.height(4.dp))
                asNeeded.sortedBy { it.at }.forEach { log ->
                    val name = medicines[log.medicineId]?.medicine?.title ?: "A medicine"
                    val reason = if (log.reason.isNotBlank()) ", ${log.reason.lowercase()}" else ""
                    Text("$name at ${Format.time(log.at, zone, use24)}$reason", style = Type.text, color = palette.ink)
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
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val tint = palette.tint(part.part)
    val states = part.doses.map { it.state }
    val (icon, status, color) =
        when {
            DoseState.MISSED in states -> Triple(RexiIcons.Cross, "Missed", palette.alert)
            DoseState.SKIPPED in states -> Triple(RexiIcons.Cross, "Skipped", palette.alert)
            DoseState.DUE in states -> Triple(null, "Due now", palette.ink)
            DoseState.LATER in states -> Triple(null, "At ${Format.time(part.minutes, use24)}", palette.muted)
            else -> Triple(RexiIcons.Check, "Taken ${part.takenAt?.let { Format.time(it, zone, use24) }.orEmpty()}", palette.muted)
        }
    val reason = part.doses.firstOrNull { it.reason.isNotBlank() }?.reason
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                part.part.label,
                style = Type.label,
                color =
                    if (part.part ==
                        DayPart.NIGHT
                    ) {
                        palette.ink
                    } else {
                        tint.label
                    },
                modifier = Modifier.weight(1f),
            )
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                status.trim(),
                style =
                    Type.label.copy(
                        fontWeight =
                            if (color ==
                                palette.alert
                            ) {
                                Type.label.fontWeight
                            } else {
                                Type.text.fontWeight
                            },
                    ),
                color = color,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            part.doses.mapNotNull { medicines[it.medId]?.medicine?.title }.joinToString(", "),
            style = Type.body.copy(fontSize = Type.text.fontSize),
            color = palette.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (reason != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                reason.replaceFirstChar { it.uppercaseChar() },
                style = Type.label.copy(fontWeight = Type.text.fontWeight),
                color = palette.muted,
            )
        }
    }
}

private val LABEL_WIDTH = 62.dp
