package com.bradflaugher.bf12c.engine

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 12c date encoding: M.DY is MM.DDYYYY and D.MY is DD.MMYYYY. */
object Dates {
    private val MIN = LocalDate.of(1582, 10, 15)
    private val MAX = LocalDate.of(4046, 11, 25)

    fun decode(x: BigDecimal, dmy: Boolean): LocalDate {
        // Month and day are at most 31, so anything from 100 up is garbage.
        if (x.signum() <= 0 || x >= BigDecimal.valueOf(100)) throw CalcError(8)
        val scaled = x.setScale(6, RoundingMode.HALF_UP).movePointRight(6).toBigIntegerExact().toLong()
        val first = (scaled / 1_000_000).toInt()
        val second = ((scaled / 10_000) % 100).toInt()
        val year = (scaled % 10_000).toInt()
        val (month, day) = if (dmy) second to first else first to second
        val date = try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            throw CalcError(8)
        }
        if (date.isBefore(MIN) || date.isAfter(MAX)) throw CalcError(8)
        return date
    }

    fun encode(date: LocalDate, dmy: Boolean): BigDecimal {
        if (date.isBefore(MIN) || date.isAfter(MAX)) throw CalcError(8)
        val (a, b) = if (dmy) date.dayOfMonth to date.monthValue else date.monthValue to date.dayOfMonth
        return BigDecimal.valueOf(a * 1_000_000L + b * 10_000L + date.year, 6)
    }

    /** DATE: the date [days] away from [start], plus the 12c weekday (1 = Monday … 7 = Sunday). */
    fun addDays(start: LocalDate, days: BigDecimal): Pair<LocalDate, Int> {
        if (!BigMath.isInteger(days)) throw CalcError(8)
        val result = try {
            start.plusDays(days.longValueExact())
        } catch (_: ArithmeticException) {
            throw CalcError(8)
        } catch (_: DateTimeException) {
            throw CalcError(8)
        }
        if (result.isBefore(MIN) || result.isAfter(MAX)) throw CalcError(8)
        return result to result.dayOfWeek.value
    }

    /** ΔDYS: (actual days, 30/360 days) from [a] to [b]. */
    fun daysBetween(a: LocalDate, b: LocalDate): Pair<Long, Long> {
        val actual = ChronoUnit.DAYS.between(a, b)
        var d1 = a.dayOfMonth
        var d2 = b.dayOfMonth
        // 12c 30/360: day 31 becomes 30; the end day becomes 30 only if the start day is 30 or 31.
        if (d1 == 31) d1 = 30
        if (d2 == 31 && d1 >= 30) d2 = 30
        val basis = (b.year - a.year) * 360L + (b.monthValue - a.monthValue) * 30L + (d2 - d1)
        return actual to basis
    }
}
