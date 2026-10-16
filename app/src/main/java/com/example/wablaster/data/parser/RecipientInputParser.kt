package com.example.wablaster.data.parser

import com.example.wablaster.util.PhoneNormalizer
import com.example.wablaster.util.PhoneValidationResult

data class ParsedRecipient(
    val lineNumber: Int,
    val rawLine: String,
    val phone: String,
    val name: String
)

data class InvalidRecipient(
    val lineNumber: Int,
    val rawLine: String,
    val rawPhone: String,
    val reason: String
)

data class RecipientParseResult(
    val totalLines: Int,
    val validRecipients: List<ParsedRecipient>,
    val invalidRecipients: List<InvalidRecipient>,
    val duplicateRecipients: List<ParsedRecipient>
) {
    val validCount: Int get() = validRecipients.size
    val invalidCount: Int get() = invalidRecipients.size
    val duplicateCount: Int get() = duplicateRecipients.size
}

object RecipientInputParser {

    private val DELIMITERS = charArrayOf('|', '\t', ',', ';')

    /**
     * Mem-parsing teks multi-baris penerima.
     * Format per baris:
     * - nomor saja: 08123456789
     * - nomor<pemisah>nama: 08123456789|Budi, 08123456789,Budi, 08123456789\tBudi, 08123456789;Budi
     */
    fun parse(rawText: String): RecipientParseResult {
        if (rawText.isBlank()) {
            return RecipientParseResult(0, emptyList(), emptyList(), emptyList())
        }

        val lines = rawText.lines()
        val validList = ArrayList<ParsedRecipient>()
        val invalidList = ArrayList<InvalidRecipient>()
        val duplicateList = ArrayList<ParsedRecipient>()
        val seenPhones = HashSet<String>()

        var totalNonEmptyLines = 0

        for (i in lines.indices) {
            val originalLine = lines[i]
            val trimmedLine = originalLine.trim()
            if (trimmedLine.isEmpty()) continue

            totalNonEmptyLines++
            val lineNumber = i + 1

            // Cari pemisah pertama di antara |, \t, ,, ;
            val delimiterIndex = trimmedLine.indexOfAny(DELIMITERS)

            val rawPhone: String
            val rawName: String

            if (delimiterIndex != -1) {
                rawPhone = trimmedLine.substring(0, delimiterIndex).trim()
                rawName = trimmedLine.substring(delimiterIndex + 1).trim()
            } else {
                rawPhone = trimmedLine
                rawName = ""
            }

            when (val normResult = PhoneNormalizer.normalize(rawPhone)) {
                is PhoneValidationResult.Valid -> {
                    val normalizedPhone = normResult.normalizedPhone
                    val recipient = ParsedRecipient(
                        lineNumber = lineNumber,
                        rawLine = originalLine,
                        phone = normalizedPhone,
                        name = rawName
                    )

                    if (seenPhones.contains(normalizedPhone)) {
                        duplicateList.add(recipient)
                    } else {
                        seenPhones.add(normalizedPhone)
                        validList.add(recipient)
                    }
                }
                is PhoneValidationResult.Invalid -> {
                    invalidList.add(
                        InvalidRecipient(
                            lineNumber = lineNumber,
                            rawLine = originalLine,
                            rawPhone = rawPhone,
                            reason = normResult.reason
                        )
                    )
                }
            }
        }

        return RecipientParseResult(
            totalLines = totalNonEmptyLines,
            validRecipients = validList,
            invalidRecipients = invalidList,
            duplicateRecipients = duplicateList
        )
    }

    /**
     * Menghapus baris yang nomor teleponnya tidak valid dari teks asli.
     */
    fun removeInvalidLines(rawText: String, parseResult: RecipientParseResult): String {
        if (parseResult.invalidRecipients.isEmpty()) return rawText
        val invalidLineNumbers = parseResult.invalidRecipients.map { it.lineNumber }.toSet()
        val lines = rawText.lines()
        return lines.filterIndexed { index, _ ->
            (index + 1) !in invalidLineNumbers
        }.joinToString("\n")
    }

    /**
     * Menghapus baris yang nomor teleponnya duplikat (menyimpan baris kemunculan pertama saja).
     */
    fun removeDuplicateLines(rawText: String, parseResult: RecipientParseResult): String {
        if (parseResult.duplicateRecipients.isEmpty()) return rawText
        val duplicateLineNumbers = parseResult.duplicateRecipients.map { it.lineNumber }.toSet()
        val lines = rawText.lines()
        return lines.filterIndexed { index, _ ->
            (index + 1) !in duplicateLineNumbers
        }.joinToString("\n")
    }
}
