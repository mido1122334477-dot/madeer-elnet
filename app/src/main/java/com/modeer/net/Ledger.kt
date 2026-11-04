package com.modeer.net

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * دفتر استهلاك محلي بالساعة، بيحسب بس اللي اتستهلك على شبكة السكن.
 * بنقرا عدّاد الواي فاي الكلي من النظام ونوزّع الفرق على الساعات،
 * ونحسبه بس لو الموبايل كان على شبكة السكن في اللحظتين (القراءة اللي فاتت والحالية).
 */
object Ledger {
    private const val HOUR = 3_600_000L
    private const val KEEP_DAYS = 200L

    private fun sp(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("ledger", Context.MODE_PRIVATE)

    private fun load(ctx: Context): MutableMap<Long, Long> {
        val m = HashMap<Long, Long>()
        val raw = sp(ctx).getString("h", "") ?: ""
        if (raw.isEmpty()) return m
        for (part in raw.split(',')) {
            val i = part.indexOf(':')
            if (i <= 0) continue
            val k = part.substring(0, i).toLongOrNull() ?: continue
            val v = part.substring(i + 1).toLongOrNull() ?: continue
            m[k] = v
        }
        return m
    }

    private fun spread(map: MutableMap<Long, Long>, from: Long, to: Long, bytes: Long) {
        if (to <= from) {
            map[to / HOUR] = (map[to / HOUR] ?: 0L) + bytes
            return
        }
        val span = to - from
        var t = from
        var rest = bytes
        while (t < to) {
            val hEnd = (t / HOUR + 1) * HOUR
            val segEnd = minOf(hEnd, to)
            val part = if (segEnd >= to) rest else bytes * (segEnd - t) / span
            map[t / HOUR] = (map[t / HOUR] ?: 0L) + part
            rest -= part
            t = segEnd
        }
    }

    @Synchronized
    fun sample(ctx: Context, onHome: Boolean, now: Long = System.currentTimeMillis()) {
        val p = sp(ctx)
        val base = p.getLong("base", 0L)
        if (base == 0L) {
            p.edit().putLong("base", now).putLong("lastAt", now).putLong("lastTotal", 0L)
                .putBoolean("lastHome", onHome).apply()
            return
        }
        // لو القراءة فشلت منلمسش أي حاجة، عشان منحسبش فرق غلط بعدين.
        val total = UsageReader.wifiBytesOrNull(ctx, base, now) ?: return
        val lastAt = p.getLong("lastAt", now)
        val lastTotal = p.getLong("lastTotal", 0L)
        val lastHome = p.getBoolean("lastHome", false)
        val delta = total - lastTotal
        val e = p.edit()
        if (delta > 0 && onHome && lastHome) {
            val map = load(ctx)
            spread(map, lastAt, now, delta)
            val cutoff = now / HOUR - KEEP_DAYS * 24
            map.keys.removeAll { it < cutoff }
            e.putString("h", map.entries.joinToString(",") { "${it.key}:${it.value}" })
        }
        e.putLong("lastAt", now).putLong("lastTotal", total).putBoolean("lastHome", onHome).apply()
    }

    fun sinceBytes(ctx: Context, from: Long): Long =
        load(ctx).entries.filter { (it.key + 1) * HOUR > from }.sumOf { it.value }

    /** العنصر i = استهلاك قبل i يوم (0 = النهارده). */
    fun daily(ctx: Context, days: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val arr = LongArray(days)
        for ((h, b) in load(ctx)) {
            val d = Instant.ofEpochMilli(h * HOUR).atZone(zone).toLocalDate()
            val idx = ChronoUnit.DAYS.between(d, today).toInt()
            if (idx in 0 until days) arr[idx] += b
        }
        return arr.toList()
    }

    /** العنصر i = أكتر ساعة استهلاك في اليوم (0..23)، أو -1 لو مفيش. */
    fun peakHours(ctx: Context, days: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val best = LongArray(days)
        val hour = LongArray(days) { -1L }
        for ((h, b) in load(ctx)) {
            val z = Instant.ofEpochMilli(h * HOUR).atZone(zone)
            val idx = ChronoUnit.DAYS.between(z.toLocalDate(), today).toInt()
            if (idx in 0 until days && b > best[idx]) {
                best[idx] = b
                hour[idx] = z.hour.toLong()
            }
        }
        return hour.toList()
    }

    /** العنصر i = استهلاك قبل i شهر (0 = الشهر الحالي). */
    fun monthly(ctx: Context, months: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val nowYm = YearMonth.now(zone)
        val arr = LongArray(months)
        for ((h, b) in load(ctx)) {
            val ym = YearMonth.from(Instant.ofEpochMilli(h * HOUR).atZone(zone))
            val idx = ChronoUnit.MONTHS.between(ym, nowYm).toInt()
            if (idx in 0 until months) arr[idx] += b
        }
        return arr.toList()
    }
}
