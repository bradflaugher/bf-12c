package com.bradflaugher.bf12c

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bradflaugher.bf12c.engine.Calculator
import com.bradflaugher.bf12c.engine.Display
import com.bradflaugher.bf12c.engine.Format
import com.bradflaugher.bf12c.engine.Key
import com.bradflaugher.bf12c.ui.Phosphor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Owns the engine. All engine access happens on one background thread so heavy
 * math (IRR, bond yields, long programs) never blocks the UI, and keystrokes
 * are applied strictly in order.
 */
class CalcViewModel(app: Application) : AndroidViewModel(app) {
    private val calc = Calculator()
    private val prefs = app.getSharedPreferences("bf12c", Context.MODE_PRIVATE)
    private val engine = Dispatchers.Default.limitedParallelism(1)

    var display: Display by mutableStateOf(calc.display())
        private set
    var phosphor by mutableStateOf(
        runCatching { Phosphor.valueOf(prefs.getString("phosphor", null) ?: "") }.getOrDefault(Phosphor.GREEN),
    )
        private set
    var haptics by mutableStateOf(prefs.getBoolean("haptics", true))
        private set
    var menuOpen by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch(engine) {
            prefs.getString("state", null)?.let { calc.restore(decode(it)) }
            publish()
        }
    }

    fun press(key: Key) {
        if (key == Key.ON) {
            menuOpen = !menuOpen
            return
        }
        viewModelScope.launch(engine) {
            calc.press(key)
            if (calc.running) runProgram() else commit()
        }
    }

    fun backspace() {
        viewModelScope.launch(engine) {
            calc.backspace()
            commit()
        }
    }

    enum class PasteResult { PASTED, HALTED, REJECTED, NOT_A_NUMBER }

    /** Pastes [text] into X, reporting what actually happened for the UI to confirm. */
    suspend fun paste(text: String): PasteResult {
        val value = Format.parseClipboard(text) ?: return PasteResult.NOT_A_NUMBER
        return withContext(engine) {
            val wasRunning = calc.running
            val applied = calc.paste(value)
            commit()
            when {
                applied -> PasteResult.PASTED
                wasRunning -> PasteResult.HALTED
                else -> PasteResult.REJECTED
            }
        }
    }

    fun closeMenu() {
        menuOpen = false
    }

    fun selectPhosphor(p: Phosphor) {
        phosphor = p
        prefs.edit { putString("phosphor", p.name) }
    }

    fun toggleHaptics() {
        haptics = !haptics
        prefs.edit { putBoolean("haptics", haptics) }
    }

    private suspend fun runProgram() {
        publish()
        var steps = 0
        while (calc.step()) {
            if (calc.pauseRequested) {
                publish()
                delay(1000)
                calc.pauseRequested = false
                publish()
            }
            // Let queued keystrokes (which halt the program) and frames through.
            if (++steps % 25 == 0) {
                yield()
                delay(1)
            }
        }
        calc.pauseRequested = false
        commit()
    }

    private suspend fun commit() {
        publish()
        val encoded = encode(calc.save())
        prefs.edit { putString("state", encoded) }
    }

    private suspend fun publish() {
        val d = calc.display()
        withContext(Dispatchers.Main.immediate) { display = d }
    }

    private fun encode(state: Map<String, String>) = state.entries.joinToString("\n") { "${it.key}=${it.value}" }

    private fun decode(text: String) = text.lineSequence()
        .mapNotNull { line -> line.indexOf('=').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
        .toMap()
}
