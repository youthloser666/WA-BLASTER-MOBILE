package com.example.wablaster.sender

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.wablaster.service.WaAccessibilityService
import com.example.wablaster.util.PackageUtils
import kotlinx.coroutines.delay

class AccessibilitySender(
    private val context: Context,
    private val targetPackage: String = "com.whatsapp",
    private val targetClass: String? = null,
    private val isClone: Boolean = false
) : MessageSender {

    override suspend fun send(phone: String, message: String): SendResult {
        val realPackage = targetPackage.substringBefore(":")
        val targetIsClone = isClone || targetPackage.endsWith(":clone")

        // 1. Periksa apakah WhatsApp terinstal
        if (!PackageUtils.isPackageInstalled(context, realPackage)) {
            return SendResult.Failed(
                FailReason.WA_NOT_INSTALLED,
                "Aplikasi WhatsApp ($realPackage) tidak ditemukan di perangkat."
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
            setPackage(realPackage)
            if (!targetClass.isNullOrBlank()) {
                setClassName(realPackage, targetClass)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            return SendResult.Failed(FailReason.UNKNOWN, "Gagal meluncurkan intent: ${e.message}")
        }

        // Berikan waktu jeda agar WhatsApp atau chooser membuka jendela percakapan ke foreground
        delay(1000)

        // 4. Jalankan alur deteksi, klik, dan verifikasi di AccessibilityService
        return service.executeSendFlow(realPackage, isClone = targetIsClone, timeoutMs = 15000)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return PackageUtils.isPackageInstalled(context, packageName)
    }
}
