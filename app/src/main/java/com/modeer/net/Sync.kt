package com.modeer.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object Sync {
    /** الجيجا = 1024^3 بايت. لو شركة النت بتحسب غير كده، غيّر الرقم ده. */
    const val GB = 1_073_741_824.0

    private const val DAY = 86_400_000L

    /** يسجّل استهلاكي على شبكة السكن، يبعته للمجموعة، ويبعت التنبيهات. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun run(ctx: Context, force: Boolean = false): Boolean {
        val st = Store(ctx)
        val code = st.groupCode
        if (code.isEmpty() || !UsageReader.hasAccess(ctx)) return false

        val now = System.currentTimeMillis()
        // التسجيل المحلي الأول، عشان الاستهلاك يتحسب حتى لو مفيش نت للمزامنة.
        Ledger.sample(ctx, NetId.isHome(NetId.current(ctx), st.homeSsid, st.homeGw), now)

        val g = Repo.getGroup(code) ?: return false
        st.homeSsid = g.ssid
        st.homeGw = g.gw

        val cs = g.cycleStart(now)
        val used = Ledger.sinceBytes(ctx, maxOf(cs, st.joinedAt))
        Repo.upsertMember(
            code, st.memberId, st.name, used, st.joinedAt,
            Ledger.daily(ctx, 30), Ledger.monthly(ctx, 6), cs, Ledger.peakHours(ctx, 30),
        )
        checkAlerts(ctx, st, g)
        return true
    }

    private fun send(ctx: Context, st: Store, key: String, title: String, text: String) {
        if (st.wasNotified(key)) return
        // نعلّم التنبيه كمُرسل بس لو اتبعت فعلاً (لو الإذن مرفوض نحاول بعدين).
        if (Notify.show(ctx, key.hashCode(), title, text)) st.markNotified(key)
    }

    private suspend fun checkAlerts(ctx: Context, st: Store, g: Group) {
        val owner = g.ownerId == st.memberId
        val now = System.currentTimeMillis()
        val cs = g.cycleStart(now)
        val next = Cycle.nextBoundary(g.cycleDay, now)
        val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        for (m in Repo.getMembers(g.code)) {
            val me = m.id == st.memberId
            // كل واحد بيوصله تنبيه نفسه. صاحب المجموعة بيوصله تنبيه الكل.
            if (!me && !owner) continue
            val who = if (me) null else m.name

            // 1) وصل 50% / 80% / 100%
            val level = g.level(m)
            if (level > 0) {
                val title = when {
                    level == 100 && me -> "خلصت حصتك"
                    level == 100 -> "${m.name} خلّص حصته"
                    me -> "وصلت $level% من حصتك"
                    else -> "${m.name} وصل $level% من حصته"
                }
                val text = if (level == 100) "الحصة خلصت لحد ما الباقة تتجدد." else "خلي بالك من الاستهلاك لآخر الباقة."
                send(ctx, st, "${g.code}_${cs}_${m.id}_$level", title, text)
            }

            // 2) إنذار مبكر: هتخلص قبل التجديد
            val fc = Forecasts.calc(g.usedGb(m), g.quotaGbFor(m), maxOf(cs, m.joinedAt), next, now)
            if (fc.ready && fc.runsOut && !fc.finished && fc.elapsedDays >= 1.0) {
                val title = if (me) "حصتك هتخلص بدري" else "حصة ${m.name} هتخلص بدري"
                val text = (Forecasts.warnLine(fc, who) ?: "") +
                    " حاول متزيدش عن ${fmt(fc.safePerDay)} جيجا في اليوم."
                send(ctx, st, "${g.code}_${cs}_${m.id}_early", title, text)
            }

            // 3) استهلاك غير طبيعي النهارده
            val fullDays = maxOf(0, ((todayStart - m.joinedAt) / DAY).toInt())
            val prev = m.daily.drop(1).take(minOf(14, fullDays)).map { it / GB }
            if (prev.size >= 3) {
                val usual = prev.sorted()[prev.size / 2]
                val today = m.daily.getOrElse(0) { 0L } / GB
                if (today >= 2.0 && today >= usual * 2.5 && today >= usual + 2.0) {
                    val day = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ar"))
                    val ph = m.peakHours.getOrElse(0) { -1L }.toInt()
                    val peak = if (ph >= 0) " أكبر استهلاك كان ${Jokes.hourLabel(ph)}." else ""
                    val title = if (me) "استهلاكك النهارده مش طبيعي" else "استهلاك ${m.name} النهارده مش طبيعي"
                    val text = (if (me) "استهلكت " else "${m.name} استهلك ") +
                        "${fmt(today)} جيجا النهارده ($day) والمعتاد حوالي ${fmt(usual)}.$peak"
                    send(ctx, st, "${g.code}_anom_${m.id}_$todayStart", title, text)
                }
            }
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            Sync.run(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
