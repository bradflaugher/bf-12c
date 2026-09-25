package com.bradflaugher.bf12c.ui

import android.content.ClipData
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
    val glow = if (p.glow) Shadow(p.ink.copy(alpha = 0.8f), blurRadius = 14f) else null
    val ink = if (p.glow) p.ink else Color(0xFFB9C4A3)
    val text = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 24.sp, shadow = glow)
    val dim = text.copy(color = ink.copy(alpha = 0.55f), fontSize = 19.sp)
    var help by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeMenu() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF030704))
                .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText("BF-12C SYSTEM MENU", style = text.copy(fontSize = 28.sp))
            BasicText("────────────────────────────", style = dim)
            if (help) {
                for (line in HELP) BasicText(line, style = dim.copy(color = ink.copy(alpha = 0.85f)))
                MenuItem("< BACK", text) { help = false }
                return@Column
            }
            BasicText("PHOSPHOR", style = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                for (option in Phosphor.entries) {
                    val selected = option == p
                    MenuItem(if (selected) "[${option.title}]" else " ${option.title} ", text.copy(fontSize = 20.sp, color = if (selected) ink else ink.copy(alpha = 0.5f))) {
                        vm.selectPhosphor(option)
                    }
                }
            }
            MenuItem("> HAPTICS ........ ${if (vm.haptics) "ON" else "OFF"}", text) { vm.toggleHaptics() }
            MenuItem("> COPY X ......... CLIPBOARD", text, onCopy)
            MenuItem("> PASTE X ........ FROM CLIPBOARD", text, onPaste)
            MenuItem("> MANUAL", text) { help = true }
            MenuItem("> EXIT", text) { vm.closeMenu() }
        }
    }
}

@Composable
private fun MenuItem(label: String, style: TextStyle, onClick: () -> Unit) {
    BasicText(label, style = style, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp))
}

private val HELP = listOf(
    "RPN: key a number, ENTER, key another, then the operation.",
    "Stack: X Y Z T. LSTx recalls the last X (g ENTER).",
    "f 0-9 ..... FIX n decimals",
    "f . ....... SCI notation",
    "f EEX ..... ALL 34 significant digits",
    "g − ....... backspace (or swipe the display left)",
    "Long-press display: copy X.  Double-tap: paste.",
    "TVM: n i PV PMT FV; key a value then the key to store,",
    "     press a key right after another TVM key to solve.",
    "g 7 / g 8 . BEGIN / END.   g 4 / g 5 . D.MY / M.DY",
    "Cash flows: g CF0, g CFj, g Nj, then f NPV or f IRR.",
    "Stats: y ENTER x Σ+, then g 0 (x̄), g . (s), g 1 / g 2.",
    "Program: f R/S toggles PRGM. g R↓ nn is GTO nn,",
    "     g R↓ . nn jumps while editing, g − deletes a line.",
    "Errors: 0 math 2 stats 3/7 IRR 4 memory 5 fin 6 reg 8 date.",
    "Memory is continuous: everything survives restarts.",
)
