package top.yeetfactory.rexi.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The app's own line icons, drawn on a 24 point grid with round ends, so they match the designs exactly. */
object RexiIcons {
    val Today = line("Today", "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z M3.5 10h17 M8 3v4 M16 3v4")
    val Medicines = line("Medicines", "M10.5 3.8a5 5 0 0 1 7.1 7.1l-6.7 6.7a5 5 0 0 1-7.1-7.1z M7.2 7.2l7.1 7.1")
    val History =
        line(
            "History",
            "M6 4h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z " +
                "M15 4h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z " +
                "M6 13h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z " +
                "M15 13h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z",
        )
    val Settings = line("Settings", "M4 7h9 M17 7h3 M4 17h3 M11 17h9 M15 4.5v5 M9 14.5v5")
    val Check = line("Check", "M5 12.5l4.5 4.5L19 7.5", width = 2.4f)
    val Cross = line("Cross", "M7 7l10 10 M17 7L7 17", width = 2.4f)
    val Close = line("Close", "M6 6l12 12 M18 6L6 18", width = 2.2f)
    val Back = line("Back", "M15 6l-6 6 6 6", width = 2.2f)
    val Forward = line("Forward", "M9 6l6 6-6 6", width = 2.2f)
    val Chevron = line("Chevron", "M9 6l6 6-6 6", width = 2f)
    val Plus = line("Plus", "M12 5v14 M5 12h14", width = 2.4f)
    val Minus = line("Minus", "M6 12h12", width = 2.4f)
    val Clock = line("Clock", "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M12 7v5l3 2", width = 2f)
    val Lock =
        line(
            "Lock",
            "M7.5 10.5h9a2.5 2.5 0 0 1 2.5 2.5v5a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 5 18v-5a2.5 2.5 0 0 1 2.5-2.5z M8.5 10.5V7.5a3.5 3.5 0 0 1 7 0v3",
        )
    val Fingerprint =
        line(
            "Fingerprint",
            "M5.5 9.5a7.5 7.5 0 0 1 13 0 M7.5 18.5c.6-1.6.9-3.3.9-5a3.6 3.6 0 0 1 7.2 0c0 1.2-.1 2.4-.3 3.5 " +
                "M12 13.5c0 2.6-.5 5-1.6 7 M4.5 14.5c.4-.9.5-1.9.5-2.9 M14.6 20.3c.3-.6.5-1.2.7-1.8",
            width = 1.8f,
        )
    val Download = line("Download", "M12 4v11 M7.5 10.5L12 15l4.5-4.5 M5 19.5h14", width = 2.2f)
    val Bell = line("Bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z M10 20.5a2 2 0 0 0 4 0")
    val Globe =
        line(
            "Globe",
            "M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z M3 12h18 M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z",
            width = 1.8f,
        )

    private fun line(
        name: String,
        path: String,
        width: Float = 1.9f,
    ): ImageVector =
        ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
}
