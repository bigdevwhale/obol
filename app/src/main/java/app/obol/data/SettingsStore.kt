package app.obol.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.obol.domain.Obols
import app.obol.domain.TariffRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    /** Black-figure is the app's home; red-figure (light) is an option. */
    val theme: ThemeMode = ThemeMode.DARK,
    val onboarded: Boolean = false,
    val nightOn: Boolean = true,
    val nightFrom: Int = 23 * 60,
    val nightTo: Int = 6 * 60,
    /** Tenths: 30 = ×3. */
    val nightX: Int = 30,
    val morningOn: Boolean = true,
    val morningTo: Int = 9 * 60,
    val morningX: Int = 20,
    val climbOn: Boolean = true,
    /** Hermes' staff: free short crossings a week. */
    val passes: Int = 3,
    /** Seconds of breathing before Charon takes the coin. */
    val pauseSec: Int = 5,
    /** Charon asks why you want to cross. */
    val askWhy: Boolean = true,
    val debtOn: Boolean = true,
    /** Whole obols. */
    val debtLimit: Int = 30,
    val debtPct: Int = 20,
    /** No touch or scroll for this long and the patron stops counting. */
    val idleSec: Int = 90,
    val haptics: Boolean = true,
    /** The plaque with the time left, drawn in the corner over other apps. */
    val badgeOn: Boolean = true,
    /** The oracle names a patron who pays double for two hours each day. */
    val oracleOn: Boolean = true,
    /** The mist of Lethe creeps in from the edges the longer you stay on the far shore. */
    val mistOn: Boolean = true,
    /** The treasury's interest a week, in percent. */
    val interestPct: Int = 5,
    /** Epoch day of the Monday the treasury was last paid. */
    val lastAccrual: Long = 0,
    /** The oath by the Styx holds until this moment (epoch ms); 0 = no oath. */
    val oathUntil: Long = 0,
    /** When an oath was last broken (epoch ms). It stays on record. */
    val oathBrokenAt: Long = 0,
    /** Epoch day on which the oracle's blessing was last announced. */
    val oracleTold: Long = 0,
) {
    fun oath(now: Long = System.currentTimeMillis()): Boolean = oathUntil > now

    val tariff: TariffRules
        get() = TariffRules(
            nightOn = nightOn, nightFrom = nightFrom, nightTo = nightTo, nightX = nightX,
            morningOn = morningOn, morningTo = morningTo, morningX = morningX,
            climbOn = climbOn,
            debtLimit = if (debtOn) debtLimit * Obols.ONE else 0L,
            debtPct = debtPct,
        )
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<Settings> = store.data.map { it.read() }

    private fun Preferences.read() = Settings(
        theme = this[THEME]?.let { t -> runCatching { ThemeMode.valueOf(t) }.getOrNull() } ?: ThemeMode.DARK,
        onboarded = this[ONBOARDED] ?: false,
        nightOn = this[NIGHT_ON] ?: true,
        nightFrom = this[NIGHT_FROM] ?: (23 * 60),
        nightTo = this[NIGHT_TO] ?: (6 * 60),
        nightX = this[NIGHT_X] ?: 30,
        morningOn = this[MORNING_ON] ?: true,
        morningTo = this[MORNING_TO] ?: (9 * 60),
        morningX = this[MORNING_X] ?: 20,
        climbOn = this[CLIMB] ?: true,
        passes = this[PASSES] ?: 3,
        pauseSec = this[PAUSE] ?: 5,
        askWhy = this[ASK_WHY] ?: true,
        debtOn = this[DEBT_ON] ?: true,
        debtLimit = this[DEBT_LIMIT] ?: 30,
        debtPct = this[DEBT_PCT] ?: 20,
        idleSec = this[IDLE] ?: 90,
        haptics = this[HAPTICS] ?: true,
        badgeOn = this[BADGE] ?: true,
        oracleOn = this[ORACLE] ?: true,
        mistOn = this[MIST] ?: true,
        interestPct = this[INTEREST] ?: 5,
        lastAccrual = this[ACCRUAL] ?: 0L,
        oathUntil = this[OATH] ?: 0L,
        oathBrokenAt = this[OATH_BROKEN] ?: 0L,
        oracleTold = this[ORACLE_TOLD] ?: 0L,
    )

    suspend fun now(): Settings = settings.first()

    suspend fun update(change: (Settings) -> Settings) {
        store.edit {
            val s = change(it.read())
            it[THEME] = s.theme.name
            it[ONBOARDED] = s.onboarded
            it[NIGHT_ON] = s.nightOn
            it[NIGHT_FROM] = s.nightFrom
            it[NIGHT_TO] = s.nightTo
            it[NIGHT_X] = s.nightX
            it[MORNING_ON] = s.morningOn
            it[MORNING_TO] = s.morningTo
            it[MORNING_X] = s.morningX
            it[CLIMB] = s.climbOn
            it[PASSES] = s.passes
            it[PAUSE] = s.pauseSec
            it[ASK_WHY] = s.askWhy
            it[DEBT_ON] = s.debtOn
            it[DEBT_LIMIT] = s.debtLimit
            it[DEBT_PCT] = s.debtPct
            it[IDLE] = s.idleSec
            it[HAPTICS] = s.haptics
            it[BADGE] = s.badgeOn
            it[ORACLE] = s.oracleOn
            it[MIST] = s.mistOn
            it[INTEREST] = s.interestPct
            it[ACCRUAL] = s.lastAccrual
            it[OATH] = s.oathUntil
            it[OATH_BROKEN] = s.oathBrokenAt
            it[ORACLE_TOLD] = s.oracleTold
        }
    }

    /** When the service last saw the screen; lets the app tell "Charon sleeps" from "nothing happened". */
    val heartbeat: Flow<Long> = store.data.map { it[HEARTBEAT] ?: 0L }

    suspend fun beat(at: Long) {
        store.edit { it[HEARTBEAT] = at }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val NIGHT_ON = booleanPreferencesKey("night_on")
        val NIGHT_FROM = intPreferencesKey("night_from")
        val NIGHT_TO = intPreferencesKey("night_to")
        val NIGHT_X = intPreferencesKey("night_x")
        val MORNING_ON = booleanPreferencesKey("morning_on")
        val MORNING_TO = intPreferencesKey("morning_to")
        val MORNING_X = intPreferencesKey("morning_x")
        val CLIMB = booleanPreferencesKey("climb")
        val PASSES = intPreferencesKey("passes")
        val PAUSE = intPreferencesKey("pause")
        val ASK_WHY = booleanPreferencesKey("ask_why")
        val DEBT_ON = booleanPreferencesKey("debt_on")
        val DEBT_LIMIT = intPreferencesKey("debt_limit")
        val DEBT_PCT = intPreferencesKey("debt_pct")
        val IDLE = intPreferencesKey("idle")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HEARTBEAT = longPreferencesKey("heartbeat")
        val BADGE = booleanPreferencesKey("badge")
        val ORACLE = booleanPreferencesKey("oracle")
        val MIST = booleanPreferencesKey("mist")
        val INTEREST = intPreferencesKey("interest")
        val ACCRUAL = longPreferencesKey("accrual")
        val OATH = longPreferencesKey("oath")
        val OATH_BROKEN = longPreferencesKey("oath_broken")
        val ORACLE_TOLD = longPreferencesKey("oracle_told")
    }
}
