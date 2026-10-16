package app.obol.ui.onboarding

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.obol.R
import app.obol.container
import app.obol.data.Entry
import app.obol.data.EntryKind
import app.obol.domain.Clock
import app.obol.domain.Obols
import app.obol.ui.OData
import app.obol.ui.components.Boat
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.Meander
import app.obol.ui.components.Medallion
import app.obol.ui.components.Obol
import app.obol.ui.components.PrimaryButton
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.launch

/** Obols for the road: enough for a short crossing on the first evening. */
private const val FIRST_OBOLS = 20

@Composable
fun OnboardingScreen(data: OData, done: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    var page by rememberSaveable { mutableIntStateOf(0) }

    fun finish() {
        container.scope.launch {
            if (container.dao.giftCount() == 0) {
                container.dao.insertEntry(
                    Entry(at = System.currentTimeMillis(), day = Clock.day(), kind = EntryKind.GIFT, cents = FIRST_OBOLS * Obols.ONE, note = "road"),
                )
            }
            container.settings.update { it.copy(onboarded = true) }
        }
        done()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Gap(16.dp)
        Meander()
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.weight(1f),
            label = "page",
        ) { p ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (p) {
                    0 -> {
                        Boat(Modifier.fillMaxWidth().height(180.dp))
                        Gap(18.dp)
                        Text(stringResource(R.string.onb1_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                        Gap(12.dp)
                        Text(stringResource(R.string.onb1_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                    }
                    1 -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            data.patrons.take(5).forEach { Medallion(it.glyph, U.c.hue(it.hue), 48.dp) }
                        }
                        Gap(22.dp)
                        Text(stringResource(R.string.onb2_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                        Gap(12.dp)
                        Text(stringResource(R.string.onb2_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                    }
                    2 -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Obol(56.dp)
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.size(10.dp).background(U.c.faint, CircleShape))
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.size(56.dp).background(U.c.lethe.copy(alpha = 0.25f), CircleShape))
                        }
                        Gap(22.dp)
                        Text(stringResource(R.string.onb3_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                        Gap(12.dp)
                        Text(stringResource(R.string.onb3_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                    }
                    else -> {
                        Text(stringResource(R.string.onb4_title), style = UType.Display.copy(textAlign = TextAlign.Center))
                        Gap(12.dp)
                        Text(stringResource(R.string.onb4_text), style = UType.Sub.copy(textAlign = TextAlign.Center))
                        Gap(18.dp)
                        Text(
                            stringResource(if (data.charon) R.string.settings_charon_on else R.string.onb4_off),
                            style = UType.Strong.copy(color = if (data.charon) U.c.good else U.c.gold, textAlign = TextAlign.Center),
                        )
                        Gap(8.dp)
                        Text(stringResource(R.string.onb4_privacy), style = UType.Small.copy(textAlign = TextAlign.Center))
                    }
                }
            }
        }
        if (page < 3) {
            PrimaryButton(stringResource(R.string.action_next), Modifier.fillMaxWidth()) { page++ }
        } else {
            if (!data.charon) {
                PrimaryButton(stringResource(R.string.shore_wake), Modifier.fillMaxWidth()) {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Gap(10.dp)
                GhostButton(stringResource(R.string.onb_later), Modifier.fillMaxWidth()) { finish() }
            } else {
                PrimaryButton(stringResource(R.string.onb_begin), Modifier.fillMaxWidth()) { finish() }
            }
        }
        Gap(16.dp)
    }
}
