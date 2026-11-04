package com.modeer.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

object Sync {
    /** الجيجا = 1024^3 بايت. لو شركة النت بتحسب غير كده، غيّر الرقم ده. */
    const val GB = 1_073_741_824.0

    private const val HISTORY_DAYS = 30
    private const val HISTORY_MONTHS = 6
    private const val HISTORY_EVERY_MS = 30 * 60_000L

    private var daily: List<Long> = emptyList()
    private var monthly: List<Long> = emptyList()
    private var historyAt = 0L

    /** يقرا استهلاكي، يبعته للمجموعة، ويبعت التنبيهات لو حد وصل لمستوى. */
    suspend fun run(ctx: Context, force: Boolean = false): Boolean {
        val st = Store(ctx)
        val code = st.groupCode
        if (code.isEmpty() || !UsageReader.hasAccess(ctx)) return false

        val g = Repo.getGroup(code) ?: return false
        val now = System.currentTimeMillis()
        val cs = g.cycleStart(now)
        val used = UsageReader.wifiBytes(ctx, maxOf(cs, st.joinedAt), now)

        // السجل التاريخي بيتحسب كل نص ساعة بس، عشان ميتقلش على الموبايل.
        if (force || daily.isEmpty() || now - historyAt > HISTORY_EVERY_MS) {
            daily = UsageReader.dailyBytes(ctx, HISTORY_DAYS)
            monthly = UsageReader.monthlyBytes(ctx, HISTORY_MONTHS)
            historyAt = now
        }

        Repo.upsertMember(code, st.memberId, st.name, used, st.joinedAt, daily, monthly, cs)
        checkAlerts(ctx, st, g)
        return true
    }

    private suspend fun checkAlerts(ctx: Context, st: Store, g: Group) {
        val owner = g.ownerId == st.memberId
        val cs = g.cycleStart()
        for (m in Repo.getMembers(g.code)) {
            val me = m.id == st.memberId
            // كل واحد بيوصله تنبيه نفسه. صاحب المجموعة بيوصله تنبيه الكل.
            if (!me && !owner) continue
            val level = g.level(m)
            if (level == 0) continue

            val key = "${g.code}_${cs}_${m.id}_$level"
            if (st.wasNotified(key)) continue

            val title = when {
                level == 100 && me -> "خلصت حصتك"
                level == 100 -> "${m.name} خلّص حصته"
                me -> "وصلت $level% من حصتك"
                else -> "${m.name} وصل $level% من حصته"
            }
            val text = if (level == 100) {
                "الحصة خلصت لحد ما الباقة تتجدد."
            } else {
                "خلي بالك من الاستهلاك لآخر الباقة."
            }
            // نعلّم التنبيه كمُرسل بس لو اتبعت فعلاً (لو الإذن مرفوض نحاول بعدين).
            if (Notify.show(ctx, key.hashCode(), title, text)) st.markNotified(key)
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
