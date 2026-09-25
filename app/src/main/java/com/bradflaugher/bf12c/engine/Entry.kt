package com.bradflaugher.bf12c.engine

import java.math.BigDecimal

/** A number being keyed in: mantissa digits, optional decimal point, optional exponent. */
internal class Entry {
    private val mantissa = StringBuilder()
    private var negative = false
    private var exponent: StringBuilder? = null
    private var exponentNegative = false

    /** Digits that count toward the register's precision: leading zeros don't. */
    private val significantDigits get() = mantissa.trimStart('0', '.').count { it.isDigit() }

    fun digit(d: Int) {
        val e = exponent
        if (e != null) {
            // Keep the last four exponent digits typed, like the 12c keeps two.
            e.append(d)
            if (e.length > 4) e.deleteCharAt(0)
            return
        }
        // Leading zeros don't count toward precision, but the buffer stays bounded:
        // past this many characters the value would underflow to zero anyway.
        // (A trailing digit there would be about 1E-9999; anything above is keyable.)
        if (significantDigits >= BigMath.DIGITS || mantissa.length >= MAX_CHARS) return
        if (mantissa.toString() == "0") mantissa.clear()
        mantissa.append(d)
    }

    private companion object {
        /** "0." plus every leading zero down to the register's smallest exponent, plus 34 digits. */
        const val MAX_CHARS = 2 + BigMath.MAX_EXPONENT + BigMath.DIGITS

        /** Longer entries show their start and end around an ellipsis. */
        const val SHOWN_CHARS = 40
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
        val m = mantissa.toString().ifEmpty { "0" }.let {
            if (it.length > SHOWN_CHARS) it.take(4) + "…" + it.takeLast(SHOWN_CHARS - 5) else it
        }
        val sign = if (negative) "-" else ""
        val e = exponent ?: return sign + Format.group(m)
        val exp = e.toString().padStart(2, '0')
        return sign + Format.group(m) + "E" + (if (exponentNegative) "-" else "+") + exp
    }
}
