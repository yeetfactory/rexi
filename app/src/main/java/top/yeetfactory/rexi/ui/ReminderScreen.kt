package top.yeetfactory.rexi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Schedule
import top.yeetfactory.rexi.remind.DoseActions
import java.time.LocalDate

/** The full screen reminder, shown over the lock screen in the colours of its part of the day. */
@Composable
internal fun ReminderScreen(
    app: RexiApp,
    day: LocalDate,
    part: DayPart,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val tint = palette.tint(part)
    val scope = rememberCoroutineScope()
    val use24 = rememberUse24h()
    var book by remember(day, part) { mutableStateOf<DoseActions.Book?>(null) }
    LaunchedEffect(day, part) { book = app.doses.book(day) }
    val current = book
    val compartment = current?.view?.part(part)
    val doses = compartment?.pending.orEmpty()
    val finish by rememberUpdatedState(onFinish)
    LaunchedEffect(current) { if (current != null && doses.isEmpty()) finish() }
    val night = part == DayPart.NIGHT
    val hide = app.settings.value.hideNames

    Column(
        modifier
            .fillMaxSize()
            .background(tint.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp, bottom = 20.dp),
    ) {
        Text("${part.label} dose", style = Type.body.copy(fontWeight = Type.name.fontWeight), color = tint.label)
        Text(
            Format.time(compartment?.minutes ?: app.settings.value.minutes(part), use24),
            style = Type.clock,
            color = tint.ink,
            maxLines = 1,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val meds = doses.mapNotNull { dose -> current?.medicines?.get(dose.medId)?.let { it to dose.amount } }
            val single = meds.singleOrNull()
            when {
                hide -> {
                    Text("Time for your dose", style = Type.display, color = tint.ink)
                }

                single != null -> {
                    val m = single.first.medicine
                    MedIcon(m.kind, m.color1, m.color2, size = 124.dp, modifier = Modifier.padding(start = 0.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(
                        m.title,
                        style = Type.display.copy(fontSize = Type.display.fontSize * 1.3f, fontWeight = Type.title.fontWeight),
                        color = tint.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOf(m.strengthText, m.amount(single.second))
                            .filter {
                                it.isNotBlank()
                            }.joinToString(", "),
                        style = Type.heading.copy(fontWeight = Type.text.fontWeight),
                        color = tint.text,
                    )
                    val extras =
                        listOfNotNull(
                            Schedule.courseDay(single.first.plan(), day)?.let { "Day $it of ${m.courseDays}" },
                            m.food.takeIf { it != Food.ANY }?.label,
                        )
                    if (extras.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(extras.joinToString(". ") + ".", style = Type.text, color = tint.text)
                    }
                }

                else -> {
                    Text("${meds.size} medicines", style = Type.display, color = tint.ink)
                    Spacer(Modifier.height(14.dp))
                    meds.forEach { (med, amount) ->
                        val m = med.medicine
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            MedIcon(m.kind, m.color1, m.color2, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(m.title, style = Type.name, color = tint.ink)
                                Text(m.amount(amount), style = Type.small, color = tint.text)
                            }
                        }
                    }
                }
            }
        }
        val swallowed =
            doses.all {
                current
                    ?.medicines
                    ?.get(it.medId)
                    ?.medicine
                    ?.unit
                    ?.swallowed != false
            }
        PrimaryButton(
            if (swallowed) "I've taken it" else "Done",
            {
                scope.launch {
                    app.doses.take(day, part)
                    onFinish()
                }
            },
            icon = RexiIcons.Check,
            height = 64.dp,
            modifier = Modifier.fillMaxWidth(),
            container = if (night) tint.ink else palette.primary,
            content = if (night) tint.background else palette.onPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10 to "10 min", 30 to "30 min", 60 to "1 hour").forEach { (minutes, label) ->
                OutlineButton(
                    label,
                    {
                        scope.launch {
                            app.doses.snooze(day, part, minutes)
                            onFinish()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    color = tint.ink,
                    border = tint.ink.copy(alpha = 0.25f),
                )
            }
        }
        TextAction(
            "Skip this dose",
            {
                scope.launch {
                    app.doses.skip(day, part)
                    onFinish()
                }
            },
            color = tint.label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
