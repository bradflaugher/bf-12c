package com.bradflaugher.bf12c.engine

import com.bradflaugher.bf12c.engine.ProgramParser.Action
import com.bradflaugher.bf12c.engine.ProgramParser.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgramParserTest {
    private fun parse(vararg keys: Key) = ProgramParser.parse(keys.toList())

    @Test fun singleKeysAreCompleteLines() {
        for (k in listOf(Key.D7, Key.ADD, Key.ENTER, Key.N, Key.CHS, Key.RS, Key.SIGMA_PLUS)) {
            assertEquals(k.name, Outcome.Complete, parse(k))
        }
    }

    @Test fun prefixesWaitForTheirKey() {
        assertEquals(Outcome.Incomplete, parse(Key.F))
        assertEquals(Outcome.Incomplete, parse(Key.G))
        assertEquals(Outcome.Incomplete, parse(Key.STO))
        assertEquals(Outcome.Incomplete, parse(Key.STO, Key.ADD))
        assertEquals(Outcome.Incomplete, parse(Key.STO, Key.ADD, Key.DOT))
        assertEquals(Outcome.Incomplete, parse(Key.RCL, Key.G))
        assertEquals(Outcome.Incomplete, parse(Key.G, Key.RDN, Key.D0))
        assertEquals(Outcome.Complete, parse(Key.STO, Key.ADD, Key.DOT, Key.D5))
        assertEquals(Outcome.Complete, parse(Key.G, Key.RDN, Key.D0, Key.D5))
        assertEquals(Outcome.Complete, parse(Key.F, Key.G, Key.D3)) // f g n! is g n!
    }

    @Test fun invalidCombinationsAreDropped() {
        assertEquals(Outcome.Invalid, parse(Key.STO, Key.CHS))
        assertEquals(Outcome.Invalid, parse(Key.RCL, Key.ADD))
        assertEquals(Outcome.Invalid, parse(Key.RCL, Key.G, Key.N))
        assertEquals(Outcome.Invalid, parse(Key.G, Key.RDN, Key.ADD))
    }

    @Test fun editingKeysActImmediately() {
        assertEquals(Outcome.Immediate(Action.EXIT), parse(Key.F, Key.RS))
        assertEquals(Outcome.Immediate(Action.CLEAR), parse(Key.F, Key.RDN))
        assertEquals(Outcome.Immediate(Action.SST), parse(Key.SST))
        assertEquals(Outcome.Immediate(Action.BST), parse(Key.G, Key.SST))
        assertEquals(Outcome.Immediate(Action.DELETE), parse(Key.G, Key.SUB))
        assertEquals(Outcome.Immediate(Action.GOTO, 12), parse(Key.G, Key.RDN, Key.DOT, Key.D1, Key.D2))
    }

    @Test fun keycodesMatchThe12c() {
        assertEquals("44 40  1", ProgramParser.codes(listOf(Key.STO, Key.ADD, Key.D1)))
        assertEquals("43,33 05", ProgramParser.codes(listOf(Key.G, Key.RDN, Key.D0, Key.D5)))
        assertEquals("42 11", ProgramParser.codes(listOf(Key.F, Key.N)))
        assertEquals("43 11", ProgramParser.codes(listOf(Key.F, Key.G, Key.N)))
        assertEquals("36", ProgramParser.codes(listOf(Key.ENTER)))
    }

    @Test fun mnemonicsAreReadable() {
        assertEquals("STO + 1", ProgramParser.mnemonic(listOf(Key.STO, Key.ADD, Key.D1)))
        assertEquals("GTO 05", ProgramParser.mnemonic(listOf(Key.G, Key.RDN, Key.D0, Key.D5)))
        assertEquals("FIX 4", ProgramParser.mnemonic(listOf(Key.F, Key.D4)))
        assertEquals("AMORT", ProgramParser.mnemonic(listOf(Key.F, Key.N)))
        assertEquals("x≤y", ProgramParser.mnemonic(listOf(Key.G, Key.SWAP)))
        assertEquals("STO EEX", ProgramParser.mnemonic(listOf(Key.STO, Key.EEX)))
    }

    @Test fun programEditingInTheCalculator() {
        val c = run("f RS 1 2 3 g RDN . 0 2 4 f RS")
        // 1 2 3, then GTO .02 moves to line 2 and 4 is inserted after it.
        assertEquals(listOf(Key.D1, Key.D2, Key.D4, Key.D3), c.program.map { it.single() })
        c.keys("f RS g RDN . 0 3 g -")
        assertEquals(listOf(Key.D1, Key.D2, Key.D3), c.program.map { it.single() })
        assertEquals(2, c.pc)
        c.keys("g RDN . 9 9")
        assertEquals(4, c.error)
    }

    @Test fun memoryHoldsNinetyNineLines() {
        val c = run("f RS")
        repeat(Calculator.MAX_LINES) { c.keys("1") }
        assertEquals(Calculator.MAX_LINES, c.program.size)
        c.keys("2")
        assertEquals(4, c.error)
        assertEquals(Calculator.MAX_LINES, c.program.size)
    }
}
