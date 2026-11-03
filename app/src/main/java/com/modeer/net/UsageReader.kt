package com.modeer.net

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process

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

    /** إجمالي البايتات (تنزيل + رفع) على الواي فاي من الوقت المحدد لحد دلوقتي. */
    fun wifiBytesSince(ctx: Context, since: Long): Long {
        return try {
            val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val b = nsm.querySummaryForDevice(
                ConnectivityManager.TYPE_WIFI,
                null,
                since,
                System.currentTimeMillis(),
            )
            b.rxBytes + b.txBytes
        } catch (e: SecurityException) {
            0L
        }
    }
}
