package com.example.wablaster.data.parser

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

class RecipientInputParserTest {

    @Test
    fun `test parse all supported delimiters pipe comma semicolon and tab`() {
        val input = """
            081234567890|Budi Pipe
            6281298765432,Siti Comma
            +62 812-9876-1111;Dewi Semicolon
            08123456789	Andi Tab
            081211112222
        """.trimIndent()

        val result = RecipientInputParser.parse(input)

        assertEquals(5, result.totalLines)
        assertEquals(5, result.validCount)
        assertEquals(0, result.invalidCount)
        assertEquals(0, result.duplicateCount)

        assertEquals("6281234567890", result.validRecipients[0].phone)
        assertEquals("Budi Pipe", result.validRecipients[0].name)

        assertEquals("6281298765432", result.validRecipients[1].phone)
        assertEquals("Siti Comma", result.validRecipients[1].name)

        assertEquals("6281298761111", result.validRecipients[2].phone)
        assertEquals("Dewi Semicolon", result.validRecipients[2].name)

        assertEquals("628123456789", result.validRecipients[3].phone)
        assertEquals("Andi Tab", result.validRecipients[3].name)

        assertEquals("6281211112222", result.validRecipients[4].phone)
        assertEquals("", result.validRecipients[4].name)
    }

    @Test
    fun `test ignores empty and blank lines correctly`() {
        val input = """
            
            081234567890|Budi
               
            
            081234567891|Siti
            
        """.trimIndent()

        val result = RecipientInputParser.parse(input)

        assertEquals(2, result.totalLines)
        assertEquals(2, result.validCount)
        assertEquals(2, result.validRecipients[0].lineNumber) // baris 1 kosong, maka Budi ada di baris 2
        assertEquals(5, result.validRecipients[1].lineNumber) // Siti ada di baris 5
    }

    @Test
    fun `test invalid numbers are tracked with line numbers and reasons`() {
        val input = """
            081234567890|Valid
            08123|Too Short
            0812abc3456|Letters
            08123456789012345678|Too Long
        """.trimIndent()

        val result = RecipientInputParser.parse(input)

        assertEquals(4, result.totalLines)
        assertEquals(1, result.validCount)
        assertEquals(3, result.invalidCount)

        assertEquals(2, result.invalidRecipients[0].lineNumber)
        assertTrue(result.invalidRecipients[0].reason.contains("Terlalu pendek"))

        assertEquals(3, result.invalidRecipients[1].lineNumber)
        assertTrue(result.invalidRecipients[1].reason.contains("karakter selain angka"))

        assertEquals(4, result.invalidRecipients[2].lineNumber)
        assertTrue(result.invalidRecipients[2].reason.contains("Terlalu panjang"))
    }

    @Test
    fun `test duplicates are recognized and distinct from first appearance`() {
        val input = """
            081234567890|User Pertama
            6281234567890|User Duplikat
            +62 812-3456-7890|User Duplikat Format Lain
            085611112222|User Unik
        """.trimIndent()

        val result = RecipientInputParser.parse(input)

        assertEquals(4, result.totalLines)
        assertEquals(2, result.validCount)
        assertEquals(2, result.duplicateCount)

        assertEquals("User Pertama", result.validRecipients[0].name)
        assertEquals("User Unik", result.validRecipients[1].name)

        assertEquals("User Duplikat", result.duplicateRecipients[0].name)
        assertEquals("User Duplikat Format Lain", result.duplicateRecipients[1].name)
    }

    @Test
    fun `test name containing special characters and commas`() {
        val input = """
            081234567890|Dr. Budi, Sp.KJ (Konsultan) - Jakarta
            081298765432,PT. Maju Bersama, Tbk. [Head Office]
        """.trimIndent()

        val result = RecipientInputParser.parse(input)

        assertEquals(2, result.validCount)
        assertEquals("Dr. Budi, Sp.KJ (Konsultan) - Jakarta", result.validRecipients[0].name)
        assertEquals("PT. Maju Bersama, Tbk. [Head Office]", result.validRecipients[1].name)
    }

    @Test
    fun `test remove invalid and duplicate lines helpers`() {
        val input = """
            081234567890|User 1
            08123|Invalid Short
            081234567890|Duplicate
            085611112222|User 2
        """.trimIndent()

        val parseResult = RecipientInputParser.parse(input)

        val withoutInvalid = RecipientInputParser.removeInvalidLines(input, parseResult)
        assertFalse(withoutInvalid.contains("Invalid Short"))
        assertTrue(withoutInvalid.contains("User 1"))
        assertTrue(withoutInvalid.contains("Duplicate"))
        assertTrue(withoutInvalid.contains("User 2"))

        val withoutDuplicates = RecipientInputParser.removeDuplicateLines(withoutInvalid, RecipientInputParser.parse(withoutInvalid))
        assertFalse(withoutDuplicates.contains("Duplicate"))
        assertTrue(withoutDuplicates.contains("User 1"))
        assertTrue(withoutDuplicates.contains("User 2"))
    }

    @Test
    fun `test performance with 5000 lines executes in less than 500ms`() {
        val sb = StringBuilder()
        for (i in 1..5000) {
            val phone = "0812" + String.format("%08d", i)
            sb.append(phone).append("|User ").append(i).append("\n")
        }
        val hugeInput = sb.toString()

        val timeTaken = measureTimeMillis {
            val result = RecipientInputParser.parse(hugeInput)
            assertEquals(5000, result.validCount)
            assertEquals(0, result.invalidCount)
        }

        println("Waktu parsing 5000 baris: $timeTaken ms")
        assertTrue("Parsing 5000 baris harus sangat cepat (< 500ms), tercatat: $timeTaken ms", timeTaken < 500)
    }
}
