package com.example.wablaster.sender

fun FailReason.toUserFriendlyDescription(): String = when (this) {
    FailReason.INVALID_NUMBER -> "Nomor telepon tidak valid atau bukan format WhatsApp."
    FailReason.WA_NOT_INSTALLED -> "Aplikasi WhatsApp target tidak terpasang di perangkat."
    FailReason.SEND_BUTTON_NOT_FOUND -> "Tombol kirim tidak ditemukan. Kemungkinan tampilan WhatsApp berubah atau chat tidak terbuka."
    FailReason.TIMEOUT -> "Batas waktu pengiriman habis (WhatsApp lambat merespons)."
    FailReason.SERVICE_NOT_ENABLED -> "Layanan Aksesibilitas WA Blaster belum diaktifkan."
    FailReason.WA_BANNED -> "Akun WhatsApp terdeteksi DIBLOKIR / BANNED oleh WhatsApp."
    FailReason.UNKNOWN -> "Terjadi kesalahan saat meluncurkan WhatsApp."
}

object FailReasonHelper {
    fun formatErrorMessage(rawError: String?): String {
        if (rawError.isNullOrBlank()) return "-"
        for (reason in FailReason.entries) {
            if (rawError.startsWith(reason.name)) {
                val detail = rawError.removePrefix(reason.name).removePrefix(":").trim()
                val friendly = reason.toUserFriendlyDescription()
                return if (detail.isNotBlank() && detail != "-") {
                    "$friendly ($detail)"
                } else {
                    friendly
                }
            }
        }
        return rawError
    }
}
