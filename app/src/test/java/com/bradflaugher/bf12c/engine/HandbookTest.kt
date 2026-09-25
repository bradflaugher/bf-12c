package com.bradflaugher.bf12c.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Worked examples from the HP-12C Owner's Handbook (collected by kimi), checked
 * to the guard digits the handbook prints. Keystrokes use the same DSL as
 * [CalculatorTest] (see `Keystrokes.kt`).
 */
class HandbookTest {
    @Test fun tvm() {
        assertNear("10470.8497", run("14 ENTER 2 * n 5.35 ENTER 2 / i 5000 CHS PV FV").x)
        assertNear("-21396.6097", run("4 g n 6 g i 500 PMT g 7 PV").x)
        assertNear("-41.65292", run("14 g n 6 g i 10925.76 FV g 8 PMT").x)
        assertNear("0.41754", run("14 g n 6 g i 10925.76 FV 45 CHS PMT i").x)
        assertNear("1.60914", run("8 ENTER 4 * n 6000 CHS PV 10000 FV i").x)
        assertNear("5389.7221", run("4 g n 15 g i 150 CHS PMT g 8 PV").x)
        assertNear("-523.98835", run("29 g n 14.25 g i 43400 PV g 8 PMT").x)
        assertNear("-42652.3689", run("5 g n 14.25 g i 43400 PV 523.99 CHS PMT g 8 FV").x)
        assertNear("1281.3359", run("2 g n 6.25 g i 50 CHS PMT g 7 FV").x)
        assertNear("-717.44030", run("15 ENTER 2 * n 9.75 ENTER 2 / i 3200 CHS PV 60000 FV g 8 PMT").x)
        assertNear("28346.9562", run("6 n 2 CHS i 32000 CHS PV FV").x)
    }

    @Test fun periodsRoundUpThenFinalPayment() {
        val c = run("10.5 g i 35000 PV 325 CHS PMT g 8 n")
        assertNear("328", c.x)
        assertNear("181.89", c.keys("328 n FV").x)
        assertNear("-141.87", run("10.5 g i 35000 PV 325 CHS PMT 327 n FV").x)
    }

    @Test fun cashFlows() {
        val duplex = "80000 CHS g PV 500 CHS g PMT 4500 g PMT 5500 g PMT 4500 g PMT 130000 g PMT"
        assertNear("212.18405", run("$duplex 13 i f PV").x)
        assertNear("13.06289", run("$duplex f FV").x)
        val grouped = "79000 CHS g PV 14000 g PMT 11000 g PMT 10000 g PMT 3 g FV 9100 g PMT 9000 g PMT 2 g FV 4500 g PMT 100000 g PMT"
        assertNear("907.76893", run("$grouped 13.5 i f PV").x)
        assertNear("13.71972", run("$grouped f FV").x)
    }

    @Test fun simpleInterest() {
        val c = run("60 n 7 i 450 CHS PV f i")
        assertNear("5.25", c.x)
        assertNear("5.17808", c.stack[2])
        assertNear("455.25", c.keys("+").x)
        val d = run("45 n 9.75 i 750 CHS PV f i")
        assertNear("9.140625", d.x)
        assertNear("9.01541", d.stack[2])
    }

    @Test fun depreciation() {
        val sl = run("50000 PV 8000 FV 6 n 1 f %T")
        assertNear("7000", sl.x)
        assertNear("35000", sl.stack[1])
        assertNear("12000", run("50000 PV 8000 FV 6 n 1 f D%").x)
        assertNear("4000", run("50000 PV 8000 FV 6 n 5 f D%").x)
        val db = "10000 PV 500 FV 5 n 200 i"
        assertNear("4000", run("$db 1 f %").x)
        assertNear("2400", run("$db 2 f %").x)
        val y3 = run("$db 3 f %")
        assertNear("1440", y3.x)
        assertNear("1660", y3.stack[1])
    }

    @Test fun amortization() {
        val c = run("f 2 13.25 g i 50000 PV 573.35 CHS PMT 12 f n")
        assertNear("-6608.89", c.x)
        assertNear("-271.31", c.stack[1])
        assertNear("49728.69", c.fin[2])
        c.keys("12 f n")
        assertNear("-6570.72", c.x)
        assertNear("-309.48", c.stack[1])
        assertNear("49419.21", c.fin[2])

        val m = run("f 2 30 g n 13.25 g i 50000 PV g 8 PMT")
        assertNear("-562.88676", m.x)
        m.keys("0 n 1 f n")
        assertNear("-552.08", m.x)
        assertNear("-10.81", m.stack[1])
        assertNear("49989.19", m.fin[2])
    }

    @Test fun calendar() {
        val c = run("g 4 14.051981 ENTER 120 g CHS")
        assertNear("11.091981", c.x)
        assertEquals(5, c.display().weekday)
        val d = run("10.262005 ENTER 90 g CHS")
        assertNear("1.242006", d.x)
        assertEquals(2, d.display().weekday)
        val e = run("6.031983 ENTER 10.151984 g EEX")
        assertNear("500", e.x)
        assertNear("492", e.stack[1])
        val f = run("g 4 13.032005 ENTER 11.082005 g EEX")
        assertNear("151", f.x)
        assertNear("148", f.stack[1])
    }

    @Test fun bonds() {
        val p = run("8.25 i 6.75 PMT 4.281982 ENTER 6.041996 f yx")
        assertNear("87.6218", p.x)
        assertNear("2.6889", p.stack[1])
        assertNear("8.1506", run("88.375 PV 6.75 PMT 4.281982 ENTER 6.041996 f 1/x").x)
        val onCoupon = run("7 i 6 PMT 1.012000 ENTER 1.012005 f yx")
        assertNear("95.8417", onCoupon.x)
        assertNear("0", onCoupon.stack[1])
    }

    @Test fun statistics() {
        val data = "32 ENTER 17000 S+ 40 ENTER 25000 S+ 45 ENTER 26000 S+ 40 ENTER 20000 S+ " +
            "38 ENTER 21000 S+ 50 ENTER 28000 S+ 35 ENTER 15000 S+"
        val mean = run("$data g 0")
        assertNear("21714.29", mean.x)
        assertNear("40.00", mean.stack[1])
        val s = run("$data g .")
        assertNear("4820.5908", s.x)
        assertNear("6.0277", s.stack[1])
        val est = run("$data 48 g 1")
        assertNear("28818.9263", est.x)
        assertNear("0.90052", est.stack[1])
        assertNear("15.54918", run("$data 0 g 2").x)
        assertNear("1.18653", run("1.16 ENTER 15 S+ 1.24 ENTER 7 S+ 1.2 ENTER 10 S+ 1.18 ENTER 17 S+ g 6").x)
    }

    @Test fun percentages() {
        assertNear("42", run("300 ENTER 14 %").x)
        assertNear("12921.40", run("13250 ENTER 8 % - 6 % +").x)
        assertNear("-8.97436", run("58.5 ENTER 53.25 D%").x)
        val t = run("3.92 ENTER 2.36 + 1.67 + 2.36 %T")
        assertNear("29.69", t.x)
        assertNear("7.95", t.stack[1])
        assertNear("49.31", t.keys("CLX 3.92 %T").x)
    }
}
