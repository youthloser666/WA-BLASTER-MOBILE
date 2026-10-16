package com.example.wablaster.sender

import org.junit.Assert.*
import org.junit.Test

class FailReasonHelperTest {

    @Test
    fun `all FailReason entries have distinct user-friendly descriptions`() {
        for (reason in FailReason.entries) {
            val description = reason.toUserFriendlyDescription()
            assertTrue(description.isNotBlank())
        }
    }

    @Test
    fun `formatErrorMessage formats known FailReasons cleanly`() {
        val formatted1 = FailReasonHelper.formatErrorMessage("SEND_BUTTON_NOT_FOUND: Node not found in hierarchy")
        assertTrue(formatted1.contains("Tombol kirim tidak ditemukan"))
        assertTrue(formatted1.contains("Node not found in hierarchy"))

        val formatted2 = FailReasonHelper.formatErrorMessage("TIMEOUT")
        assertTrue(formatted2.contains("Batas waktu pengiriman habis"))

        val formatted3 = FailReasonHelper.formatErrorMessage(null)
        assertEquals("-", formatted3)

        val formatted4 = FailReasonHelper.formatErrorMessage("")
        assertEquals("-", formatted4)
    }
}
