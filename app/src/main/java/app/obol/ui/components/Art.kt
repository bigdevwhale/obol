package app.obol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as GBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.obol.ui.theme.U

/** The symbols a patron can bear. */
object Glyphs {
    val all: List<Pair<String, ImageVector>> = listOf(
        "book" to Icons.Rounded.AutoStories,
        "scroll" to Icons.AutoMirrored.Rounded.MenuBook,
        "lang" to Icons.Rounded.Translate,
        "globe" to Icons.Rounded.Language,
        "owl" to Icons.Rounded.School,
        "mind" to Icons.Rounded.Psychology,
        "flame" to Icons.Rounded.LocalFireDepartment,
        "leaf" to Icons.Rounded.Spa,
        "run" to Icons.AutoMirrored.Rounded.DirectionsRun,
        "iron" to Icons.Rounded.FitnessCenter,
        "lyre" to Icons.Rounded.MusicNote,
        "ear" to Icons.Rounded.Headphones,
        "code" to Icons.Rounded.Code,
        "flask" to Icons.Rounded.Science,
        "brush" to Icons.Rounded.Brush,
        "pen" to Icons.Rounded.Edit,
        "work" to Icons.Rounded.Work,
        "tree" to Icons.Rounded.Park,
        "sun" to Icons.Rounded.WbSunny,
        "star" to Icons.Rounded.Star,
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Rounded.Star
}

/** A patron's medallion: the symbol in a glazed roundel with a thin ring. */
@Composable
fun Medallion(glyph: String, hue: Color, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(hue.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, hue.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Glyphs.of(glyph), null, tint = hue, modifier = Modifier.size(size * 0.48f))
    }
}

/** A row of the Greek key. */
@Composable
fun Meander(modifier: Modifier = Modifier, color: Color = U.c.accent.copy(alpha = 0.55f), cell: Dp = 12.dp) {
    Canvas(modifier.fillMaxWidth().height(cell)) {
        val s = size.height
        val stroke = s / 7f
        val u = s / 4f
        var x = 0f
        val p = Path()
        // Each cell: a key that turns in on itself, joined to the next along the bottom.
        while (x < size.width) {
            p.moveTo(x, s - stroke / 2)
            p.lineTo(x + 4 * u, s - stroke / 2)
            p.moveTo(x + u * 0.5f, s - stroke / 2)
            p.lineTo(x + u * 0.5f, stroke / 2)
            p.lineTo(x + u * 3.5f, stroke / 2)
            p.lineTo(x + u * 3.5f, u * 2.6f)
            p.lineTo(x + u * 1.6f, u * 2.6f)
            p.lineTo(x + u * 1.6f, u * 1.4f)
            p.lineTo(x + u * 2.5f, u * 1.4f)
            x += 4 * u
        }
        drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Square, join = StrokeJoin.Miter))
    }
}

/** An obol: a small silver-gold coin with Athena's owl. */
@Composable
fun Obol(size: Dp, modifier: Modifier = Modifier, color: Color = U.c.gold) {
    Canvas(modifier.size(size)) { drawObol(color) }
}

fun DrawScope.drawObol(color: Color) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(
        GBrush.radialGradient(listOf(color, color.copy(alpha = 0.78f)), center = c - Offset(r * 0.3f, r * 0.3f), radius = r * 1.6f),
        r,
        c,
    )
    drawCircle(Color.Black.copy(alpha = 0.22f), r * 0.80f, c, style = Stroke(r * 0.07f))
    // The owl: two eyes and a beak.
    val eye = r * 0.17f
    val dark = Color.Black.copy(alpha = 0.45f)
    drawCircle(dark, eye, c + Offset(-r * 0.22f, -r * 0.12f), style = Stroke(r * 0.07f))
    drawCircle(dark, eye, c + Offset(r * 0.22f, -r * 0.12f), style = Stroke(r * 0.07f))
    val beak = Path().apply {
        moveTo(c.x - r * 0.08f, c.y + r * 0.02f)
        lineTo(c.x + r * 0.08f, c.y + r * 0.02f)
        lineTo(c.x, c.y + r * 0.2f)
        close()
    }
    drawPath(beak, dark)
    drawLine(dark, c + Offset(-r * 0.3f, r * 0.42f), c + Offset(r * 0.3f, r * 0.42f), r * 0.06f, cap = StrokeCap.Round)
}

/** Charon in his boat on the dark water, in the black-figure manner: one flat silhouette. */
@Composable
fun Boat(modifier: Modifier = Modifier, figure: Color = U.c.accent, water: Color = U.c.lethe) {
    Canvas(modifier) { drawBoat(figure, water) }
}

fun DrawScope.drawBoat(figure: Color, water: Color) {
    val w = size.width
    val h = size.height
    val waterY = h * 0.78f
    // Water: three thin waves.
    for (i in 0..2) {
        val y = waterY + i * h * 0.07f
        val p = Path()
        val step = w / 10f
        p.moveTo(0f, y)
        var x = 0f
        while (x < w) {
            p.quadraticTo(x + step / 2, y - h * 0.025f, x + step, y)
            x += step
        }
        drawPath(p, water.copy(alpha = 0.55f - i * 0.15f), style = Stroke(h * 0.012f, cap = StrokeCap.Round))
    }
    // Hull: a long low boat with a curled prow.
    val hull = Path().apply {
        moveTo(w * 0.12f, waterY - h * 0.10f)
        lineTo(w * 0.80f, waterY - h * 0.10f)
        quadraticTo(w * 0.90f, waterY - h * 0.12f, w * 0.92f, waterY - h * 0.24f)
        quadraticTo(w * 0.95f, waterY - h * 0.30f, w * 0.90f, waterY - h * 0.30f)
        quadraticTo(w * 0.87f, waterY - h * 0.22f, w * 0.80f, waterY - h * 0.02f)
        lineTo(w * 0.20f, waterY - h * 0.02f)
        quadraticTo(w * 0.12f, waterY - h * 0.04f, w * 0.12f, waterY - h * 0.10f)
        close()
    }
    drawPath(hull, figure)
    // Charon: a hooded figure standing at the stern.
    val fx = w * 0.30f
    val top = waterY - h * 0.62f
    val body = Path().apply {
        moveTo(fx - w * 0.07f, waterY - h * 0.10f)
        quadraticTo(fx - w * 0.06f, top + h * 0.22f, fx - w * 0.025f, top + h * 0.10f)
        quadraticTo(fx, top - h * 0.01f, fx + w * 0.035f, top + h * 0.06f)
        quadraticTo(fx + w * 0.05f, top + h * 0.12f, fx + w * 0.035f, top + h * 0.17f)
        quadraticTo(fx + w * 0.07f, top + h * 0.30f, fx + w * 0.06f, waterY - h * 0.10f)
        close()
    }
    drawPath(body, figure)
    // The pole, leaning into the water.
    drawLine(
        figure,
        Offset(fx + w * 0.17f, top - h * 0.06f),
        Offset(fx - w * 0.13f, waterY + h * 0.08f),
        strokeWidth = h * 0.018f,
        cap = StrokeCap.Round,
    )
    // A lantern at the prow.
    drawCircle(figure.copy(alpha = 0.35f), h * 0.05f, Offset(w * 0.90f, waterY - h * 0.36f))
    drawCircle(figure, h * 0.022f, Offset(w * 0.90f, waterY - h * 0.36f))
}

/** A thin horizontal rule with a diamond in the middle. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = U.c.line) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height / 2
        val d = size.height / 2.4f
        drawLine(color, Offset(0f, y), Offset(size.width / 2 - d * 2, y), 1.dp.toPx())
        drawLine(color, Offset(size.width / 2 + d * 2, y), Offset(size.width, y), 1.dp.toPx())
        val p = Path().apply {
            moveTo(size.width / 2, y - d)
            lineTo(size.width / 2 + d, y)
            lineTo(size.width / 2, y + d)
            lineTo(size.width / 2 - d, y)
            close()
        }
        drawPath(p, color)
    }
}
