package com.example.wablaster.domain

import com.example.wablaster.data.local.entity.ContactEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class RenderTemplateTest {

    @Test
    fun `test variable replacement with contact name and phone`() {
        val contact = ContactEntity(
            phone = "628123456789",
            name = "Budi Santoso"
        )

        val template = "Halo {nama}, nomor Anda {phone} telah terdaftar."
        val result = RenderTemplate.render(template, contact)

        assertEquals("Halo Budi Santoso, nomor Anda 628123456789 telah terdaftar.", result.renderedText)
        assertTrue(result.missingVariables.isEmpty())
        assertTrue(result.usedVariables.contains("nama"))
        assertTrue(result.usedVariables.contains("phone"))
    }

    @Test
    fun `test missing variable uses empty fallback and reports in missingVariables`() {
        val contact = ContactEntity(
            phone = "628123456789",
            name = "Siti"
        )

        val template = "Halo {nama}, tagihan Anda sebesar {tagihan} sudah jatuh tempo."
        val result = RenderTemplate.render(template, contact)

        assertEquals("Halo Siti, tagihan Anda sebesar  sudah jatuh tempo.", result.renderedText)
        assertTrue(result.missingVariables.contains("tagihan"))
        assertFalse(result.missingVariables.contains("nama"))
    }

    @Test
    fun `test spintax produces one of the defined options`() {
        val template = "{Halo|Hai|Selamat pagi} semuanya!"
        val options = setOf("Halo semuanya!", "Hai semuanya!", "Selamat pagi semuanya!")

        // Jalankan beberapa kali dengan seed acak untuk memastikan opsi valid terpilih
        for (i in 1..20) {
            val result = RenderTemplate.render(template, null)
            assertTrue("Hasil '${result.renderedText}' harus salah satu dari options", options.contains(result.renderedText))
        }
    }

    @Test
    fun `test combined spintax and contact variables`() {
        val contact = ContactEntity(
            phone = "628999999999",
            name = "Rina"
        )

        val template = "{Hai|Halo} {nama}, nomor {phone} terpilih hari ini!"
        val result = RenderTemplate.render(template, contact, Random(42))

        assertTrue(result.renderedText.contains("Rina"))
        assertTrue(result.renderedText.contains("628999999999"))
        assertTrue(result.renderedText.startsWith("Hai Rina") || result.renderedText.startsWith("Halo Rina"))
    }

    @Test
    fun `test empty template returns empty result`() {
        val result = RenderTemplate.render("", null)
        assertEquals("", result.renderedText)
        assertTrue(result.missingVariables.isEmpty())
    }
}
