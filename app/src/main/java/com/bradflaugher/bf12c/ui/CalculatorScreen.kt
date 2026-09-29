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
        if (e.type != KeyEventType.KeyDown || e.isAltPressed || e.isMetaPressed) return@handler false
        if (e.isCtrlPressed) {
            when (e.key) {
                KeyCode.C -> copy()
                KeyCode.V -> paste()
                else -> return@handler false
            }
            return@handler true
        }
        when (e.key) {
            KeyCode.Escape -> if (vm.menuOpen) vm.closeMenu() else return@handler false
            // The menu has its own buttons; only Esc reaches past it.
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
    // Take focus at start and back from the menu, so typing always reaches the keys.
    LaunchedEffect(vm.menuOpen) { if (!vm.menuOpen) focus.requestFocus() }

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
                // While the menu is up, TalkBack stays in the menu.
                .then(if (vm.menuOpen) Modifier.clearAndSetSemantics {} else Modifier),
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
        // Back closes the menu instead of leaving the app.
        BackHandler(enabled = vm.menuOpen) { vm.closeMenu() }
        if (vm.menuOpen) SystemMenu(vm, onCopy = { copy(); vm.closeMenu() }, onPaste = { paste(); vm.closeMenu() })
    }
}

/** Tablets and unfolded foldables print everything bigger; phones stay at 1. */
private fun printScale(width: Dp, height: Dp): Float = (minOf(width, height) / 400.dp).coerceIn(1f, 2f)

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
    if (compact) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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

/** The ON key opens a green-screen system menu. */
@Composable
private fun SystemMenu(vm: CalcViewModel, onCopy: () -> Unit, onPaste: () -> Unit) {
    val p = vm.phosphor
    val ink = if (p.glow) p.ink else Color(0xFFB9C4A3)
    val glow = if (p.glow) Shadow(p.ink.copy(alpha = 0.8f), blurRadius = 14f) else null
    var help by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    // Back steps out of the manual before it closes the menu.
    BackHandler(enabled = help) { help = false }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Close menu",
            ) { vm.closeMenu() }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Printed as big as the keyboard's, so a tablet's menu isn't a postage stamp.
        val k = printScale(maxWidth, maxHeight)
        val style = MenuStyle(
            ink = ink,
            title = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 28.sp * k, shadow = glow),
            item = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 23.sp * k, shadow = glow),
            section = TextStyle(color = ink.copy(alpha = 0.6f), fontFamily = Fonts.crt, fontSize = 17.sp * k, letterSpacing = 2.sp * k),
            key = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 19.sp * k, shadow = glow?.copy(blurRadius = 8f)),
            body = TextStyle(color = ink.copy(alpha = 0.82f), fontFamily = Fonts.crt, fontSize = 19.sp * k, lineHeight = 21.sp * k),
        )
        // Header and footer stay put; only the body scrolls.
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp * k)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF030704))
                .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                // Swallow taps on the panel so they don't close the menu; not a control.
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics {
                    paneTitle = if (help) "Manual" else "System menu"
                    isTraversalGroup = true
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicText(
                if (help) "BF-12C MANUAL" else "BF-12C SYSTEM",
                style = style.title,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { heading() },
            )
            Rule(ink, Modifier.padding(top = 8.dp, bottom = 4.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (help) {
                    for ((title, rows) in MANUAL) {
                        Section(title, style)
                        for ((key, text) in rows) ManualRow(key, text, style)
                    }
                } else {
                    Section("PHOSPHOR", style)
                    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (option in Phosphor.entries) {
                            val selected = option == p
                            BasicText(
                                if (selected) "[${option.title}]" else " ${option.title} ",
                                style = style.item.copy(fontSize = 20.sp * k, color = if (selected) ink else ink.copy(alpha = 0.5f)),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .selectable(selected, role = Role.RadioButton) { vm.selectPhosphor(option) }
                                    .padding(vertical = 6.dp),
                            )
                        }
                    }
                    Section("SETTINGS", style)
                    MenuItem("HAPTICS", if (vm.haptics) "ON" else "OFF", style, checked = vm.haptics) { vm.toggleHaptics() }
                    Section("CLIPBOARD", style)
                    MenuItem("COPY X", null, style, onClick = onCopy)
                    MenuItem("PASTE X", null, style, onClick = onPaste)
                    Section("HELP", style)
                    MenuItem("MANUAL", null, style) { help = true }
                    // A device with no browser (kiosks, some test devices) must not crash here.
                    MenuItem("PRIVACY POLICY", null, style) { runCatching { uri.openUri(PRIVACY_POLICY_URL) } }
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
    BasicText(title, style = style.section, maxLines = 1, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).semantics { heading() })
}

/**
 * "> LABEL ........ VALUE" with a drawn dot leader that stretches to fit, never wraps.
 * A [checked] item is a switch; TalkBack hears its label and state, not the dots.
 */
@Composable
private fun MenuItem(
    label: String,
    value: String?,
    style: MenuStyle,
    prefix: String = ">",
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    val action = if (checked != null) {
        Modifier.toggleable(checked, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(action)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(prefix, style = style.item, maxLines = 1, softWrap = false, modifier = Modifier.clearAndSetSemantics {})
        BasicText(" $label", style = style.item, maxLines = 1, softWrap = false)
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
            val valueSemantics = if (checked != null) Modifier.clearAndSetSemantics {} else Modifier
            BasicText(value, style = style.item, maxLines = 1, softWrap = false, modifier = valueSemantics)
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

/** The Play privacy policy; the browser opens it, bf-12c itself has no network access. */
private const val PRIVACY_POLICY_URL = "https://bradflaugher.com/privacy/bf-12c/"

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
    "KEYBOARD" to listOf(
        "0–9 . +" to "Type digits and − × ÷ (also * /) on a hardware keyboard.",
        "Enter" to "ENTER. Backspace erases, Delete is CLx.",
        "f g e ^ %" to "f, g, EEX, yˣ and %.",
        "Ctrl C · V" to "Copy and paste X. Esc closes this menu.",
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
