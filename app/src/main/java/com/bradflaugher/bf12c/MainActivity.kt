package com.bradflaugher.bf12c

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bradflaugher.bf12c.ui.CalculatorScreen

class MainActivity : ComponentActivity() {
    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySystemBars(resources.configuration)
        // tools/screenshots/shoot.py starts every scene this way.
        if (intent.getBooleanExtra(EXTRA_SKIP_TIPS, false)) vm.skipTips()
        setContent { CalculatorScreen(vm) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars(newConfig)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        applySystemBars(newConfig)
    }

    // Coming back from another app can bring the bars back; hide them again.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(resources.configuration)
    }

    /**
     * Landscape is the full 12c keyboard: give it the whole screen. A split-screen or
     * desktop window shares the screen with other apps, so there the bars stay.
     */
    private fun applySystemBars(config: Configuration) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    companion object {
        /** `am start --ez com.bradflaugher.bf12c.SKIP_TIPS true`: open without the first-run tips. */
        const val EXTRA_SKIP_TIPS = "com.bradflaugher.bf12c.SKIP_TIPS"
    }
}
