package com.example.wablaster.domain

import org.junit.Assert.*
import org.junit.Test

class CampaignHealthCheckerTest {

    @Test
    fun `isHealthy returns true when all conditions are met in accessibility mode`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = true,
            isWhatsAppInstalled = true,
            hasValidVariants = true,
            hasValidRecipients = true,
            sendMode = "ACCESSIBILITY"
        )
        assertTrue(status.isHealthy)
        assertTrue(status.unmetReasons.isEmpty())
    }

    @Test
    fun `isHealthy returns false when accessibility is disabled in accessibility mode`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = false,
            isWhatsAppInstalled = true,
            hasValidVariants = true,
            hasValidRecipients = true,
            sendMode = "ACCESSIBILITY"
        )
        assertFalse(status.isHealthy)
        assertEquals(1, status.unmetReasons.size)
        assertTrue(status.unmetReasons[0].contains("Aksesibilitas"))
    }

    @Test
    fun `isHealthy returns true when accessibility is disabled but mode is INTENT_MANUAL`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = false,
            isWhatsAppInstalled = true,
            hasValidVariants = true,
            hasValidRecipients = true,
            sendMode = "INTENT_MANUAL"
        )
        assertTrue(status.isHealthy)
        assertTrue(status.unmetReasons.isEmpty())
    }

    @Test
    fun `isHealthy returns false when WhatsApp is not installed`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = true,
            isWhatsAppInstalled = false,
            hasValidVariants = true,
            hasValidRecipients = true
        )
        assertFalse(status.isHealthy)
        assertTrue(status.unmetReasons.any { it.contains("WhatsApp") })
    }

    @Test
    fun `isHealthy returns false when variants are empty`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = true,
            isWhatsAppInstalled = true,
            hasValidVariants = false,
            hasValidRecipients = true
        )
        assertFalse(status.isHealthy)
        assertTrue(status.unmetReasons.any { it.contains("varian") })
    }

    @Test
    fun `isHealthy returns false when recipients are empty`() {
        val status = CampaignHealthStatus(
            isAccessibilityActive = true,
            isWhatsAppInstalled = true,
            hasValidVariants = true,
            hasValidRecipients = false
        )
        assertFalse(status.isHealthy)
        assertTrue(status.unmetReasons.any { it.contains("kontak") })
    }
}
