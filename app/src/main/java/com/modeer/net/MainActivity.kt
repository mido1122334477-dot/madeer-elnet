package com.modeer.net

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

private fun fmt(v: Double): String = String.format(Locale("ar", "EG"), "%.1f", v)

/** يحوّل الأرقام العربية (٣٠٠) والفاصلة العربية لأرقام إنجليزي عشان نقدر نقراها. */
private fun latin(s: String): String = s.trim().map {
    when (it) {
        in '٠'..'٩' -> '0' + (it - '٠')
        '٫', '،', ',' -> '.'
        else -> it
    }
}.joinToString("")

private fun scheduleSync(ctx: Context) {
    val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(ctx)
        .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req)
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val scope = rememberCoroutineScope()
    val store = remember { Store(ctx) }
    var code by remember { mutableStateOf(store.groupCode) }
    var access by remember { mutableStateOf(UsageReader.hasAccess(ctx)) }

    // لما المستخدم يرجع من شاشة الإعدادات، نتأكد من الإذن تاني.
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) access = UsageReader.hasAccess(ctx)
        }
        activity.lifecycle.addObserver(obs)
        onDispose { activity.lifecycle.removeObserver(obs) }
    }

    when {
        code.isEmpty() -> LoginScreen(store) { code = store.groupCode }
        !access -> AccessScreen()
        else -> MainScreen(store) {
            val c = store.groupCode
            val id = store.memberId
            scope.launch { runCatching { Repo.removeMember(c, id) } }
            WorkManager.getInstance(ctx).cancelUniqueWork("sync")
            store.clear()
            code = ""
        }
    }
}

// ───────────── شاشة 1: الدخول ─────────────

@Composable
fun LoginScreen(store: Store, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var gname by remember { mutableStateOf("") }
    var gb by remember { mutableStateOf("") }
    var people by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    suspend fun finish(c: String, n: String) {
        // نكتب في القاعدة الأول، ولو فشل مانحفظش حاجة على الموبايل.
        Repo.upsertMember(c, store.memberId, n, 0L)
        store.name = n
        store.groupCode = c
        store.joinedAt = System.currentTimeMillis()
        onDone()
    }

    fun submit() {
        err = null
        val n = name.trim()
        if (n.isEmpty()) {
            err = "اكتب اسمك الأول"
            return
        }
        if (create) {
            val total = latin(gb).toDoubleOrNull()
            val cnt = latin(people).toIntOrNull()
            if (total == null || total <= 0) {
                err = "اكتب حجم الباقة بالجيجا"
                return
            }
            if (cnt == null || cnt !in 1..20) {
                err = "اكتب عدد الأشخاص (من 1 لـ 20)"
                return
            }
            busy = true
            scope.launch {
                try {
                    val c = Repo.createGroup(gname.trim().ifEmpty { "مجموعتي" }, total, cnt)
                    finish(c, n)
                } catch (e: Exception) {
                    err = "مقدرتش أتصل بالنت، جرّب تاني"
                }
                busy = false
            }
        } else {
            val c = code.trim().uppercase(Locale.ROOT)
            if (c.length < 4) {
                err = "اكتب كود الدعوة"
                return
            }
            busy = true
            scope.launch {
                try {
                    if (Repo.getGroup(c) == null) err = "الكود ده مش موجود، اتأكد منه" else finish(c, n)
                } catch (e: Exception) {
                    err = "مقدرتش أتصل بالنت، جرّب تاني"
                }
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("مدير النت", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("كل واحد يشوف فاضله كام من حصته في باقة النت.")
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسمك") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (create) {
            OutlinedTextField(
                value = gname,
                onValueChange = { gname = it },
                label = { Text("اسم المجموعة (مثلاً: سكن الطلبة)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = gb,
                onValueChange = { gb = it },
                label = { Text("حجم الباقة بالجيجا") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = people,
                onValueChange = { people = it },
                label = { Text("عدد الأشخاص") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("كود الدعوة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { submit() },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (busy) "ثواني..." else if (create) "إنشاء المجموعة" else "انضم")
        }
        TextButton(onClick = { create = !create; err = null }, modifier = Modifier.fillMaxWidth()) {
            Text(if (create) "عندي كود دعوة" else "أنشئ مجموعة جديدة")
        }
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
        Spacer(Modifier.height(12.dp))
        Text("عشان التطبيق يعرف استهلاكك من الواي فاي، فعّل \"مدير النت\" في الشاشة اللي هتفتح، وبعدين ارجع للتطبيق.")
        Spacer(Modifier.height(8.dp))
        Text(
            "التطبيق بيقرا حجم الاستهلاك بس. مبيشوفش مواقع ولا رسائل.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("افتح الإعدادات") }
    }
}

// ───────────── الشاشتين الرئيسيتين ─────────────

@Composable
fun MainScreen(store: Store, onLeave: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<Member>>(emptyList()) }
    var offline by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var askLeave by remember { mutableStateOf(false) }
    var askRenew by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        scheduleSync(ctx)
    }

    // تحديث كل دقيقة طول ما التطبيق مفتوح.
    LaunchedEffect(tick) {
        while (true) {
            try {
                withContext(Dispatchers.IO) { Sync.run(ctx) }
                group = Repo.getGroup(store.groupCode)
                members = Repo.getMembers(store.groupCode)
                offline = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offline = true
            }
            delay(60_000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = {}, label = { Text("شاشتي") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = {}, label = { Text("المجموعة") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val g = group
            if (g == null) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (offline) Text("مفيش اتصال بالنت") else CircularProgressIndicator()
                }
            } else if (tab == 0) {
                val me = members.find { it.id == store.memberId }
                MineTab(g, (me?.usedBytes ?: 0L) / Sync.GB, store.name, offline)
            } else {
                GroupTab(
                    g = g,
                    members = members,
                    myId = store.memberId,
                    offline = offline,
                    onRenew = { askRenew = true },
                    onLeave = { askLeave = true },
                )
            }
        }
    }

    if (askRenew) {
        AlertDialog(
            onDismissRequest = { askRenew = false },
            title = { Text("بداية باقة جديدة؟") },
            text = { Text("كل العدادات هتبدأ من الصفر عند كل الأعضاء.") },
            confirmButton = {
                TextButton(onClick = {
                    askRenew = false
                    scope.launch {
                        runCatching { Repo.renewCycle(store.groupCode) }
                        tick++
                    }
                }) { Text("آه، جدّد") }
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

@Composable
fun MineTab(g: Group, usedGb: Double, name: String, offline: Boolean) {
    val quota = g.totalGb / g.people
    val left = maxOf(0.0, quota - usedGb)
    val frac = if (quota > 0) (usedGb / quota).toFloat().coerceIn(0f, 1f) else 0f
    val barColor = if (frac >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("أهلاً $name", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(g.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(fmt(left), fontSize = 72.sp, fontWeight = FontWeight.Bold)
        Text("جيجا متبقية من حصتك (${fmt(quota)} جيجا)")
        LinearProgressIndicator(
            progress = { frac },
            color = barColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        Text("استهلكت ${fmt(usedGb)} جيجا (${(frac * 100).toInt()}%)")
        if (quota > 0 && usedGb >= quota) {
            Text("حصتك خلصت.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "الأرقام بتتحدّث كل دقيقة وإنت فاتح التطبيق، وكل 15 دقيقة تقريباً في الخلفية.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (offline) Text("مفيش اتصال بالنت دلوقتي، بحاول تاني.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun GroupTab(
    g: Group,
    members: List<Member>,
    myId: String,
    offline: Boolean,
    onRenew: () -> Unit,
    onLeave: () -> Unit,
) {
    val ctx = LocalContext.current
    val quota = g.totalGb / g.people
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(g.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("انضم ${members.size} من ${g.people}، لكل واحد ${fmt(quota)} جيجا.")

        for (m in members.sortedByDescending { it.usedBytes }) {
            // لو الشخص لسه مزامنش بعد "بداية باقة جديدة"، رقمه لسه قديم فنعتبره صفر.
            val used = if (m.updatedAt < g.cycleStart) 0.0 else m.usedBytes / Sync.GB
            val left = maxOf(0.0, quota - used)
            val frac = if (quota > 0) (used / quota).toFloat().coerceIn(0f, 1f) else 0f
            val barColor = if (frac >= 0.8f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(m.name + if (m.id == myId) " (أنت)" else "", fontWeight = FontWeight.Bold)
                    Text("فاضل ${fmt(left)} جيجا")
                }
                LinearProgressIndicator(
                    progress = { frac },
                    color = barColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("كود المجموعة: ${g.code}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "انضم لمجموعة النت بتاعتنا في تطبيق مدير النت بالكود: ${g.code}")
                }
                ctx.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("ابعت الكود لزمايلك") }
        OutlinedButton(onClick = onRenew, modifier = Modifier.fillMaxWidth()) { Text("بداية باقة جديدة") }
        TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("اخرج من المجموعة") }
        if (offline) Text("مفيش اتصال بالنت دلوقتي، بحاول تاني.", color = MaterialTheme.colorScheme.error)
    }
}
