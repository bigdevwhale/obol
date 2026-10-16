package app.obol.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.obol.R
import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.data.Presets
import app.obol.ui.OData
import app.obol.ui.components.Card
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.Medallion
import app.obol.ui.components.Obol
import app.obol.ui.components.Rule
import app.obol.ui.theme.U
import app.obol.ui.theme.UType

/** One summed line of a day: a patron served, a stream crossed, a rite. */
data class Line(val key: String, val title: String, val glyph: String?, val hue: Int, val seconds: Int, val cents: Long, val count: Int, val lethe: Boolean)

/** Sums a day's entries into lines: one per patron and one per stream. */
fun lines(entries: List<Entry>, data: OData, name: (Entry) -> String): List<Line> =
    entries.groupBy { e ->
        when (e.kind) {
            EntryKind.EARN, EntryKind.RITE -> "p${e.patronId}-${e.kind}"
            EntryKind.CROSS, EntryKind.PASS -> "s${e.streamId}"
            else -> e.kind
        }
    }.map { (key, list) ->
        val e = list.first()
        val patron = data.patron(e.patronId)
        Line(
            key = key,
            title = name(e),
            glyph = patron?.glyph,
            hue = patron?.hue ?: 0,
            seconds = list.sumOf { it.seconds },
            cents = list.sumOf { it.cents },
            count = list.size,
            lethe = e.kind == EntryKind.CROSS || e.kind == EntryKind.PASS,
        )
    }.sortedWith(compareBy<Line> { it.lethe }.thenByDescending { kotlin.math.abs(it.cents) })

@Composable
fun entryName(data: OData): (Entry) -> String {
    val context = LocalContext.current
    val gift = stringResource(R.string.line_gift)
    val gone = stringResource(R.string.line_gone)
    val riteOf = stringResource(R.string.line_rite)
    return { e ->
        when (e.kind) {
            EntryKind.EARN -> data.patron(e.patronId)?.let { Presets.patronName(context, it) } ?: gone
            EntryKind.RITE -> riteOf.format(data.patron(e.patronId)?.let { Presets.patronName(context, it) } ?: gone)
            EntryKind.CROSS, EntryKind.PASS -> data.stream(e.streamId)?.let { Presets.streamName(context, it) } ?: gone
            else -> gift
        }
    }
}

/** A day written on a potsherd: what was given to the patrons, what Lethe took, and the sum. */
@Composable
fun Ostrakon(entries: List<Entry>, data: OData, title: String, modifier: Modifier = Modifier) {
    val ls = lines(entries, data, entryName(data))
    val earned = entries.filter { it.cents > 0 }.sumOf { it.cents }
    val spent = -entries.filter { it.cents < 0 }.sumOf { it.cents }
    Card(modifier) {
        Eyebrow(title)
        Gap(12.dp)
        if (ls.isEmpty()) {
            Text(stringResource(R.string.ostrakon_empty), style = UType.Muted)
            return@Card
        }
        ls.forEach { l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (l.glyph != null) {
                    Medallion(l.glyph, U.c.hue(l.hue), 30.dp)
                } else {
                    Obol(30.dp, color = if (l.lethe) U.c.lethe else U.c.gold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = UType.Body, maxLines = 1)
                    val sub = buildString {
                        if (l.seconds > 0) append(Fmt.clock(l.seconds))
                        if (l.lethe) {
                            if (isNotEmpty()) append(" · ")
                            append(pluralStringResource(R.plurals.crossings, l.count, l.count))
                        }
                    }
                    if (sub.isNotEmpty()) Text(sub, style = UType.Small)
                }
                Text(
                    Fmt.signed(l.cents),
                    style = UType.Number.copy(color = if (l.cents < 0) U.c.danger else if (l.cents > 0) U.c.good else U.c.muted),
                )
            }
        }
        Gap(6.dp)
        Rule()
        Gap(6.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.ostrakon_earned, Fmt.obols(earned)), style = UType.Muted)
                Text(stringResource(R.string.ostrakon_spent, Fmt.obols(spent)), style = UType.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.signed(earned - spent), style = UType.Title.copy(color = if (earned >= spent) U.c.gold else U.c.danger))
                Spacer(Modifier.width(6.dp))
                Obol(20.dp, Modifier.size(20.dp))
            }
        }
    }
}
