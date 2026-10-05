package com.bradflaugher.bf12c.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.bf12c.CalcViewModel
import com.bradflaugher.bf12c.Links
import com.bradflaugher.bf12c.appVersion
import com.bradflaugher.bf12c.openWeb
import com.bradflaugher.bf12c.sendFeedback
import com.bradflaugher.bf12c.shareApp
import kotlinx.coroutines.delay

/** The ON key opens a green-screen system menu. */
@Composable
fun SystemMenu(vm: CalcViewModel, onCopy: () -> Unit, onPaste: () -> Unit) {
    var help by rememberSaveable { mutableStateOf(false) }
    // What the menu says when it couldn't hand off to another app.
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2500)
            notice = null
        }
    }
    val context = LocalContext.current
    val version = remember { context.appVersion() }
    // Back steps out of the manual before it closes the menu.
    BackHandler(enabled = help) { help = false }
    CrtOverlay(
        phosphor = vm.phosphor,
        title = if (help) "BF-12C MANUAL" else "BF-12C SYSTEM",
        pane = if (help) "Manual" else "System menu",
        onDismiss = vm::closeMenu,
        dismissLabel = "Close menu",
        footer = { style ->
            notice?.let {
                BasicText(it, style = style.body, modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Assertive })
            }
            if (help) {
                MenuItem("BACK", null, style, prefix = "<", focusFirst = true) { help = false }
            } else {
                MenuItem("EXIT", null, style) { vm.closeMenu() }
            }
        },
    ) { style ->
        if (help) {
            for ((title, rows) in MANUAL) {
                Section(title, style)
                for ((key, text) in rows) ManualRow(key, text, style)
            }
            Section("TIPS", style)
            MenuItem("SHOW TIPS AGAIN", null, style) { vm.showTips() }
            return@CrtOverlay
        }
        Section("PHOSPHOR", style)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((index, option) in Phosphor.entries.withIndex()) {
                val selected = option == vm.phosphor
                val source = remember { MutableInteractionSource() }
                val focused by source.collectIsFocusedAsState()
                val focus = remember { FocusRequester() }
                // Keyboard users land on the first control when the menu opens.
                if (index == 0) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                BasicText(
                    if (selected) "[${option.title}]" else " ${option.title} ",
                    style = style.item.copy(fontSize = style.item.fontSize * 0.87f, color = if (selected) style.ink else style.ink.copy(alpha = 0.6f)),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .focusRequester(focus)
                        .focusHighlight(focused, style.ink)
                        .selectable(selected, interactionSource = source, indication = LocalIndication.current, role = Role.RadioButton) {
                            vm.selectPhosphor(option)
                        }
                        // 48dp tall with the text: a full-size touch target.
                        .padding(vertical = 12.dp, horizontal = 2.dp),
                )
            }
        }
        Section("SETTINGS", style)
        MenuItem("HAPTICS", if (vm.haptics) "ON" else "OFF", style, checked = vm.haptics) { vm.toggleHaptics() }
        Section("CLIPBOARD", style)
        MenuItem("COPY X", null, style, onClick = onCopy)
        MenuItem("PASTE X", null, style, onClick = onPaste)
        Section("HELP", style)
        MenuItem("TIPS", null, style) { vm.showTips() }
        MenuItem("MANUAL + FAQ", null, style) { help = true }
        // A device with no browser (TVs, kiosks, some test devices) must not crash here.
        MenuItem("SEND FEEDBACK", null, style) { if (!context.sendFeedback()) notice = "NO BROWSER FOUND" }
        Section("ABOUT", style)
        MenuItem("SHARE APP", null, style) { if (!context.shareApp()) notice = "NOTHING CAN SHARE" }
        MenuItem("PRIVACY POLICY", null, style) { if (!context.openWeb(Links.PRIVACY_POLICY_URL)) notice = "NO BROWSER FOUND" }
        BasicText("VERSION $version", style = style.section, maxLines = 1, modifier = Modifier.padding(top = 10.dp))
    }
}

/**
 * The first-run tips: what nobody would guess from the keys alone. Shown once,
 * gone with one tap anywhere (or Back, Esc, Enter), and back from the menu.
 */
@Composable
fun TipsOverlay(vm: CalcViewModel) {
    CrtOverlay(
        phosphor = vm.phosphor,
        title = "BF-12C TIPS",
        pane = "Tips",
        onDismiss = vm::dismissTips,
        dismissLabel = "Dismiss tips",
        footer = { style ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MenuItem("GOT IT", null, style, focusFirst = true) { vm.dismissTips() } }
                BasicText("OR TAP ANYWHERE", style = style.section, maxLines = 1)
            }
        },
    ) { style ->
        for ((key, text) in TIPS) ManualRow(key, text, style)
        // Layout follows the window, so the hint does too: say what the other shape gives you.
        ManualRow(
            if (style.wide) "TURN UP" else "TURN",
            if (style.wide) "Upright folds the keys and shows T Z Y." else "Sideways is the full 4x10 12c keyboard.",
            style,
        )
    }
}

private val TIPS = listOf(
    "SWIPE ←" to "Swipe the display left to backspace (or g −).",
    "HOLD" to "Long-press the display to copy X.",
    "TAP TAP" to "Double-tap the display to paste.",
    "f · g" to "Light the gold or blue legends. Again cancels.",
    "ON" to "The menu: phosphors, manual, these tips, feedback.",
    "KEYBOARD" to "Type digits, + − * /, Enter. F1 menu, Esc closes.",
)

/**
 * A full-screen scrim with a phosphor panel in the middle: the system menu, the manual
 * and the tips all use it. Tapping the scrim dismisses; the header and footer stay put
 * and only the body scrolls.
 */
@Composable
private fun CrtOverlay(
    phosphor: Phosphor,
    title: String,
    pane: String,
    onDismiss: () -> Unit,
    dismissLabel: String,
    footer: @Composable ColumnScope.(MenuStyle) -> Unit,
    body: @Composable ColumnScope.(MenuStyle) -> Unit,
) {
    val ink = if (phosphor.glow) phosphor.ink else Color(0xFFB9C4A3)
    val glow = if (phosphor.glow) Shadow(phosphor.ink.copy(alpha = 0.8f), blurRadius = 14f) else null
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = dismissLabel,
            ) { onDismiss() }
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        // Printed as big as the keyboard's, so a tablet's menu isn't a postage stamp.
        val k = printScale(maxWidth, maxHeight)
        val style = MenuStyle(
            ink = ink,
            wide = maxWidth > maxHeight,
            title = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 28.sp * k, shadow = glow),
            item = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 23.sp * k, shadow = glow),
            section = TextStyle(color = ink.copy(alpha = 0.7f), fontFamily = Fonts.crt, fontSize = 17.sp * k, letterSpacing = 2.sp * k),
            key = TextStyle(color = ink, fontFamily = Fonts.crt, fontSize = 19.sp * k, shadow = glow?.copy(blurRadius = 8f)),
            body = TextStyle(color = ink.copy(alpha = 0.86f), fontFamily = Fonts.crt, fontSize = 19.sp * k, lineHeight = 21.sp * k),
        )
        Column(
            Modifier
                .padding(16.dp)
                .widthIn(max = 560.dp * k)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF030704))
                .border(1.dp, ink.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                // Swallow taps on the panel so they don't dismiss it; not a control.
                .pointerInput(Unit) { detectTapGestures {} }
                .semantics {
                    paneTitle = pane
                    isTraversalGroup = true
                }
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            BasicText(title, style = style.title, maxLines = 1, softWrap = false, modifier = Modifier.semantics { heading() })
            Rule(ink, Modifier.padding(top = 8.dp, bottom = 4.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { body(style) }
            Rule(ink, Modifier.padding(top = 6.dp, bottom = 2.dp))
            footer(style)
        }
    }
}

private class MenuStyle(
    val ink: Color,
    /** The window is landscape-shaped (the full 4×10 keyboard is showing). */
    val wide: Boolean,
    val title: TextStyle,
    val item: TextStyle,
    val section: TextStyle,
    val key: TextStyle,
    val body: TextStyle,
)

/** Keyboard and D-pad focus shows as a lit bar, since the panel has no other focus ring. */
private fun Modifier.focusHighlight(focused: Boolean, ink: Color): Modifier = if (focused) {
    background(ink.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
        .border(1.dp, ink.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
} else {
    this
}

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
 * Every item is at least 48dp tall and takes keyboard focus (Tab, arrows, Enter).
 */
@Composable
private fun MenuItem(
    label: String,
    value: String?,
    style: MenuStyle,
    prefix: String = ">",
    checked: Boolean? = null,
    focusFirst: Boolean = false,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val indication = LocalIndication.current
    val action = if (checked != null) {
        Modifier.toggleable(checked, interactionSource = source, indication = indication, role = Role.Switch) { onClick() }
    } else {
        Modifier.clickable(interactionSource = source, indication = indication, role = Role.Button, onClick = onClick)
    }
    val focus = remember { FocusRequester() }
    if (focusFirst) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .focusHighlight(focused, style.ink)
            .then(action)
            .heightIn(min = 48.dp)
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
    // A wide panel has room for the description; give it more of the row.
    val keyShare = if (style.wide) 0.26f else 0.36f
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        BasicText(key, style = style.key, modifier = Modifier.weight(keyShare).padding(end = 10.dp))
        BasicText(text, style = style.body, modifier = Modifier.weight(1f - keyShare))
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
    "KEYBOARD" to listOf(
        "0–9 . +" to "Type digits and − × ÷ (also * /) on a hardware keyboard.",
        "Enter" to "ENTER. Backspace erases, Delete is CLx.",
        "f g e ^ %" to "f, g, EEX, yˣ and %.",
        "Ctrl C · V" to "Copy and paste X.",
        "F1 · Esc" to "Open this menu, close it. Tab and the arrows move through it, Enter picks.",
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
    "CLEARING" to listOf(
        "CLx" to "Clear X only.",
        "f CLx" to "REG: the stack and every register.",
        "f x≷y" to "FIN: just the five TVM registers.",
        "f SST" to "Σ: the statistics registers R1–R6 and the stack.",
        "f R↓" to "PRGM: the program (in PRGM mode).",
    ),
    "ERRORS" to listOf(
        "Any key" to "Clears the error message; the stack is left as it was.",
        "0" to "Math, or a result out of range",
        "2" to "Statistics",
        "3 · 7" to "IRR",
        "4" to "Memory",
        "5" to "Financial",
        "6" to "Register",
        "8" to "Calendar",
    ),
    "QUESTIONS" to listOf(
        "Where's =?" to "There isn't one. ENTER separates two numbers and the operation finishes: 2 ENTER 3 + is 5.",
        "Wrong answer?" to "Check BEGIN (g 7) and the FIN registers: f x≷y clears old TVM values.",
        "PMT negative?" to "Money out is negative, money in positive: the 12c's cash-flow sign rule.",
        "Rotate?" to "Landscape is the full 4x10 keyboard; portrait folds it and shows T Z Y.",
        "Privacy?" to "No permissions, no network, no ads, no tracking. Nothing leaves the phone.",
        "A bug?" to "ON > SEND FEEDBACK opens a GitHub issue with the version filled in.",
        "Is it HP?" to "No: an independent homage, not affiliated with HP.",
    ),
)
