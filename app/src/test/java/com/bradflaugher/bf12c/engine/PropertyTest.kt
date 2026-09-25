package com.bradflaugher.bf12c.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import kotlin.random.Random

/**
 * Seeded randomized checks: identities that must hold for any input, and a
 * keystroke fuzzer proving no key sequence can crash the engine.
 */
class PropertyTest {
    private val rnd = Random(12_345)

    private fun Random.decimal(lo: Double, hi: Double, scale: Int = 4): BigDecimal =
        BigDecimal(nextDouble(lo, hi)).setScale(scale, java.math.RoundingMode.HALF_EVEN)

    private fun assertRelative(expected: BigDecimal, actual: BigDecimal, tolerance: String, message: String) {
        val scale = expected.abs().max(BigDecimal.ONE)
        val err = expected.subtract(actual).abs().divide(scale, MathContext.DECIMAL64)
        assertTrue("$message: expected $expected got $actual", err <= BigDecimal(tolerance))
    }

    @Test fun keystrokeFuzzNeverCrashesOrStalls() {
        val keys = Key.entries.filter { it != Key.ON }
        var slowest = 0L
        var slowestAfter = ""
        repeat(120) { round ->
            val typed = java.util.Collections.synchronizedList(mutableListOf<Key>())
            val c = Calculator()
            val worker = Thread {
                repeat(250) {
                    val k = keys[rnd.nextInt(keys.size)]
                    typed += k
                    val start = System.nanoTime()
                    c.press(k)
                    var steps = 0
                    while (c.running && steps++ < 50) c.step()
                    if (c.running) c.press(Key.CLX)
                    if (rnd.nextInt(40) == 0) c.backspace()
                    c.display()
                    val took = System.nanoTime() - start
                    if (took > slowest) {
                        slowest = took
                        slowestAfter = typed.takeLast(12).joinToString(" ")
                    }
                }
            }
            var failure: Throwable? = null
            worker.isDaemon = true // a stalled round must not keep the test JVM alive
            worker.setUncaughtExceptionHandler { _, e -> failure = e }
            worker.start()
            worker.join(20_000)
            if (worker.isAlive) {
                val stack = worker.stackTrace.take(12).joinToString("\n  at ")
                throw AssertionError("round $round stalled after ${typed.joinToString(" ")}\n  at $stack")
            }
            failure?.let { throw AssertionError("round $round crashed after ${typed.joinToString(" ")}", it) }
            // Whatever state we reached survives a save/restore round trip.
            val saved = c.save()
            assertEquals(saved, Calculator().apply { restore(saved) }.save())
        }
        println("slowest keystroke: ${slowest / 1_000_000} ms, ending … $slowestAfter")
        assertTrue("a keystroke took ${slowest / 1_000_000} ms: … $slowestAfter", slowest < 2_000_000_000L)
    }

    @Test fun tvmSolvesAreMutuallyConsistent() {
        repeat(150) {
            // Well-conditioned loans: (1+i)^−n stays far from zero so FV is recoverable.
            val n = BigDecimal(rnd.nextInt(1, 361))
            val i = rnd.decimal(0.05, 5.0)
            val pv = rnd.decimal(100.0, 1e6, 2)
            // A loan with a balloon: PV in, payments and FV out, so the rate is unique.
            val fv = rnd.decimal(0.0, 1e5, 2).negate()
            val begin = rnd.nextBoolean()
            val t = Finance.Tvm(n, i, pv, BigDecimal.ZERO, fv, begin)
            val pmt = Finance.solvePmt(t)
            val full = t.copy(pmt = pmt)
            val where = "n=$n i=$i pv=$pv fv=$fv begin=$begin"
            assertRelative(pv, Finance.solvePv(full), "1E-25", "PV $where")
            assertRelative(fv, Finance.solveFv(full), "1E-20", "FV $where")
            assertRelative(i, Finance.solveI(full), "1E-20", "i $where")
            assertEquals("n $where", 0, n.compareTo(Finance.solveN(full)))
        }
    }

    @Test fun npvAtIrrIsZero() {
        repeat(60) {
            val count = rnd.nextInt(2, 12)
            val flows = listOf(rnd.decimal(-1e5, -100.0, 2)) + List(count) { rnd.decimal(-2e3, 3e4, 2) }
            val counts = listOf(1) + List(count) { rnd.nextInt(1, 5) }
            val irr = try {
                Finance.irr(flows, counts)
            } catch (_: CalcError) {
                return@repeat // no sign change or no root in range: legitimately Error 3/7
            }
            val npv = Finance.npv(irr, flows, counts)
            val scale = flows.maxOf { it.abs() }
            assertTrue("NPV at IRR $irr is $npv for $flows", npv.abs() <= scale.multiply(BigDecimal("1E-24")))
        }
    }

    @Test fun mathIdentities() {
        repeat(200) {
            val x = rnd.decimal(0.001, 1000.0, 6)
            assertRelative(x, BigMath.ln(BigMath.exp(BigMath.ln(x))).let(BigMath::exp), "1E-30", "exp(ln $x)")
            val s = BigMath.sqrt(x)
            assertRelative(x, s.multiply(s), "1E-32", "sqrt($x)^2")
            val y = rnd.decimal(0.1, 50.0, 3)
            val p = rnd.decimal(-20.0, 20.0, 3)
            assertRelative(BigMath.exp(p.multiply(BigMath.ln(y))), BigMath.pow(y, p), "1E-30", "$y^$p")
            val k = rnd.nextInt(-40, 40)
            assertRelative(y.pow(k, MathContext(60)), BigMath.pow(y, BigDecimal(k)), "1E-32", "$y^$k")
        }
    }

    @Test fun dateArithmeticRoundTrips() {
        repeat(300) {
            val start = LocalDate.of(1900, 1, 1).plusDays(rnd.nextLong(0, 200 * 365L))
            val days = rnd.nextLong(-20_000, 20_000)
            for (dmy in listOf(false, true)) {
                val encoded = Dates.encode(start, dmy)
                assertEquals(start, Dates.decode(encoded, dmy))
                val c = Calculator()
                if (dmy) c.keys("g 4")
                c.paste(encoded)
                c.keys("ENTER")
                c.paste(BigDecimal(days))
                c.keys("g CHS")
                val end = Dates.decode(c.x, dmy)
                assertEquals(start.plusDays(days), end)
                assertEquals(end.dayOfWeek.value, c.display().weekday)
                assertEquals(days, Dates.daysBetween(start, end).first)
            }
        }
    }

    @Test fun formatAlwaysReadsBackToTheDisplayedValue() {
        val modes = listOf(DisplayMode.All, DisplayMode.Fix(0), DisplayMode.Fix(2), DisplayMode.Fix(9), DisplayMode.Sci(3), DisplayMode.Sci(9))
        repeat(500) {
            val x = BigDecimal(rnd.nextDouble(-1.0, 1.0)).scaleByPowerOfTen(rnd.nextInt(-40, 40)).round(BigMath.MC)
            for (m in modes) {
                val text = Format.format(x, m)
                val back = Format.parse(text)
                assertTrue("$x in $m shows unreadable '$text'", back != null)
                if (m == DisplayMode.All) assertEquals("$x in ALL shows '$text'", 0, x.compareTo(back))
            }
            assertEquals(0, x.compareTo(Format.parse(Format.plain(x))))
        }
    }
}
