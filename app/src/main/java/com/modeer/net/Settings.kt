package com.modeer.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsTab(s: Snap, refresh: () -> Unit, owner: Boolean = true, onLeave: () -> Unit) {
    val g = s.g
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askRenew by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }

    fun update(vararg f: Pair<String, Any>) {
        scope.launch {
            runCatching { Repo.updateGroup(g.code, mapOf(*f)) }
            refresh()
        }
    }

    val now = System.currentTimeMillis()
    val days = Cycle.daysUntilNext(g.cycleDay, now)
    val next = Instant.ofEpochMilli(Cycle.nextBoundary(g.cycleDay, now)).atZone(ZoneId.systemDefault()).toLocalDate()
    val nextStr = "${fmtI(next.dayOfMonth)} ${next.month.getDisplayName(TextStyle.FULL, Locale("ar"))}"

    Page {
        Text("الإعدادات", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        AppCard {
            Text(g.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("كود الدعوة", color = c.onSurfaceVariant)
            Text(
                g.code,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = c.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("code", g.code))
                        Toast.makeText(ctx, "اتنسخ الكود", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("انسخ") }
                Button(
                    onClick = { shareCode(ctx, g.code) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("ابعت") }
            }
        }

        AppCard {
            Text("الباقة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("حجم الباقة", color = c.onSurfaceVariant)
                Stepper(
                    "${fmt(g.totalGb)} جيجا",
                    { update("totalGb" to maxOf(50.0, g.totalGb - 50)) },
                    { update("totalGb" to g.totalGb + 50) },
                )
            }
        }

        AppCard {
            Text("طريقة التقسيم", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("بالتساوي", g.mode == "equal", Modifier.weight(1f)) { update("mode" to "equal") }
                Chip("حصص مختلفة", g.mode == "custom", Modifier.weight(1f)) { update("mode" to "custom") }
            }
            Text(
                if (g.mode == "equal") {
                    "${fmt(g.totalGb)} جيجا ÷ ${fmtI(g.people)} = ${fmt(g.totalGb / g.people)} جيجا لكل شخص."
                } else {
                    "عدّل حصة كل شخص من الصفحة الرئيسية."
                },
                color = c.onSurfaceVariant,
            )
        }

        AppCard {
            Text("التنبيهات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("ابعت تنبيه لما الشخص يوصل لـ:", color = c.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("50%", g.alerts.a50, Modifier.weight(1f)) { update("a50" to !g.alerts.a50) }
                Chip("80%", g.alerts.a80, Modifier.weight(1f)) { update("a80" to !g.alerts.a80) }
                Chip("100%", g.alerts.a100, Modifier.weight(1f)) { update("a100" to !g.alerts.a100) }
            }
            Text(
                "التنبيه بيوصل على الموبايلات اللي عليها التطبيق ومفعّلة الإشعارات. قطع النت عند 100% مش متاح في النسخة دي.",
                color = c.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }

        AppCard {
            Text("شبكة السكن", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val net = NetId.current(ctx)
            val homeName = when {
                g.ssid.isNotBlank() -> g.ssid
                g.gw.isNotBlank() -> "شبكة بعنوان ${g.gw}"
                else -> "لسه متحددتش، فأي واي فاي بيتحسب"
            }
            Text("الشبكة المحسوبة: $homeName")
            Text(
                "الشبكة الحالية: " + when {
                    !net.onWifi -> "مفيش واي فاي"
                    net.ssid != null -> net.ssid
                    else -> "اسمها غير معروف (فعّل إذن الموقع عشان يظهر)"
                },
                color = c.onSurfaceVariant,
            )
            Text("الاستهلاك على أي شبكة تانية (شغل، كافيه، بيت) مش بيتحسب.", color = c.onSurfaceVariant, fontSize = 13.sp)
            if (owner) {
                Button(
                    onClick = {
                        scope.launch {
                            runCatching {
                                Repo.updateGroup(g.code, mapOf("ssid" to (net.ssid ?: ""), "gw" to (net.gw ?: "")))
                            }
                            refresh()
                        }
                    },
                    enabled = net.onWifi,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("اعتبر الشبكة الحالية هي شبكة السكن") }
            }
        }

        if (owner) {
            AppCard {
                Text("الترتيب", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("أسماء مخفية", g.anon, Modifier.weight(1f)) { update("anon" to true) }
                    Chip("أسماء ظاهرة", !g.anon, Modifier.weight(1f)) { update("anon" to false) }
                }
                Text(
                    "لو الأسماء مخفية، الأعضاء بيشوفوا اسمهم بس وباقي الأسماء بتظهر \"عضو ٢\" وهكذا. إنت بتشوف الكل.",
                    color = c.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }

        AppCard {
            Text("التجديد التلقائي", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("أول يوم في الباقة", color = c.onSurfaceVariant)
                Stepper(
                    fmtI(g.cycleDay),
                    { update("cycleDay" to maxOf(1, g.cycleDay - 1)) },
                    { update("cycleDay" to minOf(28, g.cycleDay + 1)) },
                )
            }
            Text("العدادات بتتصفّر لوحدها بعد ${fmtI(days)} يوم، يوم $nextStr.", color = c.onSurfaceVariant)
            OutlinedButton(
                onClick = { askRenew = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("صفّر العدادات الآن") }
        }

        TextButton(onClick = { askLeave = true }, modifier = Modifier.fillMaxWidth()) {
            Text("اخرج من المجموعة", color = c.error)
        }
    }

    if (askRenew) {
        AlertDialog(
            onDismissRequest = { askRenew = false },
            title = { Text("تصفير العدادات؟") },
            text = { Text("كل العدادات هتبدأ من الصفر عند كل الأعضاء.") },
            confirmButton = {
                TextButton(onClick = {
                    askRenew = false
                    scope.launch {
                        runCatching { Repo.resetNow(g.code) }
                        refresh()
                    }
                }) { Text("آه، صفّر") }
            },
            dismissButton = { TextButton(onClick = { askRenew = false }) { Text("لأ") } },
        )
    }
    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("تخرج من المجموعة؟") },
            text = { Text("هتتشال من القايمة، وتقدر ترجع بنفس الكود.") },
            confirmButton = {
                TextButton(onClick = {
                    askLeave = false
                    onLeave()
                }) { Text("اخرج") }
            },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("لأ") } },
        )
    }
}
