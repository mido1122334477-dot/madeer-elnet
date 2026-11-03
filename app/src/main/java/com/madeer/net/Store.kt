package com.madeer.net

import android.content.Context
import java.util.UUID

/** تخزين محلي بسيط على الموبايل: مين أنا، وأنا في أنهي مجموعة. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("madeer", Context.MODE_PRIVATE)

    val memberId: String
        get() = sp.getString("memberId", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("memberId", it).apply()
        }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) {
            sp.edit().putString("name", v).apply()
        }

    var groupCode: String
        get() = sp.getString("groupCode", "") ?: ""
        set(v) {
            sp.edit().putString("groupCode", v).apply()
        }

    /** وقت الانضمام: الاستهلاك بيتحسب من بعده بس. */
    var joinedAt: Long
        get() = sp.getLong("joinedAt", 0L)
        set(v) {
            sp.edit().putLong("joinedAt", v).apply()
        }

    fun wasNotified(key: String): Boolean = sp.getBoolean("n_$key", false)

    fun markNotified(key: String) {
        sp.edit().putBoolean("n_$key", true).apply()
    }

    fun clear() {
        sp.edit().remove("name").remove("groupCode").remove("joinedAt").apply()
    }
}
