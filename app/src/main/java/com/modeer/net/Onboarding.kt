package com.modeer.net

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private fun copyCode(ctx: Context, code: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", code))
    Toast.makeText(ctx, "اتنسخ الكود", Toast.LENGTH_SHORT).show()
}

@Composable
private fun HowStep(num: String, text: String) {
    val c = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(num, color = c.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        Text(text)
    }
}

@Composable
private fun ChoiceCard(title: String, sub: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = c.onSurfaceVariant)
    }
}

// ───────────── 1) التسجيل ─────────────

@Composable
fun SignupScreen(store: Store, onDone: () -> Unit) {
    var name by remember { mutableStateOf(store.name) }
    var phone by remember { mutableStateOf(store.phone) }
    var err by remember { mutableStateOf<String?>(null) }

    Page {
        BrandMark()
        Text("قسّم باقة النت بالعدل بين ساكني الشقة", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        AppCard {
            HowStep("١", "سجّل وأنشئ مجموعة واكتب حجم الباقة.")
            HowStep("٢", "ابعت كود الدعوة لزمايلك.")
            HowStep("٣", "كل واحد يشوف المتبقي من حصته، والتطبيق ينبّهه لما تخلص.")
        }
        AppCard {
            Field("اسمك", name, { name = it })
            Field("رقم الموبايل (اختياري)", phone, { phone = it }, KeyboardType.Phone)
            Text(
                "رقم الموبايل بيتحفظ على جهازك بس، ومبيتبعتش لحد.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val n = name.trim()
                    if (n.isEmpty()) {
                        err = "اكتب اسمك الأول"
                    } else {
                        store.name = n
                        store.phone = phone.trim()
                        onDone()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("سجّل") }
        }
    }
}

// ───────────── 2) اختيار ─────────────

@Composable
fun ChooseScreen(store: Store, onCreate: () -> Unit, onJoin: () -> Unit) {
    Page {
        BrandMark()
        Text("أهلاً ${store.name}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("اختار تبدأ إزاي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceCard("أنشئ مجموعة جديدة", "أنا اللي هدير الباقة وأدعو زمايلي.", onCreate)
        ChoiceCard("عندي كود دعوة", "زميلي بعتلي كود أنضم بيه.", onJoin)
    }
}

// ───────────── 3) إنشاء مجموعة ─────────────

@Composable
fun CreateScreen(store: Store, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var gname by remember { mutableStateOf("") }
    var total by remember { mutableStateOf(300) }
    var people by remember { mutableStateOf(4) }
    var day by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val c = MaterialTheme.colorScheme

    Page {
        TextButton(onClick = onBack) { Text("رجوع") }
        Text("أنشئ المجموعة", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        AppCard {
            Field("اسم المجموعة (مثلاً: سكن الطلبة)", gname, { gname = it })
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("حجم الباقة")
                Stepper("${fmtI(total)} جيجا", { total = maxOf(50, total - 50) }, { total += 50 })
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("عدد الأشخاص")
                Stepper(fmtI(people), { people = maxOf(1, people - 1) }, { people = minOf(20, people + 1) })
            }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("أول يوم في الباقة")
                Stepper(fmtI(day), { day = maxOf(1, day - 1) }, { day = minOf(28, day + 1) })
            }
            Notice(
                "كل شخص هياخد ${fmt(total.toDouble() / people)} جيجا. تقدر تغيّر الحصص بعد كده.",
                c.primaryContainer,
                c.onPrimaryContainer,
            )
            err?.let { Text(it, color = c.error) }
            Button(
                onClick = {
                    busy = true
                    err = null
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            val code = Repo.createGroup(
                                gname.trim().ifEmpty { "مجموعتي" },
                                total.toDouble(),
                                people,
                                store.memberId,
                                day,
                            )
                            Repo.upsertMember(code, store.memberId, store.name, 0L, now, emptyList(), emptyList())
                            store.groupCode = code
                            store.joinedAt = now
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            err = "مقدرتش أتصل بالنت، جرّب تاني"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (busy) "ثواني..." else "إنشاء المجموعة") }
        }
    }
}

// ───────────── 4) انضمام بكود ─────────────

@Composable
fun JoinScreen(store: Store, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    Page {
        TextButton(onClick = onBack) { Text("رجوع") }
        Text("انضم لمجموعة", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        AppCard {
            Field("كود الدعوة", code, { code = it })
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val c = code.trim().uppercase(Locale.ROOT)
                    if (c.length < 4) {
                        err = "اكتب كود الدعوة"
                    } else {
                        busy = true
                        err = null
                        scope.launch {
                            try {
                                if (Repo.getGroup(c) == null) {
                                    err = "الكود ده مش موجود، اتأكد منه"
                                } else {
                                    val now = System.currentTimeMillis()
                                    Repo.upsertMember(c, store.memberId, store.name, 0L, now, emptyList(), emptyList())
                                    store.groupCode = c
                                    store.joinedAt = now
                                    onDone()
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                err = "مقدرتش أتصل بالنت، جرّب تاني"
                            }
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text(if (busy) "ثواني..." else "انضم") }
        }
    }
}

// ───────────── 5) كود الدعوة ─────────────

@Composable
fun InviteScreen(store: Store, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    val code = store.groupCode
    Page {
        BrandMark()
        Text("المجموعة جاهزة", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "ابعت الكود ده لزمايلك. كل واحد يحمّل التطبيق ويسجّل ويكتب الكود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AppCard {
            Text(
                code,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { copyCode(ctx, code) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("انسخ الكود") }
            OutlinedButton(
                onClick = { shareCode(ctx, code) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("ابعته على واتساب أو غيره") }
        }
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("كمّل للوحة التحكم") }
    }
}

// ───────────── إذن قراءة الاستهلاك ─────────────

@Composable
fun AccessScreen() {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("خطوة أخيرة", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "عشان التطبيق يعرف استهلاكك من الواي فاي، فعّل \"مدير النت\" في الشاشة اللي هتفتح، وبعدين ارجع للتطبيق.",
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "التطبيق بيقرا حجم الاستهلاك بس. مبيشوفش مواقع ولا رسائل.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("افتح الإعدادات") }
    }
}
