package com.example.wablaster.util

import kotlin.random.Random

enum class DelayMode {
    FIXED,
    RANDOM
}

data class DelayConfig(
    val mode: DelayMode = DelayMode.RANDOM,
    val fixedSeconds: Int = 10,
    val minSeconds: Int = 8,
    val maxSeconds: Int = 25,
    val batchSize: Int = 20,        // Istirahat setiap N pesan (0 = nonaktif)
    val batchPauseMinutes: Int = 5,
    val dailyLimit: Int = 200       // 0 = tanpa batas
)

sealed interface DelayValidationResult {
    data object Valid : DelayValidationResult
    data class Invalid(val errors: List<String>) : DelayValidationResult
}

data class DelayStepResult(
    val delaySeconds: Int,
    val isBatchPause: Boolean,
    val description: String
)

object DelayCalculator {

    /**
     * Memvalidasi parameter DelayConfig:
     * - minSeconds >= 1, minSeconds <= maxSeconds
     * - fixedSeconds >= 1
     * - Nilai-nilai tidak boleh negatif
     */
    fun validate(config: DelayConfig): DelayValidationResult {
        val errors = mutableListOf<String>()

        if (config.mode == DelayMode.FIXED) {
            if (config.fixedSeconds < 1) {
                errors.add("Jeda tetap minimal 1 detik")
            }
        } else {
            if (config.minSeconds < 1) {
                errors.add("Jeda minimum harus minimal 1 detik")
            }
            if (config.maxSeconds < config.minSeconds) {
                errors.add("Jeda maksimum tidak boleh lebih kecil dari jeda minimum")
            }
        }

        if (config.batchSize < 0) {
            errors.add("Ukuran batch tidak boleh bernilai negatif")
        }

        if (config.batchPauseMinutes < 0) {
            errors.add("Durasi istirahat batch tidak boleh bernilai negatif")
        }

        if (config.dailyLimit < 0) {
            errors.add("Batas harian tidak boleh bernilai negatif")
        }

        return if (errors.isEmpty()) {
            DelayValidationResult.Valid
        } else {
            DelayValidationResult.Invalid(errors)
        }
    }

    /**
     * Menghitung durasi jeda untuk pesan berikutnya:
     * - Jika telah mencapai batas batch (batchSize > 0 dan index kelipatan batchSize),
     *   maka memicu istirahat batch (batchPauseMinutes * 60 detik).
     * - Jika tidak, menghasilkan jeda (acak atau tetap) sesuai DelayConfig.
     */
    fun calculateNextDelay(
        config: DelayConfig,
        messageIndex: Int,
        random: Random = Random.Default
    ): DelayStepResult {
        // Cek apakah jeda batch istirahat terpenuhi (setelah pesan ke-N)
        val isBatchTriggered = config.batchSize > 0 &&
                config.batchPauseMinutes > 0 &&
                (messageIndex + 1) % config.batchSize == 0

        if (isBatchTriggered) {
            val pauseSeconds = config.batchPauseMinutes * 60
            val batchNumber = (messageIndex + 1) / config.batchSize
            return DelayStepResult(
                delaySeconds = pauseSeconds,
                isBatchPause = true,
                description = "Istirahat batch ke-$batchNumber (${config.batchPauseMinutes} menit)"
            )
        }

        // Jeda per pesan
        val delaySec = when (config.mode) {
            DelayMode.FIXED -> config.fixedSeconds
            DelayMode.RANDOM -> {
                val min = config.minSeconds.coerceAtLeast(1)
                val max = config.maxSeconds.coerceAtLeast(min)
                random.nextInt(min, max + 1)
            }
        }

        return DelayStepResult(
            delaySeconds = delaySec,
            isBatchPause = false,
            description = when (config.mode) {
                DelayMode.FIXED -> "Jeda tetap $delaySec detik"
                DelayMode.RANDOM -> "Jeda acak $delaySec detik (rentang ${config.minSeconds}-${config.maxSeconds}s)"
            }
        )
    }
}
