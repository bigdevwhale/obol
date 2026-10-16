package app.obol.domain

import app.obol.data.Owner
import app.obol.data.Patron
import app.obol.data.Stream
import app.obol.data.Target
import app.obol.data.TargetKind

/** What is on the screen right now, as far as Charon can tell. */
data class Seen(
    val pkg: String,
    /** The address in a browser's bar, if this is a browser. */
    val url: String? = null,
    /** The endless feed (Reels, Shorts) is visible. */
    val feed: Boolean = false,
)

sealed interface Match {
    data object None : Match
    data class Serve(val patron: Patron) : Match
    data class Lethe(val stream: Stream) : Match
}

/** A site rule or an address split into a host and a path, both lower-case and without `www.`/`m.`. */
data class Site(val host: String, val path: String) {
    /** Whether the address [url] falls under this rule: same host or a subdomain, and the path starts the same. */
    fun covers(url: Site): Boolean =
        (url.host == host || url.host.endsWith(".$host")) &&
            (path.isEmpty() || url.path == path || url.path.startsWith("$path/"))

    companion object {
        fun parse(raw: String): Site? {
            var s = raw.trim().lowercase()
            if (s.isEmpty() || s.contains(' ')) return null
            s = s.substringAfter("://")
            val slash = s.indexOf('/')
            var host = if (slash < 0) s else s.substring(0, slash)
            val path = if (slash < 0) "" else s.substring(slash).substringBefore('?').substringBefore('#').trimEnd('/')
            host = host.substringBefore(':')
            for (p in listOf("www.", "m.", "mobile.")) if (host.startsWith(p)) host = host.removePrefix(p)
            if (!host.contains('.')) return null
            return Site(host, path)
        }
    }
}

/** The patrons and streams with their apps and sites, indexed for a lookup every second. */
class Rules(patrons: List<Patron>, streams: List<Stream>, targets: List<Target>) {
    private val patronById = patrons.filter { it.active }.associateBy { it.id }
    private val streamById = streams.filter { it.active }.associateBy { it.id }

    private val streamApps = HashMap<String, Stream>()
    private val patronApps = HashMap<String, Patron>()
    private val streamSites = ArrayList<Pair<Site, Stream>>()
    private val patronSites = ArrayList<Pair<Site, Patron>>()

    init {
        for (t in targets) {
            when (t.owner) {
                Owner.STREAM -> streamById[t.ownerId]?.let { s ->
                    if (t.kind == TargetKind.APP) streamApps.putIfAbsent(t.value, s)
                    else Site.parse(t.value)?.let { streamSites += it to s }
                }
                Owner.PATRON -> patronById[t.ownerId]?.let { p ->
                    if (t.kind == TargetKind.APP) patronApps.putIfAbsent(t.value, p)
                    else Site.parse(t.value)?.let { patronSites += it to p }
                }
            }
        }
        // The most specific rule wins: youtube.com/shorts before youtube.com.
        streamSites.sortByDescending { it.first.host.length + it.first.path.length }
        patronSites.sortByDescending { it.first.host.length + it.first.path.length }
    }

    val isEmpty: Boolean get() = streamApps.isEmpty() && patronApps.isEmpty() && streamSites.isEmpty() && patronSites.isEmpty()

    fun streamForApp(pkg: String): Stream? = streamApps[pkg]
    fun patronForApp(pkg: String): Patron? = patronApps[pkg]

    /** Whether Charon needs to look for the feed inside this app. */
    fun watchesFeed(pkg: String): Boolean = streamApps[pkg]?.feedOnly == true && Feeds.markers(pkg).isNotEmpty()

    fun match(seen: Seen): Match {
        val url = seen.url?.let(Site::parse)
        if (url != null) {
            // In a browser only the address counts; the browser itself is neither good nor bad.
            streamSites.firstOrNull { it.first.covers(url) }?.let { return Match.Lethe(it.second) }
            patronSites.firstOrNull { it.first.covers(url) }?.let { return Match.Serve(it.second) }
            return Match.None
        }
        streamApps[seen.pkg]?.let { s ->
            val feedOnly = s.feedOnly && Feeds.markers(seen.pkg).isNotEmpty()
            if (!feedOnly || seen.feed) return Match.Lethe(s)
        }
        patronApps[seen.pkg]?.let { return Match.Serve(it) }
        return Match.None
    }
}

/** View ids that give away an endless feed inside an app. They change with app updates. */
object Feeds {
    private val MARKERS = mapOf(
        "com.instagram.android" to listOf("clips_viewer", "clips_video", "clips_tab", "reel_viewer"),
        "com.google.android.youtube" to listOf("reel_player", "reel_recycler", "reel_watch", "reel_progress", "shorts_"),
        "com.vkontakte.android" to listOf("clips_"),
        "com.facebook.katana" to listOf("reels_", "fb_shorts"),
        "com.snapchat.android" to listOf("spotlight"),
    )

    fun markers(pkg: String): List<String> = MARKERS[pkg].orEmpty()
}

/** Where browsers keep their address bar. */
object Browsers {
    private val URL_BARS = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("display_url", "mozac_browser_toolbar_url_view"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.yandex.browser" to listOf("bro_omnibar_address_title_text", "bro_omnibar_address_title_view", "suggest_omnibox_edit_text"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.mi.globalbrowser" to listOf("url", "title_bar_url"),
        "com.android.browser" to listOf("url", "title"),
    )

    fun isBrowser(pkg: String): Boolean = pkg in URL_BARS

    fun urlBars(pkg: String): List<String> = URL_BARS[pkg].orEmpty()
}
