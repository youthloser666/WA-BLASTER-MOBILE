package com.example.wablaster.sender

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.wablaster.service.WaAccessibilityService
import kotlinx.coroutines.delay

class AccessibilitySender(
    private val context: Context,
    private val targetPackage: String = "com.whatsapp",
    private val targetClass: String? = null
) : MessageSender {

    override suspend fun send(phone: String, message: String): SendResult {
        // 1. Periksa apakah WhatsApp terinstal
        if (!isPackageInstalled(context, targetPackage)) {
            return SendResult.Failed(
                FailReason.WA_NOT_INSTALLED,
                "Aplikasi WhatsApp ($targetPackage) tidak ditemukan di perangkat."
            )
        }

        // 2. Periksa apakah AccessibilityService aktif
        val service = WaAccessibilityService.getInstance()
        if (service == null || !WaAccessibilityService.isServiceActive.value) {
            return SendResult.Failed(
                FailReason.SERVICE_NOT_ENABLED,
                "Layanan Aksesibilitas WA Blaster belum diaktifkan oleh pengguna."
            )
        }

        // 3. Bangun Intent untuk membuka chat secara langsung
        // Gunakan endpoint resmi api.whatsapp.com yang terdaftar langsung di intent-filter WhatsApp
        val encodedMessage = Uri.encode(message)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMessage")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(targetPackage)
            if (!targetClass.isNullOrBlank()) {
                setClassName(targetPackage, targetClass)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            return SendResult.Failed(FailReason.UNKNOWN, "Gagal meluncurkan intent: ${e.message}")
        }

        // Berikan waktu jeda agar WhatsApp membuka jendela percakapan ke foreground
        delay(1000)

        // 4. Jalankan alur deteksi, klik, dan verifikasi di AccessibilityService
        return service.executeSendFlow(targetPackage, timeoutMs = 15000)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
