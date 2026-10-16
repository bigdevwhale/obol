package app.obol.ui.temple

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.obol.R
import app.obol.container
import app.obol.data.Vow
import app.obol.domain.Clock
import app.obol.domain.Obols
import app.obol.domain.Temple
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Card
import app.obol.ui.components.Chip
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.Medallion
import app.obol.ui.components.Obol
import app.obol.ui.components.PrimaryButton
import app.obol.ui.components.TopBar
import app.obol.ui.components.fieldColors
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.launch
import java.time.LocalDate

private val AMOUNTS = listOf(5, 10, 25, 50, 100)
private val TARGETS = listOf(100, 200, 400, 700, 1000, 2000)

/** The temple: the treasury that grows each Monday and the vows saved up for real rewards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TempleScreen(data: OData, go: Go) {
    val context = LocalContext.current
    val c = context.container
    val temple = remember { Temple(c.dao, c.settings) }
    val scope = rememberCoroutineScope()
    val treasury by c.dao.observeTreasury().collectAsStateWithLifecycle(initialValue = 0L)
    val vows by c.dao.observeVows().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedList by c.dao.observeVowSaved().collectAsStateWithLifecycle(initialValue = emptyList())
    val saved = remember(savedList) { savedList.associate { it.vowId to it.saved } }
    var moving by remember { mutableStateOf<Move?>(null) }
    var newVow by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.temple_title), back = { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.temple_sub), style = UType.Sub)
            Gap(16.dp)

            // The treasury
            Card {
                Eyebrow(stringResource(R.string.temple_treasury))
                Gap(8.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.obols(treasury), style = UType.Display.copy(color = U.c.gold))
                    Spacer(Modifier.width(10.dp))
                    Obol(28.dp)
                }
                val monday = LocalDate.ofEpochDay(Clock.weekStart() + 7)
                val interest = treasury * data.settings.interestPct / 100
                Text(
                    stringResource(R.string.temple_interest, data.settings.interestPct, Fmt.obols(interest), "%02d.%02d".format(monday.dayOfMonth, monday.monthValue)),
                    style = UType.Muted,
                )
                Gap(14.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(stringResource(R.string.temple_deposit), Modifier.weight(1f), enabled = data.balance >= Obols.ONE) { moving = Move.Deposit }
                    GhostButton(stringResource(R.string.temple_withdraw), Modifier.weight(1f), enabled = treasury >= Obols.ONE) { moving = Move.Withdraw }
                }
            }
            Gap(22.dp)

            // Vows
            Eyebrow(stringResource(R.string.temple_vows), Modifier.padding(start = 4.dp))
            Gap(4.dp)
            Text(stringResource(R.string.temple_vows_sub), style = UType.Muted, modifier = Modifier.padding(horizontal = 4.dp))
            Gap(12.dp)
            vows.forEach { v ->
                val s = saved[v.id] ?: 0L
                VowCard(v, s, data.balance, onOffer = { moving = Move.Offer(v) }, onKeep = { scope.launch { temple.keep(v) } }, onBreak = { scope.launch { temple.breakVow(v) } })
                Gap(10.dp)
            }
            PrimaryButton(stringResource(R.string.temple_new_vow), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) { newVow = true }
            Gap(28.dp)
        }
    }

    moving?.let { m ->
        val max = when (m) {
            Move.Withdraw -> treasury
            else -> data.balance
        }
        AmountDialog(
            title = when (m) {
                Move.Deposit -> stringResource(R.string.temple_deposit)
                Move.Withdraw -> stringResource(R.string.temple_withdraw)
                is Move.Offer -> m.vow.title
            },
            max = max,
            onDismiss = { moving = null },
        ) { cents ->
            moving = null
            scope.launch {
                when (m) {
                    Move.Deposit -> temple.deposit(cents)
                    Move.Withdraw -> temple.withdraw(cents)
                    is Move.Offer -> temple.offer(m.vow, cents)
                }
            }
        }
    }

    if (newVow) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf(400) }
        var glyph by remember { mutableStateOf("star") }
        AlertDialog(
            onDismissRequest = { newVow = false },
            title = { Text(stringResource(R.string.temple_new_vow), style = UType.Heading) },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.vow_hint), style = UType.Muted) },
                        singleLine = true,
                        textStyle = UType.Body,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Gap(12.dp)
                    Eyebrow(stringResource(R.string.vow_target))
                    Gap(6.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        TARGETS.forEach { t -> Chip("$t", t == target) { target = t } }
                    }
                    Gap(12.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("star", "sun", "lyre", "tree", "brush", "book", "flame", "run").forEach { g ->
                            Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { glyph = g }.alpha(if (g == glyph) 1f else 0.4f)) {
                                Medallion(g, U.c.gold, 38.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    {
                        val t = title.trim()
                        newVow = false
                        if (t.isNotEmpty()) c.write { upsertVow(Vow(title = t, glyph = glyph, target = target * Obols.ONE, createdAt = System.currentTimeMillis())) }
                    },
                    enabled = title.isNotBlank(),
                ) { Text(stringResource(R.string.temple_vow_make), color = U.c.accent) }
            },
            dismissButton = { TextButton({ newVow = false }) { Text(stringResource(R.string.action_cancel), color = U.c.muted) } },
            containerColor = U.c.surface,
        )
    }
}

private sealed interface Move {
    data object Deposit : Move
    data object Withdraw : Move
    data class Offer(val vow: Vow) : Move
}

@Composable
private fun VowCard(v: Vow, saved: Long, balance: Long, onOffer: () -> Unit, onKeep: () -> Unit, onBreak: () -> Unit) {
    var confirmBreak by remember { mutableStateOf(false) }
    Card(Modifier.alpha(if (v.open) 1f else 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Medallion(v.glyph, U.c.gold, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = UType.Heading)
                Text(
                    when {
                        v.keptAt != null -> stringResource(R.string.vow_kept)
                        v.brokenAt != null -> stringResource(R.string.vow_broken)
                        else -> stringResource(R.string.vow_progress, Fmt.obols(saved), Fmt.obols(v.target))
                    },
                    style = UType.Muted,
                )
            }
        }
        if (v.open) {
            Gap(10.dp)
            val f = (saved.toFloat() / v.target).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(U.RXl).background(U.c.surface2)) {
                Box(Modifier.fillMaxWidth(f).height(8.dp).clip(U.RXl).background(U.c.gold))
            }
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (saved >= v.target) {
                    PrimaryButton(stringResource(R.string.vow_keep), Modifier.weight(1f), color = U.c.gold, onClick = onKeep)
                } else {
                    PrimaryButton(stringResource(R.string.vow_offer), Modifier.weight(1f), enabled = balance >= Obols.ONE, onClick = onOffer)
                }
                GhostButton(stringResource(R.string.vow_break), color = U.c.muted) { confirmBreak = true }
            }
        }
    }
    if (confirmBreak) {
        AlertDialog(
            onDismissRequest = { confirmBreak = false },
            title = { Text(stringResource(R.string.vow_break_title), style = UType.Heading) },
            text = { Text(stringResource(R.string.vow_break_text, Fmt.obols(saved)), style = UType.Body) },
            confirmButton = {
                TextButton({
                    confirmBreak = false
                    onBreak()
                }) { Text(stringResource(R.string.vow_break), color = U.c.danger) }
            },
            dismissButton = { TextButton({ confirmBreak = false }) { Text(stringResource(R.string.action_cancel), color = U.c.muted) } },
            containerColor = U.c.surface,
        )
    }
}

/** Picks a number of whole obols up to [max] cents. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmountDialog(title: String, max: Long, onDismiss: () -> Unit, onDone: (Long) -> Unit) {
    val maxWhole = Obols.whole(max).toInt()
    var amount by remember { mutableStateOf(minOf(10, maxWhole)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = UType.Heading) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$amount", style = UType.Display.copy(color = U.c.gold))
                    Spacer(Modifier.width(8.dp))
                    Obol(24.dp)
                }
                Text(pluralStringResource(R.plurals.obols_available, maxWhole, maxWhole), style = UType.Muted)
                Gap(12.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AMOUNTS.filter { it <= maxWhole }.forEach { a -> Chip("$a", a == amount) { amount = a } }
                    Chip(stringResource(R.string.temple_all), amount == maxWhole) { amount = maxWhole }
                }
            }
        },
        confirmButton = {
            TextButton({ onDone(amount * Obols.ONE) }, enabled = amount in 1..maxWhole) { Text(stringResource(R.string.action_done), color = U.c.accent) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel), color = U.c.muted) } },
        containerColor = U.c.surface,
    )
}
