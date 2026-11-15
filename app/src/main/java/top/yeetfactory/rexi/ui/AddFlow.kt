package top.yeetfactory.rexi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseUnit
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.Plan
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.core.Supply
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.Medicine
import top.yeetfactory.rexi.data.MedicineWithSlots
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Everything the add flow collects, before it becomes a [Medicine]. Editing starts from the saved medicine. */
internal data class Draft(
    val base: Medicine? = null,
    val name: String = "",
    val strength: String = "",
    val strengthUnit: String = "mg",
    val unit: DoseUnit = DoseUnit.TABLET,
    val unitChosen: Boolean = false,
    val customUnit: String = "",
    val purpose: String = "",
    val kind: MedKind = MedKind.TABLET,
    val otherKind: String = "",
    val color1: MedColor = MedColor.WHITE,
    val color2: MedColor = MedColor.WHITE,
    val frequency: Frequency = Frequency.EVERY_DAY,
    val parts: Set<DayPart> = setOf(DayPart.MORNING),
    val eachTime: Double = 1.0,
    val amounts: Map<DayPart, Double> = emptyMap(),
    val perPart: Boolean = false,
    val daysMask: Int = MON_WED_FRI,
    val everyDays: Int = 2,
    /** The day the person picked for the next dose of an every-few-days medicine. Null keeps the current rhythm. */
    val nextDose: LocalDate? = null,
    val courseDays: Int = 7,
    val food: Food = Food.ANY,
    val asNeededAmount: Double = 1.0,
    val minGapHours: Int = 6,
    val maxPerDay: Int = 3,
    val supplyMode: SupplyMode = SupplyMode.COUNT,
    val supplyChosen: Boolean = false,
    val supplyCount: Double = 30.0,
    val packDays: Int = 30,
    val refillLeadDays: Int = 7,
) {
    val editing: Boolean get() = base != null

    fun amount(part: DayPart): Double = if (perPart) amounts[part] ?: eachTime else eachTime

    fun slots(): Map<DayPart, Double> = if (frequency == Frequency.AS_NEEDED) emptyMap() else parts.associateWith(::amount)

    /** Picking what it is also picks what a dose is counted in, until the person chooses a unit themselves. */
    fun withKind(kind: MedKind): Draft = copy(kind = kind).counting(if (unitChosen) unit else kind.defaultUnit)

    fun withUnit(unit: DoseUnit): Draft = copy(unitChosen = true).counting(unit)

    private fun counting(unit: DoseUnit): Draft =
        copy(
            unit = unit,
            supplyMode =
                if (supplyChosen) {
                    supplyMode
                } else if (unit.countable) {
                    SupplyMode.COUNT
                } else {
                    SupplyMode.DURATION
                },
        )

    fun startDay(today: LocalDate): LocalDate {
        val prior = base?.startDay?.let(LocalDate::ofEpochDay)
        val next = nextDose
        return if (frequency == Frequency.EVERY_FEW_DAYS && next != null) {
            Schedule.startFor(next, everyDays, notAfter = minOf(today, prior ?: today))
        } else {
            prior ?: today
        }
    }

    fun plan(today: LocalDate): Plan =
        Plan(
            id = base?.id ?: 0,
            frequency = frequency,
            daysMask = daysMask,
            everyDays = everyDays,
            startDay = startDay(today),
            courseDays = courseDays,
            slots = slots(),
            active = true,
        )

    fun toMedicine(today: LocalDate): Medicine {
        val prior = base
        val start = startDay(today).toEpochDay()
        val full =
            if (prior != null && prior.supplyMode == SupplyMode.COUNT && supplyMode == SupplyMode.COUNT &&
                prior.supplyFull >= supplyCount
            ) {
                prior.supplyFull
            } else {
                supplyCount
            }
        val opened =
            if (prior == null || prior.supplyMode != supplyMode || prior.packDays != packDays) today.toEpochDay() else prior.packOpenedDay
        return (prior ?: Medicine(name = name, startDay = start)).copy(
            name = name.trim(),
            strength = strength.trim(),
            strengthUnit = strengthUnit,
            unit = unit,
            customUnit = customUnit.trim(),
            purpose = purpose.trim(),
            kind = kind,
            otherKind = otherKind.trim(),
            color1 = color1,
            color2 = color2,
            frequency = frequency,
            daysMask = daysMask,
            everyDays = everyDays,
            courseDays = courseDays,
            startDay = start,
            food = food,
            asNeededAmount = asNeededAmount,
            minGapHours = minGapHours,
            maxPerDay = maxPerDay,
            supplyMode = supplyMode,
            supplyCount = supplyCount,
            supplyFull = full,
            packDays = packDays,
            packOpenedDay = opened,
            refillLeadDays = refillLeadDays,
        )
    }

    companion object {
        private const val MON_WED_FRI = 0b001_0101

        fun from(med: MedicineWithSlots): Draft {
            val m = med.medicine
            val amounts = med.amounts
            val first = amounts.values.firstOrNull() ?: 1.0
            return Draft(
                base = m,
                name = m.name,
                strength = m.strength,
                strengthUnit = m.strengthUnit,
                unit = m.unit,
                unitChosen = true,
                customUnit = m.customUnit,
                purpose = m.purpose,
                kind = m.kind,
                otherKind = m.otherKind,
                color1 = m.color1,
                color2 = m.color2,
                frequency = m.frequency,
                parts = amounts.keys.ifEmpty { setOf(DayPart.MORNING) },
                eachTime = first,
                amounts = amounts,
                perPart = amounts.values.toSet().size > 1,
                daysMask = if (m.frequency == Frequency.SOME_DAYS) m.daysMask else MON_WED_FRI,
                everyDays = m.everyDays,
                courseDays = m.courseDays,
                food = m.food,
                asNeededAmount = m.asNeededAmount,
                minGapHours = m.minGapHours,
                maxPerDay = m.maxPerDay,
                supplyMode = m.supplyMode,
                supplyChosen = true,
                supplyCount = m.supplyCount,
                packDays = m.packDays,
                refillLeadDays = m.refillLeadDays,
            )
        }
    }
}

@Composable
internal fun AddFlow(
    app: RexiApp,
    editId: Long?,
    onSave: (Long) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<Draft?>(if (editId == null) Draft() else null) }
    // What the flow opened with, so leaving only asks first when something would be lost.
    var initial by remember { mutableStateOf(draft) }
    var step by remember { mutableIntStateOf(1) }
    var confirmLeave by remember { mutableStateOf(false) }
    LaunchedEffect(editId) {
        if (editId != null) {
            val loaded =
                app.repository.dao
                    .medicine(editId)
                    ?.let(Draft::from) ?: Draft()
            draft = loaded
            initial = loaded
        }
    }
    val leave = { if (draft != initial) confirmLeave = true else onCancel() }
    BackHandler { if (step > 1) step-- else leave() }
    val current = draft ?: return
    val today = app.repository.today()

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step == 1) {
                IconCircleButton(RexiIcons.Close, "Cancel", leave)
            } else {
                IconCircleButton(RexiIcons.Back, "Back", { step-- })
            }
            Spacer(Modifier.weight(1f))
            StepBar(step, STEPS)
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "step",
            modifier = Modifier.weight(1f),
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 16.dp),
            ) {
                when (shown) {
                    1 -> WhatStep(current) { draft = it }
                    2 -> LookStep(current) { draft = it }
                    3 -> WhenStep(current, today, app.settings.value.partMinutes, rememberUse24h()) { draft = it }
                    else -> SupplyStep(current, today) { draft = it }
                }
            }
        }
        val ready =
            when (step) {
                1 -> {
                    current.name.isNotBlank()
                }

                3 -> {
                    current.frequency == Frequency.AS_NEEDED ||
                        (current.parts.isNotEmpty() && (current.frequency != Frequency.SOME_DAYS || current.daysMask != 0))
                }

                else -> {
                    true
                }
            }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 8.dp)) {
            if (step < STEPS) {
                PrimaryButton("Continue", { step++ }, enabled = ready, modifier = Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    if (current.editing) "Save changes" else "Save ${current.name.trim()}",
                    {
                        scope.launch {
                            val id = app.repository.save(current.toMedicine(today), current.slots())
                            onSave(id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = if (current.editing) "Discard your changes?" else "Discard this medicine?",
            text = if (current.editing) "Your changes won't be saved." else "What you've entered so far won't be saved.",
            confirm = "Discard",
            onConfirm = onCancel,
            onDismiss = { confirmLeave = false },
            danger = true,
        )
    }
}

@Composable
private fun StepTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val palette = LocalPalette.current
    Column(modifier) {
        Text(title, style = Type.title, color = palette.ink)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = Type.text, color = palette.muted)
        }
    }
}

@Composable
private fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = Type.label.copy(fontSize = Type.small.fontSize, fontWeight = Type.body.fontWeight),
        color = LocalPalette.current.muted,
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.WhatStep(
    draft: Draft,
    onChange: (Draft) -> Unit,
) {
    StepTitle(if (draft.editing) "Edit ${draft.base?.title}" else "What are you adding?")
    Spacer(Modifier.height(22.dp))
    Field("Name", draft.name, {
        onChange(draft.copy(name = it))
    }, placeholder = "e.g. Metformin", capitalization = KeyboardCapitalization.Words)
    Spacer(Modifier.height(18.dp))
    FieldLabel("Strength, optional")
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field(
            label = "",
            value = draft.strength,
            onValueChange = { onChange(draft.copy(strength = it)) },
            placeholder = "500",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        val units = listOf("mg", "mcg", "ml", "%", "IU")
        Segmented(units, units.indexOf(draft.strengthUnit).coerceAtLeast(0), {
            onChange(draft.copy(strengthUnit = units[it]))
        }, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(18.dp))
    Field("What's it for? Optional", draft.purpose, { onChange(draft.copy(purpose = it)) }, placeholder = "e.g. Blood sugar")
}

@Composable
private fun ColumnScope.LookStep(
    draft: Draft,
    onChange: (Draft) -> Unit,
) {
    val palette = LocalPalette.current
    var half by remember { mutableIntStateOf(0) }
    StepTitle("What is it?", subtitle = "Pick the closest match. Anything else goes under Other.")
    Spacer(Modifier.height(18.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(palette.surface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MedIcon(draft.kind, draft.color1, draft.color2, size = 96.dp)
        Text(draft.name.ifBlank { "Your medicine" }, style = Type.body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(18.dp))
    FieldLabel("Type")
    Spacer(Modifier.height(8.dp))
    MedKind.entries.chunked(KINDS_PER_ROW).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { kind ->
                val on = draft.kind == kind
                Column(
                    Modifier
                        .weight(1f)
                        .height(70.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(palette.surface)
                        .border(2.dp, if (on) palette.ink else palette.surface, RoundedCornerShape(18.dp))
                        .selectable(selected = on, role = Role.RadioButton) { onChange(draft.withKind(kind)) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MedIcon(kind, draft.color1, draft.color2, size = 32.dp)
                    Text(kind.label, style = Type.micro.copy(fontWeight = Type.label.fontWeight), color = palette.ink, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (draft.kind == MedKind.OTHER) {
        Spacer(Modifier.height(4.dp))
        Field("What is it?", draft.otherKind, { onChange(draft.copy(otherKind = it)) }, placeholder = "e.g. powder, lozenge, shampoo")
        Spacer(Modifier.height(8.dp))
    }
    UnitPicker(draft, onChange)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
        FieldLabel("Colour", Modifier.weight(1f))
        if (draft.kind == MedKind.CAPSULE) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("One half", half == 0, { half = 0 }, height = 36.dp)
                Chip("Other half", half == 1, { half = 1 }, height = 36.dp)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val active = if (draft.kind == MedKind.CAPSULE && half == 1) draft.color2 else draft.color1
    Row(
        Modifier
            .padding(horizontal = 2.dp)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MedColor.entries.forEach { color ->
            val on = color == active
            Box(
                Modifier
                    .size(46.dp)
                    .border(2.dp, if (on) palette.ink else palette.ground, RoundedCornerShape(16.dp))
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        androidx.compose.ui.graphics
                            .Color(color.argb),
                    ).border(1.dp, palette.outline, RoundedCornerShape(12.dp))
                    .selectable(selected = on, role = Role.RadioButton) {
                        onChange(if (draft.kind == MedKind.CAPSULE && half == 1) draft.copy(color2 = color) else draft.copy(color1 = color))
                    }.semantics { contentDescription = color.label },
            )
        }
    }
}

/**
 * What one dose is counted in. The type already picked the usual unit, so this stays one line until the person
 * wants another, like counting a liquid in drops. "Other" has no usual unit, so it starts open.
 */
@Composable
private fun UnitPicker(
    draft: Draft,
    onChange: (Draft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    var choosing by remember { mutableStateOf(false) }
    val open = choosing || draft.kind == MedKind.OTHER
    Column(modifier) {
        if (open) {
            Spacer(Modifier.height(8.dp))
            FieldLabel("How is one dose counted?")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DoseUnit.entries.forEach { unit -> Chip(unit.chip, draft.unit == unit, { onChange(draft.withUnit(unit)) }, height = 40.dp) }
            }
            if (draft.unit == DoseUnit.CUSTOM) {
                Spacer(Modifier.height(14.dp))
                Field(
                    "What do you call one dose? Optional",
                    draft.customUnit,
                    { onChange(draft.copy(customUnit = it)) },
                    placeholder = "e.g. scoop, capful, wash",
                    capitalization = KeyboardCapitalization.None,
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(unitHint(draft), style = Type.small, color = palette.muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
                TextAction("Change", { choosing = true })
            }
        }
    }
}

private fun unitHint(draft: Draft): String =
    when (draft.unit) {
        DoseUnit.APPLICATION -> "You log each time you apply it."
        DoseUnit.PATCH -> "You log each new patch."
        DoseUnit.INJECTION -> "You log each injection."
        DoseUnit.UNIT -> "Each dose is measured in units, like insulin."
        DoseUnit.ML -> "Each dose is measured in ml."
        else -> "Each dose is counted in ${Format.unitWord(draft.unit, draft.customUnit)}."
    }

@Composable
private fun ColumnScope.WhenStep(
    draft: Draft,
    today: LocalDate,
    partMinutes: Map<DayPart, Int>,
    use24: Boolean,
    onChange: (Draft) -> Unit,
) {
    val palette = LocalPalette.current
    val strength =
        draft.strength
            .takeIf { it.isNotBlank() }
            ?.let { " $it ${draft.strengthUnit}" }
            .orEmpty()
    StepTitle("When do you take it?", subtitle = "${draft.name.trim()}$strength")
    Spacer(Modifier.height(20.dp))
    FieldLabel("How often")
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Frequency.entries.forEach { frequency ->
            Chip(frequency.label, draft.frequency == frequency, { onChange(draft.copy(frequency = frequency)) })
        }
    }
    Spacer(Modifier.height(18.dp))
    if (draft.frequency == Frequency.AS_NEEDED) {
        Panel(padding = 0.dp) {
            StepperRow(
                label = "Each time",
                value = draft.unitAmount(draft.asNeededAmount),
                onMinus = { onChange(draft.copy(asNeededAmount = draft.stepped(draft.asNeededAmount, -1))) },
                onPlus = { onChange(draft.copy(asNeededAmount = draft.stepped(draft.asNeededAmount, 1))) },
            )
            RowLine()
            StepperRow(
                label = "At least",
                value = "${draft.minGapHours} h apart",
                onMinus = { onChange(draft.copy(minGapHours = (draft.minGapHours - 1).coerceAtLeast(0))) },
                onPlus = { onChange(draft.copy(minGapHours = (draft.minGapHours + 1).coerceAtMost(MAX_GAP_HOURS))) },
            )
            RowLine()
            StepperRow(
                label = "Up to",
                value = if (draft.maxPerDay == 0) "No limit" else "${draft.maxPerDay} a day",
                onMinus = { onChange(draft.copy(maxPerDay = (draft.maxPerDay - 1).coerceAtLeast(0))) },
                onPlus = { onChange(draft.copy(maxPerDay = (draft.maxPerDay + 1).coerceAtMost(MAX_PER_DAY))) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "No reminders for this one. You log it when you take it, and Rexi warns you if it's too soon.",
            style = Type.small,
            color = palette.muted,
        )
    } else {
        FieldLabel("Parts of the day")
        Spacer(Modifier.height(10.dp))
        DayPart.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { part ->
                    val on = part in draft.parts
                    val tint = palette.tint(part)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(84.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (on) tint.background else palette.surface)
                            .toggleable(value = on, role = Role.Checkbox) {
                                onChange(draft.copy(parts = if (on) draft.parts - part else draft.parts + part))
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                part.label,
                                style = Type.body,
                                color = if (on) tint.label else palette.muted,
                                modifier = Modifier.weight(1f),
                            )
                            if (on) Icon(RexiIcons.Check, contentDescription = null, tint = tint.ink, modifier = Modifier.size(20.dp))
                        }
                        Text(
                            Format.time(partMinutes[part] ?: part.defaultMinutes, use24),
                            style = Type.time,
                            color = if (on) tint.ink else palette.faint,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when (draft.frequency) {
            Frequency.SOME_DAYS -> {
                Spacer(Modifier.height(6.dp))
                FieldLabel("Which days")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        val on = draft.daysMask and bit != 0
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) palette.primary else palette.surface)
                                .toggleable(value = on, role = Role.Checkbox) { onChange(draft.copy(daysMask = draft.daysMask xor bit)) }
                                .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                                style = Type.label,
                                color = if (on) palette.onPrimary else palette.muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Frequency.EVERY_FEW_DAYS -> {
                Spacer(Modifier.height(6.dp))
                Panel(padding = 0.dp) {
                    StepperRow(
                        label = "Every",
                        value = "${draft.everyDays} days",
                        onMinus = { onChange(draft.copy(everyDays = (draft.everyDays - 1).coerceAtLeast(2))) },
                        onPlus = { onChange(draft.copy(everyDays = (draft.everyDays + 1).coerceAtMost(MAX_EVERY_DAYS))) },
                    )
                }
                Spacer(Modifier.height(14.dp))
                NextDosePicker(draft, today, onChange)
                Spacer(Modifier.height(8.dp))
            }

            Frequency.COURSE -> {
                Spacer(Modifier.height(6.dp))
                Panel(padding = 0.dp) {
                    StepperRow(
                        label = "For",
                        value = "${draft.courseDays} days",
                        onMinus = { onChange(draft.copy(courseDays = (draft.courseDays - 1).coerceAtLeast(1))) },
                        onPlus = { onChange(draft.copy(courseDays = (draft.courseDays + 1).coerceAtMost(MAX_COURSE_DAYS))) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            else -> {}
        }
        Spacer(Modifier.height(6.dp))
        Panel(padding = 0.dp) {
            if (!draft.perPart || draft.parts.size < 2) {
                StepperRow(
                    label = "Each time",
                    value = draft.unitAmount(draft.eachTime),
                    onMinus = { onChange(draft.copy(eachTime = draft.stepped(draft.eachTime, -1))) },
                    onPlus = { onChange(draft.copy(eachTime = draft.stepped(draft.eachTime, 1))) },
                )
            } else {
                DayPart.entries.filter { it in draft.parts }.forEachIndexed { index, part ->
                    if (index > 0) RowLine()
                    val value = draft.amount(part)
                    StepperRow(
                        label = part.label,
                        value = draft.unitAmount(value),
                        onMinus = { onChange(draft.copy(amounts = draft.amounts + (part to draft.stepped(value, -1)))) },
                        onPlus = { onChange(draft.copy(amounts = draft.amounts + (part to draft.stepped(value, 1)))) },
                    )
                }
            }
        }
        if (draft.parts.size > 1) {
            TextAction(
                if (draft.perPart) "Same amount every time" else "Different amounts at different times",
                {
                    onChange(
                        draft.copy(
                            perPart = !draft.perPart,
                            amounts = if (draft.perPart) draft.amounts else draft.parts.associateWith { draft.eachTime },
                        ),
                    )
                },
                color = palette.muted,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    FieldLabel("Food")
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Food.entries.forEach { food -> Chip(food.label, draft.food == food, { onChange(draft.copy(food = food)) }) }
    }
}

/**
 * Which day the next dose falls on. Without this, the rhythm starts on the day the medicine is added, which is
 * wrong for anyone who took their every-other-day pill yesterday.
 */
@Composable
private fun NextDosePicker(
    draft: Draft,
    today: LocalDate,
    onChange: (Draft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    // The day the current rhythm gives. Picking it again keeps the saved start day, so nothing resets.
    val usual = Schedule.nextDue(draft.copy(nextDose = null).plan(today), today)
    val next = Schedule.nextDue(draft.plan(today), today)
    Column(modifier) {
        FieldLabel("Next dose")
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until draft.everyDays.coerceAtMost(MAX_NEXT_CHOICES)).forEach { offset ->
                val day = today.plusDays(offset.toLong())
                val label =
                    when (offset) {
                        0 -> "Today"
                        1 -> "Tomorrow"
                        else -> Format.day(day)
                    }
                Chip(label, day == next, { onChange(draft.copy(nextDose = day.takeIf { it != usual })) }, height = 40.dp)
            }
        }
        if (draft.everyDays == 2) {
            Spacer(Modifier.height(8.dp))
            Text("Took it yesterday? Pick tomorrow.", style = Type.small, color = palette.muted, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** "today", "tomorrow" or a date, for sentences about the next dose. */
private fun dayWord(
    day: LocalDate,
    today: LocalDate,
): String =
    when (day) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> "on ${Format.day(day)}"
    }

private fun Draft.unitAmount(value: Double): String = Format.amount(value, unit, customUnit)

/** One step up or down, in halves for tablets and liquids and in whole doses for everything else. */
private fun Draft.stepped(
    value: Double,
    direction: Int,
): Double = (value + direction * unit.step).coerceIn(unit.step, MAX_AMOUNT)

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = Type.text.copy(fontWeight = Type.body.fontWeight),
            color = LocalPalette.current.muted,
            modifier = Modifier.weight(1f),
        )
        Stepper(value, onMinus, onPlus)
    }
}

@Composable
private fun ColumnScope.SupplyStep(
    draft: Draft,
    today: LocalDate,
    onChange: (Draft) -> Unit,
) {
    val palette = LocalPalette.current
    var typing by remember { mutableStateOf(false) }
    StepTitle(
        "Keep track of supply",
        subtitle = "Count what's left, or just say how long a pack lasts. Either way you're warned before you run out.",
    )
    Spacer(Modifier.height(18.dp))
    val modes = listOf(SupplyMode.COUNT, SupplyMode.DURATION, SupplyMode.NONE)
    Segmented(
        listOf("Count it", "Time it", "Off"),
        modes.indexOf(draft.supplyMode),
        { onChange(draft.copy(supplyMode = modes[it], supplyChosen = true)) },
    )
    Spacer(Modifier.height(8.dp))
    val explain =
        when (draft.supplyMode) {
            SupplyMode.COUNT -> "The count goes down with every dose."
            SupplyMode.DURATION -> "For things you can't count, like a spray, a cream or a bottle."
            SupplyMode.NONE -> "No warnings about running out."
        }
    Text(explain, style = Type.small, color = palette.muted, modifier = Modifier.padding(start = 4.dp))
    if (draft.supplyMode != SupplyMode.NONE) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val count = draft.supplyMode == SupplyMode.COUNT
            IconCircleButton(
                RexiIcons.Minus,
                "Fewer",
                {
                    if (count) {
                        onChange(draft.copy(supplyCount = (draft.supplyCount - 1).coerceAtLeast(0.0)))
                    } else {
                        onChange(
                            draft.copy(
                                packDays =
                                    (
                                        draft.packDays -
                                            1
                                    ).coerceAtLeast(1),
                            ),
                        )
                    }
                },
                size = 56.dp,
                background = palette.well,
            )
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(enabled = count, role = Role.Button) { typing = true },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (count) Format.number(draft.supplyCount) else "${draft.packDays}", style = Type.count, color = palette.ink)
                Text(
                    if (count) "${Format.unitWord(draft.unit, draft.customUnit, draft.supplyCount)} you have now" else "days per pack",
                    style = Type.label,
                    color = palette.muted,
                )
                // Stepping up to a full box of 90 one tap at a time would be tedious, so say the number can be typed.
                if (count) Text("Tap the number to type it", style = Type.micro, color = palette.faint)
            }
            IconCircleButton(
                RexiIcons.Plus,
                "More",
                {
                    if (count) {
                        onChange(draft.copy(supplyCount = draft.supplyCount + 1))
                    } else {
                        onChange(
                            draft.copy(
                                packDays =
                                    (
                                        draft.packDays +
                                            1
                                    ).coerceAtMost(365),
                            ),
                        )
                    }
                },
                size = 56.dp,
                background = palette.well,
            )
        }
        Spacer(Modifier.height(18.dp))
        FieldLabel("Remind me to refill this far ahead")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3 to "3 days", 7 to "1 week", 14 to "2 weeks").forEach { (days, label) ->
                Chip(label, draft.refillLeadDays == days, { onChange(draft.copy(refillLeadDays = days)) }, modifier = Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedIcon(draft.kind, draft.color1, draft.color2, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val strength =
                    draft.strength
                        .takeIf { it.isNotBlank() }
                        ?.let { " $it ${draft.strengthUnit}" }
                        .orEmpty()
                Text(draft.name.trim() + strength, style = Type.body, color = palette.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (draft.frequency == Frequency.AS_NEEDED) {
                        SoftTag("When needed")
                    } else {
                        DayPart.entries.filter { it in draft.parts }.forEach { PartTag(it) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.well))
        Spacer(Modifier.height(12.dp))
        Text(summary(draft, today), style = Type.small, color = palette.muted)
    }
    if (typing) {
        NumberDialog(
            title = "How many do you have now?",
            initial = draft.supplyCount,
            unit = Format.unitWord(draft.unit, draft.customUnit),
            onSave = {
                onChange(draft.copy(supplyCount = it))
                typing = false
            },
            onDismiss = { typing = false },
        )
    }
}

private fun summary(
    draft: Draft,
    today: LocalDate,
): String {
    val schedule =
        if (draft.frequency == Frequency.AS_NEEDED) {
            "Only when needed."
        } else {
            val amount =
                if (draft.perPart &&
                    draft.parts.size > 1
                ) {
                    "Different amounts"
                } else {
                    Format.amount(draft.eachTime, draft.unit, draft.customUnit)
                }
            val food = if (draft.food != Food.ANY) ", ${draft.food.label.lowercase()}" else ""
            val often =
                when (draft.frequency) {
                    Frequency.EVERY_DAY -> {
                        "Every day"
                    }

                    Frequency.SOME_DAYS -> {
                        weekdays(draft.daysMask)
                    }

                    Frequency.EVERY_FEW_DAYS -> {
                        val next = Schedule.nextDue(draft.plan(today), today)
                        Format.everyDays(draft.everyDays) + next?.let { ", next dose ${dayWord(it, today)}" }.orEmpty()
                    }

                    Frequency.COURSE -> {
                        "For ${draft.courseDays} days"
                    }

                    Frequency.AS_NEEDED -> {
                        ""
                    }
                }
            "$amount ${Format.inParts(draft.parts)}$food. $often."
        }
    val supply =
        when (draft.supplyMode) {
            SupplyMode.NONE -> {
                ""
            }

            SupplyMode.COUNT -> {
                val info = Supply.counted(draft.supplyCount, Schedule.dailyUse(draft.plan(today)), today, draft.refillLeadDays)
                val runOut = info.runOut
                val remind = info.remindOn
                if (runOut == null || remind == null) {
                    ""
                } else {
                    " Lasts until about ${Format.day(runOut)}, with a refill reminder on ${Format.day(maxOf(remind, today))}."
                }
            }

            SupplyMode.DURATION -> {
                val info = Supply.timed(today, draft.packDays, today, draft.refillLeadDays)
                val runOut = info.runOut ?: today
                " Starting a pack today, it runs out around ${Format.day(runOut)}. Refill reminder on ${Format.day(
                    maxOf(info.remindOn ?: today, today),
                )}."
            }
        }
    return schedule + supply
}

private const val STEPS = 4
private const val KINDS_PER_ROW = 4
private const val MAX_AMOUNT = 40.0
private const val MAX_GAP_HOURS = 48
private const val MAX_PER_DAY = 24
private const val MAX_EVERY_DAYS = 60

/** A week of days to choose the next dose from. Longer gaps rarely need more, and the row stays short. */
private const val MAX_NEXT_CHOICES = 7
private const val MAX_COURSE_DAYS = 365
