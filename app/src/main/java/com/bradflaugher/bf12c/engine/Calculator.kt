package com.bradflaugher.bf12c.engine

import com.bradflaugher.bf12c.engine.BigMath.HUNDRED
import com.bradflaugher.bf12c.engine.BigMath.WORK
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An HP-12C-style RPN engine. Feed it [Key]s with [press]; read [display].
 * Pure Kotlin with no Android dependencies so it can be unit tested on the JVM.
 */
class Calculator {
    // --- Continuous memory -------------------------------------------------------------

    /** X, Y, Z, T. */
    val stack = Array<BigDecimal>(4) { BigDecimal.ZERO }
    var lastX: BigDecimal = BigDecimal.ZERO
        private set

    /** R0–R9 then R.0–R.9. R0–R19 double as CF0–CF19; R1–R6 hold the Σ sums. */
    val regs = Array<BigDecimal>(REGISTERS) { BigDecimal.ZERO }
    val nj = IntArray(REGISTERS) { 1 }

    /** n, i, PV, PMT, FV. */
    val fin = Array<BigDecimal>(5) { BigDecimal.ZERO }

    var begin = false
        private set
    var dmy = false
        private set

    /** The C annunciator: compound (not simple) interest for odd TVM periods. */
    var compoundOdd = false
        private set
    var mode: DisplayMode = DisplayMode.All
        private set

    /** Program memory: each line is one merged keystroke sequence, e.g. [STO, ADD, D1]. */
    val program = mutableListOf<List<Key>>()

    /** Current program line (0 = line 000). */
    var pc = 0
        private set
    var programMode = false
        private set

    // --- Transient state -----------------------------------------------------------------

    private var liftEnabled = true
    private var entry: Entry? = null
    private var prefix: Prefix = Prefix.None
    private var lastWasFin = false
    private val stepBuffer = mutableListOf<Key>()

    var error: Int? = null
        private set
    private var message: String? = null
    private var weekday: Int? = null

    var running = false
        private set

    /** Set when a PSE step executes; the host shows X for a second then resumes. */
    var pauseRequested = false

    var x: BigDecimal
        get() = stack[0]
        private set(v) {
            stack[0] = BigMath.checkRange(v.round(BigMath.MC))
        }
    private var y: BigDecimal
        get() = stack[1]
        set(v) {
            stack[1] = v
        }

    // --- Public API ------------------------------------------------------------------

    fun press(key: Key) {
        if (running) {
            // Any key halts a running program, as on the real thing.
            running = false
            return
        }
        if (error != null) {
            error = null
            return
        }
        message = null
        weekday = null
        if (programMode) programKey(key) else guarded { execute(key) }
    }

    /** Starts program execution from the line after [pc]. */
    fun run() {
        if (programMode || error != null) return
        running = program.isNotEmpty()
        message = null
    }

    /** Executes one program line while running. Returns false once the program halts. */
    fun step(): Boolean {
        if (!running) return false
        if (pc >= program.size) {
            pc = 0
            running = false
            return false
        }
        pc++
        val line = program[pc - 1]
        guarded { for (k in line) execute(k) }
        if (error != null) running = false
        return running
    }

    fun display(): Display {
        val annunciators = Annunciators(
            f = prefix == Prefix.F,
            g = prefix == Prefix.G,
            begin = begin,
            dmy = dmy,
            compound = compoundOdd,
            prgm = programMode,
            running = running,
            pending = prefixLabel(),
        )
        val stackText = stack.map { Format.format(it, mode) }
        val main = when {
            error != null -> "Error ${error}"
            running && !pauseRequested -> "running"
            programMode -> programLine(pc)
            message != null -> message!!
            entry != null -> entry!!.text()
            else -> stackText[0]
        }
        return Display(
            main = main,
            stack = stackText,
            full = Format.plain(x),
            lastX = Format.format(lastX, mode),
            annunciators = annunciators,
            weekday = weekday,
            isError = error != null,
            programListing = if (programMode) programListing() else emptyList(),
            entering = entry != null && error == null && !programMode && !running && message == null,
        )
    }

    /** The g ← key, also bound to swiping the display. In program mode it deletes the line. */
    fun backspace() {
        if (running) {
            running = false
            return
        }
        if (error != null) {
            error = null
            return
        }
        message = null
        weekday = null
        prefix = Prefix.None
        if (programMode) {
            stepBuffer.clear()
            deleteLine()
        } else {
            guarded { erase() }
        }
    }

    private fun deleteLine() {
        if (pc == 0) return
        program.removeAt(pc - 1)
        pc--
    }

    /** Pastes a number into X as if it were keyed in. */
    fun paste(value: BigDecimal) {
        if (programMode) return
        finishEntry()
        pushResult(value)
    }

    // --- Dispatch --------------------------------------------------------------------

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: CalcError) {
            fail(e.code)
        } catch (_: ArithmeticException) {
            fail(0)
        }
    }

    private fun fail(code: Int) {
        error = code
        entry = null
        prefix = Prefix.None
        running = false
    }

    private fun execute(key: Key) {
        when (val p = prefix) {
            Prefix.None -> primary(key)
            Prefix.F -> { prefix = Prefix.None; gold(key) }
            Prefix.G -> { prefix = Prefix.None; blue(key) }
            is Prefix.Sto -> storage(key, p)
            is Prefix.Rcl -> recall(key, p)
            is Prefix.Gto -> gotoLine(key, p)
        }
    }

    private fun primary(key: Key) {
        val d = key.digit
        if (d != null) return digit(d)
        val wasFin = lastWasFin
        lastWasFin = false
        when (key) {
            Key.DOT -> entryOrStart { it.dot() }
            Key.EEX -> entryOrStart { it.eex() }
            Key.CHS -> {
                val e = entry
                if (e != null) {
                    e.chs()
                    x = e.value()
                } else {
                    x = x.negate()
                }
            }
            Key.ENTER -> {
                finishEntry()
                lift()
                liftEnabled = false
            }
            Key.CLX -> {
                entry = null
                x = BigDecimal.ZERO
                liftEnabled = false
            }
            Key.ADD -> binary { a, b -> a.add(b, WORK) }
            Key.SUB -> binary { a, b -> a.subtract(b, WORK) }
            Key.MUL -> binary { a, b -> a.multiply(b, WORK) }
            Key.DIV -> binary { a, b -> BigMath.divide(a, b) }
            Key.YX -> binary { a, b -> BigMath.pow(a, b) }
            Key.RECIP -> unary { BigMath.divide(BigDecimal.ONE, it) }
            Key.PCT -> percent { base, v -> base.multiply(v, WORK).divide(HUNDRED, WORK) }
            Key.DELTA_PCT -> percent { base, v -> BigMath.divide(v.subtract(base, WORK).multiply(HUNDRED, WORK), base) }
            Key.PCT_T -> percent { base, v -> BigMath.divide(v.multiply(HUNDRED, WORK), base) }
            Key.RDN -> {
                finishEntry()
                val top = stack[0]
                stack[0] = stack[1]; stack[1] = stack[2]; stack[2] = stack[3]; stack[3] = top
                liftEnabled = true
            }
            Key.SWAP -> {
                finishEntry()
                val t = stack[0]
                stack[0] = stack[1]; stack[1] = t
                liftEnabled = true
            }
            Key.STO -> { finishEntry(); prefix = Prefix.Sto() }
            Key.RCL -> { finishEntry(); prefix = Prefix.Rcl() }
            Key.F -> prefix = Prefix.F
            Key.G -> prefix = Prefix.G
            Key.N, Key.I, Key.PV, Key.PMT, Key.FV -> finKey(key, compute = wasFin && entry == null)
            Key.SIGMA_PLUS -> sigma(1)
            Key.RS -> {
                finishEntry()
                // Inside a program R/S halts; from the keyboard it starts.
                if (running) running = false else run()
            }
            Key.SST -> {
                finishEntry()
                // Single-step: execute the next program line.
                if (program.isNotEmpty()) {
                    running = true
                    step()
                    running = false
                }
            }
            Key.ON -> Unit // Handled by the host (menu).
            else -> Unit
        }
    }

    private fun gold(key: Key) {
        val d = key.digit
        if (d != null) {
            finishEntry()
            mode = DisplayMode.Fix(d)
            return
        }
        lastWasFin = false
        when (key) {
            Key.DOT -> { finishEntry(); mode = DisplayMode.Sci((mode as? DisplayMode.Fix)?.digits?.coerceAtLeast(1) ?: 9) }
            Key.EEX -> { finishEntry(); mode = DisplayMode.All }
            Key.N -> amortize()
            Key.I -> simpleInterest()
            Key.PV -> npv()
            Key.PMT -> unary { roundToDisplay(it) }
            Key.FV -> irr()
            Key.YX -> bondPrice()
            Key.RECIP -> bondYield()
            Key.PCT_T -> depreciation(Finance.Depreciation.SL)
            Key.DELTA_PCT -> depreciation(Finance.Depreciation.SOYD)
            Key.PCT -> depreciation(Finance.Depreciation.DB)
            Key.RS -> {
                finishEntry()
                programMode = true
            }
            Key.SST -> { // CLEAR Σ
                entry = null
                for (r in 1..6) regs[r] = BigDecimal.ZERO
                stack.fill(BigDecimal.ZERO)
                liftEnabled = true
            }
            Key.RDN -> { finishEntry(); pc = 0 } // CLEAR PRGM outside program mode rewinds
            Key.SWAP -> { finishEntry(); fin.fill(BigDecimal.ZERO) } // CLEAR FIN
            Key.CLX -> clearRegisters()
            Key.ENTER -> Unit // CLEAR PREFIX
            Key.F -> prefix = Prefix.F
            Key.G -> prefix = Prefix.G
            else -> primary(key)
        }
    }

    private fun blue(key: Key) {
        lastWasFin = false
        when (key) {
            Key.N -> { finishEntry(); x = x.multiply(TWELVE, WORK); fin[N] = x; liftEnabled = false; lastWasFin = true }
            Key.I -> { finishEntry(); x = x.divide(TWELVE, WORK); fin[I] = x; liftEnabled = false; lastWasFin = true }
            Key.PV -> cashFlow(initial = true)
            Key.PMT -> cashFlow(initial = false)
            Key.FV -> {
                finishEntry()
                val count = x
                if (!BigMath.isInteger(count) || count.signum() <= 0 || count > BigDecimal.valueOf(99)) throw CalcError(6)
                nj[currentFlow()] = count.toInt()
                liftEnabled = true
            }
            Key.CHS -> dateAdd()
            Key.D7 -> { finishEntry(); begin = true }
            Key.D8 -> { finishEntry(); begin = false }
            Key.D9 -> { finishEntry(); message = "P-%02d r-%02d".format(program.size, REGISTERS) }
            Key.YX -> unary { BigMath.sqrt(it) }
            Key.RECIP -> unary { BigMath.exp(it) }
            Key.PCT_T -> unary { BigMath.ln(it) }
            Key.DELTA_PCT -> unary { BigMath.fracPart(it) }
            Key.PCT -> unary { BigMath.intPart(it) }
            Key.EEX -> deltaDays()
            Key.D4 -> { finishEntry(); dmy = true }
            Key.D5 -> { finishEntry(); dmy = false }
            Key.D6 -> weightedMean()
            Key.MUL -> unary { it.multiply(it, WORK) }
            Key.RS -> { finishEntry(); pauseRequested = running }
            Key.SST -> Unit // BST only means something in program mode
            Key.RDN -> { finishEntry(); prefix = Prefix.Gto() }
            Key.SWAP -> { finishEntry(); if (running && stack[0] > stack[1]) skipLine() }
            Key.CLX -> { finishEntry(); if (running && stack[0].signum() != 0) skipLine() }
            Key.ENTER -> {
                finishEntry()
                pushResult(lastX)
            }
            Key.D1 -> estimate(xFromY = true)
            Key.D2 -> estimate(xFromY = false)
            Key.D3 -> unary { BigMath.factorial(it) }
            Key.SUB -> erase()
            Key.D0 -> mean()
            Key.DOT -> stdDev()
            Key.SIGMA_PLUS -> sigma(-1)
            Key.F -> prefix = Prefix.F
            Key.G -> prefix = Prefix.G
            else -> primary(key)
        }
    }

    // --- Entry -----------------------------------------------------------------------

    private fun digit(d: Int) {
        lastWasFin = false
        entryOrStart { it.digit(d) }
    }

    private inline fun entryOrStart(edit: (Entry) -> Unit) {
        var e = entry
        if (e == null) {
            if (liftEnabled) lift()
            e = Entry()
            entry = e
        }
        edit(e)
        x = e.value()
        liftEnabled = true
    }

    private fun finishEntry() {
        entry = null
    }

    private fun erase() {
        val e = entry
        if (e == null) {
            x = BigDecimal.ZERO
            liftEnabled = false
            return
        }
        if (e.backspace()) {
            x = e.value()
        } else {
            entry = null
            x = BigDecimal.ZERO
            liftEnabled = false
        }
    }

    // --- Stack helpers ---------------------------------------------------------------

    private fun lift() {
        stack[3] = stack[2]; stack[2] = stack[1]; stack[1] = stack[0]
    }

    private fun drop() {
        stack[1] = stack[2]; stack[2] = stack[3]
    }

    private fun pushResult(v: BigDecimal) {
        if (liftEnabled) lift()
        x = v
        liftEnabled = true
    }

    private inline fun unary(op: (BigDecimal) -> BigDecimal) {
        finishEntry()
        val r = op(x)
        lastX = x
        x = r
        liftEnabled = true
    }

    private inline fun binary(op: (BigDecimal, BigDecimal) -> BigDecimal) {
        finishEntry()
        val r = op(y, x)
        lastX = x
        drop()
        x = r
        liftEnabled = true
    }

    /** Percent keys keep Y (the base) in place. */
    private inline fun percent(op: (BigDecimal, BigDecimal) -> BigDecimal) {
        finishEntry()
        val r = op(y, x)
        lastX = x
        x = r
        liftEnabled = true
    }

    // --- Storage ---------------------------------------------------------------------

    private fun registerIndex(d: Int, dot: Boolean) = if (dot) 10 + d else d

    private fun storage(key: Key, p: Prefix.Sto) {
        val d = key.digit
        when {
            d != null -> {
                prefix = Prefix.None
                val idx = registerIndex(d, p.dot)
                regs[idx] = when (p.op) {
                    null -> x
                    Key.ADD -> regs[idx].add(x, WORK)
                    Key.SUB -> regs[idx].subtract(x, WORK)
                    Key.MUL -> regs[idx].multiply(x, WORK)
                    Key.DIV -> BigMath.divide(regs[idx], x)
                    else -> x
                }.round(BigMath.MC).let(BigMath::checkRange)
                liftEnabled = true
            }
            key == Key.DOT && !p.dot -> prefix = p.copy(dot = true)
            key == Key.EEX && p.op == null && !p.dot -> {
                prefix = Prefix.None
                compoundOdd = !compoundOdd
            }
            key in STO_OPS && p.op == null && !p.dot -> prefix = p.copy(op = key)
            key in FIN_KEYS && p.op == null && !p.dot -> {
                prefix = Prefix.None
                fin[FIN_KEYS.indexOf(key)] = x
                liftEnabled = true
            }
            else -> { prefix = Prefix.None; execute(key) }
        }
    }

    private fun recall(key: Key, p: Prefix.Rcl) {
        val d = key.digit
        when {
            d != null -> { prefix = Prefix.None; pushResult(regs[registerIndex(d, p.dot)]) }
            key == Key.DOT && !p.dot -> prefix = p.copy(dot = true)
            key == Key.G && !p.dot && !p.g -> prefix = p.copy(g = true)
            p.g && key == Key.PMT -> {
                // Review cash flows backwards: show CFj, then step n down.
                prefix = Prefix.None
                val j = currentFlow()
                pushResult(regs[j])
                if (j > 0) fin[N] = BigDecimal.valueOf((j - 1).toLong())
            }
            p.g && key == Key.FV -> { prefix = Prefix.None; pushResult(BigDecimal.valueOf(nj[currentFlow()].toLong())) }
            p.g -> { prefix = Prefix.None; execute(key) }
            key in FIN_KEYS && !p.dot -> { prefix = Prefix.None; pushResult(fin[FIN_KEYS.indexOf(key)]) }
            else -> { prefix = Prefix.None; execute(key) }
        }
    }

    private fun gotoLine(key: Key, p: Prefix.Gto) {
        val d = key.digit
        if (d == null) {
            prefix = Prefix.None
            if (key != Key.DOT) execute(key)
            return
        }
        val digits = p.digits + d
        if (digits.length < 2) {
            prefix = p.copy(digits = digits)
            return
        }
        prefix = Prefix.None
        val target = digits.toInt()
        if (target > program.size) throw CalcError(4)
        if (running) {
            if (target == 0) {
                pc = 0
                running = false
            } else {
                pc = target - 1 // step() advances before executing
            }
        } else {
            // R/S executes the line after pc, so park one line early.
            pc = (target - 1).coerceAtLeast(0)
        }
    }

    private fun skipLine() {
        pc = (pc + 1).coerceAtMost(program.size)
    }

    private fun clearRegisters() {
        entry = null
        stack.fill(BigDecimal.ZERO)
        lastX = BigDecimal.ZERO
        regs.fill(BigDecimal.ZERO)
        nj.fill(1)
        fin.fill(BigDecimal.ZERO)
        liftEnabled = true
    }

    // --- Financial -------------------------------------------------------------------

    private fun tvm() = Finance.Tvm(fin[N], fin[I], fin[PV], fin[PMT], fin[FV], begin, compoundOdd)

    private fun finKey(key: Key, compute: Boolean) {
        finishEntry()
        val idx = FIN_KEYS.indexOf(key)
        if (compute) {
            val result = when (key) {
                Key.N -> Finance.solveN(tvm())
                Key.I -> Finance.solveI(tvm())
                Key.PV -> Finance.solvePv(tvm())
                Key.PMT -> Finance.solvePmt(tvm())
                else -> Finance.solveFv(tvm())
            }.round(BigMath.MC)
            fin[idx] = result
            x = result
            liftEnabled = true
        } else {
            fin[idx] = x
            liftEnabled = false
        }
        lastWasFin = true
    }

    private fun currentFlow(): Int {
        val n = fin[N]
        if (!BigMath.isInteger(n) || n.signum() < 0 || n >= BigDecimal.valueOf(REGISTERS.toLong())) throw CalcError(6)
        return n.toInt()
    }

    private fun cashFlow(initial: Boolean) {
        finishEntry()
        val j = if (initial) 0 else currentFlow() + 1
        if (j >= REGISTERS) throw CalcError(6)
        regs[j] = x
        nj[j] = 1
        fin[N] = BigDecimal.valueOf(j.toLong())
        liftEnabled = true
    }

    private fun flows(): Pair<List<BigDecimal>, List<Int>> {
        val last = currentFlow()
        return regs.take(last + 1) to nj.take(last + 1)
    }

    private fun npv() {
        finishEntry()
        val (cf, counts) = flows()
        val v = Finance.npv(fin[I], cf, counts).round(BigMath.MC)
        fin[PV] = v
        x = v
        liftEnabled = true
    }

    private fun irr() {
        finishEntry()
        val (cf, counts) = flows()
        val v = Finance.irr(cf, counts).round(BigMath.MC)
        fin[I] = v
        x = v
        liftEnabled = true
    }

    private fun roundToDisplay(v: BigDecimal): BigDecimal = when (val m = mode) {
        is DisplayMode.Fix -> v.setScale(m.digits, RoundingMode.HALF_UP)
        is DisplayMode.Sci -> v.round(java.math.MathContext(m.digits + 1, RoundingMode.HALF_UP))
        DisplayMode.All -> v
    }

    private fun amortize() {
        finishEntry()
        val count = x
        if (!BigMath.isInteger(count) || count.signum() <= 0 || count > BigDecimal.valueOf(100_000)) throw CalcError(5)
        val done = fin[N]
        val periodsDone = if (BigMath.isInteger(done) && done.signum() >= 0) done.toInt() else 0
        val a = Finance.amortize(count.toInt(), periodsDone, fin[I], fin[PV], fin[PMT], begin, ::roundToDisplay)
        fin[PV] = a.balance.round(BigMath.MC)
        fin[N] = done.add(count)
        // T = old Y, Z = payments, Y = principal, X = interest.
        stack[3] = stack[1]
        stack[2] = count
        stack[1] = a.principal.round(BigMath.MC)
        x = a.interest
        liftEnabled = true
    }

    private fun simpleInterest() {
        finishEntry()
        val s = Finance.simpleInterest(fin[N], fin[I], fin[PV])
        stack[3] = x
        stack[2] = s.on365.round(BigMath.MC)
        stack[1] = s.principal.round(BigMath.MC)
        x = s.on360
        liftEnabled = true
    }

    private fun depreciation(method: Finance.Depreciation) {
        finishEntry()
        val (d, remaining) = Finance.depreciate(method, x, fin[PV], fin[FV], fin[N], fin[I])
        // T = old Y, Z = year, Y = remaining depreciable value, X = depreciation.
        stack[3] = stack[1]
        stack[2] = x
        stack[1] = remaining.round(BigMath.MC)
        x = d
        liftEnabled = true
    }

    private fun bondPrice() {
        finishEntry()
        val settlement = Dates.decode(y, dmy)
        val maturity = Dates.decode(x, dmy)
        val p = Finance.bondPrice(fin[I], fin[PMT], settlement, maturity)
        // T = settlement, Z = maturity, Y = accrued interest, X = price.
        stack[3] = y
        stack[2] = x
        fin[PV] = p.price.round(BigMath.MC)
        y = p.accrued.round(BigMath.MC)
        x = p.price
        liftEnabled = true
    }

    private fun bondYield() {
        finishEntry()
        val settlement = Dates.decode(y, dmy)
        val maturity = Dates.decode(x, dmy)
        val r = Finance.bondYield(fin[PV], fin[PMT], settlement, maturity).round(BigMath.MC)
        fin[I] = r
        lift()
        x = r
        liftEnabled = true
    }

    private fun dateAdd() {
        finishEntry()
        val start = Dates.decode(y, dmy)
        val (date, dow) = Dates.addDays(start, x)
        binary { _, _ -> Dates.encode(date, dmy) }
        weekday = dow
    }

    private fun deltaDays() {
        finishEntry()
        val (actual, basis) = Dates.daysBetween(Dates.decode(y, dmy), Dates.decode(x, dmy))
        lastX = x
        y = BigDecimal.valueOf(basis)
        x = BigDecimal.valueOf(actual)
        liftEnabled = true
    }

    // --- Statistics ------------------------------------------------------------------

    private fun sigma(sign: Int) {
        finishEntry()
        val s = BigDecimal.valueOf(sign.toLong())
        val xv = x
        val yv = y
        regs[1] = regs[1].add(s)
        regs[2] = regs[2].add(s.multiply(xv), WORK).round(BigMath.MC)
        regs[3] = regs[3].add(s.multiply(xv.multiply(xv, WORK)), WORK).round(BigMath.MC)
        regs[4] = regs[4].add(s.multiply(yv), WORK).round(BigMath.MC)
        regs[5] = regs[5].add(s.multiply(yv.multiply(yv, WORK)), WORK).round(BigMath.MC)
        regs[6] = regs[6].add(s.multiply(xv.multiply(yv, WORK)), WORK).round(BigMath.MC)
        lastX = xv
        x = regs[1]
        liftEnabled = false
    }

    private fun count(): BigDecimal {
        val n = regs[1]
        if (n.signum() <= 0) throw CalcError(2)
        return n
    }

    private fun mean() {
        finishEntry()
        val n = count()
        val mx = regs[2].divide(n, WORK)
        val my = regs[4].divide(n, WORK)
        pushResult(my)
        pushResult(mx)
    }

    private fun stdDev() {
        finishEntry()
        val n = count()
        if (n <= BigDecimal.ONE) throw CalcError(2)
        val d = n.multiply(n.subtract(BigDecimal.ONE), WORK)
        val vx = n.multiply(regs[3], WORK).subtract(regs[2].multiply(regs[2], WORK), WORK).divide(d, WORK)
        val vy = n.multiply(regs[5], WORK).subtract(regs[4].multiply(regs[4], WORK), WORK).divide(d, WORK)
        pushResult(BigMath.sqrt(vy.max(BigDecimal.ZERO)))
        pushResult(BigMath.sqrt(vx.max(BigDecimal.ZERO)))
    }

    private fun weightedMean() {
        finishEntry()
        count()
        pushResult(BigMath.divide(regs[6], regs[2]))
    }

    private fun estimate(xFromY: Boolean) {
        finishEntry()
        val n = count()
        val sx = regs[2]; val sx2 = regs[3]; val sy = regs[4]; val sy2 = regs[5]; val sxy = regs[6]
        val sxx = n.multiply(sx2, WORK).subtract(sx.multiply(sx, WORK), WORK)
        val syy = n.multiply(sy2, WORK).subtract(sy.multiply(sy, WORK), WORK)
        val sp = n.multiply(sxy, WORK).subtract(sx.multiply(sy, WORK), WORK)
        if (sxx.signum() == 0) throw CalcError(2)
        val b = sp.divide(sxx, WORK)
        val a = sy.subtract(b.multiply(sx, WORK), WORK).divide(n, WORK)
        val denom = sxx.multiply(syy, WORK)
        if (denom.signum() <= 0) throw CalcError(2)
        val r = sp.divide(BigMath.sqrt(denom), WORK)
        val input = x
        val est = if (xFromY) {
            if (b.signum() == 0) throw CalcError(2)
            BigMath.divide(input.subtract(a, WORK), b)
        } else {
            a.add(b.multiply(input, WORK), WORK)
        }
        lastX = input
        x = r
        lift()
        x = est
        liftEnabled = true
    }

    // --- Program mode ----------------------------------------------------------------

    private fun programKey(key: Key) {
        stepBuffer += key
        val buf = stepBuffer.toList()
        when (val outcome = ProgramParser.parse(buf)) {
            ProgramParser.Outcome.Incomplete -> return
            ProgramParser.Outcome.Invalid -> stepBuffer.clear()
            is ProgramParser.Outcome.Immediate -> {
                stepBuffer.clear()
                when (outcome.action) {
                    ProgramParser.Action.EXIT -> {
                        programMode = false
                        pc = 0
                    }
                    ProgramParser.Action.CLEAR -> { program.clear(); pc = 0 }
                    ProgramParser.Action.SST -> pc = if (pc >= program.size) 0 else pc + 1
                    ProgramParser.Action.BST -> pc = if (pc == 0) program.size else pc - 1
                    ProgramParser.Action.GOTO -> {
                        val target = outcome.line
                        if (target > program.size) fail(4) else pc = target
                    }
                    ProgramParser.Action.DELETE -> deleteLine()
                    ProgramParser.Action.NONE -> Unit
                }
            }
            ProgramParser.Outcome.Complete -> {
                stepBuffer.clear()
                if (program.size >= MAX_LINES) {
                    fail(4)
                    return
                }
                program.add(pc, buf)
                pc++
            }
        }
    }

    /** "007- 44 40  1" — the keycodes of a program line, as the 12c shows them. */
    fun programLine(line: Int): String {
        if (line == 0) return "000-"
        return "%03d- %s".format(line, ProgramParser.codes(program[line - 1]))
    }

    /** Every line with its keycodes and a readable mnemonic. */
    private fun programListing(): List<String> = (0..program.size).map { line ->
        if (line == 0) "000-" else "%-18s %s".format(programLine(line), ProgramParser.mnemonic(program[line - 1]))
    }

    private fun prefixLabel(): String? = when (val p = prefix) {
        Prefix.None, Prefix.F, Prefix.G -> null
        is Prefix.Sto -> "STO" + (p.op?.let { " ${it.label}" } ?: "") + if (p.dot) " ." else ""
        is Prefix.Rcl -> "RCL" + (if (p.g) " g" else "") + if (p.dot) " ." else ""
        is Prefix.Gto -> "GTO ${p.digits}"
    }.let { label ->
        if (programMode && stepBuffer.isNotEmpty()) ProgramParser.mnemonic(stepBuffer) else label
    }

    // --- Persistence -----------------------------------------------------------------

    fun save(): Map<String, String> = buildMap {
        put("stack", stack.joinToString(";") { it.toString() })
        put("lastX", lastX.toString())
        put("regs", regs.joinToString(";") { it.toString() })
        put("nj", nj.joinToString(";"))
        put("fin", fin.joinToString(";") { it.toString() })
        put("begin", begin.toString())
        put("dmy", dmy.toString())
        put("compoundOdd", compoundOdd.toString())
        put("mode", when (val m = mode) {
            is DisplayMode.Fix -> "fix${m.digits}"
            is DisplayMode.Sci -> "sci${m.digits}"
            DisplayMode.All -> "all"
        })
        put("program", program.joinToString(";") { line -> line.joinToString(",") { it.name } })
        put("pc", pc.toString())
    }

    fun restore(state: Map<String, String>) {
        runCatching {
            state["stack"]?.split(";")?.map(::BigDecimal)?.forEachIndexed { k, v -> if (k < 4) stack[k] = v }
            state["lastX"]?.let { lastX = BigDecimal(it) }
            state["regs"]?.split(";")?.map(::BigDecimal)?.forEachIndexed { k, v -> if (k < REGISTERS) regs[k] = v }
            state["nj"]?.split(";")?.map(String::toInt)?.forEachIndexed { k, v -> if (k < REGISTERS) nj[k] = v }
            state["fin"]?.split(";")?.map(::BigDecimal)?.forEachIndexed { k, v -> if (k < 5) fin[k] = v }
            begin = state["begin"] == "true"
            dmy = state["dmy"] == "true"
            compoundOdd = state["compoundOdd"] == "true"
            mode = state["mode"]?.let { m ->
                when {
                    m.startsWith("fix") -> DisplayMode.Fix(m.removePrefix("fix").toInt())
                    m.startsWith("sci") -> DisplayMode.Sci(m.removePrefix("sci").toInt())
                    else -> DisplayMode.All
                }
            } ?: DisplayMode.All
            program.clear()
            state["program"]?.takeIf { it.isNotEmpty() }?.split(";")?.forEach { line ->
                program += line.split(",").map { Key.valueOf(it) }
            }
            pc = state["pc"]?.toInt()?.coerceIn(0, program.size) ?: 0
        }
    }

    private sealed interface Prefix {
        data object None : Prefix
        data object F : Prefix
        data object G : Prefix
        data class Sto(val op: Key? = null, val dot: Boolean = false) : Prefix
        data class Rcl(val dot: Boolean = false, val g: Boolean = false) : Prefix
        data class Gto(val digits: String = "") : Prefix
    }

    companion object {
        const val REGISTERS = 20
        const val MAX_LINES = 99
        private const val N = 0
        private const val I = 1
        private const val PV = 2
        private const val PMT = 3
        private const val FV = 4
        private val TWELVE = BigDecimal.valueOf(12)
        private val FIN_KEYS = listOf(Key.N, Key.I, Key.PV, Key.PMT, Key.FV)
        private val STO_OPS = setOf(Key.ADD, Key.SUB, Key.MUL, Key.DIV)
    }
}

data class Annunciators(
    val f: Boolean,
    val g: Boolean,
    val begin: Boolean,
    val dmy: Boolean,
    val compound: Boolean,
    val prgm: Boolean,
    val running: Boolean,
    val pending: String?,
)

data class Display(
    val main: String,
    /** X, Y, Z, T formatted in the current display mode. */
    val stack: List<String>,
    /** X at full precision. */
    val full: String,
    val lastX: String,
    val annunciators: Annunciators,
    val weekday: Int?,
    val isError: Boolean,
    val programListing: List<String>,
    val entering: Boolean,
)
