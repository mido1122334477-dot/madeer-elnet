package com.modeer.net

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class Report(
    val low: Boolean,
    val daysData: Int,
    val title: String,
    val head: String,
    val items: List<String>,
    val tips: List<String>,
)

/** تقارير "اسأل التطبيق": كلها محسوبة من أرقام حقيقية، ومفيش تخمين. */
object Reports {
    private const val DAY = 86_400_000L
    private val ar = Locale("ar")

    private fun dateStr(d: LocalDate): String =
        "${fmtI(d.dayOfMonth)} ${d.month.getDisplayName(TextStyle.FULL, ar)}"

    fun titleOf(key: String, group: Boolean): String = when (key) {
        "top" -> if (group) "مين أكتر واحد استهلك؟" else "أنهي أيام استهلكت فيها أكتر؟"
        "when" -> "هنخلّص إمتى بالمعدل ده؟"
        else -> "ليه النت خلص بسرعة؟"
    }

    fun build(g: Group, sel: List<Member>, key: String, group: Boolean): Report {
        val now = System.currentTimeMillis()
        val title = titleOf(key, group)
        val first = sel.minOfOrNull { if (it.joinedAt > 0) it.joinedAt else now } ?: now
        val daysData = (((now - first) / DAY).toInt() + 1).coerceIn(0, 30)
        if (sel.isEmpty() || daysData < 7) {
            return Report(true, daysData, title, "", emptyList(), emptyList())
        }

        val cs = g.cycleStart(now)
        val cycleLen = ((Cycle.nextBoundary(g.cycleDay, now) - Cycle.lastBoundary(g.cycleDay, now)) / DAY)
            .toInt().coerceAtLeast(1)
        val elapsed = maxOf(1.0, (now - cs) / DAY.toDouble())
        val quota = sel.sumOf { g.quotaGbFor(it) }
        val used = sel.sumOf { g.usedGb(it, cs) }
        val pace = used / elapsed
        val remainingDays = maxOf(0.0, cycleLen - elapsed)
        val left = maxOf(0.0, quota - used)

        fun dayGb(m: Member, i: Int): Double = m.daily.getOrElse(i) { 0L } / Sync.GB
        fun sumDays(m: Member, from: Int, to: Int): Double = (from..to).sumOf { dayGb(m, it) }

        // توقع: هتخلص إمتى؟
        var runsOut = false
        val forecast: String
        if (quota > 0 && used >= quota) {
            runsOut = true
            forecast = "الحصة خلصت فعلاً."
        } else if (pace > 0 && left / pace < remainingDays) {
            runsOut = true
            val d = Math.ceil(left / pace).toInt()
            forecast = "بالمعدل ده الحصة هتخلص تقريباً يوم ${dateStr(LocalDate.now().plusDays(d.toLong()))}، يعني بعد ${fmtI(d)} يوم."
        } else {
            forecast = "بالمعدل ده الحصة تكفي لآخر الباقة."
        }

        // استهلاك كل يوم (مجمّع لكل المختارين) في آخر الأيام المتاحة
        val span = minOf(daysData, 30)
        val totals = (0 until span).map { i -> sel.sumOf { dayGb(it, i) } }

        val items = mutableListOf<String>()
        val tips = mutableListOf<String>()
        val head: String

        when (key) {
            "top" -> {
                if (group) {
                    val r = sel.sortedByDescending { g.usedGb(it, cs) }
                    head = "${r.first().name} هو الأكتر استهلاكاً."
                    for (m in r) {
                        val u = g.usedGb(m, cs)
                        val share = if (used > 0) (u / used * 100).toInt() else 0
                        items.add("${m.name}: ${fmt(u)} جيجا (${fmtI(share)}% من المستهلك)")
                    }
                } else {
                    val order = totals.indices.sortedByDescending { totals[it] }.filter { totals[it] > 0 }.take(3)
                    if (order.isEmpty()) {
                        head = "مفيش استهلاك مسجّل في الأيام اللي فاتت."
                    } else {
                        head = "أكتر يوم استهلاكاً كان ${dateStr(LocalDate.now().minusDays(order.first().toLong()))}."
                        for (i in order) {
                            items.add("${dateStr(LocalDate.now().minusDays(i.toLong()))}: ${fmt(totals[i])} جيجا")
                        }
                    }
                }
            }
            "when" -> {
                head = forecast
                if (group) {
                    for (m in sel) {
                        val u = g.usedGb(m, cs)
                        val q = g.quotaGbFor(m)
                        val p = u / elapsed
                        val line = if (q > 0 && u >= q) {
                            "الحصة خلصت"
                        } else if (p > 0 && (q - u) / p < remainingDays) {
                            "تخلص تقريباً يوم ${dateStr(LocalDate.now().plusDays(Math.ceil((q - u) / p).toLong()))}"
                        } else {
                            "تكفي لآخر الباقة"
                        }
                        items.add("${m.name}: $line")
                    }
                }
                if (runsOut && used < quota && remainingDays >= 1) {
                    tips.add("عشان الحصة تكفي لآخر الباقة حاول متزيدش عن ${fmt(left / remainingDays)} جيجا في اليوم.")
                }
            }
            else -> {
                head = "الاستهلاك وصل ${fmtI(if (quota > 0) (used / quota * 100).toInt() else 0)}% من الحصة، والباقة عدّى منها ${fmtI((elapsed / cycleLen * 100).toInt().coerceAtMost(100))}%."

                if (daysData >= 14) {
                    val last7 = sel.sumOf { sumDays(it, 0, 6) }
                    val prev7 = sel.sumOf { sumDays(it, 7, 13) }
                    if (prev7 > 0.05) {
                        val ch = ((last7 - prev7) / prev7 * 100).toInt()
                        items.add(
                            "آخر 7 أيام: ${fmt(last7)} جيجا، مقابل ${fmt(prev7)} جيجا في الأسبوع اللي قبله: " +
                                (if (ch >= 0) "زيادة " else "نقص ") + "${fmtI(Math.abs(ch))}%."
                        )
                    } else {
                        items.add("آخر 7 أيام: ${fmt(last7)} جيجا، ومفيش استهلاك يُذكر في الأسبوع اللي قبله.")
                    }
                    if (group) {
                        val best = sel.maxByOrNull { sumDays(it, 0, 6) - sumDays(it, 7, 13) }
                        if (best != null) {
                            val diff = sumDays(best, 0, 6) - sumDays(best, 7, 13)
                            if (diff > 0.05) items.add("أكبر زيادة عند ${best.name}: +${fmt(diff)} جيجا عن الأسبوع اللي قبله.")
                        }
                    }
                } else {
                    items.add("مقدرش أقارن بالأسبوع اللي فات لأن عندي أقل من 14 يوم بيانات.")
                }

                val bigIdx = totals.indices.maxByOrNull { totals[it] }
                val total = totals.sum()
                if (bigIdx != null && totals[bigIdx] > 0 && total > 0) {
                    items.add(
                        "أكتر يوم استهلاكاً: ${dateStr(LocalDate.now().minusDays(bigIdx.toLong()))} (${fmt(totals[bigIdx])} جيجا، ${fmtI((totals[bigIdx] / total * 100).toInt())}% من الفترة)."
                    )
                    tips.add("راجع اللي حصل في اليوم ده. التطبيق بيعرف اليوم بس، لكن مش بيعرف السبب (تحميل أو تحديثات أو فيديو).")
                }
                items.add(forecast)
                if (runsOut && used < quota && remainingDays >= 1) {
                    tips.add("عشان الحصة تكفي لآخر الباقة حاول متزيدش عن ${fmt(left / remainingDays)} جيجا في اليوم.")
                }
            }
        }
        return Report(false, daysData, title, head, items, tips)
    }
}
