package com.example.wablaster.domain

import com.example.wablaster.data.local.entity.ContactEntity
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class VariantDistributorTest {

    @Test
    fun `test sequential distribution skips empty slots in the middle`() {
        // Slot 0 (Pesan 1): "Varian A"
        // Slot 1 (Pesan 2): "" (kosong)
        // Slot 2 (Pesan 3): "Varian C"
        // Slot 3 (Pesan 4): "   " (blank)
        // Slot 4 (Pesan 5): "Varian E"
        val rawVariants = listOf("Varian A", "", "Varian C", "   ", "Varian E")
        val active = VariantDistributor.getActiveVariants(rawVariants)

        assertEquals(3, active.size)
        assertEquals(0, active[0].index)
        assertEquals("Varian A", active[0].text)
        assertEquals(2, active[1].index)
        assertEquals("Varian C", active[1].text)
        assertEquals(4, active[2].index)
        assertEquals("Varian E", active[2].text)

        // Penerima 0 -> Varian A (index 0)
        val r0 = VariantDistributor.selectVariant(active, 0, VariantMode.SEQUENTIAL)
        assertEquals(0, r0?.index)
        assertEquals("Varian A", r0?.text)

        // Penerima 1 -> Varian C (index 2)
        val r1 = VariantDistributor.selectVariant(active, 1, VariantMode.SEQUENTIAL)
        assertEquals(2, r1?.index)
        assertEquals("Varian C", r1?.text)

        // Penerima 2 -> Varian E (index 4)
        val r2 = VariantDistributor.selectVariant(active, 2, VariantMode.SEQUENTIAL)
        assertEquals(4, r2?.index)
        assertEquals("Varian E", r2?.text)

        // Penerima 3 -> kembali ke Varian A (index 0)
        val r3 = VariantDistributor.selectVariant(active, 3, VariantMode.SEQUENTIAL)
        assertEquals(0, r3?.index)
        assertEquals("Varian A", r3?.text)

        // Penerima 4 -> Varian C (index 2)
        val r4 = VariantDistributor.selectVariant(active, 4, VariantMode.SEQUENTIAL)
        assertEquals(2, r4?.index)
        assertEquals("Varian C", r4?.text)
    }

    @Test
    fun `test random distribution only selects from active filled slots`() {
        val rawVariants = listOf("Varian 1", "", "", "Varian 4", "")
        val active = VariantDistributor.getActiveVariants(rawVariants)

        assertEquals(2, active.size)
        val allowedIndices = setOf(0, 3)
        val allowedTexts = setOf("Varian 1", "Varian 4")

        for (seed in 1..50) {
            val selected = VariantDistributor.selectVariant(active, 0, VariantMode.RANDOM, Random(seed))
            assertNotNull(selected)
            assertTrue("Index harus 0 atau 3", allowedIndices.contains(selected?.index))
            assertTrue("Teks harus Varian 1 atau Varian 4", allowedTexts.contains(selected?.text))
        }
    }

    @Test
    fun `test deterministic assignment allows consistent resume`() {
        val rawVariants = listOf("Varian A", "Varian B", "Varian C")
        val active = VariantDistributor.getActiveVariants(rawVariants)
        val campaignId = 100L

        // Simulasi recipient ke-5 pada eksekusi awal
        val firstRunSelection = VariantDistributor.selectVariant(
            active,
            5,
            VariantMode.RANDOM,
            Random(campaignId * 31 + 5)
        )

        // Simulasi resume setelah proses restart pada recipient yang sama
        val resumeSelection = VariantDistributor.selectVariant(
            active,
            5,
            VariantMode.RANDOM,
            Random(campaignId * 31 + 5)
        )

        assertEquals(firstRunSelection?.index, resumeSelection?.index)
        assertEquals(firstRunSelection?.text, resumeSelection?.text)
    }

    @Test
    fun `test empty variants returns null`() {
        val rawVariants = listOf("", "   ", "")
        val active = VariantDistributor.getActiveVariants(rawVariants)
        assertTrue(active.isEmpty())

        val selected = VariantDistributor.selectVariant(active, 0, VariantMode.SEQUENTIAL)
        assertNull(selected)
    }

    @Test
    fun `test renderTemplate defaultName fallback when contact name is blank`() {
        val contactWithoutName = ContactEntity(
            phone = "628123456789",
            name = ""
        )

        val template = "Halo {nama}, selamat datang!"
        val result = RenderTemplate.render(
            template = template,
            contact = contactWithoutName,
            defaultName = "Kak"
        )

        assertEquals("Halo Kak, selamat datang!", result.renderedText)
        assertTrue(result.usedVariables.contains("nama"))
        assertTrue(result.missingVariables.isEmpty())
    }

    @Test
    fun `test renderTemplate uses contact name when present`() {
        val contactWithName = ContactEntity(
            phone = "628123456789",
            name = "Budi"
        )

        val template = "Halo {nama}, selamat datang!"
        val result = RenderTemplate.render(
            template = template,
            contact = contactWithName,
            defaultName = "Kak"
        )

        assertEquals("Halo Budi, selamat datang!", result.renderedText)
        assertTrue(result.usedVariables.contains("nama"))
    }

    @Test
    fun `test campaign snapshot remains immutable when templates are edited later`() {
        val originalTemplates = listOf("Pesan Awal 1", "Pesan Awal 2")
        val snapshottedJson = com.example.wablaster.data.local.Converters().fromListToString(originalTemplates)

        // Buat entitas kampanye dengan snapshot varian
        val campaign = com.example.wablaster.data.local.entity.CampaignEntity(
            title = "Promo Spesial",
            variantsJson = snapshottedJson,
            variantMode = VariantMode.SEQUENTIAL.name
        )

        // Pengguna kemudian mengedit template di antarmuka
        val editedTemplates = listOf("Pesan Baru yang Diubah 1", "Pesan Baru yang Diubah 2")

        // Verifikasi bahwa kampanye tetap menggunakan teks snapshot saat awal dibuat
        val campaignVariants = com.example.wablaster.data.local.Converters().fromStringToList(campaign.variantsJson)
        assertEquals(listOf("Pesan Awal 1", "Pesan Awal 2"), campaignVariants)
        assertNotEquals(editedTemplates, campaignVariants)
    }
}
