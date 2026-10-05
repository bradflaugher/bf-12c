package com.bradflaugher.bf12c.ui

import android.content.ClipData
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bradflaugher.bf12c.CalcViewModel
import com.bradflaugher.bf12c.engine.Key
import androidx.compose.ui.input.key.Key as KeyCode
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
        // Through the engine's queue, so Ctrl+C right after typing copies what was typed.
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("x", vm.copyX()))) }
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

    // A hardware keyboard (ChromeOS, tablets, desktop windows) types into the calculator.
    val onHardwareKey: (KeyEvent) -> Boolean = handler@{ e ->
        if (e.type != KeyEventType.KeyDown) return@handler false
        // The tips never stand in the way: any key dismisses them. Esc and Enter stop
        // there; any other key goes on to do its job.
        if (vm.tipsOpen) {
            vm.dismissTips()
            if (e.key == KeyCode.Escape || e.key == KeyCode.Enter || e.key == KeyCode.NumPadEnter) return@handler true
        }
        if (e.isAltPressed || e.isMetaPressed) return@handler false
        if (e.isCtrlPressed) {
            when (e.key) {
                KeyCode.C -> copy()
                KeyCode.V -> paste()
                else -> return@handler false
            }
            return@handler true
        }
        when (e.key) {
            // The ON key's menu, for a keyboard with no touch or mouse.
            KeyCode.F1, KeyCode.Menu -> vm.toggleMenu()
            KeyCode.Escape -> if (vm.menuOpen) vm.closeMenu() else return@handler false
            // The menu has its own buttons; only Esc and F1 reach past it.
            else -> if (vm.menuOpen) return@handler false else when (e.key) {
                KeyCode.Enter, KeyCode.NumPadEnter -> vm.press(Key.ENTER)
                KeyCode.Backspace -> vm.backspace()
                KeyCode.Delete -> vm.press(Key.CLX)
                else -> vm.press(Key.typed(e.utf16CodePoint.toChar()) ?: return@handler false)
            }
        }
        true
    }
    val focus = remember { FocusRequester() }
    // Take focus at start and back from the menu or tips, so typing always reaches the keys.
    val overlay = vm.menuOpen || vm.tipsOpen
    LaunchedEffect(overlay) { if (!overlay) focus.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1B1C), Palette.bezel, Color(0xFF050505))))
            .onKeyEvent(onHardwareKey)
            .focusRequester(focus)
            .focusTarget()
            .safeDrawingPadding(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(10.dp)
                // While the menu or the tips are up, TalkBack stays in them.
                .then(if (overlay) Modifier.clearAndSetSemantics {} else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val scale = printScale(maxWidth, maxHeight)
            CompositionLocalProvider(LocalKeyScale provides scale) {
            if (maxWidth > maxHeight) {
                // Ultra-wide desktop windows would stretch the keys flat: cap the aspect.
                Column(Modifier.fillMaxHeight().widthIn(max = maxHeight * 2.6f)) {
                    Row(Modifier.fillMaxWidth().weight(0.29f)) {
                        DisplayPanel(display, vm.phosphor, expanded = false, toast, backspace, copy, paste, Modifier.weight(1f).fillMaxHeight())
                        Spacer(Modifier.width(12.dp))
                        BrandPlate(Modifier.fillMaxHeight().width(150.dp * scale))
                    }
                    Spacer(Modifier.height(4.dp))
                    GoldStripe()
                    LandscapeKeyboard(shift, ink, onKey, Modifier.weight(0.71f).fillMaxWidth())
                }
            } else {
                // Short phones give the keys more of the height so they stay big enough to hit.
                val displayShare = if (maxHeight < 700.dp) 0.24f else 0.3f
                Column(Modifier.fillMaxSize()) {
                    BrandPlate(Modifier.fillMaxWidth().height(34.dp * scale), compact = true)
                    Spacer(Modifier.height(8.dp))
                    DisplayPanel(display, vm.phosphor, expanded = true, toast, backspace, copy, paste, Modifier.fillMaxWidth().weight(displayShare))
                    Spacer(Modifier.height(6.dp))
                    GoldStripe()
                    PortraitKeyboard(shift, ink, onKey, Modifier.weight(1f - displayShare).fillMaxWidth())
                }
            }
            }
        }
        // Back closes the menu or the tips instead of leaving the app.
        BackHandler(enabled = vm.menuOpen) { vm.closeMenu() }
        BackHandler(enabled = vm.tipsOpen && !vm.menuOpen) { vm.dismissTips() }
        if (vm.menuOpen) {
            SystemMenu(vm, onCopy = { copy(); vm.closeMenu() }, onPaste = { paste(); vm.closeMenu() })
        } else if (vm.tipsOpen) {
            TipsOverlay(vm)
        }
    }
}

/** Tablets and unfolded foldables print everything bigger; phones stay at 1. */
internal fun printScale(width: Dp, height: Dp): Float = (minOf(width, height) / 400.dp).coerceIn(1f, 2f)

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
    val k = LocalKeyScale.current
    val gold = TextStyle(color = Palette.gold, fontFamily = Fonts.mono, fontWeight = FontWeight.Bold)
    // TalkBack hears one name, not "bf", "12C" and "RPN slash slash financial".
    val named = Modifier.clearAndSetSemantics { contentDescription = "bf-12c, RPN financial calculator" }
    if (compact) {
        Row(modifier.then(named), verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.width(10.dp))
            BasicText("12C", style = gold.copy(color = Palette.keyLabel, fontSize = 22.sp * k))
            Spacer(Modifier.weight(1f))
            BasicText("RPN // FINANCIAL", style = gold.copy(fontSize = 11.sp * k, letterSpacing = 2.sp * k))
        }
        return
    }
    Column(
        modifier
            .then(named)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF202224), Color(0xFF0E0F10))))
            .border(1.dp, Palette.bezelEdge, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.weight(1f))
            BasicText("12C", style = gold.copy(color = Palette.keyLabel, fontSize = 30.sp * k))
        }
        BasicText("RPN // FINANCIAL", style = gold.copy(fontSize = 10.sp * k, letterSpacing = 2.sp * k))
    }
}

@Composable
private fun Logo() {
    val k = LocalKeyScale.current
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Palette.goldBright, Palette.gold)))
            .padding(horizontal = 8.dp * k, vertical = 2.dp * k),
    ) {
        BasicText("bf", style = TextStyle(color = Color(0xFF1A1206), fontFamily = Fonts.mono, fontWeight = FontWeight.Bold, fontSize = 20.sp * k))
    }
}
