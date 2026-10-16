package app.obol.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Money is counted in hundredths of an obol ("cents") so rates like 1.5 obols a minute and
 * interest stay exact. Only whole obols are ever shown.
 */
object Obols {
    const val ONE = 100L

    /** Whole obols for display, rounded towards zero so a debt of 0.4 shows as 0, not -1. */
    fun whole(cents: Long): Long = cents / ONE

    fun of(obols: Double): Long = (obols * ONE).roundToLong()
}

/** The knobs of Charon's prices, taken from the settings. */
data class TariffRules(
    /** Nyx's hour: the night tariff. Minutes after midnight; the window may wrap past midnight. */
    val nightOn: Boolean = true,
    val nightFrom: Int = 23 * 60,
    val nightTo: Int = 6 * 60,
    /** In tenths: 30 = ×3. */
    val nightX: Int = 30,
    /** The early-morning tariff, from the end of the night until [morningTo]. */
    val morningOn: Boolean = true,
    val morningTo: Int = 9 * 60,
    val morningX: Int = 20,
    /** Every crossing after the first one today costs half the base price more, up to ×3. */
    val climbOn: Boolean = true,
    /** Owing Hades: how far below zero the balance may go, in cents; 0 = no debt at all. */
    val debtLimit: Long = 30 * Obols.ONE,
    /** Interest on the borrowed part of a payment, in percent. */
    val debtPct: Int = 20,
)

enum class Hour { DAY, NIGHT, MORNING }

object Tariff {
    fun inWindow(minute: Int, from: Int, to: Int): Boolean =
        if (from <= to) minute in from until to else minute >= from || minute < to

    fun hour(minuteOfDay: Int, r: TariffRules): Hour = when {
        r.nightOn && inWindow(minuteOfDay, r.nightFrom, r.nightTo) -> Hour.NIGHT
        r.morningOn && inWindow(minuteOfDay, if (r.nightOn) r.nightTo else 5 * 60, r.morningTo) -> Hour.MORNING
        else -> Hour.DAY
    }

    /** Multiplier of the time of day, in tenths. */
    fun hourX(minuteOfDay: Int, r: TariffRules): Int = when (hour(minuteOfDay, r)) {
        Hour.NIGHT -> r.nightX
        Hour.MORNING -> r.morningX
        Hour.DAY -> 10
    }

    /** Multiplier for the [nth] crossing of the day (0 = the first one), in tenths. */
    fun climbX(nth: Int, r: TariffRules): Int = if (!r.climbOn) 10 else min(30, 10 + 5 * max(0, nth))

    /** What one minute on the far shore costs right now. */
    fun perMinute(basePerMin: Long, minuteOfDay: Int, nth: Int, r: TariffRules): Long =
        (basePerMin * hourX(minuteOfDay, r) * climbX(nth, r) + 50) / 100

    /** The lowest the balance may fall. */
    fun floor(r: TariffRules): Long = -max(0L, r.debtLimit)

    /**
     * What a payment of [price] actually costs with interest on the part paid with borrowed obols,
     * or null when even borrowing would sink the balance below the floor.
     */
    fun charge(price: Long, balance: Long, r: TariffRules): Long? {
        val borrowed = price - max(0L, balance).coerceAtMost(price)
        val total = price + (borrowed * r.debtPct + 99) / 100
        return if (balance - total < floor(r)) null else total
    }

    /**
     * Obols earned for [seconds] of service, where [before] seconds were already given to the
     * patron today. Past the daily [capMin] the rate halves, so an app left open doesn't farm.
     * Returns cents as a double: the tracker adds up fractions second by second.
     */
    fun earn(ratePerMin: Long, seconds: Int, before: Int, capMin: Int): Double {
        if (seconds <= 0) return 0.0
        val perSec = ratePerMin / 60.0
        if (capMin <= 0) return perSec * seconds
        val capSec = capMin * 60
        val full = (capSec - before).coerceIn(0, seconds)
        return perSec * full + perSec / 2 * (seconds - full)
    }
}
