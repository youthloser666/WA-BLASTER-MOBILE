package com.example.wablaster.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class WhatsAppPackageInfo(
    val packageName: String,
    val className: String? = null,
    val label: String,
    val isClone: Boolean = false
) {
    val activityClassName: String? get() = className
}

object PackageUtils {

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Mendeteksi seluruh varian WhatsApp yang terpasang di perangkat:
     * - WhatsApp Resmi (com.whatsapp)
     * - WhatsApp Business (com.whatsapp.w4b)
     * - WhatsApp Clone / Dual Apps (Samsung Dual Messenger, Xiaomi Dual Apps, GBWhatsApp, dsb.)
     */
    fun getInstalledWhatsAppApps(context: Context): List<WhatsAppPackageInfo> {
        val pm = context.packageManager
        val list = mutableListOf<WhatsAppPackageInfo>()

        // 1. Deteksi aplikasi yang menangani intent URL https://api.whatsapp.com
        val sendIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=0"))
        try {
            val resolved = pm.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                val label = info.loadLabel(pm).toString()
                val isClone = pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b" ||
                        label.contains("clone", ignoreCase = true) ||
                        label.contains("dual", ignoreCase = true) ||
                        label.contains("ganda", ignoreCase = true)
                list.add(WhatsAppPackageInfo(pkg, cls, label, isClone))
            }
        } catch (ignored: Exception) {}

        // 2. Deteksi paket-paket WhatsApp umum jika belum terdeteksi dari intent resolver
        val candidatePackages = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.whatsapp.clone",
            "com.gbwhatsapp",
            "com.fmwhatsapp",
            "com.yowhatsapp"
        )
        for (pkg in candidatePackages) {
            if (list.none { it.packageName == pkg }) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val isClone = pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b" ||
                            label.contains("clone", ignoreCase = true) ||
                            label.contains("dual", ignoreCase = true)
                    list.add(WhatsAppPackageInfo(pkg, null, label, isClone))
                } catch (ignored: Exception) {}
            }
        }

        // 3. Fallback default jika belum ada yang terdeteksi
        if (list.isEmpty()) {
            list.add(WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp", false))
        }

        return list.distinctBy { it.packageName }
    }
}
