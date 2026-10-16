package app.obol.domain

import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.data.ObolDao
import app.obol.data.SettingsStore
import app.obol.data.Vow

/**
 * The temple: vows saved up for real rewards and a treasury that grows by interest each week.
 * Obols laid aside here leave the purse but aren't spent, so they stay out of Charon's reach.
 */
class Temple(private val dao: ObolDao, private val settings: SettingsStore) {
    /** Lays [cents] aside for a vow; false when the purse can't cover it. */
    suspend fun offer(vow: Vow, cents: Long): Boolean {
        if (cents <= 0 || dao.balance() < cents || !vow.open) return false
        dao.insertEntry(
            Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.VOW, cents = -cents, held = cents, vowId = vow.id),
        )
        return true
    }

    /** Keeps the vow: the saved obols are offered for good and the reward is yours. */
    suspend fun keep(vow: Vow) {
        val saved = dao.vowSaved(vow.id)
        if (saved < vow.target || !vow.open) return
        dao.insertEntry(
            Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.VOW, cents = 0, held = -saved, vowId = vow.id, note = KEPT),
        )
        dao.upsertVow(vow.copy(keptAt = System.currentTimeMillis()))
    }

    /** Breaks the vow: whatever was saved goes back to the purse. */
    suspend fun breakVow(vow: Vow) {
        val saved = dao.vowSaved(vow.id)
        if (saved > 0) {
            dao.insertEntry(
                Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.VOW, cents = saved, held = -saved, vowId = vow.id, note = BROKEN),
            )
        }
        dao.upsertVow(vow.copy(brokenAt = System.currentTimeMillis()))
    }

    suspend fun deposit(cents: Long): Boolean {
        if (cents <= 0 || dao.balance() < cents) return false
        dao.insertEntry(Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.VAULT, cents = -cents, held = cents))
        return true
    }

    suspend fun withdraw(cents: Long): Boolean {
        if (cents <= 0 || dao.treasury() < cents) return false
        dao.insertEntry(Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.VAULT, cents = cents, held = -cents))
        return true
    }

    /**
     * Pays the treasury's interest for every Monday that passed since the last payment (at most
     * eight, for a phone left in a drawer). Safe to call often.
     */
    suspend fun accrue() {
        val s = settings.now()
        val week = Clock.weekStart()
        if (s.lastAccrual == 0L) {
            settings.update { it.copy(lastAccrual = week) }
            return
        }
        val weeks = ((week - s.lastAccrual) / 7).toInt().coerceIn(0, 8)
        if (weeks == 0) return
        settings.update { it.copy(lastAccrual = week) }
        var vault = dao.treasury()
        repeat(weeks) {
            val interest = vault * s.interestPct / 100
            if (interest > 0) {
                dao.insertEntry(Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.INTEREST, cents = 0, held = interest))
                vault += interest
            }
        }
    }

    companion object {
        const val KEPT = "kept"
        const val BROKEN = "broken"
    }
}

/** The oracle's word for the day: one patron pays double for two hours. */
data class Blessing(val patronId: Long, val from: Int, val to: Int) {
    fun active(minuteOfDay: Int) = minuteOfDay in from until to
}

object Oracle {
    /** The demo build always catches Athena's blessing in progress, in the app and in Charon alike. */
    fun demo(patronId: Long, minuteOfDay: Int) = Blessing(patronId, minuteOfDay - 40, minuteOfDay + 80)

    /** The same answer all day long, a different one each day. */
    fun blessing(day: Long, patronIds: List<Long>): Blessing? {
        if (patronIds.isEmpty()) return null
        val r = java.util.Random(day * 7919 + 17)
        val id = patronIds.sorted()[r.nextInt(patronIds.size)]
        val start = (8 + r.nextInt(12)) * 60
        return Blessing(id, start, start + 120)
    }
}
