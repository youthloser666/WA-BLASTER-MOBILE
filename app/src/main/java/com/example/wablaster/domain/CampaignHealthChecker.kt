package com.example.wablaster.domain

data class CampaignHealthStatus(
    val isAccessibilityActive: Boolean,
    val isWhatsAppInstalled: Boolean,
    val hasValidVariants: Boolean,
    val hasValidRecipients: Boolean,
    val sendMode: String = "ACCESSIBILITY"
) {
    val isHealthy: Boolean
        get() {
            val accessibilityOk = if (sendMode == "ACCESSIBILITY") isAccessibilityActive else true
            return accessibilityOk && isWhatsAppInstalled && hasValidVariants && hasValidRecipients
        }

    val unmetReasons: List<String>
        get() {
            val list = mutableListOf<String>()
            if (sendMode == "ACCESSIBILITY" && !isAccessibilityActive) {
                list.add("Layanan Aksesibilitas WA Blaster belum aktif.")
            }
            if (!isWhatsAppInstalled) {
                list.add("Aplikasi WhatsApp target belum terpasang di perangkat ini.")
            }
            if (!hasValidVariants) {
                list.add("Belum ada pesan yang diisi. Isi minimal 1 varian pesan di tab 'Pesan & Jeda'.")
            }
            if (!hasValidRecipients) {
                list.add("Tidak ada nomor kontak aktif untuk dikirim. Masukkan nomor di tab 'Nomor'.")
            }
            return list
        }
}
