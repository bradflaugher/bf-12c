package com.bradflaugher.bf12c.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.ln as dln
import kotlin.math.log10

/** Arbitrary-precision helpers. Results are rounded to [MC]; work is done at [WORK]. */
object BigMath {
    /** Significant digits kept in every register. */
    const val DIGITS = 34
    val MC = MathContext(DIGITS, RoundingMode.HALF_EVEN)
    val WORK = MathContext(DIGITS + 16, RoundingMode.HALF_EVEN)

    /** Largest decimal exponent a register may hold before we call it overflow. */
    const val MAX_EXPONENT = 9999

    val TWO: BigDecimal = BigDecimal.valueOf(2)
    val HUNDRED: BigDecimal = BigDecimal.valueOf(100)

    private val LN10: BigDecimal by lazy { lnReduced(BigDecimal.TEN) }

    fun round(x: BigDecimal): BigDecimal = x.round(MC)

    fun isInteger(x: BigDecimal): Boolean = x.signum() == 0 || x.stripTrailingZeros().scale() <= 0

    /** Decimal exponent e such that |x| = m * 10^e with 1 <= m < 10. */
    fun exponent(x: BigDecimal): Int = x.precision() - x.scale() - 1

    fun checkRange(x: BigDecimal): BigDecimal {
        if (x.signum() != 0 && exponent(x) > MAX_EXPONENT) throw CalcError(0)
        if (x.signum() != 0 && exponent(x) < -MAX_EXPONENT) return BigDecimal.ZERO
        return x
    }

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcError(0)
        return x.sqrt(WORK)
    }

    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        // e^x overflows past 10^MAX_EXPONENT around x = 23028.
        if (x > BigDecimal.valueOf(23100)) throw CalcError(0)
        if (x < BigDecimal.valueOf(-23100)) return BigDecimal.ZERO
        // Halve until |r| < 1/2, sum the Taylor series, then square back.
        var r = x
        var halvings = 0
        val half = BigDecimal("0.5")
        while (r.abs() > half) {
            r = r.divide(TWO, WORK)
            halvings++
        }
        val mc = MathContext(WORK.precision + halvings / 3 + 4, RoundingMode.HALF_EVEN)
        var term = BigDecimal.ONE
        var sum = BigDecimal.ONE
        var k = 1
        val eps = BigDecimal.ONE.movePointLeft(mc.precision + 2)
        while (true) {
            term = term.multiply(r, mc).divide(BigDecimal.valueOf(k.toLong()), mc)
            if (term.abs() < eps) break
            sum = sum.add(term, mc)
            k++
        }
        repeat(halvings) { sum = sum.multiply(sum, mc) }
        return sum.round(WORK)
    }

    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcError(0)
        if (x.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        // Split x = m * 10^e so the Newton seed from Double never overflows.
        val e = exponent(x)
        val m = x.movePointLeft(e)
        val lnM = lnReduced(m)
        return if (e == 0) lnM else lnM.add(LN10.multiply(BigDecimal.valueOf(e.toLong()), WORK), WORK)
    }

    /** ln for 0.1 <= x <= 10 via Halley iteration on exp. */
    private fun lnReduced(x: BigDecimal): BigDecimal {
        var y = BigDecimal(dln(x.toDouble()))
        val eps = BigDecimal.ONE.movePointLeft(WORK.precision)
        repeat(20) {
            val ey = exp(y)
            val delta = TWO.multiply(x.subtract(ey, WORK), WORK).divide(x.add(ey, WORK), WORK)
            y = y.add(delta, WORK)
            if (delta.abs() < eps) return y
        }
        return y
    }

    /** y^x, with exact repeated multiplication for integral exponents. */
    fun pow(y: BigDecimal, x: BigDecimal): BigDecimal {
        if (x.signum() == 0) {
            if (y.signum() == 0) throw CalcError(0)
            return BigDecimal.ONE
        }
        if (y.signum() == 0) {
            if (x.signum() < 0) throw CalcError(0)
            return BigDecimal.ZERO
        }
        val integral = isInteger(x)
        if (integral && x.abs() <= BigDecimal.valueOf(999_999_999)) {
            val n = x.intValueExact()
            // Estimate log10|y^n| first: results past the register range overflow,
            // results below it underflow to zero, and neither is worth multiplying out.
            val magnitude = n * log10Abs(y)
            if (magnitude > MAX_EXPONENT + 1) throw CalcError(0)
            if (magnitude < -(MAX_EXPONENT + 2)) return BigDecimal.ZERO
            return y.pow(n, WORK)
        }
        if (y.signum() < 0) {
            // A negative base only has a real power for integral exponents.
            if (!integral) throw CalcError(0)
            val r = exp(x.multiply(ln(y.negate()), WORK))
            return if (x.toBigInteger().testBit(0)) r.negate() else r
        }
        return exp(x.multiply(ln(y), WORK))
    }

    /** log10|x| to Double precision, for any register value (x ≠ 0). */
    private fun log10Abs(x: BigDecimal): Double {
        val e = exponent(x)
        return e + log10(x.movePointLeft(e).abs().toDouble())
    }

    fun factorial(x: BigDecimal): BigDecimal {
        if (x.signum() < 0 || !isInteger(x)) throw CalcError(0)
        if (x > BigDecimal.valueOf(3248)) throw CalcError(0) // 3249! > 10^9999
        val n = x.intValueExact()
        var acc = BigInteger.ONE
        for (k in 2..n) acc = acc.multiply(BigInteger.valueOf(k.toLong()))
        return BigDecimal(acc).round(MC)
    }

    fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalcError(0)
        return a.divide(b, WORK)
    }

    fun intPart(x: BigDecimal): BigDecimal = x.setScale(0, RoundingMode.DOWN)

    fun fracPart(x: BigDecimal): BigDecimal = x.subtract(intPart(x))
}

/** A calculator error; [code] matches the HP-12C "Error n" numbering. */
class CalcError(val code: Int) : Exception("Error $code")
