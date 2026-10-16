package app.obol.ui.rite

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.obol.R
import app.obol.container
import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.data.Patron
import app.obol.data.Presets
import app.obol.domain.Clock
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Chip
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.Medallion
import app.obol.ui.components.Obol
import app.obol.ui.components.PrimaryButton
import app.obol.ui.components.TopBar
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.delay

private enum class Phase { SETUP, RUNNING, DONE, BROKEN }

private val LENGTHS = listOf(10, 15, 25, 45, 60, 90)

/** Seconds the phone may lie face up before the rite is broken. */
private const val GRACE = 15

/**
 * A rite: time spent away from the phone (a paper book, practice, a workout). The timer only
 * runs while the phone lies face down, and picking it up for long breaks the rite.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiteScreen(data: OData, go: Go) {
    val context = LocalContext.current
    val patrons = data.patrons.filter { it.active }
    var patronId by rememberSaveable { mutableStateOf(patrons.firstOrNull()?.id ?: 0L) }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var phase by rememberSaveable { mutableStateOf(Phase.SETUP) }
    var done by rememberSaveable { mutableIntStateOf(0) }
    var faceUpFor by remember { mutableIntStateOf(0) }
    val patron = patrons.firstOrNull { it.id == patronId }

    val down = rememberFaceDown(active = phase == Phase.RUNNING)
    KeepScreenOn(phase == Phase.RUNNING)

    // Leaving the app breaks the rite.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, phase) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP && phase == Phase.RUNNING) phase = Phase.BROKEN }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(phase) {
        if (phase != Phase.RUNNING) return@LaunchedEffect
        while (phase == Phase.RUNNING) {
            delay(1000)
            if (down.value) {
                faceUpFor = 0
                done++
            } else if (done > 0) {
                faceUpFor++
                if (faceUpFor > GRACE) phase = Phase.BROKEN
            }
            if (done >= minutes * 60) {
                val p = patron ?: break
                context.container.dao.insertEntry(
                    Entry(
                        at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.RITE,
                        cents = p.rate * minutes, patronId = p.id, seconds = done, note = "$minutes",
                    ),
                )
                buzz(context)
                phase = Phase.DONE
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.rite_title), back = { go.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (phase) {
                Phase.SETUP -> {
                    Text(stringResource(R.string.rite_sub), style = UType.Sub.copy(textAlign = TextAlign.Center))
                    Gap(20.dp)
                    Eyebrow(stringResource(R.string.rite_whom), Modifier.fillMaxWidth())
                    Gap(8.dp)
                    patrons.forEach { p -> PatronRow(p, p.id == patronId) { patronId = p.id } }
                    Gap(16.dp)
                    Eyebrow(stringResource(R.string.rite_length), Modifier.fillMaxWidth())
                    Gap(8.dp)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LENGTHS.forEach { m -> Chip(pluralStringResource(R.plurals.minutes, m, m), m == minutes) { minutes = m } }
                    }
                    Gap(20.dp)
                    if (patron != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.rite_reward, Fmt.obols(patron.rate * minutes)), style = UType.Strong.copy(color = U.c.gold))
                            Spacer(Modifier.width(6.dp))
                            Obol(18.dp)
                        }
                    }
                }
                Phase.RUNNING -> {
                    Gap(20.dp)
                    val total = minutes * 60
                    val accent = U.c.accent
                    val line = U.c.line
                    Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val st = 6.dp.toPx()
                            drawArc(line, 0f, 360f, false, Offset(st, st), Size(size.width - 2 * st, size.height - 2 * st), style = Stroke(st))
                            drawArc(accent, -90f, 360f * done / total, false, Offset(st, st), Size(size.width - 2 * st, size.height - 2 * st), style = Stroke(st, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.clock(total - done), style = UType.Display)
                            patron?.let { Text(Presets.patronName(context, it), style = UType.Muted) }
                        }
                    }
                    Gap(24.dp)
                    Text(
                        when {
                            down.value -> stringResource(R.string.rite_running)
                            done == 0 -> stringResource(R.string.rite_put_down)
                            else -> stringResource(R.string.rite_lifted, (GRACE - faceUpFor).coerceAtLeast(0))
                        },
                        style = UType.Sub.copy(textAlign = TextAlign.Center, color = if (!down.value && done > 0) U.c.danger else U.c.muted),
                    )
                }
                Phase.DONE -> {
                    Gap(40.dp)
                    Obol(72.dp)
                    Gap(18.dp)
                    Text(stringResource(R.string.rite_done_title), style = UType.Title.copy(textAlign = TextAlign.Center))
                    Gap(8.dp)
                    Text(
                        stringResource(R.string.rite_done_sub, patron?.let { Presets.patronName(context, it) }.orEmpty(), Fmt.obols((patron?.rate ?: 0) * minutes)),
                        style = UType.Sub.copy(textAlign = TextAlign.Center),
                    )
                }
                Phase.BROKEN -> {
                    Gap(40.dp)
                    Text(stringResource(R.string.rite_broken_title), style = UType.Title.copy(textAlign = TextAlign.Center))
                    Gap(8.dp)
                    Text(stringResource(R.string.rite_broken_sub), style = UType.Sub.copy(textAlign = TextAlign.Center))
                }
            }
        }
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            when (phase) {
                Phase.SETUP -> PrimaryButton(stringResource(R.string.rite_start), Modifier.fillMaxWidth(), enabled = patron != null) {
                    done = 0
                    faceUpFor = 0
                    phase = Phase.RUNNING
                }
                Phase.RUNNING -> GhostButton(stringResource(R.string.rite_give_up), Modifier.fillMaxWidth()) { phase = Phase.BROKEN }
                Phase.DONE -> PrimaryButton(stringResource(R.string.action_done), Modifier.fillMaxWidth()) { go.back() }
                Phase.BROKEN -> PrimaryButton(stringResource(R.string.rite_again), Modifier.fillMaxWidth()) { phase = Phase.SETUP }
            }
        }
    }
}

@Composable
private fun PatronRow(p: Patron, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(U.RMd)
            .background(if (selected) U.c.accentSoft else U.c.surface)
            .border(1.dp, if (selected) U.c.accent else U.c.line, U.RMd)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Medallion(p.glyph, U.c.hue(p.hue), 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(Presets.patronName(context, p), style = UType.Body, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.patron_rate_short, Fmt.rate(p.rate)), style = UType.Small.copy(color = U.c.gold))
    }
}

/** Whether the phone lies face down, from the accelerometer. */
@Composable
private fun rememberFaceDown(active: Boolean): androidx.compose.runtime.State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                state.value = e.values[2] < -7f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(on) {
        if (on) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

private fun buzz(context: Context) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
    }
}
