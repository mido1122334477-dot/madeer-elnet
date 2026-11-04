package com.modeer.net

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Rank(
    val m: Member,
    val label: String,
    val used: Double,
    val q: Double,
    val frac: Float,
    val fc: Fc,
    val ratio: Double,
)

/** ترتيب المجموعة. الأسماء بتتخبّى عن الأعضاء لو صاحب المجموعة فعّل كده، عشان محدش يتحرج. */
@Composable
fun BoardCard(s: Snap, myId: String, owner: Boolean) {
    val g = s.g
    val c = MaterialTheme.colorScheme
    if (s.members.size < 2) return
    val now = System.currentTimeMillis()
    val cs = g.cycleStart(now)
    val next = Cycle.nextBoundary(g.cycleDay, now)
    val anon = g.anon && !owner

    val rows = s.members.withIndex().map { (i, m) ->
        val used = g.usedGb(m)
        val q = g.quotaGbFor(m)
        val start = maxOf(cs, m.joinedAt)
        val fc = Forecasts.calc(used, q, start, next, now)
        val total = (next - start).toDouble()
        val expected = if (total > 0) ((now - start) / total).coerceIn(0.0, 1.0) else 0.0
        val ratio = if (q > 0 && expected > 0.05) used / q / expected else 0.0
        val label = when {
            m.id == myId -> "${m.name} (أنت)"
            anon -> "عضو ${fmtI(i + 1)}"
            else -> m.name
        }
        Rank(m, label, used, q, if (q > 0) (used / q).toFloat().coerceIn(0f, 1f) else 0f, fc, ratio)
    }
    if (rows.sumOf { it.used } <= 0.0) return

    val byUsed = rows.sortedByDescending { it.used }
    val top = byUsed.first()
    val low = byUsed.last()
    val fast = rows.filter { it.ratio > 1.0 }.maxByOrNull { it.ratio }
    val committed = rows.count { it.used < it.q && !(it.fc.ready && it.fc.runsOut) }

    Text("الترتيب", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    AppCard {
        for ((i, r) in byUsed.withIndex()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(
                        "${fmtI(i + 1)}. ${r.label}",
                        fontWeight = if (r.m.id == myId) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text("${fmt(r.used)} جيجا", color = c.onSurfaceVariant)
                }
                UsageBar(r.frac, 8.dp)
            }
        }
        Text("🔥 الأكتر استهلاكاً: ${top.label}")
        if (low.m.id != top.m.id) Text("🌱 الأقل استهلاكاً: ${low.label}")
        if (fast != null) {
            Text("⚡ الأسرع في استهلاك حصته: ${fast.label} (أسرع من المعدل بـ ${fmtI(((fast.ratio - 1) * 100).toInt())}%)")
        }
        Text(
            if (committed == rows.size) "✅ الكل ماشي على حصته، برافو"
            else "✅ ملتزمين بحصتهم: ${fmtI(committed)} من ${fmtI(rows.size)}",
        )
        if (anon) Text("الأسماء مخفية عشان محدش يتحرج.", color = c.onSurfaceVariant, fontSize = 13.sp)
    }
}
