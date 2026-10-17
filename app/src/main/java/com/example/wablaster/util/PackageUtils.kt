package com.example.wablaster.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.provider.Settings

data class WhatsAppPackageInfo(
    val packageName: String,
    val className: String? = null,
    val label: String,
    val isClone: Boolean = false,
    val appId: String = if (isClone) "$packageName:clone" else packageName
) {
    val activityClassName: String? get() = className
}

object PackageUtils {

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val basePackage = packageName.substringBefore(":")
        return try {
            context.packageManager.getPackageInfo(basePackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Memeriksa apakah perangkat memiliki dukungan / ruang profil aplikasi ganda (Dual Apps / App Clone).
     * Mendukung deteksi profil Android Multi-User, Xiaomi XSpace, Vivo App Clone, Samsung Dual Messenger, dan Oppo/Realme.
     */
    fun isCloneFeatureAvailable(context: Context): Boolean {
        // 1. Cek profil pengguna ganda Android standar (Multi-User / Work / Managed / Clone profile)
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (userManager != null && userManager.userProfiles.size > 1) {
                return true
            }
        } catch (_: Exception) {}

        // 2. Cek fitur Xiaomi / POCO / Redmi (MIUI / HyperOS XSpace)
        try {
            val xspace = Settings.Secure.getInt(context.contentResolver, "xspace_enabled", 0)
            if (xspace == 1) return true
        } catch (_: Exception) {}

        // 3. Cek paket clone Vivo (FuntouchOS / OriginOS)
        try {
            val pm = context.packageManager
            val hasVivo = try { pm.getPackageInfo("com.vivo.doubleinstance", 0) != null } catch (_: Exception) { false }
            val hasBbk = try { pm.getPackageInfo("com.bbk.appclone", 0) != null } catch (_: Exception) { false }
            if (hasVivo || hasBbk) return true
        } catch (_: Exception) {}

        // 4. Cek Samsung Dual Messenger
        try {
            val pm = context.packageManager
            val hasSamsung = try { pm.getPackageInfo("com.samsung.android.da.daagent", 0) != null } catch (_: Exception) { false }
            if (hasSamsung) return true
        } catch (_: Exception) {}

        // 5. Cek Oppo / Realme / OnePlus App Cloner
        try {
            val pm = context.packageManager
            val hasOppo = try { pm.getPackageInfo("com.coloros.oppomultiapp", 0) != null } catch (_: Exception) { false }
            if (hasOppo) return true
        } catch (_: Exception) {}

        // 6. Vendor OEM fallback (Vivo, Xiaomi, Oppo, Realme, Samsung, Transsion/Infinix)
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("vivo") || m.contains("iqoo") ||
                m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
                m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ||
                m.contains("samsung") || m.contains("infinix") || m.contains("tecno")
    }

    /**
     * Mendeteksi seluruh varian WhatsApp yang terpasang di perangkat:
     * - WhatsApp Resmi (com.whatsapp)
     * - WhatsApp Clone / Dual Apps (II·WhatsApp / Dual Apps)
     * - WhatsApp Business (com.whatsapp.w4b)
     * - WhatsApp Business Clone (II·WA Business)
     * - WhatsApp Mod / Third-party jika ada
     */
    fun getInstalledWhatsAppApps(context: Context): List<WhatsAppPackageInfo> {
        val pm = context.packageManager
        val list = mutableListOf<WhatsAppPackageInfo>()
        val cloneAvailable = isCloneFeatureAvailable(context)

        val manufacturer = Build.MANUFACTURER.lowercase()
        val isVivo = manufacturer.contains("vivo") || manufacturer.contains("iqoo")
        val isXiaomi = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")

        val clonePrefix = if (isVivo) "II·" else ""
        val cloneSuffix = when {
            isVivo -> " (Clone)"
            isXiaomi -> " (Dual)"
            else -> " (Clone)"
        }

        // 1. Cek WhatsApp Resmi (com.whatsapp)
        if (isPackageInstalled(context, "com.whatsapp")) {
            list.add(WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp", isClone = false))
            if (cloneAvailable) {
                val cloneLabel = if (isVivo) "II·WhatsApp" else "WhatsApp$cloneSuffix"
                list.add(WhatsAppPackageInfo("com.whatsapp", null, cloneLabel, isClone = true))
            }
        }

        // 2. Cek WhatsApp Business (com.whatsapp.w4b)
        if (isPackageInstalled(context, "com.whatsapp.w4b")) {
            list.add(WhatsAppPackageInfo("com.whatsapp.w4b", null, "WhatsApp Business", isClone = false))
            if (cloneAvailable) {
                val cloneLabel = if (isVivo) "II·WA Business" else "WA Business$cloneSuffix"
                list.add(WhatsAppPackageInfo("com.whatsapp.w4b", null, cloneLabel, isClone = true))
            }
        }

        // 3. Deteksi aplikasi kustom yang menangani intent URL https://api.whatsapp.com
        val sendIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=0"))
        try {
            val resolved = pm.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolved) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                val label = info.loadLabel(pm).toString()
                if (pkg != "com.whatsapp" && pkg != "com.whatsapp.w4b") {
                    val isClone = label.contains("clone", ignoreCase = true) ||
                            label.contains("dual", ignoreCase = true) ||
                            label.contains("ganda", ignoreCase = true) ||
                            label.startsWith("II·")
                    list.add(WhatsAppPackageInfo(pkg, cls, label, isClone))
                }
            }
        } catch (_: Exception) {}

        // 4. Deteksi paket-paket WhatsApp mod umum jika ada
        val candidatePackages = listOf(
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
                    list.add(WhatsAppPackageInfo(pkg, null, label, isClone = true))
                } catch (_: Exception) {}
            }
        }

        // 5. Fallback default jika belum ada yang terdeteksi
        if (list.isEmpty()) {
            list.add(WhatsAppPackageInfo("com.whatsapp", null, "WhatsApp", isClone = false))
        }

        return list.distinctBy { it.appId }
    }
}
