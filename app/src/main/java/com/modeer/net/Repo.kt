package com.modeer.net

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class Group(
    val code: String,
    val name: String,
    val totalGb: Double,
    val people: Int,
    val cycleStart: Long,
)

data class Member(
    val id: String,
    val name: String,
    val usedBytes: Long,
    val updatedAt: Long,
)

/**
 * الاتصال بـ Firestore.
 * groups/{code}                  -> اسم المجموعة، حجم الباقة، عدد الأشخاص، بداية الباقة
 * groups/{code}/members/{id}     -> اسم الشخص، الاستهلاك بالبايت، وقت آخر تحديث
 */
object Repo {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    private fun groupRef(code: String) = db.collection("groups").document(code)

    private fun newCode(): String =
        (1..6).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

    suspend fun createGroup(name: String, totalGb: Double, people: Int): String {
        repeat(10) {
            val code = newCode()
            val ref = groupRef(code)
            if (!ref.get().await().exists()) {
                ref.set(
                    mapOf(
                        "name" to name,
                        "totalGb" to totalGb,
                        "people" to people,
                        "cycleStart" to System.currentTimeMillis(),
                    )
                ).await()
                return code
            }
        }
        error("couldn't create a unique code")
    }

    suspend fun getGroup(code: String): Group? {
        val d = groupRef(code).get().await()
        if (!d.exists()) return null
        return Group(
            code = code,
            name = d.getString("name") ?: "",
            totalGb = d.getDouble("totalGb") ?: 0.0,
            people = (d.getLong("people") ?: 1L).toInt().coerceAtLeast(1),
            cycleStart = d.getLong("cycleStart") ?: 0L,
        )
    }

    suspend fun getMembers(code: String): List<Member> {
        val snap = groupRef(code).collection("members").get().await()
        return snap.documents.map {
            Member(
                id = it.id,
                name = it.getString("name") ?: "؟",
                usedBytes = it.getLong("usedBytes") ?: 0L,
                updatedAt = it.getLong("updatedAt") ?: 0L,
            )
        }
    }

    suspend fun upsertMember(code: String, id: String, name: String, usedBytes: Long) {
        groupRef(code).collection("members").document(id).set(
            mapOf(
                "name" to name,
                "usedBytes" to usedBytes,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    suspend fun removeMember(code: String, id: String) {
        groupRef(code).collection("members").document(id).delete().await()
    }

    /** بداية باقة جديدة: كل الأجهزة هتحسب الاستهلاك من اللحظة دي. */
    suspend fun renewCycle(code: String) {
        groupRef(code).update("cycleStart", System.currentTimeMillis()).await()
    }
}
