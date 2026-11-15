package top.yeetfactory.rexi.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import top.yeetfactory.rexi.R
import top.yeetfactory.rexi.core.DayPart

/** The colours of one part of the day. Each compartment, tag and chart cell uses its part's set. */
@Immutable
data class Tint(
    val background: Color,
    val label: Color,
    val text: Color,
    /** Strong enough to stand on white, for filled chart cells. */
    val strong: Color,
    val ink: Color,
)

@Immutable
data class Palette(
    val ground: Color,
    val surface: Color,
    /** The soft fill inside white cards, such as a stepper's buttons. */
    val well: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val primary: Color,
    val onPrimary: Color,
    /** The current tab or day. In dark mode a large white pill glares, so it gets a raised grey instead. */
    val selected: Color,
    val onSelected: Color,
    /** The Undo on a snackbar, which sits on [primary]. */
    val onPrimaryAction: Color,
    val outline: Color,
    val alert: Color,
    val alertText: Color,
    val alertSoft: Color,
    val scrim: Color,
    val morning: Tint,
    val afternoon: Tint,
    val evening: Tint,
    val night: Tint,
    /**
     * Night while it's still hours away. In light mode the navy compartment is the darkest thing on the screen, so
     * it would pull the eye away from the dose that's due. It turns navy once night comes.
     */
    val nightLater: Tint,
) {
    fun tint(part: DayPart): Tint =
        when (part) {
            DayPart.MORNING -> morning
            DayPart.AFTERNOON -> afternoon
            DayPart.EVENING -> evening
            DayPart.NIGHT -> night
        }

    /** A compartment on Today. Only a later Night differs from its tag colours. */
    fun compartment(
        part: DayPart,
        later: Boolean,
    ): Tint = if (later && part == DayPart.NIGHT) nightLater else tint(part)
}

private const val THEME_FADE_MS = 420

val LightPalette =
    Palette(
        ground = Color(0xFFEEF0F3),
        surface = Color(0xFFFFFFFF),
        well = Color(0xFFEEF0F3),
        line = Color(0xFFDCE0E5),
        ink = Color(0xFF12161C),
        muted = Color(0xFF555C66),
        faint = Color(0xFF7A818B),
        primary = Color(0xFF12161C),
        onPrimary = Color(0xFFFFFFFF),
        selected = Color(0xFF12161C),
        onSelected = Color(0xFFFFFFFF),
        onPrimaryAction = Color(0xFFB9C6E4),
        outline = Color(0x3312161C),
        alert = Color(0xFFB23A14),
        alertText = Color(0xFF8F2D0E),
        alertSoft = Color(0xFFFBE3D9),
        scrim = Color(0x7312161C),
        morning = Tint(Color(0xFFFBE9B7), Color(0xFF6A4B00), Color(0xFF4A4430), Color(0xFFA97B00), Color(0xFF12161C)),
        afternoon = Tint(Color(0xFFD6E6F5), Color(0xFF1D4A73), Color(0xFF34414F), Color(0xFF2F6FA8), Color(0xFF12161C)),
        evening = Tint(Color(0xFFF7D9C9), Color(0xFF84391A), Color(0xFF4D3A31), Color(0xFFB2532A), Color(0xFF12161C)),
        night = Tint(Color(0xFF1F2A44), Color(0xFFB9C6E4), Color(0xFFD5DCEB), Color(0xFF1F2A44), Color(0xFFFFFFFF)),
        nightLater = Tint(Color(0xFFE1E5F0), Color(0xFF1F2A44), Color(0xFF3B4559), Color(0xFF1F2A44), Color(0xFF12161C)),
    )

private val DarkNight = Tint(Color(0xFF23273B), Color(0xFFB7C2EE), Color(0xFFC8CFE6), Color(0xFF7C90D2), Color(0xFFFFFFFF))

/**
 * Dark mode is drawn for the dark, not flipped from light. Surfaces step up in clear levels, and the parts of the
 * day are deep tints of one lightness, so none of them shouts and none turns muddy. Their colour shows in the labels.
 */
val DarkPalette =
    Palette(
        ground = Color(0xFF0B0D10),
        surface = Color(0xFF1A1D23),
        well = Color(0xFF262A32),
        line = Color(0xFF2E333B),
        ink = Color(0xFFE9EBEF),
        muted = Color(0xFFA3A9B3),
        faint = Color(0xFF7C828C),
        primary = Color(0xFFDDE0E6),
        onPrimary = Color(0xFF101317),
        selected = Color(0xFF30353E),
        onSelected = Color(0xFFE9EBEF),
        onPrimaryAction = Color(0xFF23407A),
        outline = Color(0x40E9EBEF),
        alert = Color(0xFFF08A63),
        alertText = Color(0xFFF4A584),
        alertSoft = Color(0xFF36221A),
        scrim = Color(0x99000000),
        morning = Tint(Color(0xFF2F2A1F), Color(0xFFF2CC6B), Color(0xFFCDBFA0), Color(0xFFD4A63A), Color(0xFFF6F1E4)),
        afternoon = Tint(Color(0xFF1C2734), Color(0xFF9DC3EA), Color(0xFFB1C0D1), Color(0xFF5F95CF), Color(0xFFEEF3F8)),
        evening = Tint(Color(0xFF30241F), Color(0xFFF0AA88), Color(0xFFD2BBAF), Color(0xFFCF7A52), Color(0xFFF8EEEA)),
        night = DarkNight,
        // Already as calm as the other parts, so a later Night needs no change.
        nightLater = DarkNight,
    )

val LocalPalette = compositionLocalOf { LightPalette }

private fun figtree(weight: Int) =
    Font(
        R.font.figtree,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))

private fun style(
    size: TextUnit,
    weight: Int,
    tracking: TextUnit = 0.sp,
    line: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = Figtree,
    fontSize = size,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking,
    lineHeight = line,
    fontFeatureSettings = "tnum",
)

/** The type scale from the designs. Numbers are tabular so times line up in lists. */
object Type {
    val display = style(32.sp, 700, (-0.8).sp, 38.sp)
    val title = style(28.sp, 800, (-0.84).sp, 33.sp)
    val heading = style(20.sp, 700, (-0.2).sp, 26.sp)
    val name = style(17.sp, 700, (-0.1).sp, 22.sp)
    val body = style(16.sp, 600, 0.sp, 21.sp)
    val text = style(15.sp, 400, 0.sp, 22.sp)
    val small = style(14.sp, 400, 0.sp, 20.sp)
    val label = style(13.sp, 700, 0.sp, 18.sp)
    val micro = style(12.sp, 600, 0.sp, 16.sp)
    val time = style(20.sp, 700, 0.sp, 24.sp)
    val count = style(60.sp, 800, (-2.4).sp, 62.sp)
    val clock = style(88.sp, 800, (-4).sp, 86.sp)
}

/** Fades between light and dark instead of snapping. Android shortens this if animations are turned off. */
@Composable
private fun animatedPalette(target: Palette): Palette {
    @Composable
    fun fade(color: Color) = animateColorAsState(color, tween(THEME_FADE_MS), label = "theme").value

    @Composable
    fun fade(tint: Tint) = Tint(fade(tint.background), fade(tint.label), fade(tint.text), fade(tint.strong), fade(tint.ink))
    return Palette(
        ground = fade(target.ground),
        surface = fade(target.surface),
        well = fade(target.well),
        line = fade(target.line),
        ink = fade(target.ink),
        muted = fade(target.muted),
        faint = fade(target.faint),
        primary = fade(target.primary),
        onPrimary = fade(target.onPrimary),
        selected = fade(target.selected),
        onSelected = fade(target.onSelected),
        onPrimaryAction = fade(target.onPrimaryAction),
        outline = fade(target.outline),
        alert = fade(target.alert),
        alertText = fade(target.alertText),
        alertSoft = fade(target.alertSoft),
        scrim = fade(target.scrim),
        morning = fade(target.morning),
        afternoon = fade(target.afternoon),
        evening = fade(target.evening),
        night = fade(target.night),
        nightLater = fade(target.nightLater),
    )
}

@Composable
fun RexiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = animatedPalette(if (dark) DarkPalette else LightPalette)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme =
        base.copy(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            surface = palette.surface,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surface,
            surfaceContainerHighest = palette.well,
            surfaceContainerLow = palette.surface,
            background = palette.ground,
            onSurface = palette.ink,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
            outlineVariant = palette.line,
            secondaryContainer = palette.well,
            onSecondaryContainer = palette.ink,
            tertiaryContainer = palette.well,
        )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** The wall clock, ticking every [periodMs] so "due now" turns up without anyone touching the screen. */
@Composable
fun rememberNow(periodMs: Long = 20_000): State<Long> =
    produceState(System.currentTimeMillis(), periodMs) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }

/** Whether the phone uses a 24 hour clock. Read again whenever the app comes back, since it can change in Settings. */
@Composable
fun rememberUse24h(): Boolean {
    val context = LocalContext.current
    var use24 by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { use24 = DateFormat.is24HourFormat(context) }
    return use24
}
