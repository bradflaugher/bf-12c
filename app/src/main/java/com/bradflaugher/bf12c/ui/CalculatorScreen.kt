package com.bradflaugher.bf12c.ui

import android.content.ClipData
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bradflaugher.bf12c.CalcViewModel
import com.bradflaugher.bf12c.engine.Key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CalculatorScreen(vm: CalcViewModel = viewModel()) {
    val view = LocalView.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1400)
            toast = null
        }
    }

    val display = vm.display
    val shift = when {
        display.annunciators.f -> Shift.F
        display.annunciators.g -> Shift.G
        else -> Shift.NONE
    }
    val ink = vm.phosphor.ink
    val onKey: (Key) -> Unit = { key ->
        if (vm.haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vm.press(key)
    }
    val copy: () -> Unit = {
        if (vm.haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("x", vm.display.full))) }
        toast = "COPIED"
    }
    val paste: () -> Unit = {
        scope.launch {
            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            toast = if (text == null) {
                "CLIPBOARD EMPTY"
            } else {
                when (vm.paste(text)) {
                    CalcViewModel.PasteResult.PASTED -> "PASTED"
                    CalcViewModel.PasteResult.HALTED -> "HALTED"
                    CalcViewModel.PasteResult.REJECTED -> "NOT PASTED"
                    CalcViewModel.PasteResult.NOT_A_NUMBER -> "NOT A NUMBER"
                }
            }
        }
    }
    val backspace: () -> Unit = {
        if (vm.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        vm.backspace()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1B1C), Palette.bezel, Color(0xFF050505))))
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            if (maxWidth > maxHeight) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().weight(0.29f)) {
                        DisplayPanel(display, vm.phosphor, expanded = false, toast, backspace, copy, paste, Modifier.weight(1f).fillMaxHeight())
                        Spacer(Modifier.width(12.dp))
                        BrandPlate(Modifier.fillMaxHeight().width(150.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    GoldStripe()
                    LandscapeKeyboard(shift, ink, onKey, Modifier.weight(0.71f).fillMaxWidth())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    BrandPlate(Modifier.fillMaxWidth().height(34.dp), compact = true)
                    Spacer(Modifier.height(8.dp))
                    DisplayPanel(display, vm.phosphor, expanded = true, toast, backspace, copy, paste, Modifier.fillMaxWidth().weight(0.3f))
                    Spacer(Modifier.height(6.dp))
                    GoldStripe()
                    PortraitKeyboard(shift, ink, onKey, Modifier.weight(0.7f).fillMaxWidth())
                }
            }
        }
        // Back closes the menu instead of leaving the app.
        BackHandler(enabled = vm.menuOpen) { vm.closeMenu() }
        if (vm.menuOpen) SystemMenu(vm, onCopy = { copy(); vm.closeMenu() }, onPaste = { paste(); vm.closeMenu() })
    }
}

@Composable
private fun GoldStripe() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(2.dp)
            .background(Brush.horizontalGradient(listOf(Palette.gold.copy(alpha = 0.1f), Palette.gold, Palette.gold.copy(alpha = 0.1f)))),
    )
}

/** Where the 12c has its logo plate: ours. */
@Composable
private fun BrandPlate(modifier: Modifier, compact: Boolean = false) {
    val gold = TextStyle(color = Palette.gold, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(10.dp))
            BasicText("12C", style = gold.copy(color = Palette.keyLabel, fontSize = 22.sp))
            Spacer(Modifier.weight(1f))
            BasicText("RPN // FINANCIAL", style = gold.copy(fontSize = 11.sp, letterSpacing = 2.sp))
        }
        return
    }
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF202224), Color(0xFF0E0F10))))
            .border(1.dp, Palette.bezelEdge, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.weight(1f))
            BasicText("12C", style = gold.copy(color = Palette.keyLabel, fontSize = 30.sp))
        }
        BasicText("RPN // FINANCIAL", style = gold.copy(fontSize = 10.sp, letterSpacing = 2.sp))
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Palette.goldBright, Palette.gold)))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        BasicText("bf", style = TextStyle(color = Color(0xFF1A1206), fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, fontSize = 20.sp))
    }
}

/** The ON key opens a green-screen system menu. */
@Composable
private fun SystemMenu(vm: CalcViewModel, onCopy: () -> Unit, onPaste: () -> Unit) {
    val p = vm.phosphor
    val ink = if (p.glow) p.ink else Color(0xFFB9C4A3)
    val glow = if (p.glow) Shadow(p.ink.copy(alpha = 0.8f), blurRadius = 14f) else null
    val style = MenuStyle(
        ink = ink,
        title = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 28.sp, shadow = glow),
        item = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 23.sp, shadow = glow),
        section = TextStyle(color = ink.copy(alpha = 0.6f), fontFamily = Fonts.crt, fontSize = 17.sp, letterSpacing = 2.sp),
        key = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 19.sp, shadow = glow?.copy(blurRadius = 8f)),
        body = TextStyle(color = ink.copy(alpha = 0.82f), fontFamily = Fonts.crt, fontSize = 19.sp, lineHeight = 21.sp),
    )
    var help by remember { mutableStateOf(false) }
    // Back steps out of the manual before it closes the menu.
    BackHandler(enabled = help) { help = false }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeMenu() }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Header and footer stay put; only the body scrolls.
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF030704))
                .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicText(if (help) "BF-12C MANUAL" else "BF-12C SYSTEM", style = style.title, maxLines = 1, softWrap = false)
            Rule(ink, Modifier.padding(top = 8.dp, bottom = 4.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (help) {
                    for ((title, rows) in MANUAL) {
                        Section(title, style)
                        for ((key, text) in rows) ManualRow(key, text, style)
                    }
                } else {
                    Section("PHOSPHOR", style)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (option in Phosphor.entries) {
                            val selected = option == p
                            BasicText(
                                if (selected) "[${option.title}]" else " ${option.title} ",
                                style = style.item.copy(fontSize = 20.sp, color = if (selected) ink else ink.copy(alpha = 0.5f)),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.clickable { vm.selectPhosphor(option) }.padding(vertical = 6.dp),
                            )
                        }
                    }
                    Section("SETTINGS", style)
                    MenuItem("HAPTICS", if (vm.haptics) "ON" else "OFF", style) { vm.toggleHaptics() }
                    Section("CLIPBOARD", style)
                    MenuItem("COPY X", null, style, onClick = onCopy)
                    MenuItem("PASTE X", null, style, onClick = onPaste)
                    Section("HELP", style)
                    MenuItem("MANUAL", null, style) { help = true }
                }
            }
            Rule(ink, Modifier.padding(top = 6.dp, bottom = 2.dp))
            if (help) {
                MenuItem("BACK", null, style, prefix = "<") { help = false }
            } else {
                MenuItem("EXIT", null, style) { vm.closeMenu() }
            }
        }
    }
}

private class MenuStyle(
    val ink: Color,
    val title: TextStyle,
    val item: TextStyle,
    val section: TextStyle,
    val key: TextStyle,
    val body: TextStyle,
)

/** A drawn divider: a text rule of box characters wraps on narrow screens. */
@Composable
private fun Rule(ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.45f)))
}

@Composable
private fun Section(title: String, style: MenuStyle) {
    BasicText(title, style = style.section, maxLines = 1, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

/** "> LABEL ........ VALUE" with a drawn dot leader that stretches to fit, never wraps. */
@Composable
private fun MenuItem(label: String, value: String?, style: MenuStyle, prefix: String = ">", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("$prefix $label", style = style.item, maxLines = 1, softWrap = false)
        if (value != null) {
            val dot = style.ink.copy(alpha = 0.4f)
            Spacer(
                Modifier
                    .weight(1f)
                    .height(style.item.fontSize.value.dp)
                    .padding(horizontal = 8.dp)
                    .drawBehind {
                        val step = 7.dp.toPx()
                        val r = 1.2.dp.toPx()
                        val y = size.height * 0.62f
                        var x = r
                        while (x < size.width - r) {
                            drawCircle(dot, r, Offset(x, y))
                            x += step
                        }
                    },
            )
            BasicText(value, style = style.item, maxLines = 1, softWrap = false)
        }
    }
}

/** One manual entry: keys in a fixed column, the description wrapping beside it. */
@Composable
private fun ManualRow(key: String, text: String, style: MenuStyle) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        BasicText(key, style = style.key, modifier = Modifier.weight(0.36f).padding(end = 10.dp))
        BasicText(text, style = style.body, modifier = Modifier.weight(0.64f))
    }
}

private val MANUAL: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "BASICS" to listOf(
        "RPN" to "Key a number, ENTER, key the next, then the operation.",
        "Stack" to "X Y Z T. g ENTER recalls LSTx, the last X.",
        "Memory" to "Continuous: everything survives restarts.",
    ),
    "DISPLAY" to listOf(
        "f 0–9" to "FIX: show n decimals.",
        "f ." to "SCI notation.",
        "f EEX" to "ALL 34 significant digits.",
    ),
    "EDITING" to listOf(
        "g −" to "Backspace, or swipe the display left.",
        "f f · g g" to "Cancel a pressed f or g.",
        "Long-press" to "Copy X from the display.",
        "Double-tap" to "Paste a number into X.",
    ),
    "FINANCE" to listOf(
        "TVM" to "n i PV PMT FV. Key a value, then the key to store it. Press a key right after another TVM key to solve for it.",
        "g 7 · g 8" to "BEGIN / END payments.",
        "g 4 · g 5" to "D.MY / M.DY dates.",
        "Cash flows" to "g CF₀, g CFⱼ, g Nⱼ, then f NPV or f IRR.",
    ),
    "STATISTICS" to listOf(
        "Σ+" to "y ENTER x Σ+ adds a data point.",
        "g 0 · g ." to "Mean x̄ and standard deviation s.",
        "g 1 · g 2" to "Linear estimates x̂,r and ŷ,r.",
    ),
    "PROGRAMS" to listOf(
        "f R/S" to "Enter or leave PRGM mode.",
        "g R↓ nn" to "GTO line nn.",
        "g R↓ . nn" to "Jump to line nn while editing.",
        "g −" to "Delete the current line.",
    ),
    "ERRORS" to listOf(
        "0" to "Math, or a result out of range",
        "2" to "Statistics",
        "3 · 7" to "IRR",
        "4" to "Memory",
        "5" to "Financial",
        "6" to "Register",
        "8" to "Calendar",
    ),
)
