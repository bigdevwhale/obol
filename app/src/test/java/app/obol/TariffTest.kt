package app.obol

import app.obol.domain.Hour
import app.obol.domain.Site
import app.obol.domain.Tariff
import app.obol.domain.TariffRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TariffTest {
    private val r = TariffRules()

    @Test fun hours() {
        assertEquals(Hour.NIGHT, Tariff.hour(23 * 60 + 30, r))
        assertEquals(Hour.NIGHT, Tariff.hour(2 * 60, r))
        assertEquals(Hour.MORNING, Tariff.hour(7 * 60, r))
        assertEquals(Hour.DAY, Tariff.hour(14 * 60, r))
        assertEquals(Hour.DAY, Tariff.hour(2 * 60, r.copy(nightOn = false, morningOn = false)))
    }

    @Test fun pricesClimbAndNight() {
        assertEquals(200L, Tariff.perMinute(200, 14 * 60, 0, r))
        assertEquals(300L, Tariff.perMinute(200, 14 * 60, 1, r))
        assertEquals(600L, Tariff.perMinute(200, 14 * 60, 9, r)) // capped at ×3
        assertEquals(900L, Tariff.perMinute(200, 23 * 60 + 30, 1, r)) // night ×3 and second crossing ×1.5
    }

    @Test fun debtCarriesInterestOnTheBorrowedPart() {
        assertEquals(1000L, Tariff.charge(1000, 5000, r))
        // 300 own, 700 borrowed at 20 %: 1000 + 140.
        assertEquals(1140L, Tariff.charge(1000, 300, r))
        // A negative balance borrows everything.
        assertEquals(1200L, Tariff.charge(1000, -500, r))
        // Below the floor of -30 obols there is no crossing.
        assertNull(Tariff.charge(2000, -2000, r))
        assertNull(Tariff.charge(100, 50, r.copy(debtLimit = 0)))
    }

    @Test fun earningHalvesPastTheCap() {
        assertEquals(100.0, Tariff.earn(100, 60, 0, 0), 1e-9)
        // One minute cap, two minutes served from scratch: 100 + 50.
        assertEquals(150.0, Tariff.earn(100, 120, 0, 1), 1e-9)
        // Already past the cap.
        assertEquals(50.0, Tariff.earn(100, 60, 600, 1), 1e-9)
    }

    @Test fun sites() {
        val shorts = Site.parse("youtube.com/shorts")!!
        assertTrue(shorts.covers(Site.parse("https://m.youtube.com/shorts/abc?x=1")!!))
        assertFalse(shorts.covers(Site.parse("youtube.com/watch?v=1")!!))
        assertFalse(shorts.covers(Site.parse("youtube.com/shortsxyz")!!))
        val wiki = Site.parse("wikipedia.org")!!
        assertTrue(wiki.covers(Site.parse("ru.wikipedia.org/wiki/Харон")!!))
        assertFalse(wiki.covers(Site.parse("notwikipedia.org")!!))
        assertNull(Site.parse("just words"))
        assertNull(Site.parse("localhost"))
    }
}

class OracleTest {
    @Test fun sameAnswerAllDayAndTwoHoursLong() {
        val ids = listOf(3L, 1L, 2L)
        val a = app.obol.domain.Oracle.blessing(20_000, ids)!!
        assertEquals(a, app.obol.domain.Oracle.blessing(20_000, ids.reversed()))
        assertEquals(120, a.to - a.from)
        assertTrue(a.from >= 8 * 60 && a.to <= 22 * 60)
        assertNull(app.obol.domain.Oracle.blessing(20_000, emptyList()))
    }
}
