package app.obol.data

import app.obol.domain.Clock
import app.obol.domain.Obols
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** Made-up weeks of service and crossings for screenshots (`-Pdemo`). */
object DemoSeed {
    suspend fun seedIfEmpty(dao: ObolDao, settings: SettingsStore, ru: Boolean) {
        if (dao.patronCount() > 0) return
        Presets.seedIfEmpty(dao)
        settings.update { it.copy(onboarded = true) }
        val rnd = Random(7)
        val today = Clock.day()
        dao.insertEntry(Entry(at = at(today - 27, 9, 0), day = today - 27, kind = EntryKind.GIFT, cents = 20 * Obols.ONE, note = "road"))
        temple(dao, today, ru)
        // Patrons and streams are seeded in Presets order: ids 1..5 and 1..6.
        for (d in (today - 27)..today) {
            val good = d != today - 9 && d != today - 16
            if (good) {
                earn(dao, d, 2, 7, 40, rnd.nextInt(12, 24) * 60, 100) // Hermes over coffee
                earn(dao, d, 1, 22, 10, rnd.nextInt(15, 45) * 60, 150) // Calliope in bed
                if (rnd.nextInt(3) == 0) earn(dao, d, 4, 21, 50, 10 * 60, 200) // Hestia
                if (d % 3 == 0L) earn(dao, d, 3, 19, 0, rnd.nextInt(20, 50) * 60, 100) // Athena
                if (rnd.nextInt(5) == 0) dao.insertEntry(
                    Entry(at = at(d, 8, 0), day = d, kind = EntryKind.RITE, cents = 150L * 45, patronId = 5, seconds = 45 * 60, note = "45"),
                )
            }
            val crossings = if (d == today) 2 else if (good) rnd.nextInt(0, 3) else 5
            repeat(crossings) { i ->
                val stream = listOf(1L, 1L, 2L, 3L)[rnd.nextInt(4)]
                val min = listOf(5, 5, 10, 15)[rnd.nextInt(4)]
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

    /** The temple and memories, in the language the demo is first opened in. */
    private suspend fun temple(dao: ObolDao, today: Long, ru: Boolean) {
        dao.insertEntry(Entry(at = at(today - 20, 21, 0), day = today - 20, kind = EntryKind.VAULT, cents = -60 * Obols.ONE, held = 60 * Obols.ONE))
        for (w in listOf(14L, 7L)) {
            dao.insertEntry(Entry(at = at(today - w, 0, 5), day = today - w, kind = EntryKind.INTEREST, cents = 0, held = 3 * Obols.ONE))
        }
        val cinema = dao.upsertVow(Vow(title = if (ru) "Кино в субботу" else "Cinema on Saturday", glyph = "star", target = 400 * Obols.ONE, createdAt = at(today - 12, 20, 0)))
        dao.insertEntry(Entry(at = at(today - 5, 22, 0), day = today - 5, kind = EntryKind.VOW, cents = -180 * Obols.ONE, held = 180 * Obols.ONE, vowId = cinema))
        dao.insertEntry(Entry(at = at(today - 1, 22, 0), day = today - 1, kind = EntryKind.VOW, cents = -110 * Obols.ONE, held = 110 * Obols.ONE, vowId = cinema))
        val book = dao.upsertVow(Vow(title = if (ru) "Новая книга Пелевина" else "A new hardback", glyph = "book", target = 250 * Obols.ONE, createdAt = at(today - 25, 20, 0), keptAt = at(today - 8, 12, 0)))
        dao.insertEntry(Entry(at = at(today - 10, 22, 0), day = today - 10, kind = EntryKind.VOW, cents = -250 * Obols.ONE, held = 250 * Obols.ONE, vowId = book))
        dao.insertEntry(Entry(at = at(today - 8, 12, 0), day = today - 8, kind = EntryKind.VOW, cents = 0, held = -250 * Obols.ONE, vowId = book, note = "kept"))
        val memories = if (ru) listOf("Рецепт шакшуки", "Сестра выложила фото из Тбилиси", "Ничего полезного, но смешной кот")
        else listOf("A shakshuka recipe", "My sister's photos from Tbilisi", "Nothing useful, but a funny cat")
        dao.upsertMemory(Memory(today - 1, memories[0]))
        dao.upsertMemory(Memory(today - 2, nothing = true))
        dao.upsertMemory(Memory(today - 3, memories[1]))
        dao.upsertMemory(Memory(today - 9, nothing = true))
        dao.upsertMemory(Memory(today - 16, nothing = true))
        dao.upsertMemory(Memory(today - 4, memories[2]))
    }

    private suspend fun earn(dao: ObolDao, day: Long, patron: Long, h: Int, m: Int, sec: Int, rate: Long) {
        dao.insertEntry(Entry(at = at(day, h, m), day = day, kind = EntryKind.EARN, cents = rate * sec / 60, patronId = patron, seconds = sec))
    }

    private fun at(day: Long, h: Int, m: Int): Long =
        LocalDate.ofEpochDay(day).atTime(h % 24, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
