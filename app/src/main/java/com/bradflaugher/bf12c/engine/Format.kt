package com.bradflaugher.bf12c.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed interface DisplayMode {
    /** f 0…9: fixed decimals, like the 12c. */
    data class Fix(val digits: Int) : DisplayMode

    /** f . : scientific notation with 7 mantissa decimals… or as many as FIX asked for. */
    data class Sci(val digits: Int) : DisplayMode

    /** f EEX: every significant digit the engine holds. */
    data object All : DisplayMode
}

object Format {
    /** Largest integer part shown before switching to scientific notation. */
    private const val MAX_PLAIN_INT_DIGITS = 22
    private const val MIN_PLAIN_EXPONENT = -12

    fun format(x: BigDecimal, mode: DisplayMode): String = when (mode) {
        is DisplayMode.Fix -> fix(x, mode.digits)
        is DisplayMode.Sci -> sci(x, mode.digits)
        DisplayMode.All -> all(x)
    }

    /** Full precision, never grouped: what the copy button hands out. */
    fun plain(x: BigDecimal): String {
        val s = x.round(BigMath.MC).stripTrailingZeros()
        val e = BigMath.exponent(s)
        if (s.signum() == 0) return "0"
        return if (e in MIN_PLAIN_EXPONENT..MAX_PLAIN_INT_DIGITS) s.toPlainString() else sciAll(s)
    }

    private fun fix(x: BigDecimal, digits: Int): String {
        if (x.signum() == 0) return group(BigDecimal.ZERO.setScale(digits).toPlainString())
        val e = BigMath.exponent(x)
        if (e >= MAX_PLAIN_INT_DIGITS) return sci(x, digits)
        val r = x.setScale(digits, RoundingMode.HALF_UP)
        // Like the 12c: a nonzero value that would display as zero switches to SCI.
        if (r.signum() == 0) return sci(x, digits)
        return group(r.toPlainString())
    }

    private fun sci(x: BigDecimal, digits: Int): String {
        if (x.signum() == 0) return BigDecimal.ZERO.setScale(digits).toPlainString() + "E+0"
        val r = x.round(MathContext(digits + 1, RoundingMode.HALF_UP))
        val e = BigMath.exponent(r)
        val mantissa = r.movePointLeft(e).setScale(digits, RoundingMode.HALF_UP)
        return mantissa.toPlainString() + exp(e)
    }

    private fun all(x: BigDecimal): String {
        val s = x.round(BigMath.MC).stripTrailingZeros()
        if (s.signum() == 0) return "0"
        val e = BigMath.exponent(s)
        if (e in MIN_PLAIN_EXPONENT until MAX_PLAIN_INT_DIGITS) return group(s.toPlainString())
        return sciAll(s)
    }

    private fun sciAll(s: BigDecimal): String {
        val e = BigMath.exponent(s)
        val mantissa = s.movePointLeft(e).stripTrailingZeros()
        val m = if (mantissa.scale() <= 0) mantissa.setScale(1).toPlainString() else mantissa.toPlainString()
        return m + exp(e)
    }

    private fun exp(e: Int) = if (e < 0) "E$e" else "E+$e"

    /** Adds thousands separators to the integer part of a plain decimal string. */
    fun group(plain: String): String {
        val negative = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val rest = if (dot >= 0) body.substring(dot) else ""
        val grouped = intPart.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) "-" else "") + grouped + rest
    }
}
