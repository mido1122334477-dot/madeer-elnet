package com.modeer.net

data class Alerts(val a50: Boolean, val a80: Boolean, val a100: Boolean)

/** مجموعة: باقة النت المشتركة. */
data class Group(
    val code: String,
    val name: String,
    val ownerId: String,
    val totalGb: Double,
    val people: Int,
    /** "equal" بالتساوي، أو "custom" حصص مختلفة. */
    val mode: String,
    val cycleDay: Int,
    /** وقت آخر "تصفير يدوي" للعدادات. */
    val cycleReset: Long,
    val alerts: Alerts,
) {
    /** بداية الباقة الحالية: آخر يوم تجديد، أو آخر تصفير يدوي لو أحدث. */
    fun cycleStart(now: Long = System.currentTimeMillis()): Long =
        maxOf(Cycle.lastBoundary(cycleDay, now), cycleReset)

    fun quotaGbFor(m: Member): Double {
        val equal = totalGb / people
        return if (mode == "custom") (m.quotaGb ?: equal) else equal
    }

    /**
     * استهلاك الشخص في الباقة الحالية بالجيجا.
     * لو الرقم المتخزّن من باقة قديمة (الموبايل لسه مزامنش بعد التجديد) نعتبره صفر.
     */
    fun usedGb(m: Member, cs: Long = cycleStart()): Double =
        if (m.usedSince == cs) m.usedBytes / Sync.GB else 0.0

    /** أعلى مستوى تنبيه وصله الشخص (100 أو 80 أو 50)، أو 0. */
    fun level(m: Member): Int {
        val q = quotaGbFor(m)
        if (q <= 0) return 0
        val pct = usedGb(m) / q * 100
        return when {
            pct >= 100 && alerts.a100 -> 100
            pct >= 80 && alerts.a80 -> 80
            pct >= 50 && alerts.a50 -> 50
            else -> 0
        }
    }
}

/** عضو في المجموعة. daily[i] = استهلاك قبل i يوم (0 = النهارده)، monthly[i] = قبل i شهر. */
data class Member(
    val id: String,
    val name: String,
    val joinedAt: Long,
    val usedBytes: Long,
    /** بداية الباقة اللي الرقم ده اتحسب منها. */
    val usedSince: Long,
    val quotaGb: Double?,
    val daily: List<Long>,
    val monthly: List<Long>,
)
