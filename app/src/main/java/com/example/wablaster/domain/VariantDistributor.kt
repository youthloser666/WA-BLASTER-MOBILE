package com.example.wablaster.domain

import kotlin.random.Random

enum class VariantMode {
    SEQUENTIAL,
    RANDOM
}

data class ActiveVariant(
    val index: Int, // 0..4 (Pesan 1 = 0, Pesan 2 = 1, dst)
    val text: String
)

object VariantDistributor {

    /**
     * Memfilter varian yang tidak kosong dan mempertahankan indeks aslinya (0..4).
     */
    fun getActiveVariants(variants: List<String>): List<ActiveVariant> {
        return variants.mapIndexedNotNull { index, text ->
            if (text.isNotBlank()) ActiveVariant(index = index, text = text) else null
        }
    }

    /**
     * Memilih varian pesan untuk penerima ke-[recipientIndex]:
     * - SEQUENTIAL: bergilir sesuai urutan slot terisi (recipientIndex % activeVariants.size)
     * - RANDOM: dipilih secara acak dari slot terisi menggunakan generator [random]
     */
    fun selectVariant(
        activeVariants: List<ActiveVariant>,
        recipientIndex: Int,
        mode: VariantMode,
        random: Random = Random.Default
    ): ActiveVariant? {
        if (activeVariants.isEmpty()) return null
        return when (mode) {
            VariantMode.SEQUENTIAL -> {
                val safeIndex = (recipientIndex % activeVariants.size).let {
                    if (it < 0) it + activeVariants.size else it
                }
                activeVariants[safeIndex]
            }
            VariantMode.RANDOM -> {
                activeVariants[random.nextInt(activeVariants.size)]
            }
        }
    }
}
