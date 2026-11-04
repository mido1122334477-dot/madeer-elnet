package com.modeer.net

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * الاتصال بـ Firestore.
 * groups/{code}                 -> بيانات المجموعة وإعداداتها
 * groups/{code}/members/{id}    -> اسم العضو واستهلاكه وسجله
 */
object Repo {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun ref(code: String) = db.collection("groups").document(code)

    private fun newCode(): String =
        (1..6).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

    private fun longs(v: Any?): List<Long> =
        (v as? List<*>)?.map { (it as? Number)?.toLong() ?: 0L } ?: emptyList()

    suspend fun createGroup(name: String, totalGb: Double, people: Int, ownerId: String, cycleDay: Int, ssid: String = "", gw: String = ""): String {
        repeat(10) {
            val code = newCode()
            val r = ref(code)
            if (!r.get().await().exists()) {
                r.set(
                    mapOf(
                        "name" to name,
                        "ownerId" to ownerId,
                        "totalGb" to totalGb,
                        "people" to people,
                        "mode" to "equal",
                        "cycleDay" to cycleDay,
                        "cycleReset" to System.currentTimeMillis(),
                        "a50" to true,
                        "a80" to true,
                        "a100" to true,
                        "ssid" to ssid,
                        "gw" to gw,
                        "anon" to true,
                    )
                ).await()
                return code
            }
        }
        error("couldn't create a unique code")
    }

    suspend fun getGroup(code: String): Group? {
        val d = ref(code).get().await()
        if (!d.exists()) return null
        return Group(
            code = code,
            name = d.getString("name") ?: "",
            ownerId = d.getString("ownerId") ?: "",
            totalGb = d.getDouble("totalGb") ?: 0.0,
            people = (d.getLong("people") ?: 1L).toInt().coerceAtLeast(1),
            mode = d.getString("mode") ?: "equal",
            cycleDay = (d.getLong("cycleDay") ?: 1L).toInt().coerceIn(1, 28),
            cycleReset = d.getLong("cycleReset") ?: 0L,
            alerts = Alerts(
                d.getBoolean("a50") ?: true,
                d.getBoolean("a80") ?: true,
                d.getBoolean("a100") ?: true,
            ),
            ssid = d.getString("ssid") ?: "",
            gw = d.getString("gw") ?: "",
            anon = d.getBoolean("anon") ?: true,
        )
    }

    suspend fun getMembers(code: String): List<Member> {
        val snap = ref(code).collection("members").get().await()
        return snap.documents.map {
            Member(
                id = it.id,
                name = it.getString("name") ?: "؟",
                joinedAt = it.getLong("joinedAt") ?: 0L,
                usedBytes = it.getLong("usedBytes") ?: 0L,
                usedSince = it.getLong("usedSince") ?: 0L,
                quotaGb = it.getDouble("quotaGb"),
                daily = longs(it.get("daily")),
                monthly = longs(it.get("monthly")),
                peakHours = longs(it.get("peakHours")),
            )
        }.sortedBy { it.joinedAt }
    }

    /** يكتب بيانات العضو. الحصة المخصصة (quotaGb) مبتتلمسش لأن الكتابة بدمج. */
    suspend fun upsertMember(
        code: String,
        id: String,
        name: String,
        usedBytes: Long,
        joinedAt: Long,
        daily: List<Long>,
        monthly: List<Long>,
        usedSince: Long = 0L,
        peakHours: List<Long> = emptyList(),
    ) {
        ref(code).collection("members").document(id).set(
            mapOf(
                "name" to name,
                "usedBytes" to usedBytes,
                "usedSince" to usedSince,
                "joinedAt" to joinedAt,
                "daily" to daily,
                "monthly" to monthly,
                "peakHours" to peakHours,
                "updatedAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun setMemberQuota(code: String, id: String, quotaGb: Double) {
        ref(code).collection("members").document(id)
            .set(mapOf("quotaGb" to quotaGb), SetOptions.merge()).await()
    }

    suspend fun updateGroup(code: String, fields: Map<String, Any>) {
        ref(code).update(fields).await()
    }

    /** تصفير العدادات الآن: كل الأجهزة هتحسب من اللحظة دي. */
    suspend fun resetNow(code: String) {
        ref(code).update("cycleReset", System.currentTimeMillis()).await()
    }

    suspend fun removeMember(code: String, id: String) {
        ref(code).collection("members").document(id).delete().await()
    }
}
