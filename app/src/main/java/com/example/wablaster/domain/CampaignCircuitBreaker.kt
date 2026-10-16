package com.example.wablaster.domain

import com.example.wablaster.sender.FailReason
import com.example.wablaster.sender.SendResult

/**
 * Pengaman otomatis (Circuit Breaker) untuk menghentikan kampanye
 * jika terjadi kegagalan deteksi UI / timeout berturut-turut.
 * Mencegah aplikasi terus mengetuk layar tanpa hasil ketika selector WhatsApp berubah.
 */
class CampaignCircuitBreaker(
    val maxConsecutiveUiFailures: Int = 5
) {
    var consecutiveUiFailures: Int = 0
        private set

    /**
     * Merekam hasil pengiriman pesan.
     * @return true jika circuit breaker terpicu (harus auto-stop), false jika pengiriman aman dilanjutkan.
     */
    fun recordResult(result: SendResult): Boolean {
        when (result) {
            is SendResult.Success -> {
                consecutiveUiFailures = 0
                return false
            }
            is SendResult.Failed -> {
                if (result.reason == FailReason.WA_BANNED) {
                    consecutiveUiFailures = maxConsecutiveUiFailures
                    return true
                }
                if (result.reason == FailReason.SEND_BUTTON_NOT_FOUND || result.reason == FailReason.TIMEOUT) {
                    consecutiveUiFailures++
                    return consecutiveUiFailures >= maxConsecutiveUiFailures
                } else {
                    // Kegagalan nomor atau non-UI tidak memicu UI circuit breaker
                    return false
                }
            }
        }
    }

    fun reset() {
        consecutiveUiFailures = 0
    }
}
