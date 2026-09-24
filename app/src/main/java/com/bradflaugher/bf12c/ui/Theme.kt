package com.bradflaugher.bf12c.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.bradflaugher.bf12c.R

/** Display color schemes. The keyboard keeps the 12c's gold and blue regardless. */
enum class Phosphor(
    val title: String,
    val ink: Color,
    val dim: Color,
    val glass: Color,
    val glow: Boolean,
) {
    GREEN("P1 GREEN", Color(0xFF39FF6A), Color(0xFF2DB356), Color(0xFF030D05), glow = true),
    AMBER("P3 AMBER", Color(0xFFFFB02E), Color(0xFFC08020), Color(0xFF100900), glow = true),
    ICE("ICE", Color(0xFF7DF3FF), Color(0xFF45B5C5), Color(0xFF020B0E), glow = true),
    LCD("12C LCD", Color(0xFF1B2016), Color(0xFF59624C), Color(0xFF9DA68D), glow = false),
}

object Palette {
    val bezel = Color(0xFF0B0C0B)
    val bezelEdge = Color(0xFF26282A)
    val faceplate = Color(0xFF151617)
    val gold = Color(0xFFE8A33D)
    val goldBright = Color(0xFFFFCB6B)
    val blue = Color(0xFF4FA3E0)
    val blueBright = Color(0xFF8CCBFF)
    val keyTop = Color(0xFF34373A)
    val keyBottom = Color(0xFF1B1D1F)
    val keyEdge = Color(0xFF4A4E52)
    val keyLabel = Color(0xFFF1F0EA)
}

object Fonts {
    val crt = FontFamily(Font(R.font.vt323))
    val mono = FontFamily(Font(R.font.share_tech_mono))
}
