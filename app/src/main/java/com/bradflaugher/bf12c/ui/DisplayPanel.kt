package com.bradflaugher.bf12c.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.bf12c.engine.Display
import kotlinx.coroutines.delay

private val ERROR_NAMES = mapOf(
    0 to "MATH", 1 to "OVERFLOW", 2 to "STATISTICS", 3 to "IRR", 4 to "MEMORY",
    5 to "FINANCIAL", 6 to "REGISTER", 7 to "IRR", 8 to "CALENDAR",
)

private const val BOOT = "BF-12C READY"

@Composable
fun DisplayPanel(
    display: Display,
    phosphor: Phosphor,
    expanded: Boolean,
    toast: String?,
    onBackspace: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flicker by rememberInfiniteTransition(label = "flicker").animateFloat(
        initialValue = 0.97f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2300, easing = LinearEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val cursorOn by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1060, easing = LinearEasing), RepeatMode.Restart),
        label = "cursor",
    )
    // Boot banner types itself out once per launch.
    var bootChars by remember { mutableIntStateOf(0) }
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (bootChars < BOOT.length) {
            bootChars++
            delay(35)
        }
        delay(500)
        booted = true
    }

    val ink = phosphor.ink
    val glow = if (phosphor.glow) Shadow(ink.copy(alpha = 0.85f), Offset.Zero, blurRadius = 22f) else null
    val small = TextStyle(color = phosphor.dim, fontFamily = Fonts.crt, fontSize = 20.sp, shadow = glow?.copy(color = phosphor.dim, blurRadius = 8f))

    // Bezel frame, then the glass.
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Palette.bezelEdge, Color.Black)))
            .padding(5.dp)
            .clip(RoundedCornerShape(7.dp))
            .crt(phosphor)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onCopy() }, onDoubleTap = { onPaste() })
            }
            .pointerInput(Unit) {
                var travelled = 0f
                detectHorizontalDragGestures(
                    onDragStart = { travelled = 0f },
                    onHorizontalDrag = { _, dx ->
                        travelled += dx
                        // Each 48dp of leftward swipe erases one keystroke.
                        val step = 48.dp.toPx()
                        while (travelled < -step) {
                            onBackspace()
                            travelled += step
                        }
                    },
                )
            }
            .graphicsLayer { alpha = flicker },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val programMode = display.annunciators.prgm
            val listing = display.programListing
            val upper: List<Pair<String, String>> = when {
                programMode -> {
                    val pcIndex = listing.indexOfFirst { it.startsWith(display.main.take(4)) }.coerceAtLeast(0)
                    val count = if (expanded) 3 else 1
                    (count downTo 1).map { back -> "" to (listing.getOrNull(pcIndex - back) ?: "") }
                }
                expanded -> listOf("T" to display.stack[3], "Z" to display.stack[2], "Y" to display.stack[1])
                else -> listOf("Y" to display.stack[1])
            }
            for ((name, value) in upper) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (name.isNotEmpty()) BasicText(name, style = small.copy(fontSize = 16.sp))
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        value,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
                        style = small.copy(textAlign = if (programMode) TextAlign.Start else TextAlign.End),
                    )
                }
            }

            val mainText = when {
                !booted -> BOOT.take(bootChars)
                programMode -> listing.firstOrNull { it.startsWith(display.main.take(4)) } ?: display.main
                else -> display.main
            }
            val cursor = if ((display.entering || !booted) && cursorOn < 0.5f) "█" else if (display.entering || !booted) " " else ""
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = if (programMode) Alignment.CenterStart else Alignment.CenterEnd) {
                BasicText(
                    mainText + cursor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 140.sp, stepSize = 2.sp),
                    style = TextStyle(
                        color = ink,
                        fontFamily = Fonts.crt,
                        textAlign = if (programMode || !booted) TextAlign.Start else TextAlign.End,
                        shadow = glow,
                    ),
                )
            }

            // Full precision readout whenever the main line is rounded.
            if (booted && display.rounded && expanded) {
                BasicText(
                    "≡ " + display.full,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 18.sp),
                    style = small.copy(textAlign = TextAlign.End),
                )
            }

            Annunciators(display, phosphor, toast, expanded)
        }
    }
}

@Composable
private fun Annunciators(display: Display, phosphor: Phosphor, toast: String?, expanded: Boolean) {
    val a = display.annunciators
    val on = TextStyle(color = phosphor.ink, fontFamily = Fonts.crt, fontSize = 17.sp)
    val off = on.copy(color = phosphor.dim.copy(alpha = 0.28f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("f", style = if (a.f) on.copy(color = Palette.goldBright) else off)
        BasicText("g", style = if (a.g) on.copy(color = Palette.blueBright) else off)
        BasicText("BEGIN", style = if (a.begin) on else off)
        BasicText("D.MY", style = if (a.dmy) on else off)
        BasicText("C", style = if (a.compound) on else off)
        BasicText("PRGM", style = if (a.prgm) on else off)
        if (a.running) BasicText("RUN", style = on)
        a.pending?.let { BasicText(it, style = on) }
        display.weekday?.let { BasicText("DOW ${it} ${WEEKDAYS[it - 1]}", style = on) }
        Spacer(Modifier.weight(1f))
        when {
            toast != null -> BasicText(toast, style = on)
            display.isError -> BasicText(ERROR_NAMES[display.main.removePrefix("Error ").toIntOrNull()] ?: "", style = on)
            expanded -> BasicText("LSTx ${display.lastX}", maxLines = 1, style = off.copy(color = phosphor.dim.copy(alpha = 0.7f)))
        }
    }
}

private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

/** Phosphor glass: glow, scanlines, vignette and a faint reflection. */
private fun Modifier.crt(p: Phosphor): Modifier = drawWithContent {
    drawRect(p.glass)
    drawRect(Brush.radialGradient(listOf(p.ink.copy(alpha = if (p.glow) 0.10f else 0.0f), Color.Transparent), radius = size.maxDimension * 0.7f))
    drawContent()
    val period = 3.dp.toPx()
    val line = period * 0.4f
    val scan = Color.Black.copy(alpha = if (p.glow) 0.16f else 0.05f)
    var y = 0f
    while (y < size.height) {
        drawRect(scan, topLeft = Offset(0f, y), size = Size(size.width, line))
        y += period
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (p.glow) 0.45f else 0.2f)), radius = size.maxDimension * 0.8f))
    drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.45f to Color.Transparent))
}
