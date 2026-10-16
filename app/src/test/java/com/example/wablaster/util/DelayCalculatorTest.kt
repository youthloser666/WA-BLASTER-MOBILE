package com.example.wablaster.util

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DelayCalculatorTest {

    @Test
    fun `test validation valid random config`() {
        val config = DelayConfig(
            mode = DelayMode.RANDOM,
            minSeconds = 8,
            maxSeconds = 25,
            batchSize = 20,
            batchPauseMinutes = 5,
            dailyLimit = 200
        )
        val result = DelayCalculator.validate(config)
        assertTrue(result is DelayValidationResult.Valid)
    }

    @Test
    fun `test validation error when min is greater than max`() {
        val config = DelayConfig(
            mode = DelayMode.RANDOM,
            minSeconds = 30,
            maxSeconds = 10
        )
        val result = DelayCalculator.validate(config)
        assertTrue(result is DelayValidationResult.Invalid)
        val errors = (result as DelayValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("tidak boleh lebih kecil") })
    }

    @Test
    fun `test validation error when min is less than 1`() {
        val config = DelayConfig(
            mode = DelayMode.RANDOM,
            minSeconds = 0,
            maxSeconds = 10
        )
        val result = DelayCalculator.validate(config)
        assertTrue(result is DelayValidationResult.Invalid)
    }

    @Test
    fun `test validation error when values are negative`() {
        val config = DelayConfig(
            mode = DelayMode.FIXED,
            fixedSeconds = -5,
            batchSize = -1,
            batchPauseMinutes = -2,
            dailyLimit = -10
        )
        val result = DelayCalculator.validate(config)
        assertTrue(result is DelayValidationResult.Invalid)
        val errors = (result as DelayValidationResult.Invalid).errors
        assertEquals(4, errors.size)
    }

    @Test
    fun `test calculate fixed delay`() {
        val config = DelayConfig(
            mode = DelayMode.FIXED,
            fixedSeconds = 15,
            batchSize = 0
        )
        val result = DelayCalculator.calculateNextDelay(config, messageIndex = 0)
        assertFalse(result.isBatchPause)
        assertEquals(15, result.delaySeconds)
    }

    @Test
    fun `test calculate random delay produces value within range`() {
        val config = DelayConfig(
            mode = DelayMode.RANDOM,
            minSeconds = 10,
            maxSeconds = 20,
            batchSize = 0
        )

        for (i in 0..50) {
            val result = DelayCalculator.calculateNextDelay(config, messageIndex = i)
            assertFalse(result.isBatchPause)
            assertTrue(
                "Delay ${result.delaySeconds} harus berada dalam 10..20",
                result.delaySeconds in 10..20
            )
        }
    }

    @Test
    fun `test batch pause triggers exactly on batch multiple`() {
        val config = DelayConfig(
            mode = DelayMode.RANDOM,
            minSeconds = 5,
            maxSeconds = 10,
            batchSize = 5,
            batchPauseMinutes = 3
        )

        // messageIndex = 3 (pesan ke-4) -> belum batch
        val normalStep = DelayCalculator.calculateNextDelay(config, messageIndex = 3)
        assertFalse(normalStep.isBatchPause)

        // messageIndex = 4 (pesan ke-5) -> batch trigger!
        val batchStep = DelayCalculator.calculateNextDelay(config, messageIndex = 4)
        assertTrue(batchStep.isBatchPause)
        assertEquals(3 * 60, batchStep.delaySeconds) // 180 detik

        // messageIndex = 5 (pesan ke-6) -> normal kembali
        val nextStep = DelayCalculator.calculateNextDelay(config, messageIndex = 5)
        assertFalse(nextStep.isBatchPause)
    }
}
