package com.example.wablaster.sender

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.coroutines.delay

/**
 * MessageSender mode semi-otomatis / manual cadangan.
 * Membuka percakapan WhatsApp dengan pesan yang sudah terisi otomatis di kolom teks,
 * namun pengguna menekan tombol "Kirim" sendiri secara manual.
 */
class IntentSender(
    private val context: Context,
    private val targetPackage: String = "com.whatsapp",
    private val targetClass: String? = null
) : MessageSender {

    override suspend fun send(phone: String, message: String): SendResult {
        if (!isPackageInstalled(context, targetPackage)) {
            return SendResult.Failed(
                FailReason.WA_NOT_INSTALLED,
                "Aplikasi WhatsApp ($targetPackage) tidak ditemukan di perangkat."
            )
        }

        val encodedMessage = Uri.encode(message)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=$encodedMessage")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(targetPackage)
            if (!targetClass.isNullOrBlank()) {
                setClassName(targetPackage, targetClass)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            context.startActivity(intent)
            // Berikan waktu kepada pengguna untuk menekan tombol kirim sendiri
            delay(1500)
            SendResult.Success
        } catch (e: Exception) {
            SendResult.Failed(FailReason.UNKNOWN, "Gagal meluncurkan WhatsApp: ${e.message}")
        }
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
