package com.modeer.net

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** حدود الباقة الشهرية: بتتجدد تلقائياً في نفس اليوم كل شهر. */
object Cycle {
    private const val DAY = 86_400_000L

    private fun today(now: Long): LocalDate =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun onDay(base: LocalDate, day: Int): LocalDate =
        base.withDayOfMonth(minOf(day, base.lengthOfMonth()))

    private fun startOf(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** بداية الباقة الحالية. */
    fun lastBoundary(day: Int, now: Long): Long {
        val t = today(now)
        var d = onDay(t, day)
        if (d.isAfter(t)) d = onDay(t.minusMonths(1), day)
        return startOf(d)
    }

    /** بداية الباقة الجاية. */
    fun nextBoundary(day: Int, now: Long): Long {
        val t = today(now)
        var d = onDay(t, day)
        if (!d.isAfter(t)) d = onDay(t.plusMonths(1), day)
        return startOf(d)
    }

    fun daysUntilNext(day: Int, now: Long): Int =
        Math.ceil((nextBoundary(day, now) - now) / DAY.toDouble()).toInt().coerceAtLeast(0)
}
