package com.bradflaugher.bf12c.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.bf12c.engine.Key

/** Which legend set is live: none, gold (f) or blue (g). */
enum class Shift { NONE, F, G }

private val LABEL_STRIP = 15.dp
private val BRACKET_STRIP = 9.dp

/** The full 12c keyboard: 4 rows of 10, ENTER spanning the last two rows. */
@Composable
fun LandscapeKeyboard(shift: Shift, ink: Color, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    val rows = Key.ROWS
    Column(modifier) {
        KeyRow(rows[0], shift, ink, onKey, Modifier.weight(1f))
        Brackets(10, Bracket("BOND", 0, 2), Bracket("DEPRECIATION", 2, 3))
        KeyRow(rows[1], shift, ink, onKey, Modifier.weight(1f))
        Brackets(10, Bracket("CLEAR", 1, 5))
        Row(Modifier.weight(2f)) {
            Column(Modifier.weight(5f)) {
                KeyRow(rows[2].take(5), shift, ink, onKey, Modifier.weight(1f))
                KeyRow(rows[3].take(5), shift, ink, onKey, Modifier.weight(1f))
            }
            KeyCell(Key.ENTER, shift, ink, onKey, Modifier.weight(1f), tall = true)
            Column(Modifier.weight(4f)) {
                KeyRow(rows[2].drop(6), shift, ink, onKey, Modifier.weight(1f))
                KeyRow(rows[3].drop(6), shift, ink, onKey, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Portrait folds the keyboard in half: the financial half on top, the number pad
 * half below where thumbs live. Every key keeps its neighbours.
 */
@Composable
fun PortraitKeyboard(shift: Shift, ink: Color, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    val rows = Key.ROWS
    Column(modifier) {
        KeyRow(rows[0].take(5), shift, ink, onKey, Modifier.weight(1f))
        Brackets(5, Bracket("BOND", 0, 2), Bracket("DEPRECIATION", 2, 3))
        KeyRow(rows[1].take(5), shift, ink, onKey, Modifier.weight(1f))
        Brackets(5, Bracket("CLEAR", 1, 4))
        KeyRow(rows[2].take(5), shift, ink, onKey, Modifier.weight(1f))
        KeyRow(rows[3].take(5), shift, ink, onKey, Modifier.weight(1f))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Palette.gold.copy(alpha = 0.5f), Color.Transparent))),
        )
        KeyRow(rows[0].drop(5), shift, ink, onKey, Modifier.weight(1f))
        KeyRow(rows[1].drop(5), shift, ink, onKey, Modifier.weight(1f))
        Row(Modifier.weight(2f)) {
            KeyCell(Key.ENTER, shift, ink, onKey, Modifier.weight(1f), tall = true)
            Column(Modifier.weight(4f)) {
                KeyRow(rows[2].drop(6), shift, ink, onKey, Modifier.weight(1f))
                KeyRow(rows[3].drop(6), shift, ink, onKey, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyRow(keys: List<Key>, shift: Shift, ink: Color, onKey: (Key) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth()) {
        for (k in keys) KeyCell(k, shift, ink, onKey, Modifier.weight(1f))
    }
}

private data class Bracket(val label: String, val start: Int, val span: Int)

/** The gold brackets printed across groups of f legends: BOND, DEPRECIATION, CLEAR. */
@Composable
private fun Brackets(totalCols: Int, vararg brackets: Bracket) {
    Row(Modifier.fillMaxWidth().height(BRACKET_STRIP)) {
        var col = 0
        for (b in brackets) {
            if (b.start > col) Spacer(Modifier.weight((b.start - col).toFloat()))
            BracketLine(b.label, Modifier.weight(b.span.toFloat()))
            col = b.start + b.span
        }
        if (totalCols > col) Spacer(Modifier.weight((totalCols - col).toFloat()))
    }
}

@Composable
private fun BracketLine(label: String, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = Palette.gold, fontSize = 8.sp, fontFamily = Fonts.mono, letterSpacing = 1.sp)
    Box(
        modifier
            .fillMaxHeight()
            .padding(horizontal = 10.dp)
            .drawBehind {
                val y = size.height * 0.55f
                val stroke = 1.dp.toPx()
                val tick = size.height * 0.45f
                val gap = measurer.measure(label, style).size.width / 2f + 4.dp.toPx()
                val mid = size.width / 2
                drawLine(Palette.gold, Offset(0f, y + tick), Offset(0f, y), stroke)
                drawLine(Palette.gold, Offset(0f, y), Offset(mid - gap, y), stroke)
                drawLine(Palette.gold, Offset(mid + gap, y), Offset(size.width, y), stroke)
                drawLine(Palette.gold, Offset(size.width, y), Offset(size.width, y + tick), stroke)
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, maxLines = 1, style = style)
    }
}

@Composable
private fun KeyCell(key: Key, shift: Shift, ink: Color, onKey: (Key) -> Unit, modifier: Modifier, tall: Boolean = false) {
    Column(modifier.padding(horizontal = 3.dp, vertical = 1.5.dp)) {
        Box(Modifier.fillMaxWidth().height(LABEL_STRIP), contentAlignment = Alignment.Center) {
            key.f?.let { Legend(it, Palette.gold, Palette.goldBright, lit = shift == Shift.F, dim = shift == Shift.G) }
        }
        KeyCap(key, shift, ink, onKey, Modifier.weight(1f).fillMaxWidth(), tall)
    }
}

@Composable
private fun Legend(text: String, color: Color, bright: Color, lit: Boolean, dim: Boolean) {
    // Every legend up to five characters gets the same size; only longer ones shrink.
    FitText(
        text,
        maxFontSize = 11.sp,
        minFontSize = 6.sp,
        modifier = Modifier.fillMaxSize(),
        sizing = listOf(text, "00000"),
        style = TextStyle(
            color = when {
                lit -> bright
                dim -> color.copy(alpha = 0.35f)
                else -> color
            },
            fontFamily = Fonts.mono,
            textAlign = TextAlign.Center,
            shadow = if (lit) Shadow(bright, blurRadius = 14f) else null,
        ),
    )
}

@Composable
private fun KeyCap(key: Key, shift: Shift, ink: Color, onKey: (Key) -> Unit, modifier: Modifier, tall: Boolean) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val (top, bottom, labelColor) = when (key) {
        Key.F -> Triple(Palette.goldBright, Palette.gold, Color(0xFF1A1206))
        Key.G -> Triple(Palette.blueBright, Palette.blue, Color(0xFF06121A))
        else -> Triple(Palette.keyTop, Palette.keyBottom, Palette.keyLabel)
    }
    val primaryAlpha = if (shift != Shift.NONE && key != Key.F && key != Key.G) 0.4f else 1f
    // The live prefix key is ringed: pressing it again cancels it.
    val active = (key == Key.F && shift == Shift.F) || (key == Key.G && shift == Shift.G)
    Box(
        modifier
            .graphicsLayer {
                translationY = if (pressed) 1.5.dp.toPx() else 0f
                scaleX = if (pressed) 0.97f else 1f
                scaleY = if (pressed) 0.97f else 1f
            }
            .drawBehind {
                // A soft phosphor bloom under the key while it is held.
                if (pressed) {
                    drawRoundRect(
                        Brush.radialGradient(listOf(ink.copy(alpha = 0.45f), Color.Transparent), radius = size.maxDimension * 0.75f),
                        topLeft = Offset(-size.width * 0.15f, -size.height * 0.15f),
                        size = androidx.compose.ui.geometry.Size(size.width * 1.3f, size.height * 1.3f),
                    )
                }
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(1.dp, Brush.verticalGradient(listOf(Palette.keyEdge.copy(alpha = 0.9f), Color.Black)), shape)
            .then(if (active) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier)
            .semantics {
                role = Role.Button
                contentDescription = listOfNotNull(key.label, key.f?.let { "f $it" }, key.g?.let { "g $it" }).joinToString(", ")
                onClick { onKey(key); true }
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onKey(key)
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // Upper sloped face: the primary legend.
            Box(Modifier.weight(if (tall) 0.82f else 0.62f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (tall) {
                    VerticalLabel(key.label, labelColor.copy(alpha = primaryAlpha))
                } else {
                    // Sized as if three characters wide, so every key's label matches.
                    FitText(
                        key.label,
                        maxFontSize = 20.sp,
                        minFontSize = 8.sp,
                        step = 1.sp,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp),
                        sizing = listOf(key.label, "000"),
                        style = TextStyle(
                            color = labelColor.copy(alpha = primaryAlpha),
                            fontFamily = Fonts.mono,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
            // Lower front face, where the 12c prints the blue legend.
            Box(
                Modifier
                    .weight(if (tall) 0.18f else 0.38f)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = if (key == Key.F || key == Key.G) 0.12f else 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                key.g?.let { Legend(it, Palette.blue, Palette.blueBright, lit = shift == Shift.G, dim = shift == Shift.F) }
            }
        }
    }
}

@Composable
private fun VerticalLabel(text: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        for (c in text) {
            BasicText(
                c.toString(),
                style = TextStyle(color = color, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp),
            )
        }
    }
}
