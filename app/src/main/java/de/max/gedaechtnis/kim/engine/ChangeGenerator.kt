package de.max.gedaechtnis.kim.engine

import kotlin.random.Random

/** Wendet die Änderungen einer Runde auf das Raster an. */
class ChangeGenerator(private val catalog: Catalog) {

    /**
     * Verändert [changed] (Kopie von [original]) und liefert die Änderungen, inklusive Fake-Änderungen.
     * Jedes Feld wird höchstens einmal verändert; es werden nur gültige Positionen verwendet.
     */
    fun apply(config: RoundConfig, original: List<Cell>, changed: MutableList<Cell?>, rng: Random): List<Change> {
        val n = original.size
        var budget = if (config.changeMax > config.changeMin) rng.nextInt(config.changeMin, config.changeMax + 1) else config.changeMin
        budget = budget.coerceAtMost(n)
        val types = config.changeTypes.filter { it != ChangeType.FAKE }
        val used = mutableSetOf<Int>()
        val changes = mutableListOf<Change>()
        val inGrid = original.map { it.obj.id }.toMutableSet()

        var guard = 0
        while (budget > 0 && types.isNotEmpty() && guard++ < 500) {
            val free = (0 until n).filter { it !in used }
            if (free.isEmpty()) break
            val type = types[rng.nextInt(types.size)]
            val pos = free[rng.nextInt(free.size)]
            val cell = original[pos]
            when (type) {
                ChangeType.REMOVED -> {
                    changed[pos] = null
                    changes += Change(type, pos, cell.obj, null, cell.rotation, 0)
                    used += pos
                    budget -= 1
                }
                ChangeType.REPLACED -> {
                    val sameGroup = rng.nextDouble() < config.similarity
                    val pool = catalog.objects.filter {
                        it.id !in inGrid && (!sameGroup || it.similarityGroup == cell.obj.similarityGroup)
                    }.ifEmpty { catalog.objects.filter { it.id !in inGrid } }
                    if (pool.isEmpty()) continue
                    val repl = pool[rng.nextInt(pool.size)]
                    val rot = if (repl.rotatable) cell.rotation else 0
                    changed[pos] = Cell(repl, rot)
                    inGrid += repl.id
                    changes += Change(type, pos, cell.obj, repl, cell.rotation, rot)
                    used += pos
                    budget -= 1
                }
                ChangeType.SWAPPED -> {
                    if (budget < 2) continue
                    val other = free.filter { it != pos && original[it].obj.id != cell.obj.id }
                    if (other.isEmpty()) continue
                    val p2 = other[rng.nextInt(other.size)]
                    val c2 = original[p2]
                    changed[pos] = c2
                    changed[p2] = cell
                    changes += Change(type, pos, cell.obj, c2.obj, cell.rotation, c2.rotation)
                    changes += Change(type, p2, c2.obj, cell.obj, c2.rotation, cell.rotation)
                    used += pos
                    used += p2
                    budget -= 2
                }
                ChangeType.RECOLORED -> {
                    val variants = catalog.variantsOf(cell.obj).filter { it.id !in inGrid }
                    if (variants.isEmpty()) continue
                    val v = variants[rng.nextInt(variants.size)]
                    changed[pos] = Cell(v, cell.rotation)
                    inGrid += v.id
                    changes += Change(type, pos, cell.obj, v, cell.rotation, cell.rotation)
                    used += pos
                    budget -= 1
                }
                ChangeType.ROTATED -> {
                    if (!cell.obj.rotatable) continue
                    val options = listOf(0, 45, 90).filter { it != cell.rotation }
                    val rot = options[rng.nextInt(options.size)]
                    changed[pos] = Cell(cell.obj, rot)
                    changes += Change(type, pos, cell.obj, cell.obj, cell.rotation, rot)
                    used += pos
                    budget -= 1
                }
                ChangeType.FAKE -> Unit
            }
        }

        // Fake-Änderungen: bereits beim Einprägen gedrehte Objekte, die unverändert bleiben.
        if (config.fakeCount > 0 && ChangeType.FAKE in config.changeTypes) {
            val candidates = (0 until n).filter { it !in used && original[it].rotation != 0 }.shuffled(rng)
            for (pos in candidates.take(config.fakeCount)) {
                val c = original[pos]
                changes += Change(ChangeType.FAKE, pos, c.obj, c.obj, c.rotation, c.rotation)
            }
        }
        return changes.sortedBy { it.position }
    }
}
