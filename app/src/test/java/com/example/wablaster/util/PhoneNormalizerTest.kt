package com.example.wablaster.util

import org.junit.Assert.*
import org.junit.Test

class PhoneNormalizerTest {

    @Test
    fun `test normalization of 08 prefix to 628`() {
        val result = PhoneNormalizer.normalize("08123456789")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("628123456789", (result as PhoneValidationResult.Valid).normalizedPhone)
    }

    @Test
    fun `test normalization of +62 prefix with spaces and dashes`() {
        val result = PhoneNormalizer.normalize("+62 812-3456-7890")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("6281234567890", (result as PhoneValidationResult.Valid).normalizedPhone)
    }

    @Test
    fun `test normalization of number starting directly with 8`() {
        val result = PhoneNormalizer.normalize("81234567890")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("6281234567890", (result as PhoneValidationResult.Valid).normalizedPhone)
    }

    @Test
    fun `test normalization with parentheses and dots`() {
        val result = PhoneNormalizer.normalize("0812.(345).6789")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("628123456789", (result as PhoneValidationResult.Valid).normalizedPhone)
    }

    @Test
    fun `test already normalized 62 number`() {
        val result = PhoneNormalizer.normalize("628123456789")
        assertTrue(result is PhoneValidationResult.Valid)
        assertEquals("628123456789", (result as PhoneValidationResult.Valid).normalizedPhone)
    }

    @Test
    fun `test invalid blank or empty input`() {
        val result = PhoneNormalizer.normalize("")
        assertTrue(result is PhoneValidationResult.Invalid)

        val nullResult = PhoneNormalizer.normalize(null)
        assertTrue(nullResult is PhoneValidationResult.Invalid)
    }

    @Test
    fun `test invalid letters or special characters`() {
        val result = PhoneNormalizer.normalize("0812abc3456")
        assertTrue(result is PhoneValidationResult.Invalid)
        assertEquals("Mengandung karakter selain angka", (result as PhoneValidationResult.Invalid).reason)
    }

    @Test
    fun `test invalid too short length`() {
        val result = PhoneNormalizer.normalize("08123")
        assertTrue(result is PhoneValidationResult.Invalid)
        assertTrue((result as PhoneValidationResult.Invalid).reason.contains("Terlalu pendek"))
    }

    @Test
    fun `test invalid too long length`() {
        val result = PhoneNormalizer.normalize("08123456789012345678")
        assertTrue(result is PhoneValidationResult.Invalid)
        assertTrue((result as PhoneValidationResult.Invalid).reason.contains("Terlalu panjang"))
    }

    @Test
    fun `test invalid non-indonesian country prefix`() {
        val result = PhoneNormalizer.normalize("+12025550143")
        assertTrue(result is PhoneValidationResult.Invalid)
        assertTrue((result as PhoneValidationResult.Invalid).reason.contains("Harus menggunakan format nomor Indonesia"))
    }
}
