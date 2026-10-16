package app.obol.ui.pantheon

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.obol.R
import app.obol.container
import app.obol.data.EntryKind
import app.obol.data.Owner
import app.obol.data.Patron
import app.obol.data.Presets
import app.obol.data.Target
import app.obol.data.TargetKind
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Card
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.Glyphs
import app.obol.ui.components.Medallion
import app.obol.ui.components.PrimaryButton
import app.obol.ui.components.StepSlider
import app.obol.ui.components.Swatch
import app.obol.ui.components.SwitchRow
import app.obol.ui.components.TargetsEditor
import app.obol.ui.components.TopBar
import app.obol.ui.components.fieldColors
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.launch

@Composable
fun PantheonTab(data: OData, go: Go) {
    val context = LocalContext.current
    val today by context.container.dao.observeDay(data.day).collectAsStateWithLifecycle(initialValue = emptyList())
    val served = remember(today) {
        today.filter { it.kind == EntryKind.EARN || it.kind == EntryKind.RITE }.groupBy { it.patronId }
            .mapValues { (_, l) -> l.sumOf { it.seconds } to l.sumOf { it.cents } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Gap(16.dp)
        Text(stringResource(R.string.pantheon_title), style = UType.Display)
        Gap(4.dp)
        Text(stringResource(R.string.pantheon_sub), style = UType.Sub)
        Gap(18.dp)
        data.patrons.forEach { p ->
            val (sec, cents) = served[p.id] ?: (0 to 0L)
            val count = data.patronTargets(p.id).size
            Card(Modifier.padding(bottom = 10.dp).alpha(if (p.active) 1f else 0.5f), onClick = { go.patron(p.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The pedestal grows with today's service.
                    Medallion(p.glyph, U.c.hue(p.hue), 52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Presets.patronName(context, p), style = UType.Heading)
                        Text(
                            Presets.patronDomain(context, p) ?: pluralStringResource(R.plurals.targets_count, count, count),
                            style = UType.Muted,
                            maxLines = 1,
                        )
                        Text(stringResource(R.string.patron_rate_short, Fmt.rate(p.rate)), style = UType.Small.copy(color = U.c.gold))
                    }
                    if (sec > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Fmt.signed(cents), style = UType.Number.copy(color = U.c.good))
                            Text(Fmt.clock(sec), style = UType.Small)
                        }
                    }
                }
            }
        }
        Gap(6.dp)
        PrimaryButton(stringResource(R.string.pantheon_new), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) { go.patron(0) }
        Gap(28.dp)
    }
}

/** Rates a patron can pay, in cents a minute. */
private val RATES = listOf(25L, 50L, 75L, 100L, 125L, 150L, 200L, 250L, 300L, 400L)
private val CAPS = listOf(0, 15, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PatronScreen(id: Long, data: OData, go: Go) {
    val context = LocalContext.current
    val container = context.container
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val existing = data.patron(id)
    var loaded by remember { mutableStateOf(id == 0L) }
    var p by remember { mutableStateOf(Patron(hue = data.patrons.size, rate = 100, capMin = 60, sort = data.patrons.size)) }
    var name by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(emptyList<String>()) }
    var sites by remember { mutableStateOf(emptyList<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, existing != null) {
        if (loaded || existing == null) return@LaunchedEffect
        p = existing
        name = Presets.patronName(context, existing)
        val t = data.patronTargets(id)
        apps = t.filter { it.kind == TargetKind.APP }.map { it.value }
        sites = t.filter { it.kind == TargetKind.SITE }.map { it.value }
        loaded = true
    }
    if (!loaded) return

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(stringResource(if (id == 0L) R.string.patron_new_title else R.string.patron_edit_title), back = { go.back() }) {
            if (id != 0L) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { confirmDelete = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = U.c.muted)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Medallion(p.glyph, U.c.hue(p.hue), 64.dp)
                Spacer(Modifier.width(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.patron_name_hint), style = UType.Muted) },
                    singleLine = true,
                    textStyle = UType.Heading,
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
            }
            Gap(6.dp)
            Text(stringResource(R.string.patron_name_help), style = UType.Small)
            Gap(18.dp)

            Eyebrow(stringResource(R.string.patron_glyph))
            Gap(8.dp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyphs.all.forEach { (key, icon) ->
                    val on = key == p.glyph
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .background(if (on) U.c.hue(p.hue).copy(alpha = 0.2f) else U.c.surface)
                            .border(1.dp, if (on) U.c.hue(p.hue) else U.c.line, CircleShape)
                            .clickable { p = p.copy(glyph = key) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) U.c.hue(p.hue) else U.c.muted, modifier = Modifier.size(22.dp)) }
                }
            }
            Gap(16.dp)
            Eyebrow(stringResource(R.string.patron_glaze))
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                U.c.hues.forEachIndexed { i, c -> Swatch(Math.floorMod(p.hue, U.c.hues.size) == i, c) { p = p.copy(hue = i) } }
            }
            Gap(20.dp)

            Card {
                val ri = RATES.indexOf(p.rate).let { if (it < 0) RATES.indexOfFirst { r -> r >= p.rate }.coerceAtLeast(0) else it }
                StepSlider(stringResource(R.string.patron_rate), ri, 0..RATES.lastIndex, { stringResource(R.string.patron_rate_value, Fmt.rate(RATES[it])) }) {
                    p = p.copy(rate = RATES[it])
                }
                val ci = CAPS.indexOf(p.capMin).let { if (it < 0) 0 else it }
                StepSlider(stringResource(R.string.patron_cap), ci, 0..CAPS.lastIndex, {
                    if (CAPS[it] == 0) context.getString(R.string.patron_cap_none) else context.resources.getQuantityString(R.plurals.minutes, CAPS[it], CAPS[it])
                }) { p = p.copy(capMin = CAPS[it]) }
                Text(stringResource(R.string.patron_cap_help), style = UType.Small)
                Gap(6.dp)
                SwitchRow(stringResource(R.string.patron_audio), p.audio, stringResource(R.string.patron_audio_help)) { p = p.copy(audio = it) }
                SwitchRow(stringResource(R.string.patron_active), p.active) { p = p.copy(active = it) }
            }
            Gap(20.dp)
            TargetsEditor(apps, sites, { apps = it }, { sites = it }, stringResource(R.string.patron_site_hint))
            Gap(28.dp)
        }
        PrimaryButton(
            stringResource(R.string.action_save),
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // A built-in patron keeps following the app language until its name is actually changed.
            val keepPreset = existing?.preset != null && name == Presets.patronName(context, existing)
            val toSave = p.copy(name = if (keepPreset) "" else name.trim(), preset = if (keepPreset) existing?.preset else null)
            val targets = apps.map { Target(owner = Owner.PATRON, ownerId = 0, kind = TargetKind.APP, value = it) } +
                sites.map { Target(owner = Owner.PATRON, ownerId = 0, kind = TargetKind.SITE, value = it) }
            container.write { savePatron(toSave, targets) }
            go.back()
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.patron_delete_title), style = UType.Heading) },
            text = { Text(stringResource(R.string.patron_delete_text), style = UType.Body) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    scope.launch {
                        container.dao.deletePatron(id)
                        go.back()
                    }
                }) { Text(stringResource(R.string.action_delete), color = U.c.danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.action_cancel), color = U.c.muted) } },
            containerColor = U.c.surface,
        )
    }
}
