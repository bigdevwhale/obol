package app.obol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.obol.domain.Obols
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.delay

/** The ground of every screen: black with a warm glow from above, like a kiln. */
@Composable
fun Ground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to U.c.bgTop, 0.45f to U.c.bg, 1f to U.c.bg)),
        content = content,
    )
}

@Composable
fun TopBar(title: String, back: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = back),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = U.c.ink) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = UType.Title, modifier = Modifier.weight(1f).padding(horizontal = 6.dp), maxLines = 1)
        trailing()
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(18.dp), onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(U.RLg)
            .background(U.c.surface)
            .border(1.dp, U.c.line, U.RLg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = U.c.faint) {
    Text(text.uppercase(), style = UType.Eyebrow.copy(color = color), modifier = modifier)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = U.c.accent, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(U.RXl)
            .background(if (enabled) color else U.c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) U.c.onAccent else U.c.faint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = UType.Button.copy(color = if (enabled) U.c.onAccent else U.c.faint), textAlign = TextAlign.Center)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = U.c.ink, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 52.dp)
            .clip(U.RXl)
            .border(1.dp, U.c.line, U.RXl)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) color else U.c.faint
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = UType.Button.copy(color = tint), textAlign = TextAlign.Center)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(U.RXl)
            .background(if (selected) U.c.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) U.c.accent else U.c.line, U.RXl)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, style = UType.Body.copy(color = if (selected) U.c.ink else U.c.muted))
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = UType.Body)
            if (sub != null) Text(sub, style = UType.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = U.c.onAccent,
                checkedTrackColor = U.c.accent,
                uncheckedThumbColor = U.c.faint,
                uncheckedTrackColor = U.c.surface2,
                uncheckedBorderColor = U.c.line,
            ),
        )
    }
}

/** A slider over whole steps with its value shown on the right. */
@Composable
fun StepSlider(title: String, value: Int, range: IntRange, label: @Composable (Int) -> String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = UType.Body, modifier = Modifier.weight(1f))
            Text(label(value), style = UType.Strong.copy(color = U.c.gold))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = U.c.accent,
                activeTrackColor = U.c.accent,
                inactiveTrackColor = U.c.surface2,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** The current time, ticking every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

object Fmt {
    /** Whole obols with a sign only for debts. */
    fun obols(cents: Long): String = Obols.whole(cents).toString()

    /** A signed amount for a ledger line: +12, −3. */
    fun signed(cents: Long): String {
        val w = Obols.whole(cents)
        return when {
            w > 0 -> "+$w"
            w < 0 -> "−${-w}"
            else -> if (cents > 0) "+0" else if (cents < 0) "−0" else "0"
        }
    }

    /** A rate in obols a minute: 1.5, 2, 0.75. */
    fun rate(cents: Long): String {
        val whole = cents / 100
        val frac = cents % 100
        return if (frac == 0L) "$whole" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}"
    }

    fun clock(sec: Int): String {
        val s = sec.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    fun hm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    fun times(tenths: Int): String = if (tenths % 10 == 0) "×${tenths / 10}" else "×${tenths / 10}.${tenths % 10}"
}

/** A length of time in words: 36 min, 1 h 5 min, 3 h. Countdowns use [Fmt.clock] instead. */
@Composable
fun duration(sec: Int): String {
    val m = (sec + 30) / 60
    return when {
        m < 60 -> androidx.compose.ui.res.stringResource(app.obol.R.string.dur_min, m)
        m % 60 == 0 -> androidx.compose.ui.res.stringResource(app.obol.R.string.dur_h, m / 60)
        else -> androidx.compose.ui.res.stringResource(app.obol.R.string.dur_hm, m / 60, m % 60)
    }
}
