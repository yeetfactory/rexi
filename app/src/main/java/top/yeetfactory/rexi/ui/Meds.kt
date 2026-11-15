package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Frequency
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.core.SupplyMode
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.remind.Reminders
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun MedsScreen(
    app: RexiApp,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onLogDose: (Long) -> Unit,
    onRefill: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val medicines by remember { app.repository.dao.watchMedicines() }.collectAsStateWithLifecycle(null)
    val today = app.repository.today()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        ScreenTitle("Medicines") {
            PrimaryButton("Add", onAdd, icon = RexiIcons.Plus, height = 44.dp)
        }
        val meds = medicines ?: return@Column
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (meds.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                Panel {
                    Text("No medicines yet", style = Type.name, color = palette.ink)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tablets, sprays, drops, injections, creams: anything you take can go in. Tap Add to start.",
                        style = Type.small,
                        color = palette.muted,
                    )
                }
                return@Column
            }
            val low = meds.firstOrNull { !it.medicine.paused && Reminders.supply(it, today).low }
            if (low != null) {
                val info = Reminders.supply(low, today)
                Spacer(Modifier.height(16.dp))
                Banner(
                    title = "${low.medicine.title} is running low",
                    text = lowText(low, info.daysLeft ?: 0),
                    action = {
                        SoftButton(
                            if (low.medicine.supplyMode ==
                                SupplyMode.COUNT
                            ) {
                                "Refilled"
                            } else {
                                "New pack"
                            },
                            { onRefill(low.id) },
                        )
                    },
                )
            }
            val active = meds.filter { !it.medicine.paused }
            val groups =
                listOf(
                    "Every day" to active.filter { it.medicine.frequency == Frequency.EVERY_DAY },
                    "Some days" to active.filter { it.medicine.frequency in listOf(Frequency.SOME_DAYS, Frequency.EVERY_FEW_DAYS) },
                    "Short course" to active.filter { it.medicine.frequency == Frequency.COURSE },
                    "When needed" to active.filter { it.medicine.frequency == Frequency.AS_NEEDED },
                    "Paused" to meds.filter { it.medicine.paused },
                ).filter { it.second.isNotEmpty() }
            groups.forEach { (title, items) ->
                Spacer(Modifier.height(16.dp))
                SectionLabel(title)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(palette.surface)
                        .padding(horizontal = 4.dp),
                ) {
                    items.forEachIndexed { index, med ->
                        if (index > 0) RowLine()
                        MedRow(
                            med = med,
                            today = today,
                            onClick = { if (med.asNeeded && !med.medicine.paused) onLogDose(med.id) else onOpen(med.id) },
                            onOpen = { onOpen(med.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedRow(
    med: MedicineWithSlots,
    today: LocalDate,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val medicine = med.medicine
    val info = Reminders.supply(med, today)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = if (med.asNeeded) onOpen else onClick)
            .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(medicine.kind, medicine.color1, medicine.color2, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    medicine.title,
                    style = Type.body,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (medicine.strengthText.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(medicine.strengthText, style = Type.text, color = palette.muted, maxLines = 1)
                }
            }
            Spacer(Modifier.height(5.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    medicine.paused -> {
                        SoftTag("Reminders paused")
                    }

                    med.asNeeded -> {
                        SoftTag(if (medicine.maxPerDay > 0) "Up to ${medicine.maxPerDay} a day" else "When needed")
                    }

                    else -> {
                        med.slots.sortedBy { it.part.ordinal }.forEach { PartTag(it.part) }
                        if (medicine.frequency != Frequency.EVERY_DAY) SoftTag(shortSchedule(med, today))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            med.asNeeded && !medicine.paused -> {
                Box(
                    Modifier
                        .height(40.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, palette.outline, CircleShape)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Log", style = Type.label.copy(fontSize = Type.small.fontSize), color = palette.ink) }
            }

            medicine.frequency == Frequency.COURSE -> {
                Text(
                    Schedule.courseDay(med.plan(), today)?.let { "Day $it of ${medicine.courseDays}" } ?: "",
                    style = Type.small,
                    color = palette.muted,
                )
            }

            medicine.supplyMode == SupplyMode.COUNT -> {
                Text(
                    "${Format.number(medicine.supplyCount)} left",
                    style = Type.small.copy(fontWeight = if (info.low) FontWeight.Bold else FontWeight.Normal),
                    color = if (info.low) palette.alert else palette.muted,
                )
            }

            medicine.supplyMode == SupplyMode.DURATION -> {
                Text(
                    "${info.daysLeft ?: 0} days left",
                    style = Type.small.copy(fontWeight = if (info.low) FontWeight.Bold else FontWeight.Normal),
                    color = if (info.low) palette.alert else palette.muted,
                )
            }

            else -> {
                Icon(RexiIcons.Chevron, contentDescription = null, tint = palette.faint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

internal fun lowText(
    med: MedicineWithSlots,
    daysLeft: Int,
): String {
    val m = med.medicine
    val days = if (daysLeft == 1) "1 day" else "$daysLeft days"
    return when (m.supplyMode) {
        SupplyMode.COUNT -> "${Format.number(m.supplyCount)} ${m.unitWord(m.supplyCount)} left, about $days"
        else -> "About $days left in this pack"
    }
}

/** "Mon, Wed and Fri", "Every other day", or a course's end date. */
internal fun shortSchedule(
    med: MedicineWithSlots,
    today: LocalDate,
): String {
    val m = med.medicine
    return when (m.frequency) {
        Frequency.EVERY_DAY -> {
            "Every day"
        }

        Frequency.SOME_DAYS -> {
            weekdays(m.daysMask)
        }

        Frequency.EVERY_FEW_DAYS -> {
            Format.everyDays(m.everyDays)
        }

        Frequency.COURSE -> {
            val end = Schedule.courseEnd(med.plan())
            if (end < today) "Ended ${Format.day(end)}" else "Ends ${Format.day(end)}"
        }

        Frequency.AS_NEEDED -> {
            "When needed"
        }
    }
}

internal fun weekdays(mask: Int): String {
    val days =
        DayOfWeek.entries
            .filter { mask and (1 shl (it.value - 1)) != 0 }
            .map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    return when {
        days.size == 7 -> "Every day"
        days.isEmpty() -> "No days chosen"
        days.size == 1 -> days.first()
        else -> days.dropLast(1).joinToString(", ") + " and " + days.last()
    }
}
