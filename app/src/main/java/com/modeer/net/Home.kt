package com.modeer.net

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** لقطة من حالة المجموعة: بياناتها وأعضاؤها. */
data class Snap(val g: Group, val members: List<Member>)

@Composable
fun PersonCard(g: Group, m: Member, isMe: Boolean, editable: Boolean, delayMs: Int = 0, onQuota: (Double) -> Unit) {
    val c = MaterialTheme.colorScheme
    val q = g.quotaGbFor(m)
    val used = g.usedGb(m)
    val left = maxOf(0.0, q - used)
    val frac = if (q > 0) (used / q).toFloat().coerceIn(0f, 1f) else 0f
    val lv = g.level(m)

    AppCard(delayMs = delayMs) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(m.name + if (isMe) " (أنت)" else "", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (lv > 0) LevelPill(lv)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(left, 44.sp, c.primary)
            Text("جيجا متبقية من ${fmt(q)}", color = c.onSurfaceVariant)
        }
        UsageBar(frac)
        Text("استهلك ${fmt(used)} جيجا (${fmtI((frac * 100).toInt())}%)", color = c.onSurfaceVariant, fontSize = 14.sp)
        val now = System.currentTimeMillis()
        val cs = g.cycleStart(now)
        val fc = Forecasts.calc(used, q, maxOf(cs, m.joinedAt), Cycle.nextBoundary(g.cycleDay, now), now)
        val who = if (isMe) null else m.name
        Text(Forecasts.lastsLine(fc, now, who), color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Forecasts.warnLine(fc, who)?.let { Notice(it, c.tertiaryContainer, c.onTertiaryContainer) }
        if (editable) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("الحصة", color = c.onSurfaceVariant)
                Stepper(
                    "${fmt(q)} جيجا",
                    { onQuota(maxOf(5.0, q - 5)) },
                    { onQuota(q + 5) },
                )
            }
        }
    }
}

@Composable
fun HomeTab(s: Snap, myId: String, owner: Boolean, name: String, offline: Boolean, refresh: () -> Unit) {
    val g = s.g
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val days = Cycle.daysUntilNext(g.cycleDay, System.currentTimeMillis())

    Page {
        val ctx = LocalContext.current
        val net = NetId.current(ctx)
        if ((g.ssid.isNotBlank() || g.gw.isNotBlank()) && !NetId.isHome(net, g.ssid, g.gw)) {
            Notice(
                if (net.onWifi) "إنت على شبكة تانية غير شبكة السكن، فاستهلاكك دلوقتي مش بيتحسب."
                else "إنت مش متصل بالواي فاي دلوقتي، ومفيش استهلاك بيتحسب.",
                c.primaryContainer,
                c.onPrimaryContainer,
            )
        }
        if (!owner) {
            val me = s.members.find { it.id == myId }
            Column {
                Text("أهلاً $name", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${g.name}، والباقة بتتجدد بعد ${fmtI(days)} يوم.", color = c.onSurfaceVariant)
            }
            if (me != null) {
                PersonCard(g, me, true, false) {}
                val lv = g.level(me)
                if (lv == 100) {
                    Notice("خلصت حصتك لحد ما الباقة تتجدد.", c.errorContainer, c.onErrorContainer)
                } else if (lv == 80) {
                    Notice("وصلت 80% من حصتك. خلي بالك لآخر الباقة.", c.tertiaryContainer, c.onTertiaryContainer)
                }
            } else {
                Text("ثواني لحد ما أجيب أرقامك...", color = c.onSurfaceVariant)
            }
        } else {
            val used = s.members.sumOf { g.usedGb(it) }
            val allocated = s.members.sumOf { g.quotaGbFor(it) }
            val frac = if (g.totalGb > 0) (used / g.totalGb).toFloat().coerceIn(0f, 1f) else 0f
            Text(g.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            AppCard {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("الباقة", color = c.onSurfaceVariant)
                    Text("${fmt(g.totalGb)} جيجا", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(maxOf(0.0, g.totalGb - used), 44.sp, c.primary)
                    Text("جيجا متبقية في المجموعة", color = c.onSurfaceVariant)
                }
                UsageBar(frac)
                val nowG = System.currentTimeMillis()
                val fcG = Forecasts.calc(used, g.totalGb, g.cycleStart(nowG), Cycle.nextBoundary(g.cycleDay, nowG), nowG)
                Text(Forecasts.groupLine(fcG, nowG), color = c.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    "استهلكتوا ${fmt(used)} جيجا، والباقة بتتجدد بعد ${fmtI(days)} يوم.",
                    color = c.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                if (allocated > g.totalGb + 0.05) {
                    Notice("الحصص الموزعة (${fmt(allocated)}) أكبر من الباقة.", c.errorContainer, c.onErrorContainer)
                }
            }

            val hot = s.members.map { it to g.level(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
            if (hot.isNotEmpty()) {
                Text("تنبيهات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                for ((m, lv) in hot) {
                    val bg = when (lv) {
                        100 -> c.errorContainer
                        80 -> c.tertiaryContainer
                        else -> c.primaryContainer
                    }
                    val fg = when (lv) {
                        100 -> c.onErrorContainer
                        80 -> c.onTertiaryContainer
                        else -> c.onPrimaryContainer
                    }
                    Notice(
                        if (lv == 100) "${m.name} خلّص حصته" else "${m.name} وصل ${fmtI(lv)}% من حصته",
                        bg,
                        fg,
                    )
                }
            }

            Text("الأشخاص (${fmtI(s.members.size)})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            for ((i, m) in s.members.sortedByDescending { g.usedGb(it) }.withIndex()) {
                PersonCard(g, m, m.id == myId, g.mode == "custom", delayMs = 120 + i * 90) { v ->
                    scope.launch {
                        runCatching { Repo.setMemberQuota(g.code, m.id, v) }
                        refresh()
                    }
                }
            }
        }
        BoardCard(s, myId, owner)
        if (offline) Notice("مفيش اتصال بالنت دلوقتي، بحاول تاني.", c.errorContainer, c.onErrorContainer)
    }
}
