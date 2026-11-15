package top.yeetfactory.rexi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DayView
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.DoseView
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.PartState
import top.yeetfactory.rexi.core.PartView
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.data.MedicineWithSlots
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A short message at the bottom of the screen with an Undo. */
internal data class Snack(
    val text: String,
    val undo: (suspend () -> Unit)?,
)

@Composable
internal fun TodayScreen(
    app: RexiApp,
    onLogDose: (Long) -> Unit,
    onLate: (LocalDate, DayPart) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    val now by rememberNow()
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val medicines by remember { app.repository.dao.watchMedicines() }.collectAsStateWithLifecycle(null)
    val logs by remember(
        date,
    ) { app.repository.dao.watchLogs(date.toEpochDay(), date.toEpochDay()) }.collectAsStateWithLifecycle(emptyList())
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var snoozeTick by remember { mutableIntStateOf(0) }
    var snack by remember { mutableStateOf<Snack?>(null) }
    var expanded by remember { mutableStateOf(setOf<DayPart>()) }
    var health by remember { mutableStateOf(ReminderHealth.read(app)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { health = ReminderHealth.read(app) }
    LaunchedEffect(snack) {
        if (snack != null) {
            delay(SNACK_MS)
            snack = null
        }
    }

    val meds = medicines
    val byId = remember(meds) { meds.orEmpty().associateBy { it.id } }
    val view =
        remember(meds, logs, now, settings) {
            DayBook.build(
                date,
                meds.orEmpty().map { it.plan() },
                logs.map { it.logged() },
                LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone),
                settings.clock,
            )
        }

    fun takeAll(part: PartView) {
        expanded = expanded - part.part
        scope.launch {
            val taken = app.doses.take(date, part.part, only = part.pending.map { it.medId }.toSet())
            if (taken.isNotEmpty()) {
                val words = if (taken.size == 1) taken.first().medicine.title else "${part.part.label} doses"
                val verb = if (taken.all { it.medicine.unit.swallowed }) "taken" else "done"
                snack =
                    Snack("$words $verb at ${Format.time(Format.minutesOfDay(app.repository.now(), zone), use24)}") {
                        app.doses.undo(date, part.part, taken.map { it.id })
                    }
            }
        }
    }

    fun toggleOne(
        part: DayPart,
        dose: DoseView,
    ) {
        scope.launch {
            if (dose.state == DoseState.TAKEN) {
                app.doses.undo(date, part, listOf(dose.medId))
            } else {
                app.doses.takeOne(date, part, dose.medId, dose.amount)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                Text(Format.longDay(date), style = Type.body.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
                Spacer(Modifier.height(4.dp))
                Text(headline(view, meds), style = Type.display, color = palette.ink, modifier = Modifier.semantics { heading() })
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (settings.zoneNotice) {
                    Spacer(Modifier.height(16.dp))
                    Banner(
                        title = "You're in a new time zone",
                        text = "Reminders now follow the local clock.",
                        icon = RexiIcons.Globe,
                        action = { SoftButton("OK", { app.settings.update { it.copy(zoneNotice = false) } }) },
                    )
                }
                if (meds != null && meds.isNotEmpty() && !health.ok) {
                    Spacer(Modifier.height(16.dp))
                    Banner(
                        title = health.title,
                        text = health.text,
                        icon = RexiIcons.Bell,
                        action = { SoftButton("Fix", { health.fix(app) }) },
                    )
                }
                Spacer(Modifier.height(18.dp))
                when {
                    meds == null -> {}

                    meds.isEmpty() -> {
                        EmptyPillbox(onAdd)
                    }

                    view.parts.isEmpty() -> {
                        NothingToday()
                    }

                    else -> {
                        Tray(
                            view = view,
                            medicines = byId,
                            date = date,
                            use24 = use24,
                            snoozedUntil = { part ->
                                snoozeTick.let { app.settings.snoozedUntil(date, part) }.takeIf { it > now }
                            },
                            onTake = ::takeAll,
                            onToggle = ::toggleOne,
                            expanded = expanded,
                            onExpand = { part -> expanded = if (part in expanded) expanded - part else expanded + part },
                            onSnooze = { part ->
                                scope.launch {
                                    val until = app.doses.snooze(date, part.part, settings.snoozeMinutes)
                                    snoozeTick++
                                    snack = Snack("Snoozed until ${Format.time(Format.minutesOfDay(until, zone), use24)}", null)
                                }
                            },
                            onSkip = { part ->
                                expanded = expanded - part.part
                                scope.launch {
                                    app.doses.skip(date, part.part)
                                    snack = Snack("Skipped") { part.pending.forEach { app.repository.undo(it.medId, date, part.part) } }
                                }
                            },
                            onLate = { part -> onLate(date, part.part) },
                            onOpen = onOpen,
                        )
                    }
                }
                val asNeeded = meds.orEmpty().filter { it.asNeeded && !it.medicine.paused }
                if (asNeeded.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    asNeeded.forEach { med ->
                        AsNeededRow(med, onClick = { onLogDose(med.id) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        SnackHost(
            snack = snack,
            onUndo = { undo ->
                snack = null
                scope.launch { undo() }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

private fun headline(
    view: DayView,
    medicines: List<MedicineWithSlots>?,
): String =
    when {
        medicines == null -> ""
        medicines.isEmpty() -> "Welcome"
        view.total == 0 -> "Nothing due today"
        else -> "${view.taken} of ${view.total} taken"
    }

@Composable
private fun Tray(
    view: DayView,
    medicines: Map<Long, MedicineWithSlots>,
    date: LocalDate,
    use24: Boolean,
    snoozedUntil: (DayPart) -> Long?,
    onTake: (PartView) -> Unit,
    onToggle: (DayPart, DoseView) -> Unit,
    onSnooze: (PartView) -> Unit,
    onSkip: (PartView) -> Unit,
    onLate: (PartView) -> Unit,
    onOpen: (Long) -> Unit,
    expanded: Set<DayPart>,
    onExpand: (DayPart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val nextPart = if (view.due == null) view.next?.part else null
    Column(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(34.dp), clip = false, ambientColor = palette.line, spotColor = palette.line)
            .clip(RoundedCornerShape(34.dp))
            .background(palette.surface)
            .padding(8.dp)
            .animateContentSize(spring(stiffness = 600f)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        view.parts.forEach { part ->
            when (part.state) {
                PartState.DUE -> {
                    OpenCompartment(
                        part = part,
                        medicines = medicines,
                        date = date,
                        use24 = use24,
                        snoozedUntil = snoozedUntil(part.part),
                        onTake = { onTake(part) },
                        onToggle = { onToggle(part.part, it) },
                        onSnooze = { onSnooze(part) },
                        onSkip = { onSkip(part) },
                        onOpen = onOpen,
                    )
                }

                else -> {
                    if (part.part in expanded) {
                        ReviewCompartment(
                            part = part,
                            medicines = medicines,
                            date = date,
                            onToggle = { onToggle(part.part, it) },
                            onClose = { onExpand(part.part) },
                            onOpen = onOpen,
                        )
                    } else {
                        ClosedCompartment(
                            part = part,
                            medicines = medicines,
                            use24 = use24,
                            next = part.part == nextPart,
                            onLate = { onLate(part) },
                            onExpand = { onExpand(part.part) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClosedCompartment(
    part: PartView,
    medicines: Map<Long, MedicineWithSlots>,
    use24: Boolean,
    next: Boolean,
    onLate: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val tint = palette.compartment(part.part, later = part.state == PartState.LATER)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(tint.background)
            .clickable(role = Role.Button, onClickLabel = "See or change these doses", onClick = onExpand)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(part.part.label, style = Type.name, color = tint.label, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(3.dp))
            Text(
                part.doses.mapNotNull { medicines[it.medId]?.medicine?.title }.joinToString(", "),
                style = Type.small,
                color = tint.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        when (part.state) {
            PartState.DONE -> {
                DoneStatus(part, use24, tint.ink)
            }

            PartState.MISSED -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text("Missed", style = Type.label, color = if (part.part == DayPart.NIGHT) tint.label else palette.alert)
                    Spacer(Modifier.height(4.dp))
                    SoftButton("I took it", onLate, height = 36.dp, background = palette.surface.copy(alpha = 0.85f))
                }
            }

            else -> {
                if (next) {
                    Box(
                        Modifier
                            .height(28.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, tint.label, CircleShape)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Next", style = Type.label, color = tint.label) }
                    Spacer(Modifier.width(10.dp))
                }
                Text(Format.time(part.minutes, use24), style = Type.time, color = tint.ink)
            }
        }
    }
}

/** A compartment opened to correct it: each dose can be ticked or unticked, including taking a later dose early. */
@Composable
private fun ReviewCompartment(
    part: PartView,
    medicines: Map<Long, MedicineWithSlots>,
    date: LocalDate,
    onToggle: (DoseView) -> Unit,
    onClose: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = LocalPalette.current.compartment(part.part, later = part.state == PartState.LATER)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(tint.background)
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(part.part.label, style = Type.name, color = tint.label, modifier = Modifier.weight(1f).semantics { heading() })
            TextAction("Close", onClose, color = tint.ink)
        }
        part.doses.forEach { dose ->
            val med = medicines[dose.medId] ?: return@forEach
            Spacer(Modifier.height(8.dp))
            DoseLine(
                med = med,
                dose = dose,
                date = date,
                time = null,
                textColor = tint.text,
                inkColor = tint.ink,
                onInk = tint.background,
                onToggle = { onToggle(dose) },
                onOpen = { onOpen(med.id) },
            )
        }
    }
}

@Composable
private fun DoneStatus(
    part: PartView,
    use24: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val taken = part.doses.count { it.state == DoseState.TAKEN }
    val skipped = part.doses.count { it.state == DoseState.SKIPPED }
    val zone = ZoneId.systemDefault()
    val text =
        when {
            taken == part.doses.size -> "Taken ${part.takenAt?.let { Format.time(it, zone, use24) }.orEmpty()}"
            skipped == part.doses.size -> "Skipped"
            else -> "$taken of ${part.doses.size} taken"
        }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (taken > 0) RexiIcons.Check else RexiIcons.Cross,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text.trim(), style = Type.label.copy(fontSize = Type.small.fontSize), color = color)
    }
}

@Composable
private fun OpenCompartment(
    part: PartView,
    medicines: Map<Long, MedicineWithSlots>,
    date: LocalDate,
    use24: Boolean,
    snoozedUntil: Long?,
    onTake: () -> Unit,
    onToggle: (DoseView) -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val tint = palette.tint(part.part)
    val night = part.part == DayPart.NIGHT
    val single = part.doses.size == 1
    val swallowed = part.pending.all { medicines[it.medId]?.medicine?.unit?.swallowed != false }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(tint.background)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(part.part.label, style = Type.name, color = tint.label, modifier = Modifier.weight(1f).semantics { heading() })
            val chip = if (snoozedUntil != null) "Snoozed until ${Format.time(snoozedUntil, ZoneId.systemDefault(), use24)}" else "Due now"
            Box(
                Modifier
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(if (night) tint.ink else palette.primary)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(chip, style = Type.label, color = if (night) palette.night.background else palette.onPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))
        part.doses.forEach { dose ->
            val med = medicines[dose.medId] ?: return@forEach
            DoseLine(
                med = med,
                dose = dose,
                date = date,
                time = if (single) Format.time(part.minutes, use24) else null,
                textColor = tint.text,
                inkColor = tint.ink,
                onInk = tint.background,
                onToggle = if (single) null else ({ onToggle(dose) }),
                onOpen = { onOpen(med.id) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = ButtonGap) {
            val label =
                when {
                    part.pending.size <= 1 -> if (swallowed) "Take" else "Done"
                    else -> if (swallowed) "Take all" else "All done"
                }
            PrimaryButton(
                text = label,
                onClick = onTake,
                icon = RexiIcons.Check,
                height = 52.dp,
                modifier = Modifier.weight(1f),
                container = if (night) tint.ink else palette.primary,
                content = if (night) palette.night.background else palette.onPrimary,
            )
            OutlineButton("Snooze", onSnooze, color = tint.ink, border = tint.ink.copy(alpha = 0.25f))
            OutlineButton("Skip", onSkip, color = tint.ink, border = tint.ink.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun DoseLine(
    med: MedicineWithSlots,
    dose: DoseView,
    date: LocalDate,
    time: String?,
    textColor: androidx.compose.ui.graphics.Color,
    inkColor: androidx.compose.ui.graphics.Color,
    onInk: androidx.compose.ui.graphics.Color,
    onToggle: (() -> Unit)?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val medicine = med.medicine
    val amount = listOf(medicine.strengthText, medicine.amount(dose.amount)).filter { it.isNotBlank() }.joinToString(", ")
    val status =
        when (dose.state) {
            DoseState.SKIPPED -> "Skipped" + if (dose.reason.isNotBlank()) ", ${dose.reason.lowercase()}" else ""
            DoseState.MISSED -> "Missed"
            else -> null
        }
    val extras =
        listOfNotNull(
            status,
            Schedule.courseDay(med.plan(), date)?.let { "Day $it of ${medicine.courseDays}" },
            medicine.food.takeIf { it != Food.ANY }?.label,
        ).joinToString(". ")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedIcon(medicine.kind, medicine.color1, medicine.color2, size = if (time != null) 52.dp else 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medicine.title,
                    style = if (time != null) Type.heading else Type.name,
                    color = inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(amount, style = Type.small, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (extras.isNotEmpty()) Text(extras, style = Type.small, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            Text(time, style = Type.time.copy(fontSize = Type.title.fontSize, fontWeight = Type.time.fontWeight), color = inkColor)
        }
        if (onToggle != null) {
            Spacer(Modifier.width(8.dp))
            val done = dose.state == DoseState.TAKEN
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (done) inkColor else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.5.dp, inkColor.copy(alpha = if (done) 1f else 0.3f), CircleShape)
                    .toggleable(value = done, role = Role.Checkbox, onValueChange = { onToggle() }),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(RexiIcons.Check, contentDescription = null, tint = onInk, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AsNeededRow(
    med: MedicineWithSlots,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val medicine = med.medicine
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(palette.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(medicine.kind, medicine.color1, medicine.color2, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(medicine.title, style = Type.body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val limit = if (medicine.maxPerDay > 0) ", up to ${medicine.maxPerDay} a day" else ""
            Text("As needed$limit", style = Type.label.copy(fontWeight = Type.text.fontWeight), color = palette.muted)
        }
        Box(
            Modifier
                .height(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, palette.outline, CircleShape)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Log", style = Type.label.copy(fontSize = Type.small.fontSize), color = palette.ink) }
    }
}

@Composable
private fun EmptyPillbox(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(palette.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DayPart.entries.forEach { part ->
                val tint = palette.tint(part)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(tint.background)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(part.label, style = Type.body, color = tint.label) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Your pillbox is empty", style = Type.heading, color = palette.ink, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            "Add a medicine and Rexi will remind you when it's due, warn you before it runs out, and keep a simple record.",
            style = Type.text,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Add a medicine", onAdd, icon = RexiIcons.Plus, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NothingToday(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Panel(modifier) {
        Text("Nothing scheduled today", style = Type.name, color = palette.ink)
        Spacer(Modifier.height(4.dp))
        Text("Your medicines for other days, and any you take when needed, are under Medicines.", style = Type.small, color = palette.muted)
    }
}

@Composable
internal fun SnackHost(
    snack: Snack?,
    onUndo: (suspend () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    AnimatedVisibility(
        visible = snack != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val current = snack ?: return@AnimatedVisibility
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(palette.primary)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(current.text, style = Type.text, color = palette.onPrimary, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            val undo = current.undo
            if (undo != null) {
                TextAction("Undo", { onUndo(undo) }, color = palette.onPrimaryAction)
            }
        }
    }
}

/** Whether reminders can actually reach the person, and how to fix it if not. */
internal data class ReminderHealth(
    val notifications: Boolean,
    val exact: Boolean,
) {
    val ok: Boolean get() = notifications && exact

    val title: String get() = if (!notifications) "Reminders can't reach you" else "Reminders may come late"

    val text: String
        get() =
            if (!notifications) {
                "Notifications are turned off for Rexi."
            } else {
                "Allow alarms and reminders so they ring at the exact minute."
            }

    fun fix(app: RexiApp) {
        val intent =
            if (!notifications) {
                android.content
                    .Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, app.packageName)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                app.reminders.exactAlarmSettings()
            }
        app.startActivity(intent)
    }

    companion object {
        fun read(app: RexiApp) = ReminderHealth(app.notifier.canNotify(), app.reminders.canRingOnTime())
    }
}

private const val SNACK_MS = 4_500L
