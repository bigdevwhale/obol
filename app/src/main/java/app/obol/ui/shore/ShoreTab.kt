package app.obol.ui.shore

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.obol.R
import app.obol.container
import app.obol.data.EntryKind
import app.obol.data.Memory
import app.obol.data.Presets
import app.obol.domain.Oracle
import app.obol.ui.components.fieldColors
import app.obol.domain.Clock
import app.obol.domain.Hour
import app.obol.domain.Obols
import app.obol.domain.Tariff
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Boat
import app.obol.ui.components.Card
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.duration
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.Meander
import app.obol.ui.components.Medallion
import app.obol.ui.components.Obol
import app.obol.ui.components.PrimaryButton
import app.obol.ui.ledger.Ostrakon
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import app.obol.watch.CharonService
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ShoreTab(data: OData, go: Go) {
    val context = LocalContext.current
    val dao = context.container.dao
    val today by dao.observeDay(data.day).collectAsStateWithLifecycle(initialValue = emptyList())
    val passesUsed by dao.observePassesSince(Clock.weekStart()).collectAsStateWithLifecycle(initialValue = 0)
    val live by CharonService.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0])), Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { go.settings() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, null, tint = U.c.muted)
            }
        }
        Gap(8.dp)
        Boat(Modifier.fillMaxWidth().height(120.dp))
        Gap(6.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.obols(data.balance), style = UType.Big.copy(color = if (data.balance < 0) U.c.danger else U.c.ink))
            Spacer(Modifier.width(12.dp))
            Obol(40.dp)
        }
        val whole = Obols.whole(data.balance).toInt()
        Text(
            if (data.balance < 0) stringResource(R.string.shore_debt) else pluralStringResource(R.plurals.obols_in_purse, kotlin.math.abs(whole)),
            style = UType.Sub,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Gap(16.dp)
        Meander()
        Gap(16.dp)

        if (!data.charon) {
            Card {
                Text(stringResource(R.string.shore_asleep_title), style = UType.Heading)
                Gap(6.dp)
                Text(stringResource(R.string.shore_asleep_sub), style = UType.Muted)
                Gap(14.dp)
                PrimaryButton(stringResource(R.string.shore_wake), Modifier.fillMaxWidth()) {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            Gap(14.dp)
        }

        live.serving?.let { p ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Medallion(p.glyph, U.c.hue(p.hue), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Eyebrow(stringResource(R.string.shore_serving_now))
                        Text(Presets.patronName(context, p), style = UType.Heading)
                    }
                }
            }
            Gap(14.dp)
        }
        live.crossing?.let { c ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.HourglassBottom, null, tint = U.c.lethe)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (c.meter) stringResource(R.string.shore_on_meter, live.stream?.name.orEmpty())
                        else stringResource(R.string.shore_on_far_shore, live.stream?.name.orEmpty(), Fmt.clock(c.leftSec)),
                        style = UType.Body,
                    )
                }
            }
            Gap(14.dp)
        }

        // The tariff of this very minute.
        val rules = data.settings.tariff
        val hour = Tariff.hour(Clock.minuteOfDay(), rules)
        if (hour != Hour.DAY) {
            Text(
                if (hour == Hour.NIGHT) stringResource(R.string.shore_nyx, Fmt.times(rules.nightX))
                else stringResource(R.string.shore_morning, Fmt.times(rules.morningX)),
                style = UType.Muted.copy(color = U.c.gold),
            )
            Gap(10.dp)
        }

        // The oracle's word for today.
        if (data.settings.oracleOn) {
            val b = remember(data.day, data.patrons) {
                // The demo always catches a blessing in progress, for the screenshots.
                if (app.obol.BuildConfig.DEMO) data.patrons.firstOrNull { it.preset == "athena" }?.let { Oracle.demo(it.id, Clock.minuteOfDay()) }
                else Oracle.blessing(data.day, data.patrons.filter { it.active }.map { it.id })
            }
            val p = data.patron(b?.patronId)
            if (b != null && p != null) {
                val minute = Clock.minuteOfDay()
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Medallion(p.glyph, U.c.hue(p.hue), 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Eyebrow(stringResource(R.string.oracle_eyebrow), color = U.c.gold)
                            Text(
                                when {
                                    b.active(minute) -> stringResource(R.string.oracle_now, Presets.patronName(context, p), Fmt.hm(b.to))
                                    minute < b.from -> stringResource(R.string.oracle_later, Presets.patronName(context, p), Fmt.hm(b.from), Fmt.hm(b.to))
                                    else -> stringResource(R.string.oracle_past, Presets.patronName(context, p))
                                },
                                style = UType.Body,
                            )
                        }
                    }
                }
                Gap(14.dp)
            }
        }

        Ostrakon(today, data, stringResource(R.string.shore_today))
        Gap(14.dp)

        // In the evening: what is left from the far shore?
        val memory by dao.observeMemory(data.day).collectAsStateWithLifecycle(initialValue = null)
        val shoreSec = today.filter { it.kind == EntryKind.CROSS || it.kind == EntryKind.PASS }.sumOf { it.seconds }
        if (Clock.minuteOfDay() >= 18 * 60 && shoreSec >= 60 && memory == null) {
            MemoryCard(shoreSec) { text, nothing -> context.container.write { upsertMemory(Memory(data.day, text, nothing)) } }
            Gap(14.dp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(R.string.shore_rite), Modifier.weight(1f), icon = Icons.Rounded.Timer) { go.rite() }
        }
        Gap(10.dp)
        val passesLeft = (data.settings.passes - passesUsed).coerceAtLeast(0)
        Text(
            stringResource(R.string.shore_passes, passesLeft, data.settings.passes),
            style = UType.Small,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Gap(16.dp)

        // The temple
        val treasury by dao.observeTreasury().collectAsStateWithLifecycle(initialValue = 0L)
        val vows by dao.observeVows().collectAsStateWithLifecycle(initialValue = emptyList())
        Card(onClick = { go.temple() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountBalance, null, tint = U.c.gold, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.temple_title), style = UType.Heading)
                    val open = vows.count { it.open }
                    Text(stringResource(R.string.shore_temple, Fmt.obols(treasury), open), style = UType.Muted)
                }
            }
        }

        // The oath by the Styx
        val now = System.currentTimeMillis()
        if (data.settings.oath(now)) {
            Gap(12.dp)
            Text(
                stringResource(R.string.shore_oath, dateTime(data.settings.oathUntil)),
                style = UType.Small.copy(color = U.c.lethe),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else if (data.settings.oathBrokenAt > now - 7 * 86_400_000L) {
            Gap(12.dp)
            Text(
                stringResource(R.string.shore_oath_broken, dateTime(data.settings.oathBrokenAt)),
                style = UType.Small.copy(color = U.c.danger),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Gap(28.dp)
    }
}

@Composable
private fun MemoryCard(seconds: Int, save: (String, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    Card {
        Eyebrow(stringResource(R.string.memory_eyebrow), color = U.c.lethe)
        Gap(6.dp)
        Text(stringResource(R.string.memory_question, duration(seconds)), style = UType.Heading)
        Gap(10.dp)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.memory_hint), style = UType.Muted) },
            textStyle = UType.Body,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(stringResource(R.string.action_save), Modifier.weight(1f), enabled = text.isNotBlank()) { save(text.trim(), false) }
            GhostButton(stringResource(R.string.memory_nothing), Modifier.weight(1f), color = U.c.lethe) { save("", true) }
        }
    }
}

private fun dateTime(ms: Long): String {
    val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return "%02d.%02d %02d:%02d".format(t.dayOfMonth, t.monthValue, t.hour, t.minute)
}
