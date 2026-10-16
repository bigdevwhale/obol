package app.obol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.obol.R
import app.obol.data.ThemeMode

/**
 * Black-figure pottery: terracotta and marble on black. By day the figures turn red on a
 * pale marble ground, like red-figure vases.
 */
@Immutable
data class Palette(
    val bg: Color,
    /** The warm glow at the top of the screen. */
    val bgTop: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** Terracotta: actions and the figures. */
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    /** The gold of an obol. */
    val gold: Color,
    /** The grey-blue mist of Lethe. */
    val lethe: Color,
    val good: Color,
    val danger: Color,
    /** Glaze colours a patron can wear. */
    val hues: List<Color>,
    val dark: Boolean,
) {
    fun hue(i: Int): Color = hues[Math.floorMod(i, hues.size)]
}

val BlackFigure = Palette(
    bg = Color(0xFF14100E),
    bgTop = Color(0xFF2A1A12),
    surface = Color(0xFF1E1713),
    surface2 = Color(0xFF281F19),
    line = Color(0xFF3B2E25),
    ink = Color(0xFFF1E6D4),
    muted = Color(0xFFBCA88F),
    faint = Color(0xFF7F6C59),
    accent = Color(0xFFD9774A),
    accentSoft = Color(0xFF3A2219),
    onAccent = Color(0xFF14100E),
    gold = Color(0xFFDDB45E),
    lethe = Color(0xFF8FA4B2),
    good = Color(0xFFA7C98F),
    danger = Color(0xFFE5705F),
    hues = listOf(
        Color(0xFFD9774A), // terracotta
        Color(0xFFDDB45E), // ochre
        Color(0xFF9FB57A), // olive
        Color(0xFF7FA8C2), // aegean
        Color(0xFFC9707F), // wine
        Color(0xFF8FC2AE), // sage
        Color(0xFFA493D1), // amethyst
        Color(0xFFE0C9A6), // marble
    ),
    dark = true,
)

val RedFigure = Palette(
    bg = Color(0xFFF4ECDF),
    bgTop = Color(0xFFEBD8BD),
    surface = Color(0xFFFCF7EF),
    surface2 = Color(0xFFF2E8D8),
    line = Color(0xFFE2D3BC),
    ink = Color(0xFF1E1511),
    muted = Color(0xFF6E5A49),
    faint = Color(0xFFA48F79),
    accent = Color(0xFFB4502A),
    accentSoft = Color(0xFFF3DCCB),
    onAccent = Color(0xFFFFF8EF),
    gold = Color(0xFFA6761C),
    lethe = Color(0xFF55707F),
    good = Color(0xFF4F8A3A),
    danger = Color(0xFFC0392B),
    hues = listOf(
        Color(0xFFB4502A),
        Color(0xFFA6761C),
        Color(0xFF66802F),
        Color(0xFF2F6F93),
        Color(0xFF9A3748),
        Color(0xFF3E8A70),
        Color(0xFF6A55A8),
        Color(0xFF8A6E4B),
    ),
    dark = false,
)

val LocalPalette = staticCompositionLocalOf { BlackFigure }

/** Display: Roman inscriptional capitals with a Cyrillic set. */
val Forum = FontFamily(Font(R.font.forum_regular, FontWeight(400)))

/** Body: a humanist sans with a calligraphic warmth. Static cuts, like the other Stillcraft apps. */
val Alegreya = FontFamily(
    Font(R.font.alegreyasans_regular, FontWeight(400)),
    Font(R.font.alegreyasans_medium, FontWeight(500)),
    Font(R.font.alegreyasans_bold, FontWeight(700)),
)

object U {
    val c: Palette @Composable get() = LocalPalette.current

    val RXl = RoundedCornerShape(28.dp)
    val RLg = RoundedCornerShape(22.dp)
    val RMd = RoundedCornerShape(16.dp)
    val RSm = RoundedCornerShape(10.dp)
}

object UType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = Forum, fontSize = 32.sp, letterSpacing = 0.02.em, lineHeight = 38.sp, color = U.c.ink)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = Forum, fontSize = 25.sp, letterSpacing = 0.02.em, lineHeight = 31.sp, color = U.c.ink)
    val Heading: TextStyle @Composable get() = TextStyle(fontFamily = Forum, fontSize = 19.sp, letterSpacing = 0.02.em, lineHeight = 24.sp, color = U.c.ink)
    val Sub: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 22.sp, color = U.c.muted)
    val Eyebrow: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(700), fontSize = 12.sp, letterSpacing = 0.16.em, color = U.c.faint)
    val Strong: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(700), fontSize = 17.sp, color = U.c.ink)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(500), fontSize = 16.sp, lineHeight = 22.sp, color = U.c.ink)
    val Muted: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(400), fontSize = 14.5.sp, lineHeight = 19.sp, color = U.c.muted)
    val Small: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(500), fontSize = 13.sp, color = U.c.faint)
    val Number: TextStyle @Composable get() = TextStyle(fontFamily = Forum, fontSize = 24.sp, color = U.c.ink, fontFeatureSettings = "tnum, lnum")
    val Big: TextStyle @Composable get() = TextStyle(fontFamily = Forum, fontSize = 64.sp, lineHeight = 64.sp, color = U.c.ink, fontFeatureSettings = "tnum, lnum")
    val Button: TextStyle @Composable get() = TextStyle(fontFamily = Alegreya, fontWeight = FontWeight(700), fontSize = 16.sp, letterSpacing = 0.02.em)
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ObolTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) BlackFigure else RedFigure
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = p.accent,
                onPrimary = p.onAccent,
                primaryContainer = p.accentSoft,
                onPrimaryContainer = p.ink,
                secondaryContainer = p.accentSoft,
                onSecondaryContainer = p.ink,
                background = p.bg,
                surface = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surface2,
                surfaceContainerLow = p.surface2,
                onBackground = p.ink,
                onSurface = p.ink,
                onSurfaceVariant = p.muted,
                outline = p.line,
                outlineVariant = p.line,
                error = p.danger,
            ),
            content = content,
        )
    }
}
