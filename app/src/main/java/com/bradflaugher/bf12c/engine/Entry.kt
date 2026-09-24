package com.bradflaugher.bf12c.engine

import java.math.BigDecimal

/** A number being keyed in: mantissa digits, optional decimal point, optional exponent. */
internal class Entry {
    private val mantissa = StringBuilder()
    private var negative = false
    private var exponent: StringBuilder? = null
    private var exponentNegative = false

    private val significantDigits get() = mantissa.count { it.isDigit() }

    fun digit(d: Int) {
        val e = exponent
        if (e != null) {
            // Keep the last four exponent digits typed, like the 12c keeps two.
            e.append(d)
            if (e.length > 4) e.deleteCharAt(0)
            return
        }
        if (significantDigits >= BigMath.DIGITS) return
        if (mantissa.toString() == "0") mantissa.clear()
        mantissa.append(d)
    }

    fun dot() {
        if (exponent != null || mantissa.contains('.')) return
        if (mantissa.isEmpty()) mantissa.append('0')
        mantissa.append('.')
    }

    fun eex() {
        if (exponent != null) return
        if (mantissa.isEmpty() || mantissa.none { it in '1'..'9' }) {
            mantissa.clear()
            mantissa.append('1')
        }
        exponent = StringBuilder()
    }

    fun chs() {
        if (exponent != null) exponentNegative = !exponentNegative else negative = !negative
    }

    /** Removes the last keystroke; returns false once nothing is left. */
    fun backspace(): Boolean {
        val e = exponent
        when {
            e != null && e.isNotEmpty() -> e.deleteCharAt(e.length - 1)
            e != null -> { exponent = null; exponentNegative = false }
            mantissa.isNotEmpty() -> mantissa.deleteCharAt(mantissa.length - 1)
        }
        return mantissa.isNotEmpty() || exponent != null
    }

    fun value(): BigDecimal {
        val m = mantissa.toString().ifEmpty { "0" }.let { if (it.endsWith('.')) it + "0" else it }
        var v = BigDecimal(m)
        val e = exponent
        if (e != null && e.isNotEmpty()) {
            val n = e.toString().toInt()
            v = v.scaleByPowerOfTen(if (exponentNegative) -n else n)
        }
        return if (negative) v.negate() else v
    }

    /** What the display shows while typing: grouped digits and a live exponent field. */
    fun text(): String {
        val m = mantissa.toString().ifEmpty { "0" }
        val sign = if (negative) "-" else ""
        val e = exponent ?: return sign + Format.group(m)
        val exp = e.toString().padStart(2, '0')
        return sign + Format.group(m) + "E" + (if (exponentNegative) "-" else "+") + exp
    }
}
