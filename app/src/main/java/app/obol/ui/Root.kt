package app.obol.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Sailing
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.obol.R
import app.obol.container
import app.obol.data.Owner
import app.obol.data.Patron
import app.obol.data.Settings
import app.obol.data.Stream
import app.obol.data.Target
import app.obol.domain.Clock
import app.obol.ui.components.Ground
import app.obol.ui.components.rememberNow
import app.obol.ui.ledger.LedgerTab
import app.obol.ui.lethe.LetheTab
import app.obol.ui.lethe.StreamScreen
import app.obol.ui.onboarding.OnboardingScreen
import app.obol.ui.pantheon.PantheonTab
import app.obol.ui.pantheon.PatronScreen
import app.obol.ui.rite.RiteScreen
import app.obol.ui.settings.SettingsScreen
import app.obol.ui.shore.ShoreTab
import app.obol.ui.temple.TempleScreen
import app.obol.ui.theme.Alegreya
import app.obol.ui.theme.U
import app.obol.watch.CharonService

enum class Tab { SHORE, PANTHEON, LETHE, LEDGER }

/** Everything stored, gathered once for all screens. */
@Immutable
class OData(
    val patrons: List<Patron>,
    val streams: List<Stream>,
    val targets: List<Target>,
    val balance: Long,
    val settings: Settings,
    val day: Long,
    /** Whether Charon's accessibility service is switched on. */
    val charon: Boolean,
) {
    fun patron(id: Long?): Patron? = patrons.firstOrNull { it.id == id }
    fun stream(id: Long?): Stream? = streams.firstOrNull { it.id == id }
    fun targetsOf(owner: Int, id: Long): List<Target> = targets.filter { it.owner == owner && it.ownerId == id }
    fun patronTargets(id: Long) = targetsOf(Owner.PATRON, id)
    fun streamTargets(id: Long) = targetsOf(Owner.STREAM, id)
}

class Go(private val nav: NavHostController) {
    fun patron(id: Long) = nav.navigate("patron/$id")
    fun stream(id: Long) = nav.navigate("stream/$id")
    fun rite() = nav.navigate("rite")
    fun settings() = nav.navigate("settings")
    fun temple() = nav.navigate("temple")
    fun back() = nav.popBackStack()
}

@Composable
fun ObolRoot(settings: Settings) {
    val context = LocalContext.current
    val dao = context.container.dao
    val nav = rememberNavController()
    val go = remember(nav) { Go(nav) }
    var tab by rememberSaveable { mutableStateOf(Tab.SHORE) }
    val start = remember { if (settings.onboarded) "main" else "welcome" }

    val patrons by dao.observePatrons().collectAsStateWithLifecycle(initialValue = emptyList())
    val streams by dao.observeStreams().collectAsStateWithLifecycle(initialValue = emptyList())
    val targets by dao.observeTargets().collectAsStateWithLifecycle(initialValue = emptyList())
    val balance by dao.observeBalance().collectAsStateWithLifecycle(initialValue = 0L)
    val live by CharonService.state.collectAsStateWithLifecycle()
    val tick = rememberNow(2_000)
    val charon = remember(tick, live.running) { live.running || CharonService.enabled(context) || app.obol.BuildConfig.DEMO }
    val day = remember(tick / 60_000) { Clock.day() }
    val data = remember(patrons, streams, targets, balance, settings, day, charon) {
        OData(patrons, streams, targets, balance, settings, day, charon)
    }

    // Charon's quiet notice and the oracle need notifications.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(settings.onboarded) {
        if (settings.onboarded && !app.obol.watch.Notices.canPost(context) && android.os.Build.VERSION.SDK_INT >= 33 && !app.obol.BuildConfig.DEMO) {
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The oath by the Styx: let go of the admin rights when it ends; note it when Charon is put to sleep during one.
    LaunchedEffect(settings.oathUntil, charon) {
        val c = context.container
        val now = System.currentTimeMillis()
        when {
            settings.oathUntil != 0L && !settings.oath(now) -> {
                app.obol.watch.Styx.release(context)
                c.settings.update { it.copy(oathUntil = 0) }
            }
            settings.oath(now) && !charon -> c.settings.update { it.copy(oathBrokenAt = now, oathUntil = 0) }
        }
    }

    Ground {
        NavHost(
            navController = nav,
            startDestination = start,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) },
        ) {
            composable("welcome") {
                OnboardingScreen(data) { nav.navigate("main") { popUpTo("welcome") { inclusive = true } } }
            }
            composable("main") { MainTabs(tab, { tab = it }, go, data) }
            composable("patron/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                PatronScreen(e.arguments?.getLong("id") ?: 0L, data, go)
            }
            composable("stream/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                StreamScreen(e.arguments?.getLong("id") ?: 0L, data, go)
            }
            composable("rite") { RiteScreen(data, go) }
            composable("settings") { SettingsScreen(data, go) }
            composable("temple") { TempleScreen(data, go) }
        }
    }
}

@Composable
private fun MainTabs(tab: Tab, setTab: (Tab) -> Unit, go: Go, data: OData) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.SHORE -> ShoreTab(data, go)
                Tab.PANTHEON -> PantheonTab(data, go)
                Tab.LETHE -> LetheTab(data, go)
                Tab.LEDGER -> LedgerTab(data, go)
            }
        }
        HorizontalDivider(color = U.c.line)
        Row(
            Modifier.fillMaxWidth().background(U.c.bg).navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(Icons.Rounded.Sailing, stringResource(R.string.tab_shore), tab == Tab.SHORE) { setTab(Tab.SHORE) }
            TabItem(Icons.Rounded.AccountBalance, stringResource(R.string.tab_pantheon), tab == Tab.PANTHEON) { setTab(Tab.PANTHEON) }
            TabItem(Icons.Rounded.Waves, stringResource(R.string.tab_lethe), tab == Tab.LETHE) { setTab(Tab.LETHE) }
            TabItem(Icons.AutoMirrored.Rounded.ReceiptLong, stringResource(R.string.tab_ledger), tab == Tab.LEDGER) { setTab(Tab.LEDGER) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) U.c.accent else U.c.faint
    Column(
        Modifier
            .weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = color, fontFamily = Alegreya, fontSize = 12.sp)
    }
}
