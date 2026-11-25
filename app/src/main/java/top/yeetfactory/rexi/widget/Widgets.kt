package top.yeetfactory.rexi.widget

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import top.yeetfactory.rexi.MainActivity
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DayClock
import top.yeetfactory.rexi.core.DayPart
import top.yeetfactory.rexi.core.DayView
import top.yeetfactory.rexi.core.DoseSlot
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.DoseStatus
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.core.Logged
import top.yeetfactory.rexi.core.MedColor
import top.yeetfactory.rexi.core.MedKind
import top.yeetfactory.rexi.core.PartState
import top.yeetfactory.rexi.core.PartView
import top.yeetfactory.rexi.data.Medicine
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.data.Slot
import top.yeetfactory.rexi.ui.DarkPalette
import top.yeetfactory.rexi.ui.LightPalette
import top.yeetfactory.rexi.ui.MedArt
import java.time.LocalDate
import java.time.ZoneId
import androidx.glance.color.ColorProvider as DayNight

/** The home screen widgets. Both read the same day as Today, and both can take a dose without opening the app. */
object Widgets {
    // A widget's session can outlive one update, and its content would keep showing what it loaded first.
    // Each change moves this number, and a running widget reloads when it does.
    private val version = MutableStateFlow(0)
    internal val changes: StateFlow<Int> = version

    /**
     * Gives the launcher's widget picker a real preview of each widget instead of the app icon. Android limits how
     * often an app may do this, and the previews only change with the app, so it happens once per install or update.
     */
    suspend fun publishPreviews(context: Context) {
        val installed =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                .lastUpdateTime
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong(PREVIEWS_FOR, 0) == installed) return
        val manager = GlanceAppWidgetManager(context)
        val results = listOf(TodayWidgetReceiver::class, NextWidgetReceiver::class).map { manager.setWidgetPreviews(it) }
        // A refused try is simply made again the next time the app starts.
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs.edit { putLong(PREVIEWS_FOR, installed) }
        } else {
            Log.w(TAG, "Widget previews weren't published, results $results. Trying again next time.")
        }
    }

    private const val TAG = "RexiWidgets"
    private const val PREFS = "widgets"
    private const val PREVIEWS_FOR = "previewsFor"

    suspend fun update(context: Context) {
        version.update { it + 1 }
        runCatching {
            TodayWidget().updateAll(context)
            NextWidget().updateAll(context)
        }
    }
}

private data class WidgetData(
    val date: LocalDate,
    val view: DayView,
    val medicines: Map<Long, MedicineWithSlots>,
    val use24: Boolean,
    val hideNames: Boolean,
    /** When the dose that's due was snoozed until, if it still is. */
    val snoozedUntil: Long?,
)

private suspend fun load(context: Context): WidgetData {
    val app = context.applicationContext as RexiApp
    val date = app.repository.today()
    val book = app.doses.book(date)
    val snoozed =
        book.view.due
            ?.let { app.settings.snoozedUntil(date, it.part) }
            ?.takeIf { it > System.currentTimeMillis() }
    return WidgetData(date, book.view, book.medicines, DateFormat.is24HourFormat(context), app.settings.value.hideNames, snoozed)
}

/**
 * A believable evening for the widget picker: morning and afternoon taken, the evening dose due, night still to come.
 * It goes through [DayBook] like the real thing, so the preview can't drift from what the widget shows.
 */
private fun previewData(context: Context): WidgetData {
    val today = LocalDate.now()
    val start = today.minusDays(PREVIEW_HISTORY_DAYS).toEpochDay()

    fun sample(
        id: Long,
        name: String,
        kind: MedKind,
        color: MedColor,
        part: DayPart,
    ) = MedicineWithSlots(Medicine(id = id, name = name, kind = kind, color1 = color, startDay = start), listOf(Slot(id, part, 1.0)))
    val medicines =
        listOf(
            sample(1, "Vitamin D3", MedKind.SOFTGEL, MedColor.AMBER, DayPart.MORNING),
            sample(2, "Lisinopril", MedKind.TABLET, MedColor.PINK, DayPart.AFTERNOON),
            sample(3, "Metformin", MedKind.OVAL, MedColor.WHITE, DayPart.EVENING),
            sample(4, "Atorvastatin", MedKind.TABLET, MedColor.WHITE, DayPart.NIGHT),
        )
    val clock = DayClock(DayPart.entries.associateWith { it.defaultMinutes }, PREVIEW_MISSED_AFTER_MINUTES)
    val zone = ZoneId.systemDefault()
    val taken =
        listOf(1L to DayPart.MORNING, 2L to DayPart.AFTERNOON).map { (id, part) ->
            Logged(
                id,
                today,
                part,
                DoseStatus.TAKEN,
                clock
                    .dueAt(today, part)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
                1.0,
            )
        }
    val now = clock.dueAt(today, DayPart.EVENING).plusMinutes(PREVIEW_MINUTES_PAST_DUE)
    val view = DayBook.build(today, medicines.map { it.plan(zone) }, taken, now, clock)
    return WidgetData(
        today,
        view,
        medicines.associateBy { it.id },
        DateFormat.is24HourFormat(context),
        hideNames = false,
        snoozedUntil = null,
    )
}

private const val PREVIEW_HISTORY_DAYS = 30L
private const val PREVIEW_MISSED_AFTER_MINUTES = 180
private const val PREVIEW_MINUTES_PAST_DUE = 5L

/** The widget's data, loaded again whenever something in the app changes. */
@Composable
private fun rememberData(
    context: Context,
    first: WidgetData,
): WidgetData {
    val version by Widgets.changes.collectAsState()
    var data by remember { mutableStateOf(first) }
    LaunchedEffect(version) { data = load(context) }
    return data
}

/** "Taken", "Skipped" or "1 of 2 taken", the way the app's own compartments say it. */
private fun outcome(part: PartView): String {
    val taken = part.doses.count { it.state == DoseState.TAKEN }
    return when {
        taken == part.doses.size -> "Taken"
        part.doses.all { it.state == DoseState.SKIPPED } -> "Skipped"
        else -> "$taken of ${part.doses.size}"
    }
}

/** "Due now", or "Snoozed until 3:31 PM" while a snooze is running. */
private fun dueWord(data: WidgetData): String =
    data.snoozedUntil?.let { "Snoozed until ${Format.time(it, ZoneId.systemDefault(), data.use24)}" } ?: "Due now"

private object Colors {
    val surface = pair(LightPalette.surface, DarkPalette.surface)
    val ink = pair(LightPalette.ink, DarkPalette.ink)
    val muted = pair(LightPalette.muted, DarkPalette.muted)
    val primary = pair(LightPalette.primary, DarkPalette.primary)
    val onPrimary = pair(LightPalette.onPrimary, DarkPalette.onPrimary)
    val alert = pair(LightPalette.alert, DarkPalette.alert)

    fun background(part: DoseSlot) = pair(LightPalette.tint(part).background, DarkPalette.tint(part).background)

    fun label(part: DoseSlot) = pair(LightPalette.tint(part).label, DarkPalette.tint(part).label)

    fun ink(part: DoseSlot) = pair(LightPalette.tint(part).ink, DarkPalette.tint(part).ink)

    private fun pair(
        day: Color,
        night: Color,
    ): ColorProvider = DayNight(day = day, night = night)
}

private val DAY_KEY = ActionParameters.Key<Long>("day")
private val PART_KEY = ActionParameters.Key<String>("part")

/** The widget's Take button. */
class TakeAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val app = context.applicationContext as RexiApp
        val day = parameters[DAY_KEY] ?: return
        val part = parameters[PART_KEY]?.let(DoseSlot::fromKey) ?: return
        app.doses.take(LocalDate.ofEpochDay(day), part)
        Widgets.update(context)
    }
}

class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val first = load(context)
        provideContent { TodayContent(rememberData(context, first)) }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val sample = previewData(context)
        provideContent { TodayContent(sample) }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class NextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val first = load(context)
        provideContent { NextContent(rememberData(context, first)) }
    }

    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val sample = previewData(context)
        provideContent { NextContent(sample) }
    }
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}

private fun style(
    size: Int,
    color: ColorProvider,
    bold: Boolean = false,
) = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun names(
    part: PartView,
    data: WidgetData,
): String =
    if (data.hideNames) {
        "${part.part.label(data.use24)} dose"
    } else {
        part.doses.mapNotNull { data.medicines[it.medId]?.medicine?.title }.joinToString(", ")
    }

@Composable
private fun TodayContent(data: WidgetData) {
    val view = data.view
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(Colors.surface)
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = style(15, Colors.ink, bold = true))
            Spacer(GlanceModifier.defaultWeight())
            if (view.total > 0) Text("${view.taken} of ${view.total} taken", style = style(12, Colors.muted))
        }
        Spacer(GlanceModifier.height(10.dp))
        if (view.parts.isEmpty()) {
            Text(if (data.medicines.isEmpty()) "Add a medicine in Rexi" else "Nothing scheduled today", style = style(13, Colors.muted))
            return@Column
        }
        val displayed = (listOfNotNull(view.due, view.next) + view.parts).distinct().take(4).sortedBy { it.minutes }
        Row(GlanceModifier.fillMaxWidth()) {
            displayed.forEachIndexed { index, part ->
                if (index > 0) Spacer(GlanceModifier.width(6.dp))
                Column(
                    GlanceModifier
                        .defaultWeight()
                        .height(56.dp)
                        .background(Colors.background(part.part))
                        .cornerRadius(16.dp)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                ) {
                    Text(part.part.label(data.use24), style = style(10, Colors.label(part.part), bold = true), maxLines = 1)
                    val status =
                        when (part.state) {
                            PartState.DONE -> outcome(part)
                            PartState.DUE -> if (data.snoozedUntil != null) "Snoozed" else "Due"
                            PartState.MISSED -> "Missed"
                            PartState.LATER -> if (part.part is DayPart) Format.time(part.minutes, data.use24) else "Later"
                        }
                    Text(
                        status,
                        style =
                            style(
                                13,
                                if (part.state ==
                                    PartState.MISSED
                                ) {
                                    Colors.alert
                                } else {
                                    Colors.ink(part.part)
                                },
                                bold = true,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        val due = view.due
        val next = view.next
        when {
            due != null -> {
                DueRow(due, data)
            }

            next != null -> {
                Text(
                    "Next: ${names(next, data)} at ${Format.time(next.minutes, data.use24)}",
                    style = style(13, Colors.muted),
                    maxLines = 1,
                )
            }

            else -> {
                Text("All done for today", style = style(13, Colors.ink, bold = true))
            }
        }
    }
}

@Composable
private fun DueRow(
    part: PartView,
    data: WidgetData,
) {
    val first = part.pending.firstOrNull()?.let { data.medicines[it.medId] }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (first != null && !data.hideNames) {
            val m = first.medicine
            Image(
                ImageProvider(MedArt.bitmap(m.kind, m.color1, m.color2, ICON_PX)),
                contentDescription = null,
                modifier = GlanceModifier.size(34.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(names(part, data), style = style(14, Colors.ink, bold = true), maxLines = 1)
            val amount = if (part.pending.size == 1 && first != null) ", ${first.medicine.amount(part.pending.first().amount)}" else ""
            Text(dueWord(data) + amount, style = style(12, Colors.muted), maxLines = 1)
        }
        TakeButton(part, data, "Take")
    }
}

@Composable
private fun TakeButton(
    part: PartView,
    data: WidgetData,
    label: String,
    modifier: GlanceModifier = GlanceModifier,
    onTint: Boolean = false,
) {
    // On Night's navy the ink button disappears, so there it turns light, like the due compartment in the app.
    val light = onTint && part.part == DayPart.NIGHT
    Box(
        modifier
            .background(if (light) Colors.ink(part.part) else Colors.primary)
            .cornerRadius(22.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .clickable(actionRunCallback<TakeAction>(actionParametersOf(DAY_KEY to data.date.toEpochDay(), PART_KEY to part.part.key))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style(14, if (light) Colors.background(part.part) else Colors.onPrimary, bold = true))
    }
}

@Composable
private fun NextContent(data: WidgetData) {
    val view = data.view
    val shown = view.due ?: view.next
    if (shown == null) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Colors.surface)
                .cornerRadius(28.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Rexi", style = style(12, Colors.muted, bold = true))
            Spacer(GlanceModifier.defaultWeight())
            Text(if (view.total == 0) "Nothing due today" else "All done for today", style = style(16, Colors.ink, bold = true))
            Text("Next: tomorrow", style = style(12, Colors.muted))
        }
        return
    }
    val due = shown.state == PartState.DUE
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(if (due) Colors.background(shown.part) else Colors.surface)
            .cornerRadius(28.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        val heading =
            if (due && data.snoozedUntil != null) {
                if (shown.part is DayPart) "${shown.part.label}, snoozed" else "Snoozed"
            } else if (due) {
                if (shown.part is DayPart) shown.part.label else "Due now"
            } else {
                "Next"
            }
        Text(heading, style = style(12, if (due) Colors.label(shown.part) else Colors.muted, bold = true))
        Text(Format.time(shown.minutes, data.use24), style = style(26, if (due) Colors.ink(shown.part) else Colors.ink, bold = true))
        Text(names(shown, data), style = style(14, if (due) Colors.ink(shown.part) else Colors.ink), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        if (due) {
            TakeButton(shown, data, "Take", GlanceModifier.fillMaxWidth(), onTint = true)
        } else if (shown.part is DayPart) {
            Text(shown.part.label, style = style(12, Colors.muted))
        }
    }
}

private const val ICON_PX = 96
