package top.yeetfactory.rexi.core

/** Preset slots follow the person's settings; custom slots carry their own local time. */
sealed interface DoseSlot {
    val key: String

    fun label(use24: Boolean): String =
        when (this) {
            is DayPart -> label
            is CustomTime -> Format.time(minutes, use24)
        }

    companion object {
        fun fromKey(key: String): DoseSlot? {
            if (!key.startsWith(CustomTime.PREFIX)) return DayPart.entries.firstOrNull { it.name == key }
            val minutes = key.removePrefix(CustomTime.PREFIX).toIntOrNull() ?: return null
            return if (minutes in 0 until CustomTime.MINUTES_PER_DAY) CustomTime(minutes) else null
        }
    }
}

data class CustomTime(
    val minutes: Int,
) : DoseSlot {
    init {
        require(minutes in 0 until MINUTES_PER_DAY)
    }

    override val key: String get() = "$PREFIX$minutes"

    companion object {
        internal const val PREFIX = "time-"
        internal const val MINUTES_PER_DAY = 24 * 60
    }
}

/** The four shared compartments of a day. */
enum class DayPart(
    val label: String,
    val defaultMinutes: Int,
) : DoseSlot {
    MORNING("Morning", 8 * 60),
    AFTERNOON("Afternoon", 14 * 60),
    EVENING("Evening", 19 * 60 + 30),
    NIGHT("Night", 22 * 60),
    ;

    override val key: String get() = name
}

/** How a medicine looks, from a fixed set of drawings. OTHER covers everything else, with a name the person types. */
enum class MedKind(
    val label: String,
) {
    TABLET("Tablet"),
    OVAL("Oval tablet"),
    CAPSULE("Capsule"),
    SOFTGEL("Softgel"),
    LIQUID("Liquid"),
    DROPS("Drops"),
    SPRAY("Spray"),
    INHALER("Inhaler"),
    INJECTION("Injection"),
    CREAM("Cream"),
    PATCH("Patch"),
    OTHER("Other"),
    ;

    /** What a dose of this is usually counted in. The person can still pick another unit. */
    val defaultUnit: DoseUnit
        get() =
            when (this) {
                TABLET, OVAL -> DoseUnit.TABLET
                CAPSULE, SOFTGEL -> DoseUnit.CAPSULE
                LIQUID -> DoseUnit.ML
                DROPS -> DoseUnit.DROP
                SPRAY -> DoseUnit.SPRAY
                INHALER -> DoseUnit.PUFF
                INJECTION -> DoseUnit.INJECTION
                CREAM -> DoseUnit.APPLICATION
                PATCH -> DoseUnit.PATCH
                OTHER -> DoseUnit.CUSTOM
            }
}

/** The colours a medicine's drawing can take. Pale ones get an outline so they still show on white. */
enum class MedColor(
    val label: String,
    val argb: Long,
    val pale: Boolean = false,
) {
    WHITE("White", 0xFFFFFFFF, pale = true),
    CREAM("Cream", 0xFFF3E6C4, pale = true),
    YELLOW("Yellow", 0xFFF2D04B, pale = true),
    AMBER("Amber", 0xFFE9A236),
    ORANGE("Orange", 0xFFE2762F),
    PINK("Pink", 0xFFF4B6C6),
    RED("Red", 0xFFD9473B),
    BLUE("Blue", 0xFF3D6FD8),
    GREEN("Green", 0xFF4FA36B),
    TEAL("Teal", 0xFF2FA39A),
}

/**
 * What one dose is counted in. Any medicine fits one of these, and CUSTOM takes a word the person types,
 * so nothing in the app depends on what kind of medicine it is.
 */
enum class DoseUnit(
    val chip: String,
    val one: String,
    val many: String,
    /** Swallowed things are "taken". Everything else, like a spray or a cream, is "done". */
    val swallowed: Boolean,
    /** Whether a pack can sensibly be counted. A bottle of spray can't, so it's tracked by how long it lasts. */
    val countable: Boolean,
) {
    TABLET("Tablets", "tablet", "tablets", true, true),
    CAPSULE("Capsules", "capsule", "capsules", true, true),
    ML("ml", "ml", "ml", true, false),
    DROP("Drops", "drop", "drops", false, false),
    SPRAY("Sprays", "spray", "sprays", false, false),
    PUFF("Puffs", "puff", "puffs", true, true),
    APPLICATION("Applications", "application", "applications", false, false),
    PATCH("Patches", "patch", "patches", false, true),
    INJECTION("Injections", "injection", "injections", false, true),
    UNIT("Units", "unit", "units", false, false),
    SACHET("Sachets", "sachet", "sachets", true, true),
    CUSTOM("Something else", "dose", "doses", false, false),
    ;

    /** The smallest change to a dose. A tablet can be halved and a liquid measured in halves; a spray can't. */
    val step: Double get() = if (this == TABLET || this == ML) 0.5 else 1.0
}

enum class Frequency(
    val label: String,
) {
    EVERY_DAY("Every day"),
    SOME_DAYS("Some days"),
    EVERY_FEW_DAYS("Every few days"),
    COURSE("A short course"),
    AS_NEEDED("When needed"),
}

enum class Food(
    val label: String,
) {
    WITH_FOOD("With food"),
    BEFORE_FOOD("Before food"),
    AFTER_FOOD("After food"),
    ANY("Any time"),
}

enum class SupplyMode {
    NONE,
    COUNT,
    DURATION,
}

enum class DoseStatus {
    TAKEN,
    SKIPPED,
    MISSED,
}
