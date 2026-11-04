package com.modeer.net

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AskTab(s: Snap, myId: String, owner: Boolean) {
    val c = MaterialTheme.colorScheme
    var key by remember { mutableStateOf("why") }
    var q by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("all") }
    var hint by remember { mutableStateOf<String?>(null) }

    val sel = when {
        !owner -> s.members.filter { it.id == myId }
        who == "all" -> s.members
        else -> s.members.filter { it.id == who }
    }
    val group = owner && who == "all"
    val r = Reports.build(s.g, sel, key, group)

    fun submit() {
        val t = q.trim()
        if (t.isEmpty()) {
            hint = "اكتب سؤالك الأول"
            return
        }
        val k = when {
            Regex("امتى|إمتى|متى").containsMatchIn(t) -> "when"
            Regex("مين|أكتر|اكتر|أكثر").containsMatchIn(t) -> "top"
            Regex("ليه|لماذا|خلص|سريع|زاد|زيادة").containsMatchIn(t) -> "why"
            else -> null
        }
        if (k == null) {
            hint = "لسه بجاوب على الأسئلة اللي تحت بس."
        } else {
            key = k
            hint = null
        }
    }

    Page {
        Text("اسأل التطبيق", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (owner) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("الكل", who == "all") { who = "all" }
                for (m in s.members) Chip(m.name, who == m.id) { who = m.id }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                placeholder = { Text("اكتب سؤالك") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { submit() }, shape = RoundedCornerShape(12.dp)) { Text("اسأل") }
        }
        hint?.let { Text(it, color = c.error) }

        for (k in listOf("why", "top", "when")) {
            Chip(Reports.titleOf(k, group), key == k, Modifier.fillMaxWidth()) {
                key = k
                hint = null
            }
        }

        AppCard {
            Text(r.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (r.low) {
                Text("لسه مفيش بيانات كفاية. محتاج 7 أيام على الأقل من الاستخدام عشان أقدر أفسّر الاستهلاك بدل ما أخمّن.")
                Text("عندنا دلوقتي ${fmtI(r.daysData)} يوم.", color = c.onSurfaceVariant)
            } else {
                Text(r.head, fontWeight = FontWeight.Bold)
                for (line in r.items) Text("• $line")
                if (r.tips.isNotEmpty()) {
                    Text("ممكن تعمل إيه", fontWeight = FontWeight.Bold)
                    for (tip in r.tips) Text("• $tip")
                }
                Text(
                    "مبني على ${fmtI(r.daysData)} يوم من البيانات.",
                    fontSize = 13.sp,
                    color = c.onSurfaceVariant,
                )
            }
        }
    }
}
