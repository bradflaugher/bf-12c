package com.bradflaugher.bf12c.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext

class CalculatorTest {
    private val names = mapOf(
        "n" to Key.N, "i" to Key.I, "PV" to Key.PV, "PMT" to Key.PMT, "FV" to Key.FV,
        "CHS" to Key.CHS, "/" to Key.DIV, "yx" to Key.YX, "1/x" to Key.RECIP, "%T" to Key.PCT_T,
        "D%" to Key.DELTA_PCT, "%" to Key.PCT, "EEX" to Key.EEX, "*" to Key.MUL, "RS" to Key.RS,
        "SST" to Key.SST, "RDN" to Key.RDN, "SWAP" to Key.SWAP, "CLX" to Key.CLX, "ENTER" to Key.ENTER,
        "-" to Key.SUB, "f" to Key.F, "g" to Key.G, "STO" to Key.STO, "RCL" to Key.RCL, "." to Key.DOT,
        "S+" to Key.SIGMA_PLUS, "+" to Key.ADD,
    )

    /** Types a space-separated script: numbers become digit keystrokes, names become keys. */
    private fun Calculator.keys(script: String): Calculator {
        for (token in script.trim().split(Regex("\\s+"))) {
            val key = names[token]
            if (key != null) {
                press(key)
                continue
            }
            require(token.matches(Regex("[0-9.]+"))) { "Unknown token $token" }
            for (c in token) press(if (c == '.') Key.DOT else Key.digit(c - '0'))
        }
        return this
    }

    private fun run(script: String) = Calculator().keys(script)

    private fun assertClose(expected: String, actual: BigDecimal, digits: Int = 30) {
        val mc = MathContext(digits)
        val e = BigDecimal(expected).round(mc)
        val a = actual.round(mc)
        assertTrue("expected $expected got $actual", e.compareTo(a) == 0)
    }

    @Test fun basicRpn() {
        assertClose("7", run("3 ENTER 4 +").x)
        assertClose("-1", run("3 ENTER 4 -").x)
        assertClose("0.75", run("3 ENTER 4 /").x)
        // Stack lift: 2 ENTER 3 * 4 + = 10
        assertClose("10", run("2 ENTER 3 * 4 +").x)
        // ENTER duplicates X: 5 ENTER * = 25
        assertClose("25", run("5 ENTER *").x)
    }

    @Test fun thirtyFourDigits() {
        assertClose("0.3333333333333333333333333333333333", run("1 ENTER 3 /").x, 34)
        assertClose("1.414213562373095048801688724209698", run("2 g yx").x, 34)
        assertClose("2.718281828459045235360287471352662", run("1 g 1/x").x, 34)
        assertClose("2.302585092994045684017991454684364", run("10 g %T").x, 34)
    }

    @Test fun powers() {
        assertClose("1024", run("2 ENTER 10 yx").x)
        assertClose("-8", run("2 CHS ENTER 3 yx").x)
        assertClose("0.25", run("2 ENTER 2 CHS yx").x)
        assertClose("3", run("9 ENTER .5 yx").x)
    }

    @Test fun lastXAndSwap() {
        val c = run("3 ENTER 4 * g ENTER")
        assertClose("4", c.x)
        assertClose("12", c.stack[1])
        val s = run("1 ENTER 2 SWAP")
        assertClose("1", s.x)
        assertClose("2", s.stack[1])
    }

    @Test fun percentKeepsBase() {
        val c = run("200 ENTER 15 %")
        assertClose("30", c.x)
        assertClose("200", c.stack[1])
        assertClose("25", run("40 ENTER 50 D%").x)
        assertClose("25", run("200 ENTER 50 %T").x)
    }

    @Test fun exponentEntry() {
        assertClose("1.5E+12", run("1.5 EEX 12").x)
        assertClose("2E-3", run("2 EEX 3 CHS").x)
    }

    @Test fun registerArithmetic() {
        val c = run("5 STO 1 3 STO + 1 RCL 1")
        assertClose("8", c.x)
        assertClose("42", run("42 STO . 5 CLX RCL . 5").x)
    }

    @Test fun mortgagePayment() {
        // 30 years monthly, 6.5% a year, $100,000.
        val c = run("30 g n 6.5 g i 100000 PV 0 FV PMT")
        assertClose("-632.0680234929637320458316762386597659253", c.x, 28)
    }

    @Test fun futureValue() {
        assertClose("-1628.89462677744140625", run("10 n 5 i 1000 PV 0 PMT FV").x)
    }

    @Test fun beginMode() {
        assertClose("-1280.932804332894178678130100", run("g 7 12 n 1 i 0 PV 100 PMT FV").x)
    }

    @Test fun solveRate() {
        assertClose("7.177346253629316421300632502334", run("10 n 1000 CHS PV 0 PMT 2000 FV i").x)
    }

    @Test fun solvePeriodsRoundsUp() {
        // 1000 at 10% to 2000 takes 7.27 periods: the 12c says 8.
        assertClose("8", run("10 i 1000 CHS PV 0 PMT 2000 FV n").x)
    }

    @Test fun cashFlows() {
        val c = run("1000 CHS g PV 300 g PMT 400 g PMT 500 g PMT f FV")
        assertClose("8.896339469334993531776567968686", c.x, 28)
        val npv = run("1000 CHS g PV 300 g PMT 400 g PMT 500 g PMT 10 i f PV")
        assertClose("-21.03681442524417731029301277235", npv.x, 28)
    }

    @Test fun groupedCashFlows() {
        // -100, then 50 three times at Nj = 3.
        val grouped = run("100 CHS g PV 50 g PMT 3 g FV f FV").x
        val listed = run("100 CHS g PV 50 g PMT 50 g PMT 50 g PMT f FV").x
        assertClose(listed.toPlainString(), grouped, 28)
    }

    @Test fun dates() {
        val c = run("2.142004 ENTER 120 g CHS")
        assertClose("6.132004", c.x)
        assertEquals(7, c.display().weekday)
        val d = run("6.032004 ENTER 10.142004 g EEX")
        assertClose("133", d.x)
        assertClose("131", d.stack[1])
    }

    @Test fun statistics() {
        // y values 2 4 6 with x 1 2 3: y = 2x, perfect correlation.
        val c = run("2 ENTER 1 S+ 4 ENTER 2 S+ 6 ENTER 3 S+")
        assertClose("3", c.x)
        c.keys("g 0")
        assertClose("2", c.x)
        assertClose("4", c.stack[1])
        c.keys("g .")
        assertClose("1", c.x)
        c.keys("10 g 2")
        assertClose("20", c.x)
        assertClose("1", c.stack[1])
    }

    @Test fun depreciation() {
        // Cost 10,000, salvage 1,000, 5 years.
        val sl = run("10000 PV 1000 FV 5 n 2 f %T")
        assertClose("1800", sl.x)
        assertClose("5400", sl.stack[1])
        val soyd = run("10000 PV 1000 FV 5 n 1 f D%")
        assertClose("3000", soyd.x)
        val db = run("10000 PV 1000 FV 5 n 200 i 1 f %")
        assertClose("4000", db.x)
    }

    @Test fun amortization() {
        val c = run("f 2 30 g n 6.5 g i 100000 PV 0 FV PMT")
        c.keys("0 n 12 f n")
        // First year's interest on the loan, each month rounded to cents.
        assertTrue(c.x.signum() < 0)
        assertClose("12", c.fin[0])
    }

    @Test fun errorsClearOnNextKey() {
        val c = run("1 ENTER 0 /")
        assertEquals(0, c.error)
        c.keys("5")
        assertEquals(null, c.error)
    }

    @Test fun program() {
        // Program: square X and add one.  f P/R  ENTER * 1 +  f P/R
        val c = run("f RS ENTER * 1 + f RS")
        assertEquals(4, c.program.size)
        // Leaving program mode rewinds to line 00, so R/S runs from line 01.
        c.keys("7 RS")
        while (c.step()) Unit
        assertClose("50", c.x)
    }

    @Test fun programLoopWithCondition() {
        // Count down from X to 0:  001 1  002 -  003 g x=0  004 GTO 06  005 GTO 01  006 R/S
        val c = run("f RS 1 - g CLX g RDN 0 6 g RDN 0 1 RS f RS")
        assertEquals(6, c.program.size)
        c.keys("f RDN 5 RS")
        var guard = 0
        while (c.step() && guard++ < 1000) Unit
        assertClose("0", c.x)
    }

    @Test fun continuousMemoryRoundTrip() {
        val c = run("f 4 g 7 3.25 STO 9 12 n f RS 1 + f RS 42")
        val restored = Calculator().apply { restore(c.save()) }
        assertEquals(c.save(), restored.save())
        assertClose("42", restored.x)
    }

    @Test fun formatting() {
        assertEquals("1,234.57", Format.format(BigDecimal("1234.5678"), DisplayMode.Fix(2)))
        assertEquals("1.2346E+3", Format.format(BigDecimal("1234.5678"), DisplayMode.Sci(4)))
        assertEquals("0.1", Format.format(BigDecimal("0.10"), DisplayMode.All))
        assertEquals("1.0E+30", Format.format(BigDecimal("1E30"), DisplayMode.All))
    }

    @Test fun financialStoreDisablesLiftAndSolveOverwritesX() {
        val c = run("100000 PV 5")
        assertClose("5", c.x)
        assertClose("0", c.stack[1])
        val solved = run("7 ENTER 10 n 5 i 1000 PV 0 PMT FV")
        // The solve replaces X; Y still holds the 7 keyed before.
        assertClose("7", solved.stack[1])
    }

    @Test fun oddPeriodSimpleAndCompound() {
        assertClose("-1132.459155282629569264507005", run("12.5 n 1 i 1000 PV 0 PMT FV").x, 28)
        assertClose("-1132.445139959209509076566907757", run("STO EEX 12.5 n 1 i 1000 PV 0 PMT FV").x, 28)
    }

    @Test fun amortizationStack() {
        val c = run("f 2 99 ENTER 30 g n 6.5 g i 100000 PV 632.07 CHS PMT 0 n 1 f n")
        // First month: interest 541.67 and principal 90.40, both paid out.
        assertClose("-541.67", c.x)
        assertClose("-90.40", c.stack[1])
        assertClose("1", c.stack[2])
        assertClose("99909.60", c.fin[2])
    }

    @Test fun depreciationStack() {
        val c = run("7 ENTER 10000 PV 1000 FV 5 n 2 f %T")
        assertClose("1800", c.x)
        assertClose("5400", c.stack[1])
        assertClose("2", c.stack[2])
    }

    @Test fun keyboardGotoRunsNamedLine() {
        // 001 1  002 +  003 2  004 *  — GTO 03 then R/S runs only "2 *".
        val c = run("f RS 1 + 2 * f RS 5 g RDN 0 3 RS")
        while (c.step()) Unit
        assertClose("10", c.x)
    }

    @Test fun irrSameSignIsError7() {
        assertEquals(7, run("100 g PV 50 g PMT f FV").error)
    }

    @Test fun reviewCashFlows() {
        val c = run("100 CHS g PV 30 g PMT 4 g FV 20 g PMT RCL g FV")
        assertClose("1", c.x)
        c.keys("RCL g PMT")
        assertClose("20", c.x)
        c.keys("RCL g FV")
        assertClose("4", c.x)
    }
}
