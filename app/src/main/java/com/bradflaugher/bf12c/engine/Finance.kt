package com.bradflaugher.bf12c.engine

import com.bradflaugher.bf12c.engine.BigMath.HUNDRED
import com.bradflaugher.bf12c.engine.BigMath.WORK
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow

/**
 * The 12c financial equations. Rates are in percent, as keyed. Sign convention:
 * money received is positive, money paid out is negative.
 */
object Finance {
    private val ONE = BigDecimal.ONE
    private val ZERO = BigDecimal.ZERO

    data class Tvm(
        val n: BigDecimal,
        val i: BigDecimal,
        val pv: BigDecimal,
        val pmt: BigDecimal,
        val fv: BigDecimal,
        val begin: Boolean,
        /** C annunciator: compound interest over a fractional first period. */
        val compoundOdd: Boolean = false,
    )

    // 0 = PV + (1 + r·s)·PMT·[(1 − (1+r)^−n) / r] + FV·(1+r)^−n

    private fun discount(r: BigDecimal, n: BigDecimal): BigDecimal =
        BigMath.pow(ONE.add(r, WORK), n.negate())

    /** Present value of an annuity of 1 per period, with the BEGIN adjustment. */
    private fun annuity(r: BigDecimal, n: BigDecimal, begin: Boolean): BigDecimal {
        if (r.signum() == 0) return n
        val a = ONE.subtract(discount(r, n), WORK).divide(r, WORK)
        return if (begin) a.multiply(ONE.add(r, WORK), WORK) else a
    }

    private fun rate(t: Tvm): BigDecimal {
        val r = t.i.divide(HUNDRED, WORK)
        if (r <= ONE.negate()) throw CalcError(5)
        return r
    }

    /**
     * The TVM terms at rate r: odd-period factor on PV, annuity factor on PMT and
     * discount factor on FV. A fractional n is an odd first period, charged simple
     * interest (or compound with the C flag), exactly as the 12c does.
     */
    private data class Terms(val odd: BigDecimal, val annuity: BigDecimal, val discount: BigDecimal)

    private fun terms(t: Tvm, r: BigDecimal): Terms {
        val whole = if (BigMath.isInteger(t.n)) t.n else t.n.setScale(0, RoundingMode.FLOOR)
        val frac = t.n.subtract(whole)
        val odd = when {
            frac.signum() == 0 -> ONE
            t.compoundOdd -> BigMath.pow(ONE.add(r, WORK), frac)
            else -> ONE.add(r.multiply(frac, WORK), WORK)
        }
        return Terms(odd, annuity(r, whole, t.begin), discount(r, whole))
    }

    fun solvePv(t: Tvm): BigDecimal {
        val k = terms(t, rate(t))
        return t.pmt.multiply(k.annuity, WORK).add(t.fv.multiply(k.discount, WORK), WORK).negate().divide(k.odd, WORK)
    }

    fun solveFv(t: Tvm): BigDecimal {
        val k = terms(t, rate(t))
        if (k.discount.signum() == 0) throw CalcError(5)
        val rest = t.pv.multiply(k.odd, WORK).add(t.pmt.multiply(k.annuity, WORK), WORK)
        return rest.negate().divide(k.discount, WORK)
    }

    fun solvePmt(t: Tvm): BigDecimal {
        val k = terms(t, rate(t))
        if (k.annuity.signum() == 0) throw CalcError(5)
        return t.pv.multiply(k.odd, WORK).add(t.fv.multiply(k.discount, WORK), WORK).negate().divide(k.annuity, WORK)
    }

    /** Like the 12c, a fractional number of periods is rounded to a whole number (see [ceilTolerant]). */
    fun solveN(t: Tvm): BigDecimal {
        val r = rate(t)
        val exact: BigDecimal = if (r.signum() == 0) {
            if (t.pmt.signum() == 0) throw CalcError(5)
            t.pv.add(t.fv, WORK).negate().divide(t.pmt, WORK)
        } else {
            // (1+r)^n = (PMT' − FV·r) / (PMT' + PV·r), PMT' = PMT·(1 + r·s)
            val p = if (t.begin) t.pmt.multiply(ONE.add(r, WORK), WORK) else t.pmt
            val num = p.subtract(t.fv.multiply(r, WORK), WORK)
            val den = p.add(t.pv.multiply(r, WORK), WORK)
            if (den.signum() == 0) throw CalcError(5)
            val ratio = num.divide(den, WORK)
            if (ratio.signum() <= 0) throw CalcError(5)
            BigMath.ln(ratio).divide(BigMath.ln(ONE.add(r, WORK)), WORK)
        }
        if (exact.signum() <= 0) throw CalcError(5)
        return ceilTolerant(exact)
    }

    /** The 12c rounds n up, unless the fraction is under 0.005, then down. */
    private fun ceilTolerant(x: BigDecimal): BigDecimal {
        val floor = x.setScale(0, RoundingMode.FLOOR)
        if (x.subtract(floor) < BigDecimal("0.005")) return floor
        return x.setScale(0, RoundingMode.CEILING)
    }

    fun solveI(t: Tvm): BigDecimal {
        fun f(r: BigDecimal): BigDecimal {
            val k = terms(t, r)
            return t.pv.multiply(k.odd, WORK).add(t.pmt.multiply(k.annuity, WORK), WORK)
                .add(t.fv.multiply(k.discount, WORK), WORK)
        }
        if (t.n.signum() <= 0) throw CalcError(5)
        val whole = t.n.setScale(0, RoundingMode.FLOOR).toDouble()
        val frac = t.n.toDouble() - whole
        val r = findRoot(::f, fdouble = { r ->
            val v = if (r == 0.0) 1.0 else (1 + r).pow(-whole)
            val a = if (r == 0.0) whole else (1 - v) / r * if (t.begin) 1 + r else 1.0
            val odd = if (frac == 0.0) 1.0 else if (t.compoundOdd) (1 + r).pow(frac) else 1 + r * frac
            t.pv.toDouble() * odd + t.pmt.toDouble() * a + t.fv.toDouble() * v
        }) ?: throw CalcError(5)
        return r.multiply(HUNDRED, WORK)
    }

    // --- Discounted cash flows -------------------------------------------------

    /** [flows] is CF0..CFj, [counts] the matching Nj (CF0's count is ignored). */
    fun npv(rPercent: BigDecimal, flows: List<BigDecimal>, counts: List<Int>): BigDecimal =
        npvAt(rPercent.divide(HUNDRED, WORK), flows, counts)

    private fun npvAt(r: BigDecimal, flows: List<BigDecimal>, counts: List<Int>): BigDecimal {
        if (r <= ONE.negate()) throw CalcError(3)
        val base = ONE.add(r, WORK)
        var sum = flows[0]
        var period = 0
        for (j in 1 until flows.size) {
            repeat(counts[j]) {
                period++
                sum = sum.add(flows[j].divide(base.pow(period, WORK), WORK), WORK)
            }
        }
        return sum
    }

    fun irr(flows: List<BigDecimal>, counts: List<Int>): BigDecimal {
        val expanded = buildList {
            add(flows[0].toDouble())
            for (j in 1 until flows.size) repeat(counts[j]) { add(flows[j].toDouble()) }
        }
        if (expanded.none { it > 0 } || expanded.none { it < 0 }) throw CalcError(7)
        val r = findRoot({ npvAt(it, flows, counts) }, fdouble = { r ->
            var s = 0.0
            var d = 1.0
            for (cf in expanded) {
                s += cf / d
                d *= 1 + r
            }
            s
        }) ?: throw CalcError(3)
        return r.multiply(HUNDRED, WORK)
    }

    /**
     * Root of f in rate space (r > −1). A Double scan brackets a sign change nearest
     * zero, bisection narrows it, then BigDecimal secant polishes to full precision.
     */
    private fun findRoot(f: (BigDecimal) -> BigDecimal, fdouble: (Double) -> Double): BigDecimal? {
        val sorted = buildList {
            var x = -0.9999
            while (x < 10.0) {
                add(x)
                x += if (abs(x) < 1) 0.0025 else 0.05
            }
        }
        var bracket: Pair<Double, Double>? = null
        var best = Double.MAX_VALUE
        for (k in 0 until sorted.size - 1) {
            val a = sorted[k]
            val b = sorted[k + 1]
            val fa = fdouble(a)
            val fb = fdouble(b)
            if (fa.isFinite() && fb.isFinite() && (fa == 0.0 || fa * fb < 0)) {
                val dist = minOf(abs(a), abs(b))
                if (dist < best) {
                    best = dist
                    bracket = a to b
                }
            }
        }
        var (lo, hi) = bracket ?: return null
        var flo = fdouble(lo)
        // The scan landed exactly on the root: keep it rather than bisecting away.
        if (flo == 0.0) hi = lo
        repeat(80) {
            val mid = (lo + hi) / 2
            val fm = fdouble(mid)
            if (fm == 0.0) {
                lo = mid
                hi = mid
            } else if (flo * fm < 0) {
                hi = mid
            } else {
                lo = mid
                flo = fm
            }
        }
        // Secant refinement in full precision.
        val mc = MathContext(WORK.precision, RoundingMode.HALF_EVEN)
        var x0 = BigDecimal(lo)
        var x1 = BigDecimal((lo + hi) / 2 + 1e-12)
        var f0 = f(x0)
        val eps = BigDecimal.ONE.movePointLeft(BigMath.DIGITS + 4)
        repeat(60) {
            val f1 = f(x1)
            val denom = f1.subtract(f0, mc)
            if (denom.signum() == 0) return x1
            val x2 = x1.subtract(f1.multiply(x1.subtract(x0, mc), mc).divide(denom, mc), mc)
            if (x2.subtract(x1).abs() < eps) return x2
            x0 = x1
            f0 = f1
            x1 = x2
        }
        return x1
    }

    // --- Amortization ------------------------------------------------------------

    data class Amort(val interest: BigDecimal, val principal: BigDecimal, val balance: BigDecimal)

    /**
     * Amortizes [payments] periods starting after [periodsDone]. Each period's interest
     * is rounded with [roundTo] (the display setting), exactly as the 12c does.
     */
    fun amortize(
        payments: Int,
        periodsDone: Int,
        iPercent: BigDecimal,
        pv: BigDecimal,
        pmt: BigDecimal,
        begin: Boolean,
        roundTo: (BigDecimal) -> BigDecimal,
    ): Amort {
        if (payments <= 0) throw CalcError(5)
        val r = iPercent.divide(HUNDRED, WORK)
        var balance = pv
        var interest = ZERO
        var principal = ZERO
        for (k in 0 until payments) {
            val firstBeginPeriod = begin && periodsDone + k == 0
            // Interest accrues against the balance: owed on a loan (PV > 0), earned on
            // savings (PV < 0). Payments then reduce or grow the balance.
            val periodInterest = if (firstBeginPeriod) ZERO else roundTo(balance.multiply(r, WORK).negate())
            val periodPrincipal = pmt.subtract(periodInterest, WORK)
            interest = interest.add(periodInterest, WORK)
            principal = principal.add(periodPrincipal, WORK)
            // Overflow now: a runaway balance would otherwise grow without bound
            // (and rounding it to the display would expand millions of digits).
            balance = BigMath.checkRange(balance.add(periodPrincipal, WORK))
        }
        return Amort(interest, principal, balance)
    }

    // --- Simple interest -----------------------------------------------------------

    data class SimpleInterest(val on360: BigDecimal, val on365: BigDecimal, val principal: BigDecimal)

    fun simpleInterest(days: BigDecimal, iPercent: BigDecimal, pv: BigDecimal): SimpleInterest {
        val base = pv.negate().multiply(iPercent, WORK).divide(HUNDRED, WORK).multiply(days, WORK)
        return SimpleInterest(
            on360 = base.divide(BigDecimal.valueOf(360), WORK),
            on365 = base.divide(BigDecimal.valueOf(365), WORK),
            principal = pv.negate(),
        )
    }

    // --- Depreciation -----------------------------------------------------------------

    enum class Depreciation { SL, SOYD, DB }

    /** Returns (depreciation for [year], remaining depreciable value). */
    fun depreciate(
        method: Depreciation,
        year: BigDecimal,
        cost: BigDecimal,
        salvage: BigDecimal,
        life: BigDecimal,
        factorPercent: BigDecimal,
    ): Pair<BigDecimal, BigDecimal> {
        if (!BigMath.isInteger(year) || year.signum() <= 0 || life.signum() <= 0) throw CalcError(5)
        val depreciable = cost.subtract(salvage, WORK)
        when (method) {
            Depreciation.SL -> {
                if (year > life) return ZERO to ZERO
                val d = depreciable.divide(life, WORK)
                return d to depreciable.subtract(d.multiply(year, WORK), WORK)
            }
            Depreciation.SOYD -> {
                if (year > life) return ZERO to ZERO
                // Year j takes (life − j + 1) / SYD; the first j years take
                // (j·life − j(j−1)/2) / SYD. Closed form, so a long life can't stall.
                val sum = life.multiply(life.add(ONE), WORK).divide(BigMath.TWO, WORK)
                val d = depreciable.multiply(life.subtract(year, WORK).add(ONE), WORK).divide(sum, WORK)
                val taken = year.multiply(life, WORK)
                    .subtract(year.multiply(year.subtract(ONE), WORK).divide(BigMath.TWO, WORK), WORK)
                return d to depreciable.subtract(depreciable.multiply(taken, WORK).divide(sum, WORK), WORK)
            }
            Depreciation.DB -> {
                if (year > MAX_DB_YEAR) throw CalcError(5)
                val rate = factorPercent.divide(HUNDRED, WORK).divide(life, WORK)
                var book = cost
                var d = ZERO
                for (k in 1..year.toInt()) {
                    d = book.multiply(rate, WORK)
                    val floor = book.subtract(salvage, WORK)
                    if (d > floor) d = floor.max(ZERO)
                    book = book.subtract(d, WORK)
                }
                return d to book.subtract(salvage, WORK)
            }
        }
    }

    /** DB walks year by year; like AMORT, cap the walk so a typo can't stall the engine. */
    private val MAX_DB_YEAR = BigDecimal.valueOf(100_000)

    // --- Bonds (semiannual coupons, actual/actual) -------------------------------------

    private data class CouponWindow(val previous: LocalDate, val next: LocalDate, val remaining: Int)

    /** The coupon period containing [settlement], and how many coupons remain. */
    private fun couponWindow(settlement: LocalDate, maturity: LocalDate): CouponWindow {
        if (!settlement.isBefore(maturity)) throw CalcError(8)
        if (settlement.plusYears(500).isBefore(maturity)) throw CalcError(8)
        // The 12c rejects maturities with no coupon date six months earlier (e.g. the 31st).
        if (maturity.minusMonths(6).dayOfMonth != maturity.dayOfMonth) throw CalcError(8)
        var k = 1L
        while (maturity.minusMonths(6 * k).isAfter(settlement)) k++
        return CouponWindow(maturity.minusMonths(6 * k), maturity.minusMonths(6 * (k - 1)), k.toInt())
    }

    data class BondPrice(val price: BigDecimal, val accrued: BigDecimal)

    fun bondPrice(yieldPercent: BigDecimal, couponPercent: BigDecimal, settlement: LocalDate, maturity: LocalDate): BondPrice {
        val w = couponWindow(settlement, maturity)
        val e = BigDecimal.valueOf(ChronoUnit.DAYS.between(w.previous, w.next))
        val a = BigDecimal.valueOf(ChronoUnit.DAYS.between(w.previous, settlement))
        val dsc = e.subtract(a)
        val halfCoupon = couponPercent.divide(BigMath.TWO, WORK)
        val halfYield = yieldPercent.divide(BigDecimal.valueOf(200), WORK)
        val accrued = halfCoupon.multiply(a, WORK).divide(e, WORK)
        val redemption = HUNDRED
        val price = if (w.remaining == 1) {
            redemption.add(halfCoupon, WORK)
                .divide(ONE.add(dsc.divide(e, WORK).multiply(halfYield, WORK), WORK), WORK)
                .subtract(accrued, WORK)
        } else {
            val base = ONE.add(halfYield, WORK)
            val fracFactor = BigMath.pow(base, dsc.divide(e, WORK))
            var factor = fracFactor // base^(k−1+DSC/E)
            var p = ZERO
            for (k in 1..w.remaining) {
                p = p.add(halfCoupon.divide(factor, WORK), WORK)
                if (k == w.remaining) p = p.add(redemption.divide(factor, WORK), WORK)
                factor = factor.multiply(base, WORK)
            }
            p.subtract(accrued, WORK)
        }
        return BondPrice(price, accrued)
    }

    fun bondYield(price: BigDecimal, couponPercent: BigDecimal, settlement: LocalDate, maturity: LocalDate): BigDecimal {
        if (price.signum() <= 0) throw CalcError(5)
        val f: (BigDecimal) -> BigDecimal = { r ->
            bondPrice(r.multiply(HUNDRED, WORK), couponPercent, settlement, maturity).price.subtract(price, WORK)
        }
        val w = couponWindow(settlement, maturity)
        val e = ChronoUnit.DAYS.between(w.previous, w.next).toDouble()
        val a = ChronoUnit.DAYS.between(w.previous, settlement).toDouble()
        val c = couponPercent.toDouble() / 2
        val target = price.toDouble()
        val r = findRoot(f, fdouble = { r ->
            val h = r / 2
            val accrued = c * a / e
            val frac = (e - a) / e
            val p = if (w.remaining == 1) {
                (100 + c) / (1 + frac * h) - accrued
            } else {
                var sum = 100 / (1 + h).pow(w.remaining - 1 + frac)
                for (k in 1..w.remaining) sum += c / (1 + h).pow(k - 1 + frac)
                sum - accrued
            }
            p - target
        }) ?: throw CalcError(5)
        return r.multiply(HUNDRED, WORK)
    }
}
