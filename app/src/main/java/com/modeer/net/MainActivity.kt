package com.modeer.net

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MadeerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App()
                }
            }
        }
    }
}

private enum class Step { Signup, Choose, Create, Join, Invite, Main }

@Composable
private fun App() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }

    fun initial(): Step = when {
        store.name.isBlank() -> Step.Signup
        store.groupCode.isBlank() -> Step.Choose
        else -> Step.Main
    }

    var step by remember { mutableStateOf(initial()) }
    var access by remember { mutableStateOf(UsageReader.hasAccess(ctx)) }

    // نعيد فحص إذن "الوصول للاستخدام" كل ما المستخدم يرجع من الإعدادات.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) access = UsageReader.hasAccess(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = {
            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 12 }) togetherWith fadeOut(tween(200))
        },
        label = "steps",
    ) { st ->
        when (st) {
            Step.Signup -> SignupScreen(store) { step = Step.Choose }
            Step.Choose -> ChooseScreen(store, onCreate = { step = Step.Create }, onJoin = { step = Step.Join })
            Step.Create -> CreateScreen(store, onBack = { step = Step.Choose }, onDone = { step = Step.Invite })
            Step.Join -> JoinScreen(store, onBack = { step = Step.Choose }, onDone = { step = Step.Main })
            Step.Invite -> InviteScreen(store) { step = Step.Main }
            Step.Main -> if (!access) {
                AccessScreen()
            } else {
                MainScreen(store, onLeft = { step = Step.Choose })
            }
        }
    }
}

@Composable
private fun MainScreen(store: Store, onLeft: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var snap by remember { mutableStateOf<Snap?>(null) }
    var offline by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    // مزامنة + تحميل كل 60 ثانية، وفورًا لما نطلب refresh.
    LaunchedEffect(tick) {
        var first = true
        while (true) {
            try {
                Sync.run(ctx, force = first)
                first = false
                val code = store.groupCode
                val g = Repo.getGroup(code)
                if (g == null) {
                    // المجموعة اتمسحت.
                    store.forgetGroup()
                    onLeft()
                    return@LaunchedEffect
                }
                snap = Snap(g, Repo.getMembers(code))
                offline = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                offline = true
            }
            delay(60_000)
        }
    }

    val s = snap
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val myId = store.memberId
    val isOwner = s.g.ownerId == myId
    val refresh: () -> Unit = { tick++ }
    val tabs = listOf("الرئيسية", "السجل", "اسأل", "الإعدادات")
    val icons = listOf(Icons.Filled.Home, Icons.Filled.DateRange, Icons.Filled.Info, Icons.Filled.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = t) },
                        label = { Text(t) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 5 * dir }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(300)) { -it / 5 * dir })
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeTab(s, myId, isOwner, store.name, offline, refresh)
                    1 -> HistoryTab(s, myId, isOwner)
                    2 -> AskTab(s, myId, isOwner)
                    else -> SettingsTab(s, refresh) {
                        scope.launch {
                            try {
                                Repo.removeMember(s.g.code, myId)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                            }
                            store.forgetGroup()
                            onLeft()
                        }
                    }
                }
            }
        }
    }
}
