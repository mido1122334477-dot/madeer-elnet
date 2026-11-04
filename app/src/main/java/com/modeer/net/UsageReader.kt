package com.modeer.net

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** بيقرا استهلاك الواي فاي للموبايل من نظام أندرويد نفسه، من غير VPN. */
object UsageReader {

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName,
        )
        // بعض الموبايلات بترجّع MODE_DEFAULT حتى بعد ما المستخدم يفعّل الإذن.
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            ctx.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** إجمالي البايتات (تنزيل + رفع) على الواي فاي بين وقتين. */
    fun wifiBytes(ctx: Context, from: Long, to: Long): Long {
        if (to <= from) return 0L
        return try {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, from, to)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            0L
        }
    }

    /** زي wifiBytes، بس بيرجّع null لو القراءة فشلت (عشان منحسبش فرق غلط). */
    fun wifiBytesOrNull(ctx: Context, from: Long, to: Long): Long? {
        if (to <= from) return 0L
        return try {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_WIFI, null, from, to)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            null
        }
    }

    /** استهلاك آخر [days] يوم: العنصر i = قبل i يوم (0 = النهارده). */
    fun dailyBytes(ctx: Context, days: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        return (0 until days).map { i ->
            val start = today.minusDays(i.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.minusDays(i.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli()
            wifiBytes(ctx, start, minOf(end, now))
        }
    }

    /** استهلاك آخر [months] شهر: العنصر i = قبل i شهر (0 = الشهر الحالي). */
    fun monthlyBytes(ctx: Context, months: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val ym = YearMonth.now(zone)
        return (0 until months).map { i ->
            val start = ym.minusMonths(i.toLong()).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = ym.minusMonths(i.toLong() - 1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            wifiBytes(ctx, start, minOf(end, now))
        }
    }
}
