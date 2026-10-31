package de.max.gedaechtnis.kim.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogEntry(
    val id: String,
    val category: String,
    val base: String,
    val label: String,
    val variant: String,
    val color: String,
    val icon: String,
    val group: String,
    val rotatable: Boolean = true,
)

/** Objekt-Katalog (assets/catalog.json). */
class Catalog(val objects: List<GameObject>) {
    private val byId = objects.associateBy { it.id }
    val groups: Map<String, List<GameObject>> = objects.groupBy { it.similarityGroup }
    val bases: List<String> = objects.map { it.baseObject }.distinct()
    private val labelByBase = objects.associate { it.baseObject to it.label }

    operator fun get(id: String): GameObject? = byId[id]
    fun label(base: String): String = labelByBase[base] ?: base

    /** Alle Farbvarianten desselben Grundobjekts. */
    fun variantsOf(obj: GameObject): List<GameObject> =
        objects.filter { it.baseObject == obj.baseObject && it.id != obj.id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Catalog {
            val entries = json.decodeFromString<List<CatalogEntry>>(text)
            return Catalog(
                entries.map {
                    GameObject(
                        id = it.id, category = it.category, baseObject = it.base, variant = it.variant,
                        color = ObjColor.valueOf(it.color.uppercase()), iconRes = it.icon,
                        similarityGroup = it.group, label = it.label, rotatable = it.rotatable,
                    )
                },
            )
        }
    }
}
