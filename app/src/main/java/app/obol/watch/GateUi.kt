package app.obol.watch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.obol.R
import app.obol.data.Crossing
import app.obol.data.Settings
import app.obol.domain.Fare
import app.obol.domain.Hour
import app.obol.domain.Obols
import app.obol.domain.Quote
import app.obol.domain.Tariff
import app.obol.ui.components.Boat
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.Meander
import app.obol.ui.components.Obol
import app.obol.ui.components.PrimaryButton
import app.obol.ui.theme.ObolTheme
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { WHY, BREATHE, FARE }

/** Why you want to cross; kept on the ledger line. */
enum class Why(val key: String) { BORED("bored"), FRIEND("friend"), REST("rest"), THING("thing") }

@Composable
fun GateScreen(
    state: GateState,
    settings: Settings,
    quote: suspend () -> Quote,
    pay: suspend (Quote, Fare, String) -> Boolean,
    back: () -> Unit,
) = ObolTheme(dark = true) {
    val scope = rememberCoroutineScope()
    var over by remember(state) { mutableStateOf(state !is GateState.Ask) }
    var step by remember(state) { mutableStateOf(if (settings.askWhy) Step.WHY else if (settings.pauseSec > 0) Step.BREATHE else Step.FARE) }
    var why by remember(state) { mutableStateOf<Why?>(null) }
    var q by remember(state) { mutableStateOf<Quote?>(null) }
    var refused by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) { q = quote() }

    Box(
        Modifier
            .fillMaxSize()
            .background(U.c.bg.copy(alpha = 0.97f))
            .clickable(enabled = false) {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(20.dp)
            Meander()
            Gap(18.dp)
            Boat(Modifier.fillMaxWidth().height(150.dp))
            Gap(10.dp)
            Text(state.stream.name, style = UType.Eyebrow.copy(color = U.c.lethe, fontSize = 13.sp))
            Gap(6.dp)
            val title = when {
                over && state is GateState.Over -> stringResource(R.string.gate_over_title)
                over && state is GateState.Broke -> stringResource(R.string.gate_broke_title)
                else -> stringResource(R.string.gate_title)
            }
            Text(title, style = UType.Display.copy(textAlign = TextAlign.Center))
            Gap(22.dp)

            AnimatedContent(
                targetState = if (over) null else step,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "gate",
            ) { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        null -> {
                            Text(
                                stringResource(if (state is GateState.Broke) R.string.gate_broke_sub else R.string.gate_over_sub),
                                style = UType.Sub.copy(textAlign = TextAlign.Center),
                            )
                            Gap(24.dp)
                            PrimaryButton(stringResource(R.string.gate_return), Modifier.fillMaxWidth(), onClick = back)
                            Gap(10.dp)
                            GhostButton(stringResource(R.string.gate_pay_more), Modifier.fillMaxWidth()) {
                                over = false
                                scope.launch { q = quote() }
                            }
                        }
                        Step.WHY -> WhyStep { w ->
                            why = w
                            step = if (settings.pauseSec > 0) Step.BREATHE else Step.FARE
                        }
                        Step.BREATHE -> Breathe(settings.pauseSec) { step = Step.FARE }
                        Step.FARE -> q?.let { quote ->
                            FareStep(quote, refused) { fare ->
                                scope.launch {
                                    val ok = pay(quote, fare, why?.key.orEmpty())
                                    if (!ok) {
                                        refused = true
                                        q = quote()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!over) {
                Gap(18.dp)
                Text(
                    stringResource(R.string.gate_back),
                    style = UType.Body.copy(color = U.c.muted),
                    modifier = Modifier.clip(U.RXl).clickable(onClick = back).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            Gap(24.dp)
        }
    }
}

@Composable
private fun WhyStep(pick: (Why) -> Unit) {
    Text(stringResource(R.string.gate_why), style = UType.Heading)
    Gap(14.dp)
    val labels = mapOf(
        Why.BORED to R.string.why_bored,
        Why.FRIEND to R.string.why_friend,
        Why.REST to R.string.why_rest,
        Why.THING to R.string.why_thing,
    )
    labels.forEach { (w, res) ->
        GhostButton(stringResource(res), Modifier.fillMaxWidth().padding(vertical = 4.dp)) { pick(w) }
    }
}

/** A few slow seconds before the coin changes hands. */
@Composable
private fun Breathe(seconds: Int, done: () -> Unit) {
    var left by remember { mutableIntStateOf(seconds) }
    var inhale by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left--
            inhale = !inhale
        }
        done()
    }
    val scale by animateFloatAsState(if (inhale) 1f else 0.62f, tween(1000), label = "breath")
    val accent = U.c.accent
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = 0.12f), size.minDimension / 2 * scale)
            drawCircle(accent.copy(alpha = 0.6f), size.minDimension / 2 * scale, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        Text("$left", style = UType.Title)
    }
    Gap(12.dp)
    Text(stringResource(R.string.gate_breathe), style = UType.Sub.copy(textAlign = TextAlign.Center))
}

@Composable
private fun FareStep(q: Quote, refused: Boolean, choose: (Fare) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Obol(22.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.gate_balance, Fmt.obols(q.balance)), style = UType.Strong)
    }
    Gap(6.dp)
    Text(stringResource(R.string.gate_price, Fmt.rate(q.perMin)), style = UType.Sub)
    val notes = buildList {
        when (q.hour) {
            Hour.NIGHT -> add(stringResource(R.string.gate_night, Fmt.times(q.rules.nightX)))
            Hour.MORNING -> add(stringResource(R.string.gate_morning, Fmt.times(q.rules.morningX)))
            Hour.DAY -> Unit
        }
        if (q.nth > 0 && q.rules.climbOn) add(stringResource(R.string.gate_climb, q.nth + 1, Fmt.times(Tariff.climbX(q.nth, q.rules))))
    }
    notes.forEach {
        Gap(4.dp)
        Text(it, style = UType.Muted.copy(color = U.c.gold, textAlign = TextAlign.Center))
    }
    if (refused) {
        Gap(8.dp)
        Text(stringResource(R.string.gate_refused), style = UType.Muted.copy(color = U.c.danger, textAlign = TextAlign.Center))
    }
    Gap(18.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(5, 15, 30).forEach { min ->
            val cost = q.cost(min)
            FareTile(
                title = pluralStringResource(R.plurals.minutes, min, min),
                price = cost?.let { Fmt.obols(it) },
                debt = cost != null && cost > q.perMin * min,
                modifier = Modifier.weight(1f),
            ) { choose(Fare.Minutes(min)) }
        }
    }
    Gap(10.dp)
    GhostButton(
        stringResource(R.string.gate_meter, Fmt.rate(q.perMin)),
        Modifier.fillMaxWidth(),
        enabled = q.canMeter,
    ) { choose(Fare.Meter) }
    if (q.passesLeft > 0) {
        Gap(10.dp)
        GhostButton(
            stringResource(R.string.gate_pass, q.passesLeft),
            Modifier.fillMaxWidth(),
            color = U.c.lethe,
        ) { choose(Fare.Pass) }
    }
    if (q.balance < q.perMin * 5 && q.rules.debtLimit > 0) {
        Gap(10.dp)
        Text(
            stringResource(R.string.gate_debt_note, q.rules.debtPct, Obols.whole(q.rules.debtLimit)),
            style = UType.Small.copy(textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun FareTile(title: String, price: String?, debt: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val enabled = price != null
    Column(
        modifier
            .clip(U.RMd)
            .background(if (enabled) U.c.surface else U.c.bg)
            .border(1.dp, if (enabled) U.c.accent.copy(alpha = 0.6f) else U.c.line, U.RMd)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = UType.Body.copy(color = if (enabled) U.c.ink else U.c.faint))
        Gap(4.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(price ?: "—", style = UType.Number.copy(color = if (enabled) U.c.gold else U.c.faint))
            if (enabled) {
                Spacer(Modifier.width(4.dp))
                Obol(14.dp)
            }
        }
        if (debt) Text(stringResource(R.string.gate_on_debt), style = UType.Small.copy(color = U.c.danger))
    }
}

/** The little plaque at the top of the screen while you're on the far shore. */
@Composable
fun Badge(c: Crossing, balance: Long) = ObolTheme(dark = true) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(top = 4.dp)
            .clip(U.RXl)
            .background(U.c.bg.copy(alpha = 0.86f))
            .border(1.dp, U.c.accent.copy(alpha = 0.5f), U.RXl)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (c.meter) {
            Obol(14.dp)
            Spacer(Modifier.width(6.dp))
            Text("${Fmt.obols(balance)} · −${Fmt.rate(c.perMin)}/${stringResource(R.string.unit_min)}", style = UType.Small.copy(color = U.c.ink, fontSize = 12.sp))
        } else {
            Icon(Icons.Rounded.HourglassBottom, null, tint = U.c.gold, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(Fmt.clock(c.leftSec), style = UType.Small.copy(color = U.c.ink, fontSize = 12.sp, fontFeatureSettings = "tnum"))
        }
    }
}
