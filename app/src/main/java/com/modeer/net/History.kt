package com.modeer.net

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun BarChart(values: List<Double>, labels: List<String>) {
    val c = MaterialTheme.colorScheme
    val mx = maxOf(values.maxOrNull() ?: 0.0, 0.001)
    Row(
        Modifier
            .fillMaxWidth()
            .height(190.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(fmt(v), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height((120 * (v / mx)).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(c.primary),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    labels.getOrElse(i) { "" },
                    fontSize = 11.sp,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun HistoryTab(s: Snap, myId: String, owner: Boolean) {
    val c = MaterialTheme.colorScheme
    var range by remember { mutableStateOf("day") }
    var who by remember { mutableStateOf("all") }
    val ar = Locale("ar")

    val sel = when {
        !owner -> s.members.filter { it.id == myId }
        who == "all" -> s.members
        else -> s.members.filter { it.id == who }
    }

    val data: Triple<List<Double>, List<String>, String> = when (range) {
        "week" -> Triple(
            (3 downTo 0).map { k ->
                sel.sumOf { m -> (7 * k..7 * k + 6).sumOf { m.daily.getOrElse(it) { 0L } } } / Sync.GB
            },
            (3 downTo 0).map { if (it == 0) "الحالي" else "قبل ${fmtI(it)}" },
            "آخر 4 أسابيع",
        )
        "month" -> Triple(
            (5 downTo 0).map { i -> sel.sumOf { it.monthly.getOrElse(i) { 0L } } / Sync.GB },
            (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()).month.getDisplayName(TextStyle.SHORT, ar) },
            "آخر 6 شهور",
        )
        else -> Triple(
            (6 downTo 0).map { i -> sel.sumOf { it.daily.getOrElse(i) { 0L } } / Sync.GB },
            (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, ar) },
            "آخر 7 أيام",
        )
    }
    val (vals, labels, caption) = data

    Page {
        Text("سجل الاستهلاك", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("يومي", range == "day", Modifier.weight(1f)) { range = "day" }
            Chip("أسبوعي", range == "week", Modifier.weight(1f)) { range = "week" }
            Chip("شهري", range == "month", Modifier.weight(1f)) { range = "month" }
        }
        if (owner) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chip("الكل", who == "all") { who = "all" }
                for (m in s.members) Chip(m.name, who == m.id) { who = m.id }
            }
        }
        AppCard {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(caption, color = c.onSurfaceVariant)
                Text("${fmt(vals.sum())} جيجا", fontWeight = FontWeight.Bold)
            }
            BarChart(vals, labels)
        }
        Text(
            "السجل بيتحسب من استهلاك الواي فاي على كل موبايل عليه التطبيق. الأيام اللي قبل ما الشخص ينضم بتظهر من بيانات موبايله نفسه.",
            color = c.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}
