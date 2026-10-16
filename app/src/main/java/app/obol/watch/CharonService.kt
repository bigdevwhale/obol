package app.obol.watch

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings.Secure
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.obol.BuildConfig
import app.obol.Lang
import android.view.accessibility.AccessibilityNodeInfo
import app.obol.container
import app.obol.data.Crossing
import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.data.Patron
import app.obol.data.Settings
import app.obol.data.Stream
import app.obol.domain.Bank
import app.obol.domain.Blessing
import app.obol.domain.Oracle
import app.obol.domain.Temple
import app.obol.domain.Browsers
import app.obol.domain.Clock
import app.obol.domain.Fare
import app.obol.domain.Feeds
import app.obol.domain.Match
import app.obol.domain.Rules
import app.obol.domain.Seen
import app.obol.domain.Tariff
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor

/** What the gate is showing. */
sealed interface GateState {
    val stream: Stream

    /** Charon waits for the fare. */
    data class Ask(override val stream: Stream) : GateState

    /** The prepaid time is over. */
    data class Over(override val stream: Stream) : GateState

    /** The meter ran the purse dry. */
    data class Broke(override val stream: Stream) : GateState
}

/** The live state, for the app's own screens. */
data class Live(
    val running: Boolean = false,
    val serving: Patron? = null,
    val crossing: Crossing? = null,
    val stream: Stream? = null,
)

/**
 * Charon: watches which app and site is on the screen, credits time given to patrons, and
 * stands at the gate of every stream of Lethe.
 */
class CharonService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var gate: OverlayHost
    private lateinit var badge: OverlayHost
    private lateinit var mist: OverlayHost
    private val mistLevel = mutableStateOf(0f)
    /** Seconds on the far shore today, for the mist; -1 until loaded. */
    private var shoreToday = -1
    private var shoreDay = 0L
    private var patronIds: List<Long> = emptyList()
    private var demoPatron: Long? = null
    private lateinit var bank: Bank

    private var rules = Rules(emptyList(), emptyList(), emptyList())
    private var settings = Settings()

    /** Last time the person touched, scrolled or typed. */
    private var lastActivity = 0L

    /** The last app seen with the screen on: audio patrons keep counting after the screen goes dark. */
    private var lastPkg: String? = null

    private var earn: EarnRun? = null
    private val open = HashMap<Long, Crossing>()
    private var gateFor: GateState? = null
    private var focus: AudioFocusRequest? = null
    private var tickNo = 0L
    private var lastMatch: Match = Match.None
    private var balance = 0L
    private val badgeData = mutableStateOf<Pair<Crossing, Long>?>(null)

    private class EarnRun(val patron: Patron, val day: Long, val before: Int) {
        var entryId = 0L
        var seconds = 0
        var cents = 0.0
        var flushed = 0
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected")
        // The system may connect again (after an update or a toggle); never run two clocks at once.
        scope.coroutineContext.cancelChildren()
        if (::gate.isInitialized) {
            gate.destroy()
            badge.destroy()
            mist.destroy()
        }
        stayAwake()
        val c = applicationContext.container
        bank = Bank(c.dao)
        // Overlays and notifications speak the language chosen in the app, not the phone's.
        val ui = Lang.wrap(this)
        val wm = getSystemService(android.view.WindowManager::class.java)
        gate = OverlayHost(ui, blocking = true, wm = wm)
        badge = OverlayHost(ui, blocking = false, wm = wm)
        mist = OverlayHost(ui, blocking = false, veil = true, wm = wm)
        lastActivity = System.currentTimeMillis()
        scope.launch {
            combine(c.dao.observePatrons(), c.dao.observeStreams(), c.dao.observeTargets()) { p, s, t ->
                patronIds = p.filter { it.active }.map { it.id }
                demoPatron = p.firstOrNull { it.preset == "athena" }?.id
                Rules(p, s, t)
            }.collect { rules = it }
        }
        scope.launch { c.settings.settings.collect { settings = it } }
        scope.launch { c.dao.observeBalance().collect { balance = it } }
        scope.launch { Temple(c.dao, c.settings).accrue() }
        scope.launch {
            c.dao.openCrossings().forEach { open[it.streamId] = it }
            while (isActive) {
                runCatching { tick() }.onFailure { Log.w(TAG, "tick", it) }
                delay(1000)
            }
        }
        live.value = Live(running = true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            -> if (event.packageName?.toString() != packageName) lastActivity = System.currentTimeMillis()
        }
    }

    override fun onInterrupt() = Unit

    /** Foreground with a quiet notice: without it HyperOS freezes the process whenever a feed is open. */
    private fun stayAwake() {
        runCatching {
            val n = Notices.watch(Lang.wrap(this))
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(Notices.WATCH_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(Notices.WATCH_ID, n)
            }
        }.onFailure { Log.w(TAG, "foreground", it) }
    }

    override fun onDestroy() {
        // The service scope dies below; the app's scope outlives it long enough to save the last seconds.
        applicationContext.container.scope.launch(Dispatchers.Main) { flushEarn() }
        live.value = Live()
        if (::gate.isInitialized) {
            gate.destroy()
            badge.destroy()
            mist.destroy()
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        releaseFocus()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        if (tickNo++ % 30 == 0L) {
            applicationContext.container.settings.beat(now)
            tellOracle()
        }

        val awake = getSystemService(PowerManager::class.java).isInteractive &&
            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val match = if (awake) {
            val seen = look()
            if (seen != null) lastPkg = seen.pkg
            seen?.let { rules.match(it) } ?: Match.None
        } else {
            // A meditation or an audiobook keeps going with the screen off.
            val p = lastPkg?.let { rules.patronForApp(it) }
            if (p != null && p.audio && getSystemService(AudioManager::class.java).isMusicActive) Match.Serve(p) else Match.None
        }

        if (BuildConfig.DEBUG && match != lastMatch) Log.i(TAG, "match $match awake=$awake root=${rootInActiveWindow?.packageName} rules=${!rules.isEmpty}")
        lastMatch = match
        serve(match as? Match.Serve, awake, now)
        lethe(match as? Match.Lethe)
        live.value = Live(
            running = true,
            serving = (match as? Match.Serve)?.patron,
            stream = (match as? Match.Lethe)?.stream,
            crossing = (match as? Match.Lethe)?.let { open[it.stream.id] },
        )
    }

    /** The app in front, the browser's address and whether a feed is showing. */
    private fun look(): Seen? {
        val root = rootInActiveWindow ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg == packageName || pkg == "com.android.systemui") return null
        if (Browsers.isBrowser(pkg)) {
            val url = Browsers.urlBars(pkg).firstNotNullOfOrNull { id ->
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstNotNullOfOrNull { it.text?.toString()?.takeIf { t -> t.isNotBlank() } }
            }
            return Seen(pkg, url = url)
        }
        val feed = rules.watchesFeed(pkg) && hasMarker(root, Feeds.markers(pkg))
        return Seen(pkg, feed = feed)
    }

    /** Looks through the visible views for an id that gives a feed away. */
    private fun hasMarker(root: AccessibilityNodeInfo, markers: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 900) {
            val n = queue.removeFirst()
            seen++
            val id = n.viewIdResourceName
            if (id != null && n.isVisibleToUser) {
                val name = id.substringAfter(":id/")
                if (markers.any { name.contains(it) }) return true
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
        }
        return false
    }

    // Patrons

    private suspend fun serve(m: Match.Serve?, awake: Boolean, now: Long) {
        val run = earn
        if (m == null || run == null || run.patron.id != m.patron.id || run.day != Clock.day()) {
            flushEarn()
            earn = null
        }
        if (m == null) return
        val r = earn ?: EarnRun(m.patron, Clock.day(), applicationContext.container.dao.earnedSeconds(m.patron.id, Clock.day()))
            .also { earn = it }
        val active = !awake || now - lastActivity < settings.idleSec * 1000L
        if (!active) return
        val blessed = blessing()?.let { it.patronId == r.patron.id && it.active(Clock.minuteOfDay()) } == true
        r.cents += Tariff.earn(r.patron.rate, 1, r.before + r.seconds, r.patron.capMin) * (if (blessed) 2 else 1)
        r.seconds++
        if (r.seconds - r.flushed >= 15) flushEarn()
    }

    private suspend fun flushEarn() {
        val r = earn ?: return
        if (r.seconds == r.flushed) return
        val dao = applicationContext.container.dao
        val cents = floor(r.cents).toLong()
        if (r.entryId == 0L) {
            r.entryId = dao.insertEntry(
                Entry(at = System.currentTimeMillis(), day = r.day, kind = EntryKind.EARN, cents = cents, patronId = r.patron.id, seconds = r.seconds),
            )
        } else {
            dao.entry(r.entryId)?.let { dao.updateEntry(it.copy(cents = cents, seconds = r.seconds)) }
        }
        r.flushed = r.seconds
    }

    private fun blessing(): Blessing? = when {
        !settings.oracleOn -> null
        BuildConfig.DEMO -> demoPatron?.let { Oracle.demo(it, Clock.minuteOfDay()) }
        else -> Oracle.blessing(Clock.day(), patronIds)
    }

    /** Once a day, when the blessing begins, the oracle speaks. */
    private suspend fun tellOracle() {
        val b = blessing() ?: return
        val today = Clock.day()
        if (settings.oracleTold == today || !b.active(Clock.minuteOfDay())) return
        val patron = rules.patron(b.patronId) ?: return
        // Only once it was heard: with notifications off the oracle tries again next time.
        if (Oracles.tell(Lang.wrap(this), patron, b)) applicationContext.container.settings.update { it.copy(oracleTold = today) }
    }

    // Lethe

    private suspend fun lethe(m: Match.Lethe?) {
        val shown = gateFor
        // The meter stops when you step off the far shore.
        open.values.filter { it.meter && it.streamId != m?.stream?.id }.forEach { finish(it) }
        if (m == null) {
            if (shown != null) closeGate()
            leaveShore()
            return
        }
        val c = open[m.stream.id]
        if (c == null) {
            leaveShore()
            if (shown == null || shown.stream.id != m.stream.id) openGate(GateState.Ask(m.stream))
            return
        }
        if (shown != null) closeGate()
        step(m.stream, c)
    }

    /** One second on the far shore. */
    private suspend fun step(stream: Stream, c0: Crossing) {
        var c = c0
        if (c.meter) {
            if (c.usedSec % 60 == 0) {
                c = bank.meterMinute(c, settings) ?: run {
                    finish(c)
                    openGate(GateState.Broke(stream))
                    return
                }
            }
            c = c.copy(usedSec = c.usedSec + 1)
        } else {
            c = c.copy(usedSec = c.usedSec + 1, leftSec = c.leftSec - 1)
            if (c.leftSec <= 0) {
                finish(c)
                openGate(GateState.Over(stream))
                return
            }
        }
        open[stream.id] = c
        if (c.usedSec % 10 == 0) save(c)
        badgeData.value = c to balance
        if (settings.badgeOn && !badge.showing) badge.show { badgeData.value?.let { (cr, bal) -> Badge(cr, bal) } }
        if (!settings.badgeOn) badge.hide()
        veil()
    }

    private suspend fun save(c: Crossing) {
        val dao = applicationContext.container.dao
        dao.updateCrossing(c)
        dao.entry(c.entryId)?.let { dao.updateEntry(it.copy(seconds = c.usedSec)) }
    }

    private suspend fun finish(c: Crossing) {
        open.remove(c.streamId)
        save(c.copy(open = false))
        leaveShore()
    }

    /**
     * The mist of Lethe: after five minutes on the far shore today it creeps in from the edges,
     * thickest after twenty-five.
     */
    private suspend fun veil() {
        val today = Clock.day()
        if (shoreToday < 0 || shoreDay != today) {
            shoreToday = applicationContext.container.dao.shoreSeconds(today)
            shoreDay = today
        }
        shoreToday++
        // Noticeable from the first moment, thickest after twenty-five minutes.
        val progress = ((shoreToday - 300) / 1200f).coerceIn(0f, 1f)
        val level = if (settings.mistOn && shoreToday > 300) 0.55f + 0.45f * progress else 0f
        mistLevel.value = level
        if (level > 0f && !mist.showing) mist.show { Mist(mistLevel.value) }
        if (level == 0f) mist.hide()
    }

    /** Off the far shore: the badge and the mist go, the prepaid time waits. */
    private fun leaveShore() {
        badge.hide()
        mist.hide()
    }

    private fun openGate(state: GateState) {
        gateFor = state
        holdFocus()
        gate.show {
            GateScreen(
                state = state,
                settings = settings,
                quote = { bank.quote(state.stream, settings) },
                pay = { q, fare, reason -> paid(state.stream, bank.pay(q, fare, reason)) },
                back = { turnBack() },
            )
        }
    }

    private fun paid(stream: Stream, c: Crossing?): Boolean {
        if (c == null) return false
        open[stream.id] = c
        closeGate()
        return true
    }

    private fun closeGate() {
        gateFor = null
        gate.hide()
        releaseFocus()
    }

    private fun turnBack() {
        closeGate()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Silences the reel playing under the gate. */
    private fun holdFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { }
            .build()
        getSystemService(AudioManager::class.java).requestAudioFocus(req)
        focus = req
    }

    private fun releaseFocus() {
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        private const val TAG = "Charon"
        private val live = MutableStateFlow(Live())
        val state: StateFlow<Live> get() = live

        fun enabled(context: Context): Boolean {
            val list = Secure.getString(context.contentResolver, Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = "${context.packageName}/${CharonService::class.java.name}"
            return list.split(':').any { it.equals(me, ignoreCase = true) || it.equals("${context.packageName}/.watch.CharonService", ignoreCase = true) }
        }
    }
}
