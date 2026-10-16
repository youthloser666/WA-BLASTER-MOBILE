package com.example.wablaster.sender

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaSelectorsTest {

    @Test
    fun `test invalid number detection matches common whatsapp error strings`() {
        val sampleErrorMessageIndonesian = "Nomor telepon tidak terdaftar di WhatsApp."
        val sampleErrorMessageEnglish = "The phone number isn't on WhatsApp."
        val normalText = "Ketik pesan..."

        val isInvalidIndo = WaSelectors.INVALID_NUMBER_TEXTS.any {
            sampleErrorMessageIndonesian.contains(it, ignoreCase = true)
        }
        val isInvalidEng = WaSelectors.INVALID_NUMBER_TEXTS.any {
            sampleErrorMessageEnglish.contains(it, ignoreCase = true)
        }
        val isInvalidNormal = WaSelectors.INVALID_NUMBER_TEXTS.any {
            normalText.contains(it, ignoreCase = true)
        }

        assertTrue(isInvalidIndo)
        assertTrue(isInvalidEng)
        assertFalse(isInvalidNormal)
    }

    @Test
    fun `test send button descriptions are not empty and contain standard labels`() {
        assertTrue(WaSelectors.SEND_BUTTON_DESCRIPTIONS.contains("Kirim"))
        assertTrue(WaSelectors.SEND_BUTTON_DESCRIPTIONS.contains("Send"))
        assertTrue(WaSelectors.SEND_BUTTON_IDS.isNotEmpty())
    }
}
