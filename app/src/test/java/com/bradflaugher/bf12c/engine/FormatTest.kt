package com.bradflaugher.bf12c.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class FormatTest {
    private fun fmt(x: String, mode: DisplayMode) = Format.format(BigDecimal(x), mode)

    @Test fun fixRoundsHalfUpAndGroups() {
        assertEquals("1,234,567.00", fmt("1234567", DisplayMode.Fix(2)))
        assertEquals("-1,234.57", fmt("-1234.565", DisplayMode.Fix(2)))
        assertEquals("3", fmt("2.5", DisplayMode.Fix(0)))
        assertEquals("0.0000", fmt("0", DisplayMode.Fix(4)))
        assertEquals("999", fmt("999", DisplayMode.Fix(0)))
        assertEquals("1,000", fmt("999.5", DisplayMode.Fix(0)))
    }

    @Test fun fixSwitchesToSciWhenTheValueWouldVanish() {
        // Like the 12c: a nonzero value never displays as zero.
        assertEquals("1.23E-5", fmt("0.0000123", DisplayMode.Fix(2)))
        assertEquals("1.00E+25", fmt("1E25", DisplayMode.Fix(2)))
    }

    @Test fun sci() {
        assertEquals("1.2346E+3", fmt("1234.5678", DisplayMode.Sci(4)))
        assertEquals("-5.000000000E-7", fmt("-0.0000005", DisplayMode.Sci(9)))
        assertEquals("1.0E+1", fmt("9.96", DisplayMode.Sci(1)))
        assertEquals("0.00E+0", fmt("0", DisplayMode.Sci(2)))
    }

    @Test fun allShowsEveryDigit() {
        assertEquals("0.3333333333333333333333333333333333", fmt("0.3333333333333333333333333333333333", DisplayMode.All))
        assertEquals("123,456,789.5", fmt("123456789.50", DisplayMode.All))
        assertEquals("1.5E-20", fmt("1.5E-20", DisplayMode.All))
        assertEquals("-2.0E+9999", fmt("-2E9999", DisplayMode.All))
        assertEquals("0", fmt("0.000", DisplayMode.All))
    }

    @Test fun plainIsUngroupedFullPrecision() {
        assertEquals("1234567.125", Format.plain(BigDecimal("1234567.125")))
        assertEquals("1.0E+30", Format.plain(BigDecimal("1E30")))
        assertEquals("0", Format.plain(BigDecimal("-0.00")))
    }

    @Test fun clipboardAcceptsCommonNotations() {
        val cases = mapOf(
            "1,234.50" to "1234.50",
            "  42\n" to "42",
            "$1,000" to "1000",
            "€ 99.95" to "99.95",
            "(250.00)" to "-250.00",
            "−3.5" to "-3.5",
            "+7" to "7",
            "12%" to "12",
            "6.02e23" to "6.02E+23",
            "1 234 567" to "1234567",
            "1_000_000" to "1000000",
            ".5" to "0.5",
            "-.5" to "-0.5",
            "5." to "5",
        )
        for ((text, expected) in cases) {
            assertEquals(text, 0, BigDecimal(expected).compareTo(Format.parseClipboard(text)))
        }
    }

    @Test fun clipboardRejectsNonNumbers() {
        for (text in listOf("", "abc", "12abc", "1.2.3", "--5", "()", "e5", "1e", "NaN", "Infinity", "0x1F", "1e9999999999", "1".repeat(300))) {
            assertNull(text, Format.parseClipboard(text))
        }
    }

    @Test fun groupHandlesSignsAndFractions() {
        assertEquals("-12,345.678", Format.group("-12345.678"))
        assertEquals("100", Format.group("100"))
        assertEquals("1,000", Format.group("1000"))
    }
}
