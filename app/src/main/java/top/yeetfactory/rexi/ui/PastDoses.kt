package top.yeetfactory.rexi.ui

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import top.yeetfactory.rexi.core.DayClock
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.PastDose
import top.yeetfactory.rexi.core.Plan
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.data.Repository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Parcelize
internal data class PastDoseDraft(
    val at: Long,
    val amount: Double,
    val slotKey: String?,
) : Parcelable {
    fun toDose(slots: Set<DoseSlot>): PastDose = PastDose(at, amount, slots.firstOrNull { it.key == slotKey })
}

@Parcelize
internal data class PastScheduleStart(
    val day: Long,
    val firstKey: String,
    val through: Long,
) : Parcelable {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)

    fun doses(
        plan: Plan,
        clock: DayClock,
        zone: ZoneId,
    ): List<PastDose> {
        val first = DoseSlot.fromKey(firstKey) ?: return emptyList()
        return Schedule.pastDoses(plan.copy(startDay = date), first, through, clock, zone)
    }
}

@Composable
internal fun PastScheduleStep(
    draft: Draft,
    clock: DayClock,
    confirmed: PastScheduleStart?,
    onConfirm: (PastScheduleStart) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val now by rememberNow(60_000)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val oldest = today.minusDays(PastDose.DAYS_BACK)
    val plan = draft.plan(today)
    val slots = plan.slots.keys.sortedBy { clock.minutes(it) }

    fun slotsOn(date: LocalDate): List<DoseSlot> =
        if (Schedule.isDueOn(plan.copy(startDay = date), date)) {
            slots.filter {
                clock
                    .dueAt(date, it)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli() <= now
            }
        } else {
            emptyList()
        }

    var day by rememberSaveable {
        mutableLongStateOf(
            confirmed?.day ?: (0L..PastDose.DAYS_BACK)
                .map { today.minusDays(it) }
                .firstOrNull { slotsOn(it).isNotEmpty() }
                ?.toEpochDay() ?: today.toEpochDay(),
        )
    }
    var key by rememberSaveable { mutableStateOf(confirmed?.firstKey) }
    var picker by rememberSaveable { mutableStateOf<PastField?>(null) }
    val date = LocalDate.ofEpochDay(day).coerceIn(oldest, today)
    val choices = slotsOn(date)
    val first = choices.firstOrNull { it.key == key } ?: choices.singleOrNull()
    val selection = first?.let { PastScheduleStart(date.toEpochDay(), it.key, now) }
    val doses = selection?.doses(plan, clock, zone).orEmpty()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Panel {
            PastDoseField("Start date", Format.day(date), { picker = PastField.DATE })
            Spacer(Modifier.height(12.dp))
            if (choices.isNotEmpty()) {
                PastDoseField("First dose", first?.let { scheduledTime(it, clock, use24) } ?: "Choose a time", { picker = PastField.SLOT })
            } else {
                Text("Choose a date with a past scheduled dose.", style = Type.small, color = palette.muted)
            }
        }
        if (doses.isNotEmpty()) {
            val count = doses.size
            Text(
                if (count == 1) "Rexi will log 1 scheduled dose as taken." else "Rexi will log $count scheduled doses as taken.",
                style = Type.body,
                color = palette.ink,
            )
            if (draft.frequency == Frequency.COURSE) {
                val end = Schedule.courseEnd(plan.copy(startDay = date))
                val verb = if (end < today) "ended" else "ends"
                Text("This course $verb ${Format.day(end)}.", style = Type.small, color = palette.muted)
            }
            PrimaryButton(
                if (count == 1) "I took this dose" else "I took all $count doses",
                { if (selection != null) onConfirm(selection) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onClear != null) TextAction("Remove past doses", onClear, color = palette.muted)
    }
    if (picker == PastField.DATE) {
        PastDateDialog(
            date = date,
            oldest = oldest,
            today = today,
            onPick = {
                day = it.toEpochDay()
                picker = null
            },
            onDismiss = { picker = null },
            selectable = { slotsOn(it).isNotEmpty() },
        )
    }
    if (picker == PastField.SLOT) {
        ChoiceDialog(
            title = "When was your first dose?",
            options = choices.map { it to scheduledTime(it, clock, use24) },
            selected = first,
            onPick = {
                key = it?.key
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

private fun scheduledTime(
    slot: DoseSlot,
    clock: DayClock,
    use24: Boolean,
): String {
    val time = Format.time(clock.minutes(slot), use24)
    return if (slot is DayPart) "${slot.label}, $time" else time
}

@Composable
internal fun AsNeededPastDoseStep(
    draft: Draft,
    repository: Repository,
    entries: List<PastDoseDraft>,
    editing: Boolean,
    onEdit: (Boolean) -> Unit,
    onAdd: (PastDoseDraft) -> Unit,
    onRemove: (PastDoseDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val use24 = rememberUse24h()
    val zone = ZoneId.systemDefault()
    val today = repository.today()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var time by rememberSaveable { mutableIntStateOf(Format.minutesOfDay(repository.now(), zone)) }
    var amount by rememberSaveable { mutableDoubleStateOf(draft.asNeededAmount) }
    var picker by rememberSaveable { mutableStateOf<PastField?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val oldest = today.minusDays(PastDose.DAYS_BACK)
    val date = LocalDate.ofEpochDay(day).coerceIn(oldest, today)
    val dismissPicker = { picker = null }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (editing) {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PastDoseField("Date", Format.day(date), { picker = PastField.DATE }, Modifier.weight(1f))
                    PastDoseField("Time taken", Format.time(time, use24), { picker = PastField.TIME }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                PastDoseField("Amount taken", Format.amount(amount, draft.unit, draft.customUnit), { picker = PastField.AMOUNT })
                error?.let { Text(it, style = Type.small, color = palette.alert, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextAction("Cancel", { onEdit(false) })
                    PrimaryButton("Add dose", {
                        val local = date.atStartOfDay().plusMinutes(time.toLong())
                        val zoned = local.atZone(zone)
                        val at = zoned.toInstant().toEpochMilli()
                        error =
                            when {
                                zoned.toLocalDateTime() != local -> {
                                    "That time did not occur on this date."
                                }

                                at > repository.now() -> {
                                    "Choose a time that has passed."
                                }

                                !amount.isFinite() || amount <= 0 -> {
                                    "Enter an amount greater than zero."
                                }

                                else -> {
                                    null
                                }
                            }
                        if (error == null) {
                            onAdd(PastDoseDraft(at, amount, slotKey = null))
                            onEdit(false)
                        }
                    })
                }
            }
        } else {
            SoftButton(
                if (entries.isEmpty()) "Add a past dose" else "Add another past dose",
                {
                    if (entries.isEmpty()) {
                        time = Format.minutesOfDay(repository.now(), zone)
                        amount = draft.asNeededAmount
                    }
                    error = null
                    onEdit(true)
                },
                height = 48.dp,
            )
        }
        if (entries.isNotEmpty()) {
            Panel(padding = 0.dp) {
                entries.sortedByDescending { it.at }.forEachIndexed { index, entry ->
                    if (index > 0) RowLine()
                    val localDate = repository.dayOf(entry.at)
                    val timeText = Format.time(entry.at, zone, use24)
                    val dateText = Format.day(localDate)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("$dateText, $timeText", style = Type.body, color = palette.ink)
                            Text(Format.amount(entry.amount, draft.unit, draft.customUnit), style = Type.small, color = palette.muted)
                        }
                        IconCircleButton(
                            RexiIcons.Close,
                            "Remove dose from $dateText at $timeText",
                            { onRemove(entry) },
                            size = 48.dp,
                        )
                    }
                }
            }
        }
    }
    when (picker) {
        PastField.DATE -> {
            PastDateDialog(date, oldest, today, {
                day = it.toEpochDay()
                dismissPicker()
            }, dismissPicker)
        }

        PastField.TIME -> {
            TimeDialog("When did you take it?", time, {
                time = it
                dismissPicker()
            }, dismissPicker)
        }

        PastField.AMOUNT -> {
            NumberDialog("How much did you take?", amount, Format.unitWord(draft.unit, draft.customUnit), {
                amount = it
                dismissPicker()
            }, dismissPicker)
        }

        null, PastField.SLOT -> {}
    }
}

private enum class PastField {
    DATE,
    TIME,
    SLOT,
    AMOUNT,
}

@Composable
private fun PastDoseField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Type.label, color = palette.muted)
        SoftButton(value, onClick, modifier = Modifier.fillMaxWidth(), height = 48.dp, background = palette.well)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDateDialog(
    date: LocalDate,
    oldest: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectable: (LocalDate) -> Boolean = { true },
) {
    val palette = LocalPalette.current
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            yearRange = oldest.year..today.year,
            selectableDates =
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val candidate = utcDate(utcTimeMillis)
                        return candidate in oldest..today && selectable(candidate)
                    }
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onPick(utcDate(it)) } },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("Done", color = palette.ink)
            }
        },
        modifier = modifier,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.muted) } },
        colors = DatePickerDefaults.colors(containerColor = palette.surface),
    ) {
        DatePicker(
            state,
            colors =
                DatePickerDefaults.colors(
                    containerColor = palette.surface,
                    selectedDayContainerColor = palette.primary,
                    selectedDayContentColor = palette.onPrimary,
                ),
        )
    }
}

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
