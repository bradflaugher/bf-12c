package com.bradflaugher.bf12c.engine

/** Groups program-mode keystrokes into merged lines, the way the 12c records them. */
object ProgramParser {
    enum class Action { EXIT, CLEAR, SST, BST, GOTO, DELETE, NONE }

    sealed interface Outcome {
        data object Incomplete : Outcome
        data object Invalid : Outcome
        data object Complete : Outcome
        data class Immediate(val action: Action, val line: Int = 0) : Outcome
    }

    private val FIN = setOf(Key.N, Key.I, Key.PV, Key.PMT, Key.FV)
    private val OPS = setOf(Key.ADD, Key.SUB, Key.MUL, Key.DIV)

    fun parse(keys: List<Key>): Outcome {
        val first = keys.first()
        val rest = keys.drop(1)
        return when (first) {
            Key.F -> when (rest.firstOrNull()) {
                null -> Outcome.Incomplete
                Key.RS -> Outcome.Immediate(Action.EXIT)
                Key.RDN -> Outcome.Immediate(Action.CLEAR)
                Key.ENTER -> Outcome.Immediate(Action.NONE)
                Key.F, Key.G -> parse(rest)
                else -> Outcome.Complete
            }
            Key.G -> when (rest.firstOrNull()) {
                null -> Outcome.Incomplete
                Key.SST -> Outcome.Immediate(Action.BST)
                Key.SUB -> Outcome.Immediate(Action.DELETE)
                Key.RDN -> gto(rest.drop(1))
                Key.F, Key.G -> parse(rest)
                else -> Outcome.Complete
            }
            Key.STO -> register(rest, allowOps = true)
            Key.RCL -> register(rest, allowOps = false)
            Key.SST -> Outcome.Immediate(Action.SST)
            Key.ON -> Outcome.Immediate(Action.NONE)
            else -> Outcome.Complete
        }
    }

    private fun gto(args: List<Key>): Outcome {
        val immediate = args.firstOrNull() == Key.DOT
        val digits = (if (immediate) args.drop(1) else args)
        if (digits.any { it.digit == null }) return Outcome.Invalid
        if (digits.size < 2) return Outcome.Incomplete
        val line = digits[0].digit!! * 10 + digits[1].digit!!
        return if (immediate) Outcome.Immediate(Action.GOTO, line) else Outcome.Complete
    }

    private fun register(args: List<Key>, allowOps: Boolean): Outcome {
        var rest = args
        val head = rest.firstOrNull() ?: return Outcome.Incomplete
        if (head in FIN) return if (rest.size == 1) Outcome.Complete else Outcome.Invalid
        // STO EEX toggles the C annunciator.
        if (allowOps && head == Key.EEX) return if (rest.size == 1) Outcome.Complete else Outcome.Invalid
        // RCL g CFj / RCL g Nj review cash flows.
        if (!allowOps && head == Key.G) {
            val k = rest.getOrNull(1) ?: return Outcome.Incomplete
            return if (rest.size == 2 && (k == Key.PMT || k == Key.FV)) Outcome.Complete else Outcome.Invalid
        }
        if (allowOps && head in OPS) rest = rest.drop(1)
        if (rest.firstOrNull() == Key.DOT) rest = rest.drop(1)
        val d = rest.firstOrNull() ?: return Outcome.Incomplete
        return if (d.digit != null) Outcome.Complete else Outcome.Invalid
    }

    /** "44 40  1" style keycodes, with GTO targets merged like the 12c's "43,33 05". */
    fun codes(line: List<Key>): String {
        val stripped = stripPrefixes(line)
        if (stripped.size >= 2 && stripped[0] == Key.G && stripped[1] == Key.RDN) {
            val target = stripped.drop(2).joinToString("") { it.digit.toString() }
            return "43,33 $target"
        }
        return stripped.joinToString(" ") { "%2d".format(it.code) }
    }

    /** A readable name for a line: "STO + 1", "g x̄", "FIX 4". */
    fun mnemonic(line: List<Key>): String {
        val keys = stripPrefixes(line)
        val first = keys.first()
        val second = keys.getOrNull(1)
        return when {
            first == Key.F && second == null -> "f"
            first == Key.G && second == null -> "g"
            first == Key.F && second!!.digit != null -> "FIX ${second.digit}"
            first == Key.F -> second!!.f ?: second.label
            first == Key.G && second == Key.RDN -> "GTO " + keys.drop(2).joinToString("") { it.label }
            first == Key.G -> second!!.g ?: second.label
            first == Key.RCL && second == Key.G -> "RCL " + (keys.getOrNull(2)?.g ?: "g")
            else -> keys.joinToString(" ") { it.label }
        }
    }

    private fun stripPrefixes(line: List<Key>): List<Key> {
        var keys = line
        while (keys.size >= 2 && keys[0] in setOf(Key.F, Key.G) && keys[1] in setOf(Key.F, Key.G)) keys = keys.drop(1)
        return keys
    }
}
