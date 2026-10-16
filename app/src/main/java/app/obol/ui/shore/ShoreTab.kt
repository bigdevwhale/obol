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
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.obol.data.Presets
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(LocalDate.now().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), Modifier.weight(1f))
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

        Ostrakon(today, data, stringResource(R.string.shore_today))
        Gap(14.dp)
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
        Gap(28.dp)
    }
}
