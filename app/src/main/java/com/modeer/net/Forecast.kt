package com.modeer.net

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** توقع استهلاك شخص (أو مجموعة) لحد آخر الباقة. */
data class Fc(
    val ready: Boolean,
    val elapsedDays: Double,
    val perDay: Double,
    val left: Double,
    val finished: Boolean,
    /** هتخلص قبل التجديد؟ */
    val runsOut: Boolean,
    val runOutAt: Long,
    val daysShort: Int,
    /** +40 يعني أسرع من المعدل الطبيعي بـ 40%. */
    val pace: Int,
    val safePerDay: Double,
)

object Forecasts {
    private const val DAY = 86_400_000L
    private val ar = Locale("ar")
    private fun zone() = ZoneId.systemDefault()

    fun calc(used: Double, quota: Double, start: Long, next: Long, now: Long): Fc {
        val left = maxOf(0.0, quota - used)
        val elapsed = maxOf(0.0, (now - start) / DAY.toDouble())
        val remaining = maxOf(0.0, (next - now) / DAY.toDouble())
        val none = Fc(false, elapsed, 0.0, left, false, false, next, 0, 0, left)
        if (quota <= 0.0) return none
        if (used >= quota) return Fc(true, elapsed, 0.0, 0.0, true, true, now, ceil(remaining).toInt(), 0, 0.0)
        // محتاجين نص يوم على الأقل عشان التوقع ميبقاش تخمين.
        if (elapsed < 0.5) return none
        val perDay = used / elapsed
        val total = (next - start).toDouble()
        val expected = if (total > 0) ((now - start) / total).coerceIn(0.0, 1.0) else 0.0
        val pace = if (expected > 0.05) ((used / quota / expected - 1.0) * 100).roundToInt() else 0
        val daysLeft = if (perDay > 1e-6) left / perDay else Double.MAX_VALUE
        val runs = daysLeft < remaining
        val at = if (runs) now + (daysLeft * DAY).toLong() else next
        val short = if (runs) ceil((next - at) / DAY.toDouble()).toInt().coerceAtLeast(1) else 0
        val safe = if (remaining >= 0.5) left / remaining else left
        return Fc(true, elapsed, perDay, left, false, runs, at, short, pace, safe)
    }

    fun dayWhen(at: Long, now: Long): String {
        val d = Instant.ofEpochMilli(at).atZone(zone()).toLocalDate()
        val t = Instant.ofEpochMilli(now).atZone(zone()).toLocalDate()
        val diff = ChronoUnit.DAYS.between(t, d).toInt()
        return when {
            diff <= 0 -> "آخر النهارده"
            diff == 1 -> "بكرة"
            diff in 2..6 -> "يوم ${d.dayOfWeek.getDisplayName(TextStyle.FULL, ar)} الجاي"
            else -> "يوم ${fmtI(d.dayOfMonth)} ${d.month.getDisplayName(TextStyle.FULL, ar)}"
        }
    }

    /** الجملة اللي تحت الرقم الكبير. who = null يعني "أنت". */
    fun lastsLine(fc: Fc, now: Long, who: String?): String {
        if (fc.finished) return "الحصة خلصت لحد التجديد."
        if (!fc.ready) return "لسه بجمع بيانات عشان أتوقع الجيجات تكفي لحد إمتى."
        val what = if (who == null) "الجيجات دي هتكفيك" else "جيجات $who هتكفيه"
        return if (fc.runsOut) {
            "بالاستهلاك الحالي، $what لحد ${dayWhen(fc.runOutAt, now)}."
        } else {
            "بالاستهلاك الحالي، $what لحد التجديد وهيفضل منها كمان."
        }
    }

    fun groupLine(fc: Fc, now: Long): String {
        if (fc.finished) return "الباقة خلصت لحد التجديد."
        if (!fc.ready) return "لسه بجمع بيانات عشان أتوقع الباقة تكفي لحد إمتى."
        return if (fc.runsOut) {
            "بالاستهلاك الحالي، الباقة هتكفي المجموعة لحد ${dayWhen(fc.runOutAt, now)}."
        } else {
            "بالاستهلاك الحالي، الباقة هتكفي لحد التجديد وهيفضل منها كمان."
        }
    }

    /** إنذار مبكر، أو null لو مفيش مشكلة جاية. */
    fun warnLine(fc: Fc, who: String?): String? {
        if (!fc.ready || fc.finished || !fc.runsOut) return null
        val subj = who ?: "إنت"
        val verb = if (who == null) "بتستهلك" else "بيستهلك"
        val keep = if (who == null) "لو كملت كده" else "لو كمّل كده"
        val fin = if (who == null) "هتخلص حصتك" else "هتخلص حصته"
        val head = if (fc.pace >= 10) "$subj $verb أسرع من المعدل الطبيعي بـ ${fmtI(fc.pace)}%. " else ""
        return head + "$keep $fin قبل التجديد بـ ${fmtI(fc.daysShort)} يوم."
    }
}

/** الأسلوب المرح في "اسأل التطبيق". */
object Jokes {
    private val ar = Locale("ar")

    fun hourLabel(h: Int): String {
        val h12 = if (h % 12 == 0) 12 else h % 12
        val part = when (h) {
            in 5..11 -> "الصبح"
            in 12..15 -> "الضهر"
            in 16..18 -> "العصر"
            in 19..23 -> "بالليل"
            else -> "بعد نص الليل"
        }
        return "الساعة ${fmtI(h12)} $part"
    }

    fun bigDay(date: LocalDate, gb: Double, hour: Int): String {
        val dn = date.dayOfWeek.getDisplayName(TextStyle.FULL, ar)
        val tail = when {
            hour < 0 -> ""
            hour >= 22 || hour <= 4 -> " والذروة كانت ${hourLabel(hour)}، سهرة مسلسلات صريحة 🍿"
            hour in 18..21 -> " والذروة كانت ${hourLabel(hour)}، يوتيوب وتيك توك على راحتهم 📱"
            hour in 5..11 -> " والذروة كانت ${hourLabel(hour)}، غالباً تحديثات أو تحميل تقيل ⬇️"
            else -> " والذروة كانت ${hourLabel(hour)}، حد فتح فيديوهات وقت الشغل 😏"
        }
        return "يوم $dn كان فيه هجوم مسلسلات وتيك توك غير طبيعي: ${fmt(gb)} جيجا في يوم واحد!$tail"
    }
}
