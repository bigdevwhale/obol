package app.obol.ui.lethe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.obol.R
import app.obol.container
import app.obol.data.EntryKind
import app.obol.data.Owner
import app.obol.data.Presets
import app.obol.data.Stream
import app.obol.data.Target
import app.obol.data.TargetKind
import app.obol.domain.Feeds
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.AppIcon
import app.obol.ui.components.Apps
import app.obol.ui.components.Card
import app.obol.ui.components.Fmt
import app.obol.ui.components.duration
import app.obol.ui.components.Gap
import app.obol.ui.components.PrimaryButton
import app.obol.ui.components.StepSlider
import app.obol.ui.components.SwitchRow
import app.obol.ui.components.TargetsEditor
import app.obol.ui.components.TopBar
import app.obol.ui.components.fieldColors
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.launch

@Composable
fun LetheTab(data: OData, go: Go) {
    val context = LocalContext.current
    val today by context.container.dao.observeDay(data.day).collectAsStateWithLifecycle(initialValue = emptyList())
    val spent = remember(today) {
        today.filter { it.kind == EntryKind.CROSS || it.kind == EntryKind.PASS }.groupBy { it.streamId }
            .mapValues { (_, l) -> l.sumOf { it.seconds } to l.sumOf { it.cents } }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Gap(16.dp)
        Text(stringResource(R.string.lethe_title), style = UType.Display)
        Gap(4.dp)
        Text(stringResource(R.string.lethe_sub), style = UType.Sub)
        Gap(18.dp)
        data.streams.forEach { s ->
            val (sec, cents) = spent[s.id] ?: (0 to 0L)
            val apps = data.streamTargets(s.id).filter { it.kind == TargetKind.APP }.map { it.value }
            val icon = remember(apps) { apps.firstOrNull { Apps.installed(context, it) } }
            Card(Modifier.padding(bottom = 10.dp).alpha(if (s.active) 1f else 0.5f), onClick = { go.stream(s.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) AppIcon(icon, 44.dp)
                    else Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Waves, null, tint = U.c.lethe) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Presets.streamName(context, s), style = UType.Heading)
                        Text(stringResource(R.string.stream_price_short, Fmt.rate(s.price)), style = UType.Small.copy(color = U.c.gold))
                        if (s.feedOnly) Text(stringResource(R.string.stream_feed_only_short), style = UType.Small.copy(color = U.c.lethe))
                    }
                    if (sec > 0 || cents != 0L) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.signed(cents), style = UType.Number.copy(color = U.c.danger))
                            Text(duration(sec), style = UType.Small)
                        }
                    }
                }
            }
        }
        Gap(6.dp)
        PrimaryButton(stringResource(R.string.lethe_new), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) { go.stream(0) }
        Gap(28.dp)
    }
}

/** Prices of a minute on the far shore, in cents. */
private val PRICES = listOf(50L, 75L, 100L, 150L, 200L, 250L, 300L, 400L, 500L)

@Composable
fun StreamScreen(id: Long, data: OData, go: Go) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val existing = data.stream(id)
    var loaded by remember { mutableStateOf(id == 0L) }
    var s by remember { mutableStateOf(Stream(price = 200, sort = data.streams.size)) }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var sites by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, existing != null) {
        if (loaded || existing == null) return@LaunchedEffect
        s = existing
        val t = data.streamTargets(id)
        apps = t.filter { it.kind == TargetKind.APP }.map { it.value }
        sites = t.filter { it.kind == TargetKind.SITE }.map { it.value }
        loaded = true
    }
    if (!loaded) return
    // Under the oath a stream can only get dearer.
    val sworn = data.settings.oath() && id != 0L
    val minPrice = if (sworn) existing?.price ?: 0L else 0L

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(stringResource(if (id == 0L) R.string.stream_new_title else R.string.stream_edit_title), back = { go.back() }) {
            if (id != 0L && !sworn) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = U.c.muted)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (sworn) {
                Text(stringResource(R.string.stream_sworn), style = UType.Small.copy(color = U.c.lethe))
                Gap(10.dp)
            }
            OutlinedTextField(
                value = s.name,
                onValueChange = { s = s.copy(name = it) },
                placeholder = { Text(stringResource(R.string.stream_name_hint), style = UType.Muted) },
                singleLine = true,
                textStyle = UType.Heading,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(18.dp)
            Card {
                val pi = PRICES.indexOf(s.price).let { if (it < 0) PRICES.indexOfFirst { p -> p >= s.price }.coerceAtLeast(0) else it }
                StepSlider(stringResource(R.string.stream_price), pi, 0..PRICES.lastIndex, { stringResource(R.string.patron_rate_value, Fmt.rate(PRICES[it])) }) {
                    if (PRICES[it] >= minPrice) s = s.copy(price = PRICES[it])
                }
                val canFeed = apps.any { Feeds.markers(it).isNotEmpty() }
                if (canFeed && !(sworn && !s.feedOnly)) {
                    SwitchRow(stringResource(R.string.stream_feed_only), s.feedOnly, stringResource(R.string.stream_feed_only_help)) { s = s.copy(feedOnly = it) }
                }
                if (!sworn) SwitchRow(stringResource(R.string.stream_active), s.active) { s = s.copy(active = it) }
            }
            Gap(20.dp)
            TargetsEditor(apps, sites, { if (!sworn || it.containsAll(apps)) apps = it }, { if (!sworn || it.containsAll(sites)) sites = it }, stringResource(R.string.stream_site_hint))
            Gap(28.dp)
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            enabled = s.name.isNotBlank(),
        ) {
            val targets = apps.map { Target(owner = Owner.STREAM, ownerId = 0, kind = TargetKind.APP, value = it) } +
                sites.map { Target(owner = Owner.STREAM, ownerId = 0, kind = TargetKind.SITE, value = it) }
            container.write { saveStream(s.copy(name = s.name.trim()), targets) }
            go.back()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.stream_delete_title), style = UType.Heading) },
            text = { Text(stringResource(R.string.stream_delete_text), style = UType.Body) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        container.dao.deleteStream(id)
                        go.back()
                    }
                }) { Text(stringResource(R.string.action_delete), color = U.c.danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel), color = U.c.muted) } },
            containerColor = U.c.surface,
        )
    }
}
