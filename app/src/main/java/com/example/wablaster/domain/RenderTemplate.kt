package com.example.wablaster.domain

import com.example.wablaster.data.local.entity.ContactEntity
import kotlin.random.Random

data class RenderResult(
    val renderedText: String,
    val missingVariables: List<String> = emptyList(),
    val usedVariables: List<String> = emptyList()
)

object RenderTemplate {

    // Regex untuk mendeteksi spintax: {opsi1|opsi2|opsi3}
    private val SPINTAX_REGEX = Regex("""\{([^{}]+?\|[^{}]+?)\}""")

    // Regex untuk mendeteksi variabel placeholder: {nama}, {kota}, dll (tanpa pipe |)
    private val VARIABLE_REGEX = Regex("""\{([a-zA-Z0-9_-]+)\}""")

    /**
     * Memproses template pesan:
     * 1. Menyelesaikan Spintax secara acak: {Halo|Hai|Selamat Siang} -> salah satu pilihan.
     * 2. Mensubstitusi variabel {nama} dan {phone}.
     * 3. Jika variabel tidak ditemukan di kontak, fallback ke string kosong ""
     *    dan dicatat ke dalam missingVariables untuk ditampilkan sebagai peringatan.
     */
    fun render(
        template: String,
        contact: ContactEntity?,
        random: Random
    ): RenderResult = render(template, contact, defaultName = "Kak", random = random)

    fun render(
        template: String,
        contact: ContactEntity? = null,
        defaultName: String = "Kak",
        random: Random = Random.Default
    ): RenderResult {
        if (template.isBlank()) {
            return RenderResult(renderedText = "")
        }

        // 1. Eksekusi Spintax (mendukung spintax berulang hingga selesai)
        var spintaxResolved = template
        while (SPINTAX_REGEX.containsMatchIn(spintaxResolved)) {
            spintaxResolved = SPINTAX_REGEX.replace(spintaxResolved) { matchResult ->
                val content = matchResult.groupValues[1]
                val options = content.split("|").map { it.trim() }
                if (options.isNotEmpty()) {
                    options[random.nextInt(options.size)]
                } else {
                    ""
                }
            }
        }

        // 2. Eksekusi Substitusi Variabel
        val missingVars = mutableListOf<String>()
        val usedVars = mutableListOf<String>()

        val finalRendered = VARIABLE_REGEX.replace(spintaxResolved) { matchResult ->
            val varName = matchResult.groupValues[1]
            val varKey = varName.lowercase()

            val value = when {
                varKey == "nama" || varKey == "name" -> {
                    val n = contact?.name?.trim() ?: ""
                    if (n.isNotEmpty()) n else defaultName.trim().ifEmpty { null }
                }
                varKey == "phone" || varKey == "nomor" || varKey == "no_hp" -> {
                    contact?.phone?.ifBlank { null }
                }
                else -> null
            }

            if (value != null && value.isNotBlank()) {
                usedVars.add(varName)
                value
            } else {
                missingVars.add(varName)
                "" // Fallback kosong sesuai aturan 4.2
            }
        }

        return RenderResult(
            renderedText = finalRendered,
            missingVariables = missingVars.distinct(),
            usedVariables = usedVars.distinct()
        )
    }
}
