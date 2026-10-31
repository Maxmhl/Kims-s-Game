package de.max.gedaechtnis

import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.kim.engine.Catalog
import java.io.File

/** Lädt die echten Assets, damit Tests die tatsächlichen Zahlen prüfen. */
object TestData {
    private fun asset(name: String): String {
        val candidates = listOf("src/main/assets/$name", "app/src/main/assets/$name")
        return candidates.map(::File).first { it.exists() }.readText()
    }

    val balancing: Balancing by lazy { Balancing.parse(asset("balancing.json")) }
    val catalog: Catalog by lazy { Catalog.parse(asset("catalog.json")) }
}
