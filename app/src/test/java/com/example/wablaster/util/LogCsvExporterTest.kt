package com.example.wablaster.util

import com.example.wablaster.data.local.entity.SendLogEntity
import org.junit.Assert.*
import org.junit.Test

class LogCsvExporterTest {

    @Test
    fun `toCsvString exports valid CSV format with header`() {
        val logs = listOf(
            SendLogEntity(
                id = 1,
                campaignId = 10,
                phone = "628123456789",
                contactName = "Budi Hartono",
                variantIndex = 0,
                renderedMessage = "Halo Budi, selamat siang!",
                status = "SENT",
                errorMessage = null,
                sentAt = 1700000000000L
            )
        )

        val csv = LogCsvExporter.toCsvString(logs)
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        assertEquals("Waktu,Nomor,Nama,Status,Varian,Pesan,Keterangan Gagal", lines[0])
        assertTrue(lines[1].contains("628123456789"))
        assertTrue(lines[1].contains("Budi Hartono"))
        assertTrue(lines[1].contains("SENT"))
        assertTrue(lines[1].contains("Varian 1"))
        assertTrue(lines[1].contains("\"Halo Budi, selamat siang!\""))
    }

    @Test
    fun `escapeCsv escapes commas, quotes, and newlines correctly`() {
        assertEquals("normal_text", LogCsvExporter.escapeCsv("normal_text"))
        assertEquals("\"text,with,comma\"", LogCsvExporter.escapeCsv("text,with,comma"))
        assertEquals("\"text with \"\"quotes\"\" inside\"", LogCsvExporter.escapeCsv("text with \"quotes\" inside"))
        assertEquals("\"line1\nline2\"", LogCsvExporter.escapeCsv("line1\nline2"))
    }

    @Test
    fun `empty log list generates header only`() {
        val csv = LogCsvExporter.toCsvString(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertEquals("Waktu,Nomor,Nama,Status,Varian,Pesan,Keterangan Gagal", lines[0])
    }
}
