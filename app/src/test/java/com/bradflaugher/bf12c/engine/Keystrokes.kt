package com.bradflaugher.bf12c.engine

import org.junit.Assert.assertTrue
import java.math.BigDecimal
import java.math.MathContext

/**
 * The tiny keystroke DSL every engine test types with. A script is space-separated:
 * key names press keys, numbers become digit (and `.`) keystrokes.
 * `g n` is 12×, `f n` AMORT, `f yx` PRICE, `g CHS` DATE and so on.
 */
private val KEY_NAMES = mapOf(
    "n" to Key.N, "i" to Key.I, "PV" to Key.PV, "PMT" to Key.PMT, "FV" to Key.FV,
    "CHS" to Key.CHS, "/" to Key.DIV, "yx" to Key.YX, "1/x" to Key.RECIP, "%T" to Key.PCT_T,
    "D%" to Key.DELTA_PCT, "%" to Key.PCT, "EEX" to Key.EEX, "*" to Key.MUL, "RS" to Key.RS,
    "SST" to Key.SST, "RDN" to Key.RDN, "SWAP" to Key.SWAP, "CLX" to Key.CLX, "ENTER" to Key.ENTER,
    "-" to Key.SUB, "f" to Key.F, "g" to Key.G, "STO" to Key.STO, "RCL" to Key.RCL, "." to Key.DOT,
    "S+" to Key.SIGMA_PLUS, "+" to Key.ADD, "ON" to Key.ON,
)

/** Types [script] into this calculator. */
fun Calculator.keys(script: String): Calculator {
    for (token in script.trim().split(Regex("\\s+"))) {
        val key = KEY_NAMES[token]
        if (key != null) {
            press(key)
            continue
        }
        require(token.matches(Regex("[0-9.]+"))) { "Unknown token $token" }
        for (c in token) press(if (c == '.') Key.DOT else Key.digit(c - '0'))
    }
    return this
}

/** A fresh calculator with [script] typed in. */
fun run(script: String): Calculator = Calculator().keys(script)

/** Runs the program from the keyboard (R/S) until it halts, with a runaway guard. */
fun Calculator.runProgram(maxSteps: Int = 100_000): Calculator {
    press(Key.RS)
    var steps = 0
    while (step()) check(++steps < maxSteps) { "program did not halt" }
    return this
}

/** Equal when both round to the same [digits] significant digits. */
fun assertClose(expected: String, actual: BigDecimal, digits: Int = 30) {
    val mc = MathContext(digits)
    val e = BigDecimal(expected).round(mc)
    val a = actual.round(mc)
    assertTrue("expected $expected got ${actual.toPlainString()}", e.compareTo(a) == 0)
}

/** Equal to within half a unit in the last digit [expected] prints. */
fun assertNear(expected: String, actual: BigDecimal) {
    val e = BigDecimal(expected)
    val tolerance = BigDecimal.ONE.movePointLeft(e.scale()).divide(BigDecimal(2))
    assertTrue("expected $expected got ${actual.toPlainString()}", e.subtract(actual).abs() <= tolerance)
}
