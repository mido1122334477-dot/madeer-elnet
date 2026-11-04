package com.modeer.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

/** الشبكة المتصل بيها الموبايل دلوقتي. */
data class Net(val onWifi: Boolean, val ssid: String?, val gw: String?)

/**
 * هوية شبكة السكن: اسم الواي فاي (محتاج إذن الموقع، وأندرويد بيخبّيه في الخلفية)،
 * وعنوان البوابة (الراوتر) كبديل بيشتغل من غير أي إذن.
 */
object NetId {
    fun current(ctx: Context): Net {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return Net(false, null, null)
        val caps = cm.getNetworkCapabilities(n)
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Net(false, null, null)
        val gw = try {
            cm.getLinkProperties(n)?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
                ?.gateway?.hostAddress
        } catch (e: Exception) {
            null
        }
        return Net(true, ssidOf(ctx), gw)
    }

    @Suppress("DEPRECATION")
    private fun ssidOf(ctx: Context): String? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val raw = wm.connectionInfo?.ssid ?: return null
            val t = raw.trim('"')
            if (t.isBlank() || t == "<unknown ssid>") null else t
        } catch (e: Exception) {
            null
        }
    }

    /** هل الشبكة الحالية هي شبكة السكن؟ لو السكن لسه متحددش، أي واي فاي بيتحسب. */
    fun isHome(net: Net, homeSsid: String, homeGw: String): Boolean {
        if (!net.onWifi) return false
        if (homeSsid.isBlank() && homeGw.isBlank()) return true
        if (net.ssid != null && homeSsid.isNotBlank()) return net.ssid == homeSsid
        if (net.gw != null && homeGw.isNotBlank()) return net.gw == homeGw
        return false
    }
}
