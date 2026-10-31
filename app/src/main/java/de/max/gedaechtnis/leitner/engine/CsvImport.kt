package de.max.gedaechtnis.leitner.engine

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class CsvResult(
    val cards: List<Pair<String, String>>,
    val duplicates: Int,
    val invalid: Int,
    val headerSkipped: Boolean,
)

/** CSV-Import im Format `Vorderseite;Rückseite`, UTF-8, erste Zeile darf Kopfzeile sein. */
object CsvImport {
    private val BOM = Char(0xFEFF).toString()


    fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1) // Excel-Export (Windows-1252/Latin-1)
        }
        return text.removePrefix(BOM)
    }

    fun parse(bytes: ByteArray, existingFronts: Set<String> = emptySet()): CsvResult =
        parse(decode(bytes), existingFronts)

    fun parse(text: String, existingFronts: Set<String> = emptySet()): CsvResult {
        val rows = splitRows(text.removePrefix(BOM))
        val seen = existingFronts.map { it.trim().lowercase() }.toMutableSet()
        val cards = mutableListOf<Pair<String, String>>()
        var dup = 0
        var invalid = 0
        var header = false
        for ((i, row) in rows.withIndex()) {
            if (row.isEmpty() || row.all { it.isBlank() }) continue
            if (i == 0 && isHeader(row)) { header = true; continue }
            if (row.size < 2 || row[0].isBlank() || row[1].isBlank()) { invalid++; continue }
            val front = row[0].trim()
            val back = row[1].trim()
            if (!seen.add(front.lowercase())) { dup++; continue }
            cards += front to back
        }
        return CsvResult(cards, dup, invalid, header)
    }

    private fun isHeader(row: List<String>): Boolean {
        if (row.size < 2) return false
        val a = row[0].trim().lowercase()
        val b = row[1].trim().lowercase()
        return a in setOf("vorderseite", "front") && b in setOf("rückseite", "rueckseite", "back")
    }

    /** Zerlegt Text in Zeilen und Felder (Semikolon, Anführungszeichen mit "" als Escape). */
    private fun splitRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        fun endField() { fields += sb.toString(); sb.clear() }
        fun endRow() { endField(); rows += fields; fields = mutableListOf() }
        while (i < text.length) {
            val ch = text[i]
            when {
                inQuotes -> when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { sb.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> sb.append(ch)
                }
                ch == '"' && sb.isEmpty() -> inQuotes = true
                ch == ';' -> endField()
                ch == '\r' -> Unit
                ch == '\n' -> endRow()
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || fields.isNotEmpty()) endRow()
        return rows
    }
}
