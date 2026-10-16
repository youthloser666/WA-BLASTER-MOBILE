package com.example.wablaster.domain

import com.example.wablaster.sender.FailReason
import com.example.wablaster.sender.SendResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CampaignCircuitBreakerTest {

    private lateinit var circuitBreaker: CampaignCircuitBreaker

    @Before
    fun setUp() {
        circuitBreaker = CampaignCircuitBreaker(maxConsecutiveUiFailures = 5)
    }

    @Test
    fun `initial state has zero failures and does not trip`() {
        assertEquals(0, circuitBreaker.consecutiveUiFailures)
    }

    @Test
    fun `send button not found increments failure count`() {
        val tripped = circuitBreaker.recordResult(
            SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND, "Button missing")
        )
        assertEquals(1, circuitBreaker.consecutiveUiFailures)
        assertFalse(tripped)
    }

    @Test
    fun `timeout increments failure count`() {
        val tripped = circuitBreaker.recordResult(
            SendResult.Failed(FailReason.TIMEOUT, "Timeout waiting")
        )
        assertEquals(1, circuitBreaker.consecutiveUiFailures)
        assertFalse(tripped)
    }

    @Test
    fun `non-UI failures like invalid number do not increment failure count`() {
        circuitBreaker.recordResult(
            SendResult.Failed(FailReason.INVALID_NUMBER, "Bad phone")
        )
        assertEquals(0, circuitBreaker.consecutiveUiFailures)
    }

    @Test
    fun `five consecutive UI failures trips the circuit breaker`() {
        assertFalse(circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND)))
        assertEquals(1, circuitBreaker.consecutiveUiFailures)

        assertFalse(circuitBreaker.recordResult(SendResult.Failed(FailReason.TIMEOUT)))
        assertEquals(2, circuitBreaker.consecutiveUiFailures)

        assertFalse(circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND)))
        assertEquals(3, circuitBreaker.consecutiveUiFailures)

        assertFalse(circuitBreaker.recordResult(SendResult.Failed(FailReason.TIMEOUT)))
        assertEquals(4, circuitBreaker.consecutiveUiFailures)

        val tripped = circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND))
        assertEquals(5, circuitBreaker.consecutiveUiFailures)
        assertTrue(tripped)
    }

    @Test
    fun `success resets consecutive failure counter to zero`() {
        circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND))
        circuitBreaker.recordResult(SendResult.Failed(FailReason.TIMEOUT))
        circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND))
        assertEquals(3, circuitBreaker.consecutiveUiFailures)

        val tripped = circuitBreaker.recordResult(SendResult.Success)
        assertFalse(tripped)
        assertEquals(0, circuitBreaker.consecutiveUiFailures)
    }

    @Test
    fun `wa banned failure trips circuit breaker immediately`() {
        val tripped = circuitBreaker.recordResult(
            SendResult.Failed(FailReason.WA_BANNED, "Akun ini tidak diizinkan menggunakan WhatsApp")
        )
        assertTrue(tripped)
    }

    @Test
    fun `manual reset clears failure counter`() {
        circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND))
        circuitBreaker.recordResult(SendResult.Failed(FailReason.SEND_BUTTON_NOT_FOUND))
        assertEquals(2, circuitBreaker.consecutiveUiFailures)

        circuitBreaker.reset()
        assertEquals(0, circuitBreaker.consecutiveUiFailures)
    }
}
