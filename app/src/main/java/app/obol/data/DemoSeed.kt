package app.obol.data

import app.obol.domain.Clock
import app.obol.domain.Obols
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Made-up weeks of service and crossings for screenshots (`-Pdemo`). */
object DemoSeed {
    suspend fun seedIfEmpty(dao: ObolDao, settings: SettingsStore) {
        if (dao.patronCount() > 0) return
        Presets.seedIfEmpty(dao)
        settings.update { it.copy(onboarded = true) }
        val rnd = Random(7)
        val today = Clock.day()
        dao.insertEntry(Entry(at = at(today - 27, 9, 0), day = today - 27, kind = EntryKind.GIFT, cents = 20 * Obols.ONE, note = "road"))
        // Patrons and streams are seeded in Presets order: ids 1..5 and 1..6.
        for (d in (today - 27)..today) {
            val good = d != today - 9 && d != today - 16
            if (good) {
                earn(dao, d, 2, 7, 40, rnd.nextInt(12, 24) * 60, 100) // Hermes over coffee
                earn(dao, d, 1, 22, 10, rnd.nextInt(15, 45) * 60, 150) // Calliope in bed
                if (rnd.nextInt(3) == 0) earn(dao, d, 4, 21, 50, 10 * 60, 200) // Hestia
                if (rnd.nextInt(4) == 0) earn(dao, d, 3, 19, 0, rnd.nextInt(20, 50) * 60, 100) // Athena
                if (rnd.nextInt(5) == 0) dao.insertEntry(
                    Entry(at = at(d, 8, 0), day = d, kind = EntryKind.RITE, cents = 150L * 45, patronId = 5, seconds = 45 * 60, note = "45"),
                )
            }
            val crossings = if (good) rnd.nextInt(1, 4) else 6
            repeat(crossings) { i ->
                val stream = listOf(1L, 1L, 2L, 3L)[rnd.nextInt(4)]
                val min = listOf(5, 15, 15, 30)[rnd.nextInt(4)]
                val price = if (stream == 2L) 300L else 200L
                dao.insertEntry(
                    Entry(
                        at = at(d, 12 + i * 3, 15), day = d, kind = EntryKind.CROSS, cents = -price * min * (10 + 5 * i) / 10,
                        streamId = stream, seconds = min * 60, note = listOf("rest", "bored", "friend", "rest")[rnd.nextInt(4)],
                    ),
                )
            }
        }
    }

    private suspend fun earn(dao: ObolDao, day: Long, patron: Long, h: Int, m: Int, sec: Int, rate: Long) {
        dao.insertEntry(Entry(at = at(day, h, m), day = day, kind = EntryKind.EARN, cents = rate * sec / 60, patronId = patron, seconds = sec))
    }

    private fun at(day: Long, h: Int, m: Int): Long =
        LocalDate.ofEpochDay(day).atTime(h % 24, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
