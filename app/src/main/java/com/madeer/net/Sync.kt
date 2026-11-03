package com.madeer.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

object Sync {
    /** الجيجا = 1024^3 بايت. لو شركة النت بتحسب غير كده، غيّر الرقم ده. */
    const val GB = 1_073_741_824.0

    /** يقرا استهلاكي، يبعته للمجموعة، ويبعت التنبيهات لو حد وصل 80% أو 100%. */
    suspend fun run(ctx: Context): Boolean {
        val st = Store(ctx)
        val code = st.groupCode
        if (code.isEmpty() || !UsageReader.hasAccess(ctx)) return false

        val g = Repo.getGroup(code) ?: return false
        val since = maxOf(g.cycleStart, st.joinedAt)
        val used = UsageReader.wifiBytesSince(ctx, since)
        Repo.upsertMember(code, st.memberId, st.name, used)
        checkAlerts(ctx, st, g)
        return true
    }

    private suspend fun checkAlerts(ctx: Context, st: Store, g: Group) {
        val quota = g.totalGb / g.people * GB
        if (quota <= 0) return
        // بعد "بداية باقة جديدة" أرقام الناس اللي لسه مزامنوش لسه من الباقة القديمة، فنتجاهلها.
        for (m in Repo.getMembers(g.code).filter { it.updatedAt >= g.cycleStart }) {
            val pct = m.usedBytes / quota * 100
            val me = m.id == st.memberId
            for (level in listOf(100, 80)) {
                if (pct < level) continue
                val key = "${g.code}_${g.cycleStart}_${m.id}_$level"
                if (!st.wasNotified(key)) {
                    val title = when {
                        level == 100 && me -> "خلصت حصتك"
                        level == 100 -> "${m.name} خلّص حصته"
                        me -> "وصلت 80% من حصتك"
                        else -> "${m.name} وصل 80% من حصته"
                    }
                    val text = if (level == 100) {
                        "الحصة خلصت لحد ما الباقة تتجدد."
                    } else {
                        "خلي بالك من الاستهلاك لآخر الشهر."
                    }
                    // نعلّم التنبيه كمُرسل بس لو اتبعت فعلاً (لو الإذن مرفوض نحاول بعدين).
                    if (Notify.show(ctx, key.hashCode(), title, text)) st.markNotified(key)
                }
                break
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
