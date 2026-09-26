package com.bradflaugher.bf12c.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * One line of text at the largest size (up to [maxFontSize], in [step]s) at which
 * every string in [sizing] fits the available space.
 *
 * The size is a pure function of the constraints and [sizing], never of the
 * previous layout, so it can't drift: a blinking cursor, a rotation or a trip
 * to the background always lands on the same size. Pass a fixed [sizing] to
 * give a whole family of labels one size.
 */
@Composable
fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 6.sp,
    step: TextUnit = 0.5.sp,
    sizing: List<String> = listOf(text.text),
    contentAlignment: Alignment = Alignment.Center,
    textModifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val fontSize = remember(sizing, style, maxFontSize, minFontSize, step, width, height) {
            val reference = style.copy(fontSize = maxFontSize)
            var scale = 1f
            for (s in sizing) {
                if (s.isEmpty()) continue
                val size = measurer.measure(s, reference, maxLines = 1, softWrap = false).size
                if (size.width > 0 && width != Int.MAX_VALUE) scale = minOf(scale, width.toFloat() / size.width)
                if (size.height > 0 && height != Int.MAX_VALUE) scale = minOf(scale, height.toFloat() / size.height)
            }
            val steps = floor(maxFontSize.value * scale / step.value)
            (steps * step.value).coerceIn(minFontSize.value, maxFontSize.value).sp
        }
        BasicText(
            text,
            modifier = textModifier,
            style = style.copy(fontSize = fontSize),
            maxLines = 1,
            softWrap = false,
            onTextLayout = onTextLayout,
        )
    }
}

@Composable
fun FitText(
    text: String,
    style: TextStyle,
    maxFontSize: TextUnit,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 6.sp,
    step: TextUnit = 0.5.sp,
    sizing: List<String> = listOf(text),
    contentAlignment: Alignment = Alignment.Center,
) = FitText(AnnotatedString(text), style, maxFontSize, modifier, minFontSize, step, sizing, contentAlignment)
