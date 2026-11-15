package top.yeetfactory.rexi.report

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.format.DateFormat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yeetfactory.rexi.R
import top.yeetfactory.rexi.RexiApp
import top.yeetfactory.rexi.core.DayBook
import top.yeetfactory.rexi.core.DoseState
import top.yeetfactory.rexi.core.Food
import top.yeetfactory.rexi.core.Format
import top.yeetfactory.rexi.data.MedicineWithSlots
import top.yeetfactory.rexi.ui.shortSchedule
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A PDF of the last 30 days to show a doctor. It's made on the phone and shared through the system's share sheet,
 * so the person chooses where it goes. It says plainly that "taken" means logged, which is all an app can know.
 */
object Report {
    private const val DAYS = 30
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val MAX_LISTED = 20

    private class Tally(
        var taken: Int = 0,
        var due: Int = 0,
        val misses: MutableList<String> = mutableListOf(),
    )

    suspend fun make(app: RexiApp): File =
        withContext(Dispatchers.IO) {
            val today = app.repository.today()
            val from = today.minusDays(DAYS - 1L)
            val zone = ZoneId.systemDefault()
            val medicines = app.repository.dao.allMedicinesWithSlots()
            val logs =
                app.repository.dao
                    .logs(from.toEpochDay(), today.toEpochDay())
                    .map { it.logged() }
            val settings = app.settings.value
            val now = LocalDateTime.now(zone)
            val plans = medicines.map { it.plan() }
            val tallies = mutableMapOf<Long, Tally>()
            (0 until DAYS).map { from.plusDays(it.toLong()) }.forEach { date ->
                DayBook.build(date, plans, logs, now, settings.clock).parts.forEach { part ->
                    part.doses.filter { !it.pending }.forEach { dose ->
                        val tally = tallies.getOrPut(dose.medId) { Tally() }
                        tally.due++
                        if (dose.state == DoseState.TAKEN) {
                            tally.taken++
                        } else {
                            val word = if (dose.state == DoseState.SKIPPED) "skipped" else "missed"
                            val reason = if (dose.reason.isNotBlank()) ", ${dose.reason.lowercase()}" else ""
                            tally.misses += "${Format.day(date)} ${part.part.label.lowercase()} ($word$reason)"
                        }
                    }
                }
            }
            val asNeeded = logs.filter { it.part == null && it.status == top.yeetfactory.rexi.core.DoseStatus.TAKEN }.groupBy { it.medId }
            val included =
                medicines.filter { med ->
                    !med.medicine.archived || (tallies[med.id]?.due ?: 0) > 0 || asNeeded.containsKey(med.id)
                }

            val document = PdfDocument()
            val writer = Writer(app, document)
            writer.line("Medication report", writer.title, gapAfter = 6f)
            writer.line("Last $DAYS days: ${Format.range(from, today)} ${today.year}", writer.body)
            writer.line("Made on ${Format.fullDate(today)} with Rexi.", writer.muted, gapAfter = 18f)
            if (included.isEmpty()) writer.line("No medicines were recorded in this period.", writer.body)
            included.forEach { med -> writer.medicine(med, tallies[med.id], asNeeded[med.id].orEmpty().size, today) }
            writer.line(
                "Rexi records what the person logged on their own phone. A dose marked as taken was logged, not observed.",
                writer.muted,
                gapBefore = 10f,
            )
            writer.finish()

            val folder = File(app.cacheDir, "reports").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val file = File(folder, "rexi-report-$today.pdf")
            file.outputStream().use { document.writeTo(it) }
            document.close()
            file
        }

    fun share(
        context: Context,
        file: File,
    ) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "Medication report")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun Writer.medicine(
        med: MedicineWithSlots,
        tally: Tally?,
        asNeededCount: Int,
        today: LocalDate,
    ) {
        val m = med.medicine
        val stopped = if (m.archived) " (stopped)" else ""
        ensure(90f)
        line(listOf(m.title, m.strengthText).filter { it.isNotBlank() }.joinToString(" ") + stopped, heading, gapAfter = 4f)
        if (m.purpose.isNotBlank()) line("For ${m.purpose.lowercase()}.", body)
        if (med.asNeeded) {
            val rules =
                listOfNotNull(
                    if (m.minGapHours > 0) "at least ${m.minGapHours} hours apart" else null,
                    if (m.maxPerDay > 0) "up to ${m.maxPerDay} a day" else null,
                ).joinToString(", ")
            line("Taken only when needed${if (rules.isNotEmpty()) ", $rules" else ""}.", body)
            line(if (asNeededCount == 1) "Taken once in this period." else "Taken $asNeededCount times in this period.", body)
        } else {
            val settings = app.settings.value
            // The report follows the phone's clock like the rest of the app, so it matches what the person sees.
            val use24 = DateFormat.is24HourFormat(app)
            val parts =
                med.slots.sortedBy { settings.minutes(it.part) }.joinToString(", ") {
                    "${m.amount(it.amount)} ${Format.inPart(it.part)} (${Format.time(settings.minutes(it.part), use24)})"
                }
            val food = if (m.food != Food.ANY) ", ${m.food.label.lowercase()}" else ""
            line("${shortSchedule(med, today)}: $parts$food.", body)
            val due = tally?.due ?: 0
            val taken = tally?.taken ?: 0
            if (due > 0) {
                // Rounded down on purpose, so a single missed dose never shows as 100%.
                val percent = taken * 100 / due
                line("Taken $taken of $due doses ($percent%).", body)
            } else {
                line("No doses were due in this period.", body)
            }
            val misses = tally?.misses.orEmpty()
            if (misses.isNotEmpty()) {
                val shown = misses.takeLast(MAX_LISTED)
                val more = if (misses.size > shown.size) " Plus ${misses.size - shown.size} earlier." else ""
                line("Not taken: ${shown.joinToString("; ")}.$more", body)
            }
        }
        if (m.notes.isNotBlank()) line("Notes: ${m.notes}", muted)
        space(14f)
    }

    private class Writer(
        val app: RexiApp,
        private val document: PdfDocument,
    ) {
        private val regular: Typeface = ResourcesCompat.getFont(app, R.font.figtree) ?: Typeface.DEFAULT
        private val bold: Typeface = Typeface.create(regular, 700, false)
        val title = paint(22f, bold, INK)
        val heading = paint(13.5f, bold, INK)
        val body = paint(10.5f, regular, INK)
        val muted = paint(9.5f, regular, MUTED)
        private var number = 0
        private var page: PdfDocument.Page = newPage()
        private var y = MARGIN

        private fun paint(
            size: Float,
            face: Typeface,
            color: Int,
        ) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = face
            this.color = color
        }

        private fun newPage(): PdfDocument.Page {
            number++
            return document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create())
        }

        fun ensure(space: Float) {
            if (y + space > PAGE_HEIGHT - MARGIN) {
                footer()
                document.finishPage(page)
                page = newPage()
                y = MARGIN
            }
        }

        fun space(amount: Float) {
            y += amount
        }

        /** Writes [text], wrapping it to the page width and starting new pages as needed. */
        fun line(
            text: String,
            paint: Paint,
            gapBefore: Float = 0f,
            gapAfter: Float = 3f,
        ) {
            y += gapBefore
            val width = PAGE_WIDTH - MARGIN * 2
            val words = text.split(" ")
            var current = ""
            val lines = mutableListOf<String>()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) > width && current.isNotEmpty()) {
                    lines += current
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) lines += current
            val height = paint.textSize * LINE_HEIGHT
            lines.forEach {
                ensure(height)
                y += height
                page.canvas.drawText(it, MARGIN, y - paint.textSize * 0.3f, paint)
            }
            y += gapAfter
        }

        private fun footer() {
            page.canvas.drawText("Rexi medication report, page $number", MARGIN, PAGE_HEIGHT - MARGIN / 2, muted)
        }

        fun finish() {
            footer()
            document.finishPage(page)
        }

        companion object {
            private const val INK = 0xFF12161C.toInt()
            private const val MUTED = 0xFF555C66.toInt()
            private const val LINE_HEIGHT = 1.45f
        }
    }
}
