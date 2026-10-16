package com.example.wablaster.util

sealed interface PhoneValidationResult {
    data class Valid(val normalizedPhone: String) : PhoneValidationResult
    data class Invalid(val rawPhone: String, val reason: String) : PhoneValidationResult
}

object PhoneNormalizer {

    /**
     * Membersihkan dan menormalisasi nomor telepon:
     * - Menghapus spasi, strip (-), tanda kurung (), dan simbol plus (+).
     * - Mengubah awalan '08xx' menjadi '628xx'.
     * - Mengubah awalan '8xx' (tanpa 0) menjadi '628xx'.
     * - Mengubah awalan '+62' menjadi '62'.
     * - Menolak nomor yang memiliki karakter non-digit selain simbol umum di atas.
     * - Memvalidasi panjang 10-15 digit, dengan awalan 62 untuk nomor Indonesia.
     */
    fun normalize(rawInput: String?): PhoneValidationResult {
        if (rawInput.isNullOrBlank()) {
            return PhoneValidationResult.Invalid("", "Nomor telepon kosong")
        }

        val trimmed = rawInput.trim()

        // 1. Bersihkan karakter formatting yang diizinkan: spasi, -, (), .
        var cleaned = trimmed
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        // 2. Tangani tanda plus di awal
        if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }

        // 3. Pastikan hanya berisi angka
        if (!cleaned.all { it.isDigit() }) {
            return PhoneValidationResult.Invalid(trimmed, "Mengandung karakter selain angka")
        }

        // 4. Transformasi awalan Indonesia
        var normalized = cleaned
        when {
            normalized.startsWith("08") -> {
                // 08xx -> 628xx
                normalized = "62" + normalized.substring(1)
            }
            normalized.startsWith("0") -> {
                // 021xx / 0xxx -> 62xxx
                normalized = "62" + normalized.substring(1)
            }
            normalized.startsWith("8") -> {
                // 812xxx -> 62812xxx
                normalized = "62$normalized"
            }
        }

        // 5. Validasi panjang digit (standar E.164 / regulasi Indonesia 10-15 digit)
        if (normalized.length < 10) {
            return PhoneValidationResult.Invalid(trimmed, "Terlalu pendek (kurang dari 10 digit)")
        }

        if (normalized.length > 15) {
            return PhoneValidationResult.Invalid(trimmed, "Terlalu panjang (lebih dari 15 digit)")
        }

        // 6. Validasi awalan 62 untuk default Indonesia
        if (!normalized.startsWith("62")) {
            return PhoneValidationResult.Invalid(trimmed, "Harus menggunakan format nomor Indonesia (awalan 62/08)")
        }

        return PhoneValidationResult.Valid(normalized)
    }
}
