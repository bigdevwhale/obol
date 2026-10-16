package app.obol.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.obol.R
import app.obol.container
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Card
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The ledger: a frieze of the last weeks and the potsherd of any chosen day. */
@Composable
fun LedgerTab(data: OData, @Suppress("UNUSED_PARAMETER") go: Go) {
    val dao = LocalContext.current.container.dao
    var day by rememberSaveable { mutableLongStateOf(data.day) }
    val entries by remember(day) { dao.observeDay(day) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val days by remember(data.day) { dao.observeDays(data.day - 27) }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Gap(16.dp)
        Text(stringResource(R.string.ledger_title), style = UType.Display)
        Gap(4.dp)
        Text(stringResource(R.string.ledger_sub), style = UType.Sub)
        Gap(18.dp)

        // The frieze: four weeks, each day a column; gold above the line is given to the patrons, grey below is Lethe's.
        val byDay = days.associateBy { it.day }
        val peak = (days.maxOfOrNull { maxOf(it.earned, it.spent) } ?: 0L).coerceAtLeast(100L)
        Card {
            Eyebrow(stringResource(R.string.ledger_frieze))
            Gap(12.dp)
            Row(Modifier.fillMaxWidth().height(120.dp), verticalAlignment = Alignment.CenterVertically) {
                for (d in (data.day - 27)..data.day) {
                    val s = byDay[d]
                    val selected = d == day
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(U.RSm)
                            .background(if (selected) U.c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { day = d }
                            .padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            val f = (s?.earned ?: 0L).toFloat() / peak
                            if (f > 0f) Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight(f).clip(U.RSm).background(U.c.gold))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(U.c.line))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            val f = (s?.spent ?: 0L).toFloat() / peak
                            if (f > 0f) Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight(f).clip(U.RSm).background(U.c.lethe))
                        }
                    }
                }
            }
            Gap(10.dp)
            val earned = days.sumOf { it.earned }
            val spent = days.sumOf { it.spent }
            Row {
                Text(stringResource(R.string.ostrakon_earned, Fmt.obols(earned)), style = UType.Muted.copy(color = U.c.gold), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.ostrakon_spent, Fmt.obols(spent)), style = UType.Muted.copy(color = U.c.lethe))
            }
        }
        Gap(14.dp)
        val title = if (day == data.day) stringResource(R.string.shore_today)
        else LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        Ostrakon(entries, data, title)
        Gap(14.dp)

        // Every line of the day, with the reasons given to Charon.
        if (entries.isNotEmpty()) {
            Eyebrow(stringResource(R.string.ledger_lines), Modifier.padding(start = 4.dp))
            Gap(6.dp)
            val name = entryName(data)
            val time = DateTimeFormatter.ofPattern("HH:mm")
            entries.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault()).format(time),
                        style = UType.Small,
                        modifier = Modifier.width(48.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(name(e), style = UType.Body, maxLines = 1)
                        val why = reasonLabel(e.note)
                        val sub = listOfNotNull(e.seconds.takeIf { it > 0 }?.let(Fmt::clock), why).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = UType.Small)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.signed(e.cents), style = UType.Strong.copy(color = if (e.cents < 0) U.c.danger else if (e.cents > 0) U.c.good else U.c.muted))
                }
            }
        }
        Gap(28.dp)
    }
}

@Composable
private fun reasonLabel(note: String): String? {
    val debt = note.endsWith(app.obol.domain.Bank.DEBT)
    val key = note.removeSuffix(app.obol.domain.Bank.DEBT)
    val why = when (key) {
        "bored" -> stringResource(R.string.why_bored)
        "friend" -> stringResource(R.string.why_friend)
        "rest" -> stringResource(R.string.why_rest)
        "thing" -> stringResource(R.string.why_thing)
        else -> null
    }
    val debtText = if (debt) stringResource(R.string.ledger_debt) else null
    return listOfNotNull(why, debtText).joinToString(" · ").ifEmpty { null }
}
