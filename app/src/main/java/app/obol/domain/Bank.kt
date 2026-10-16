package app.obol.domain

import app.obol.data.Crossing
import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.data.ObolDao
import app.obol.data.Settings
import app.obol.data.Stream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Days and minutes in the phone's time zone. */
object Clock {
    fun today(): LocalDate = LocalDate.now()
    fun day(): Long = today().toEpochDay()
    fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** The Monday that starts this week: Hermes' passes refill then. */
    fun weekStart(): Long = today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
}

/** What Charon asks at the gate. */
data class Quote(
    val stream: Stream,
    val balance: Long,
    /** One minute right now, with the tariffs. */
    val perMin: Long,
    /** Crossings already made today: the next one is number [nth] + 1. */
    val nth: Int,
    val hour: Hour,
    val passesLeft: Int,
    val rules: TariffRules,
) {
    /** What [minutes] cost with any interest, or null when the purse and the debt can't cover it. */
    fun cost(minutes: Int): Long? = Tariff.charge(perMin * minutes, balance, rules)

    /** The meter needs at least one minute's worth. */
    val canMeter: Boolean get() = Tariff.charge(perMin, balance, rules) != null
}

sealed interface Fare {
    data class Minutes(val minutes: Int) : Fare
    data object Meter : Fare
    data object Pass : Fare
}

/** Payments to Charon. Everything that moves obols goes through here. */
class Bank(private val dao: ObolDao) {
    suspend fun quote(stream: Stream, s: Settings): Quote {
        val rules = s.tariff
        val nth = dao.crossingsOn(Clock.day())
        val minute = Clock.minuteOfDay()
        return Quote(
            stream = stream,
            balance = dao.balance(),
            perMin = Tariff.perMinute(stream.price, minute, nth, rules),
            nth = nth,
            hour = Tariff.hour(minute, rules),
            passesLeft = (s.passes - dao.passesSince(Clock.weekStart())).coerceAtLeast(0),
            rules = rules,
        )
    }

    /** Pays and opens the crossing, or returns null when the fare can't be paid. */
    suspend fun pay(q: Quote, fare: Fare, reason: String): Crossing? {
        val now = System.currentTimeMillis()
        val day = Clock.day()
        val base = Crossing(streamId = q.stream.id, startedAt = now, day = day, reason = reason)
        return when (fare) {
            is Fare.Minutes -> {
                val cost = q.cost(fare.minutes) ?: return null
                val debt = cost > q.perMin * fare.minutes
                dao.cross(
                    Entry(at = now, day = day, kind = EntryKind.CROSS, cents = -cost, streamId = q.stream.id, note = note(reason, debt)),
                    base.copy(leftSec = fare.minutes * 60, perMin = q.perMin),
                )
            }
            Fare.Meter -> {
                if (!q.canMeter) return null
                dao.cross(
                    Entry(at = now, day = day, kind = EntryKind.CROSS, cents = 0, streamId = q.stream.id, note = reason),
                    base.copy(meter = true, perMin = q.perMin),
                )
            }
            Fare.Pass -> {
                if (q.passesLeft <= 0) return null
                dao.cross(
                    Entry(at = now, day = day, kind = EntryKind.PASS, cents = 0, streamId = q.stream.id, note = reason),
                    base.copy(leftSec = PASS_MINUTES * 60),
                )
            }
        }
    }

    /**
     * The meter takes the next minute. Returns the updated crossing, or null when the purse is
     * empty and the crossing is over.
     */
    suspend fun meterMinute(c: Crossing, s: Settings): Crossing? {
        val entry = dao.entry(c.entryId) ?: return null
        val cost = Tariff.charge(c.perMin, dao.balance(), s.tariff) ?: return null
        val debt = cost > c.perMin
        dao.updateEntry(
            entry.copy(
                cents = entry.cents - cost,
                note = if (debt && !entry.note.endsWith(DEBT)) note(entry.note, true) else entry.note,
            ),
        )
        return c
    }

    private fun note(reason: String, debt: Boolean) = if (debt) "$reason$DEBT" else reason

    companion object {
        const val PASS_MINUTES = 5

        /** Marks a payment made partly with borrowed obols. */
        const val DEBT = "#debt"
    }
}
