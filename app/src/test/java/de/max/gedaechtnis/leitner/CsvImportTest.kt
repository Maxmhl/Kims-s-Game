package de.max.gedaechtnis.leitner

import de.max.gedaechtnis.leitner.engine.CsvImport
import org.junit.Assert.*
import org.junit.Test

class CsvImportTest {
    @Test fun semicolonSeparated() {
        val r = CsvImport.parse("Hund;dog\nKatze;cat\n")
        assertEquals(listOf("Hund" to "dog", "Katze" to "cat"), r.cards)
        assertFalse(r.headerSkipped)
    }

    @Test fun headerLineIsSkipped() {
        val r = CsvImport.parse("Vorderseite;Rückseite\nHaus;house")
        assertTrue(r.headerSkipped)
        assertEquals(listOf("Haus" to "house"), r.cards)
        assertEquals(1, CsvImport.parse("front;back\na;b").cards.size)
    }

    @Test fun utf8UmlautsAndBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Vorderseite;Rückseite\nGrün;Übergrößenträger äöüß\n".toByteArray(Charsets.UTF_8)
        val r = CsvImport.parse(bytes)
        assertEquals(listOf("Grün" to "Übergrößenträger äöüß"), r.cards)
    }

    @Test fun latin1FallbackKeepsUmlauts() {
        val r = CsvImport.parse("Käse;cheese\n".toByteArray(Charsets.ISO_8859_1))
        assertEquals(listOf("Käse" to "cheese"), r.cards)
    }

    @Test fun duplicatesAreSkippedWithinFileAndAgainstDeck() {
        val r = CsvImport.parse("A;1\na;2\nB;3\nC;4", existingFronts = setOf("c "))
        assertEquals(listOf("A" to "1", "B" to "3"), r.cards)
        assertEquals(2, r.duplicates)
    }

    @Test fun quotedFieldsCrLfAndInvalidLines() {
        val r = CsvImport.parse("\"a;b\";\"x \"\"y\"\"\"\r\nnur eine Spalte\r\n;leer\r\n\r\nok;fertig")
        assertEquals(listOf("a;b" to "x \"y\"", "ok" to "fertig"), r.cards)
        assertEquals(2, r.invalid)
    }
}
