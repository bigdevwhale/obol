package app.obol.data

import android.content.Context
import app.obol.R

/** The pantheon and the streams of Lethe a new install starts with. */
object Presets {
    class P(
        val key: String,
        val glyph: String,
        val hue: Int,
        val rate: Long,
        val capMin: Int,
        val audio: Boolean = false,
        val apps: List<String> = emptyList(),
        val sites: List<String> = emptyList(),
    )

    class S(
        val key: String,
        val name: String,
        val price: Long,
        val feedOnly: Boolean = false,
        val apps: List<String> = emptyList(),
        val sites: List<String> = emptyList(),
    )

    val patrons = listOf(
        P(
            "calliope", "book", 0, rate = 150, capMin = 120,
            apps = listOf(
                "com.amazon.kindle", "com.flyersoft.moonreaderp", "com.flyersoft.moonreader", "ru.litres.android",
                "org.geometerplus.zlibrary.ui.android", "com.google.android.apps.books", "com.bookmate",
                "ru.mybook", "org.readera", "com.ebooks.ebookreader", "com.audible.application",
            ),
        ),
        P(
            "hermes", "lang", 1, rate = 100, capMin = 60,
            apps = listOf(
                "com.duolingo", "com.ichi2.anki", "com.busuu.android.enc", "com.babbel.mobile.android.en",
                "com.memrise.android.memrisecompanion", "com.lingualeo.android", "com.lingvist.android",
                "com.ewa.ewaapp", "ru.puzzle.english", "com.hellotalk",
            ),
            sites = listOf("duolingo.com", "lingualeo.com"),
        ),
        P(
            "athena", "owl", 2, rate = 100, capMin = 120,
            apps = listOf(
                "org.coursera.android", "org.brilliant.android", "org.khanacademy.android", "com.udemy.android",
                "org.stepic.droid", "com.edx.mobile", "com.sololearn",
            ),
            sites = listOf("wikipedia.org", "coursera.org", "stepik.org", "khanacademy.org", "habr.com", "arxiv.org"),
        ),
        P(
            "hestia", "flame", 3, rate = 200, capMin = 40, audio = true,
            apps = listOf(
                "com.getsomeheadspace.android", "com.calm.android", "meditofoundation.medito", "app.serene",
                "com.insighttimer.android", "com.meditopia.android",
            ),
        ),
        P("nike", "run", 4, rate = 150, capMin = 90, apps = listOf("com.strava", "com.nike.plusgps")),
    )

    val streams = listOf(
        S("instagram", "Instagram", 200, feedOnly = true, apps = listOf("com.instagram.android"), sites = listOf("instagram.com")),
        S("tiktok", "TikTok", 300, apps = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), sites = listOf("tiktok.com")),
        S("youtube", "YouTube Shorts", 200, feedOnly = true, apps = listOf("com.google.android.youtube"), sites = listOf("youtube.com/shorts")),
        S("vk", "VK", 150, apps = listOf("com.vkontakte.android"), sites = listOf("vk.com/clips")),
        S("reddit", "Reddit", 150, apps = listOf("com.reddit.frontpage"), sites = listOf("reddit.com")),
        S("x", "X", 150, apps = listOf("com.twitter.android"), sites = listOf("x.com", "twitter.com")),
    )

    /** Fills an empty database: the pantheon, the streams and the first obols for the road. */
    suspend fun seedIfEmpty(dao: ObolDao) {
        if (dao.patronCount() > 0) return
        patrons.forEachIndexed { i, p ->
            dao.savePatron(
                Patron(preset = p.key, glyph = p.glyph, hue = p.hue, rate = p.rate, capMin = p.capMin, audio = p.audio, sort = i),
                p.apps.map { Target(owner = Owner.PATRON, ownerId = 0, kind = TargetKind.APP, value = it) } +
                    p.sites.map { Target(owner = Owner.PATRON, ownerId = 0, kind = TargetKind.SITE, value = it) },
            )
        }
        streams.forEachIndexed { i, s ->
            dao.saveStream(
                Stream(preset = s.key, name = s.name, price = s.price, feedOnly = s.feedOnly, sort = i),
                s.apps.map { Target(owner = Owner.STREAM, ownerId = 0, kind = TargetKind.APP, value = it) } +
                    s.sites.map { Target(owner = Owner.STREAM, ownerId = 0, kind = TargetKind.SITE, value = it) },
            )
        }
    }

    fun patronName(context: Context, p: Patron): String =
        p.name.ifBlank {
            when (p.preset) {
                "calliope" -> context.getString(R.string.patron_calliope)
                "hermes" -> context.getString(R.string.patron_hermes)
                "athena" -> context.getString(R.string.patron_athena)
                "hestia" -> context.getString(R.string.patron_hestia)
                "nike" -> context.getString(R.string.patron_nike)
                else -> context.getString(R.string.patron_unnamed)
            }
        }

    /** What the patron looks after, for built-in ones. */
    fun patronDomain(context: Context, p: Patron): String? = when (p.preset) {
        "calliope" -> context.getString(R.string.patron_calliope_domain)
        "hermes" -> context.getString(R.string.patron_hermes_domain)
        "athena" -> context.getString(R.string.patron_athena_domain)
        "hestia" -> context.getString(R.string.patron_hestia_domain)
        "nike" -> context.getString(R.string.patron_nike_domain)
        else -> null
    }

    fun streamName(context: Context, s: Stream): String = s.name.ifBlank { context.getString(R.string.stream_unnamed) }
}
