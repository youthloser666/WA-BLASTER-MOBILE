package com.example.wablaster.sender

/**
 * File konfigurasi pemilih (selector) untuk elemen UI WhatsApp.
 * Semua id, teks, dan deskripsi diletakkan di sini agar mudah diperbarui
 * saat WhatsApp memperbarui UI atau resource ID-nya.
 */
object WaSelectors {
    // ID tombol kirim di WhatsApp dan WhatsApp Business
    val SEND_BUTTON_IDS = listOf(
        "com.whatsapp:id/send",
        "com.whatsapp.w4b:id/send",
        "send"
    )

    // Deskripsi aksesibilitas tombol kirim
    val SEND_BUTTON_DESCRIPTIONS = listOf(
        "Kirim",
        "Send",
        "Enviar",
        "Envoyer"
    )

    // ID kolom input pesan (conversation message entry)
    val ENTRY_BOX_IDS = listOf(
        "com.whatsapp:id/entry",
        "com.whatsapp.w4b:id/entry",
        "entry"
    )

    // Indikator pesan/dialog nomor tidak valid atau tidak terdaftar
    val INVALID_NUMBER_TEXTS = listOf(
        "tidak terdaftar di WhatsApp",
        "nomor telepon tidak valid",
        "bukan nomor WhatsApp",
        "isn't on WhatsApp",
        "is not on WhatsApp",
        "invalid phone number",
        "not registered on WhatsApp"
    )

    // Indikator pesan/dialog akun WhatsApp terblokir atau banned
    val BANNED_ACCOUNT_TEXTS = listOf(
        "tidak diizinkan menggunakan WhatsApp",
        "not allowed to use WhatsApp",
        "akun ini tidak diizinkan",
        "account is not allowed",
        "telah diblokir",
        "akun diblokir",
        "banned",
        "temporarily banned",
        "spam"
    )


    // Teks tombol untuk menutup dialog kesalahan (dismiss dialog)
    val DISMISS_DIALOG_BUTTON_TEXTS = listOf(
        "OK",
        "BATAL",
        "TUTUP",
        "CANCEL",
        "DISMISS",
        "CLOSE"
    )
}
