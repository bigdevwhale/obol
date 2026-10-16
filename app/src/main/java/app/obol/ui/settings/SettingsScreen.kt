package app.obol.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.obol.watch.Styx
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import app.obol.BuildConfig
import app.obol.R
import app.obol.container
import app.obol.data.Settings
import app.obol.data.ThemeMode
import app.obol.ui.Go
import app.obol.ui.OData
import app.obol.ui.components.Card
import app.obol.ui.components.Chip
import app.obol.ui.components.Eyebrow
import app.obol.ui.components.Fmt
import app.obol.ui.components.Gap
import app.obol.ui.components.GhostButton
import app.obol.ui.components.PrimaryButton
import app.obol.ui.components.StepSlider
import app.obol.ui.components.SwitchRow
import app.obol.ui.components.TopBar
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(data: OData, go: Go) {
    val context = LocalContext.current
    val container = context.container
    val s = data.settings
    fun set(change: (Settings) -> Settings) {
        container.scope.launch { container.settings.update(change) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), back = { go.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            // Charon himself
            Section(stringResource(R.string.settings_charon)) {
                Text(
                    stringResource(if (data.charon) R.string.settings_charon_on else R.string.settings_charon_off),
                    style = UType.Body.copy(color = if (data.charon) U.c.good else U.c.danger),
                )
                Gap(10.dp)
                val openAccess = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (data.charon) GhostButton(stringResource(R.string.settings_charon_open), Modifier.fillMaxWidth(), onClick = openAccess)
                else PrimaryButton(stringResource(R.string.shore_wake), Modifier.fillMaxWidth(), onClick = openAccess)
                Gap(10.dp)
                Text(stringResource(R.string.settings_autostart), style = UType.Small)
                Gap(8.dp)
                GhostButton(stringResource(R.string.settings_app_info), Modifier.fillMaxWidth()) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                Gap(6.dp)
                StepSlider(stringResource(R.string.settings_idle), s.idleSec / 30, 1..10, { stringResource(R.string.settings_seconds, it * 30) }) { v ->
                    set { it.copy(idleSec = v * 30) }
                }
                Text(stringResource(R.string.settings_idle_help), style = UType.Small)
            }

            val locked = s.oath()
            if (locked) {
                Section(stringResource(R.string.settings_tariffs)) {
                    Text(stringResource(R.string.settings_locked), style = UType.Body.copy(color = U.c.lethe))
                }
            } else {
                // Charon's prices
                Section(stringResource(R.string.settings_tariffs)) {
                    SwitchRow(stringResource(R.string.settings_night), s.nightOn, stringResource(R.string.settings_night_help, Fmt.hm(s.nightFrom), Fmt.hm(s.nightTo))) { v ->
                        set { it.copy(nightOn = v) }
                    }
                    if (s.nightOn) {
                        Hours(stringResource(R.string.settings_night_from), s.nightFrom, listOf(21, 22, 23, 0)) { v -> set { it.copy(nightFrom = v) } }
                        Hours(stringResource(R.string.settings_night_to), s.nightTo, listOf(5, 6, 7)) { v -> set { it.copy(nightTo = v) } }
                        StepSlider(stringResource(R.string.settings_multiplier), s.nightX / 5, 3..8, { Fmt.times(it * 5) }) { v -> set { it.copy(nightX = v * 5) } }
                    }
                    SwitchRow(stringResource(R.string.settings_morning), s.morningOn, stringResource(R.string.settings_morning_help, Fmt.hm(s.morningTo))) { v ->
                        set { it.copy(morningOn = v) }
                    }
                    if (s.morningOn) {
                        Hours(stringResource(R.string.settings_morning_to), s.morningTo, listOf(8, 9, 10, 11)) { v -> set { it.copy(morningTo = v) } }
                        StepSlider(stringResource(R.string.settings_multiplier), s.morningX / 5, 3..8, { Fmt.times(it * 5) }) { v -> set { it.copy(morningX = v * 5) } }
                    }
                    SwitchRow(stringResource(R.string.settings_climb), s.climbOn, stringResource(R.string.settings_climb_help)) { v -> set { it.copy(climbOn = v) } }
                }

                // The gate
                Section(stringResource(R.string.settings_gate)) {
                    SwitchRow(stringResource(R.string.settings_ask_why), s.askWhy) { v -> set { it.copy(askWhy = v) } }
                    SwitchRow(stringResource(R.string.settings_badge), s.badgeOn, stringResource(R.string.settings_badge_help)) { v -> set { it.copy(badgeOn = v) } }
                    StepSlider(stringResource(R.string.settings_pause), s.pauseSec, 0..15, { stringResource(R.string.settings_seconds, it) }) { v -> set { it.copy(pauseSec = v) } }
                    StepSlider(stringResource(R.string.settings_passes), s.passes, 0..7, { "$it" }) { v -> set { it.copy(passes = v) } }
                    Text(stringResource(R.string.settings_passes_help), style = UType.Small)
                }

                // Owing Hades
                Section(stringResource(R.string.settings_debt)) {
                    SwitchRow(stringResource(R.string.settings_debt_on), s.debtOn, stringResource(R.string.settings_debt_help)) { v -> set { it.copy(debtOn = v) } }
                    if (s.debtOn) {
                        StepSlider(stringResource(R.string.settings_debt_limit), s.debtLimit / 10, 1..10, { pluralStringResource(R.plurals.obols, it * 10, it * 10) }) { v ->
                            set { it.copy(debtLimit = v * 10) }
                        }
                        StepSlider(stringResource(R.string.settings_debt_pct), s.debtPct / 5, 0..10, { "${it * 5}%" }) { v -> set { it.copy(debtPct = v * 5) } }
                    }
                }
            }

            // The temple and the oracle
            Section(stringResource(R.string.settings_temple)) {
                SwitchRow(stringResource(R.string.settings_oracle), s.oracleOn, stringResource(R.string.settings_oracle_help)) { v -> set { it.copy(oracleOn = v) } }
                SwitchRow(stringResource(R.string.settings_mist), s.mistOn, stringResource(R.string.settings_mist_help)) { v -> set { it.copy(mistOn = v) } }
                StepSlider(stringResource(R.string.settings_interest), s.interestPct, 0..10, { "$it%" }) { v -> set { it.copy(interestPct = v) } }
            }

            // The oath by the Styx
            Section(stringResource(R.string.settings_oath)) { Oath(s) { change -> set(change) } }

            Section(stringResource(R.string.widget_name)) {
                Text(stringResource(R.string.widget_desc), style = UType.Muted)
                Gap(10.dp)
                GhostButton(stringResource(R.string.settings_widget_add), Modifier.fillMaxWidth()) {
                    val asked = app.obol.widget.PurseWidget.pin(context)
                    // HyperOS ignores the request unless "Home screen shortcuts" is allowed for the app.
                    val xiaomi = android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
                    if (!asked || xiaomi) {
                        android.widget.Toast.makeText(context, if (asked) R.string.settings_widget_xiaomi else R.string.settings_widget_manual, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }

            Section(stringResource(R.string.settings_look)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.theme_dark), s.theme == ThemeMode.DARK) { set { it.copy(theme = ThemeMode.DARK) } }
                    Chip(stringResource(R.string.theme_light), s.theme == ThemeMode.LIGHT) { set { it.copy(theme = ThemeMode.LIGHT) } }
                    Chip(stringResource(R.string.theme_system), s.theme == ThemeMode.SYSTEM) { set { it.copy(theme = ThemeMode.SYSTEM) } }
                }
                Gap(14.dp)
                Eyebrow(stringResource(R.string.settings_language))
                Gap(8.dp)
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.lang_system), current.isEmpty()) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
                    Chip("English", current.startsWith("en")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                    Chip("Русский", current.startsWith("ru")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru")) }
                }
            }

            Gap(10.dp)
            Text(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME), style = UType.Small, modifier = Modifier.padding(horizontal = 4.dp))
            Gap(28.dp)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Gap(10.dp)
    Eyebrow(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
    Card { content() }
    Gap(6.dp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hours(title: String, value: Int, hours: List<Int>, onChange: (Int) -> Unit) {
    Text(title, style = UType.Muted, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        hours.forEach { h -> Chip(Fmt.hm(h * 60), value == h * 60) { onChange(h * 60) } }
    }
}

private val OATH_DAYS = listOf(1, 3, 7, 30)

/** Swearing by the Styx: pick the days, become an administrator, and the gate's settings lock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Oath(s: Settings, set: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var days by remember { mutableIntStateOf(3) }
    val swear = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Styx.active(context)) set { it.copy(oathUntil = System.currentTimeMillis() + days * 86_400_000L) }
    }
    val now = System.currentTimeMillis()
    if (s.oath(now)) {
        val t = java.time.Instant.ofEpochMilli(s.oathUntil).atZone(java.time.ZoneId.systemDefault())
        Text(stringResource(R.string.settings_oath_until, "%02d.%02d %02d:%02d".format(t.dayOfMonth, t.monthValue, t.hour, t.minute)), style = UType.Body.copy(color = U.c.gold))
        Gap(6.dp)
        Text(stringResource(R.string.settings_oath_holding), style = UType.Small)
        return
    }
    Text(stringResource(R.string.settings_oath_help), style = UType.Muted)
    Gap(10.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OATH_DAYS.forEach { d -> Chip(pluralStringResource(R.plurals.days, d, d), d == days) { days = d } }
    }
    Gap(12.dp)
    PrimaryButton(stringResource(R.string.settings_oath_swear), Modifier.fillMaxWidth()) {
        if (Styx.active(context)) set { it.copy(oathUntil = System.currentTimeMillis() + days * 86_400_000L) }
        else swear.launch(Styx.request(context))
    }
    if (s.oathBrokenAt > 0) {
        Gap(8.dp)
        val t = java.time.Instant.ofEpochMilli(s.oathBrokenAt).atZone(java.time.ZoneId.systemDefault())
        Text(stringResource(R.string.settings_oath_broken, "%02d.%02d".format(t.dayOfMonth, t.monthValue)), style = UType.Small.copy(color = U.c.danger))
    }
}
