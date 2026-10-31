package de.max.gedaechtnis.kim.engine

import kotlin.random.Random

/** Erzeugt Runden. Gleicher Seed und gleiche Konfiguration ergeben immer dieselbe Runde. */
class RoundGenerator(private val catalog: Catalog, private val balancing: Balancing) {

    fun generate(config: RoundConfig): Round {
        val rng = Random(config.seed)
        val uniqueBases = config.mode == Mode.FREE_RECALL || config.mode == Mode.SEQUENCE
        val objects = pickObjects(config.objectCount, config.similarity, uniqueBases, rng)
        val original = objects.map { Cell(it, 0) }.toMutableList()

        // Grundrotation: ab Stufen mit Drehung sind einige Objekte schon beim Einprägen gedreht.
        val rotates = ChangeType.ROTATED in config.changeTypes && config.mode == Mode.CHANGED
        if (rotates) {
            for (i in original.indices) {
                val o = original[i].obj
                if (o.rotatable && rng.nextDouble() < 0.2) {
                    original[i] = Cell(o, if (rng.nextBoolean()) 45 else 90)
                }
            }
        }

        return when (config.mode) {
            Mode.MISSING, Mode.CHANGED -> changeRound(config, original, rng)
            Mode.RECONSTRUCTION -> reconstructionRound(config, original, rng)
            Mode.FREE_RECALL -> Round(config, original, original.toList(), emptyList(), emptyList(), emptyList(), emptyList())
            Mode.SEQUENCE -> sequenceRound(config, original, rng)
        }
    }

    private fun changeRound(config: RoundConfig, original: List<Cell>, rng: Random): Round {
        val changed = original.toMutableList<Cell?>()
        val changes = ChangeGenerator(catalog).apply(config, original, changed, rng)
        var options: List<GameObject> = emptyList()
        if (config.mode == Mode.MISSING) {
            val missing = changes.filter { it.type == ChangeType.REMOVED }.mapNotNull { it.before }
            val inGrid = original.map { it.obj.id }.toSet()
            val decoys = pickDecoys(missing.size, inGrid, missing.firstOrNull()?.similarityGroup, config.similarity, rng)
            options = (missing + decoys).shuffled(rng)
        }
        return Round(config, original, changed, changes, emptyList(), options, emptyList())
    }

    private fun reconstructionRound(config: RoundConfig, original: List<Cell>, rng: Random): Round {
        val r = balancing.reconstruction
        val tested = (original.size * r.testFraction).toInt().coerceIn(r.minTested, r.maxTested).coerceAtMost(original.size)
        val positions = original.indices.shuffled(rng).take(tested).sorted()
        val options = positions.map { original[it].obj }.shuffled(rng)
        return Round(config, original, original.toList(), emptyList(), emptyList(), options, positions)
    }

    private fun sequenceRound(config: RoundConfig, original: List<Cell>, rng: Random): Round {
        val seq = original.map { it.obj }
        val inSeq = seq.map { it.id }.toSet()
        val decoys = pickDecoys(seq.size, inSeq, seq.first().similarityGroup, config.similarity, rng, uniqueBases = true)
        val options = (seq + decoys).shuffled(rng)
        return Round(config, original, original.toList(), emptyList(), seq, options, emptyList())
    }

    /** Wählt [n] Objekte; der Anteil [similarity] stammt aus einer gemeinsamen Ähnlichkeitsgruppe. */
    fun pickObjects(n: Int, similarity: Double, uniqueBases: Boolean, rng: Random): List<GameObject> {
        val chosen = mutableListOf<GameObject>()
        val usedBases = mutableSetOf<String>()
        fun tryAdd(o: GameObject): Boolean {
            if (chosen.any { it.id == o.id }) return false
            if (uniqueBases && o.baseObject in usedBases) return false
            chosen += o
            usedBases += o.baseObject
            return true
        }

        val want = Math.round(n * similarity).toInt().coerceIn(0, n)
        val primary = if (want > 0) catalog.groups.keys.sorted()[rng.nextInt(catalog.groups.size)] else null
        if (primary != null) {
            for (o in catalog.groups.getValue(primary).shuffled(rng)) {
                if (chosen.size >= want) break
                tryAdd(o)
            }
        }
        val others = catalog.objects.filter { it.similarityGroup != primary }.shuffled(rng)
        for (o in others) {
            if (chosen.size >= n) break
            tryAdd(o)
        }
        if (chosen.size < n) {
            for (o in catalog.objects.shuffled(rng)) {
                if (chosen.size >= n) break
                tryAdd(o)
            }
        }
        return chosen.shuffled(rng)
    }

    private fun pickDecoys(
        n: Int,
        excludeIds: Set<String>,
        preferGroup: String?,
        similarity: Double,
        rng: Random,
        uniqueBases: Boolean = false,
    ): List<GameObject> {
        val excludedBases = if (uniqueBases) {
            catalog.objects.filter { it.id in excludeIds }.map { it.baseObject }.toSet()
        } else emptySet()
        val pool = catalog.objects.filter { it.id !in excludeIds && it.baseObject !in excludedBases }.shuffled(rng)
        val similar = if (preferGroup != null) pool.filter { it.similarityGroup == preferGroup } else emptyList()
        val result = mutableListOf<GameObject>()
        val wantSimilar = Math.round(n * similarity).toInt()
        for (o in similar) {
            if (result.size >= wantSimilar) break
            if (uniqueBases && result.any { it.baseObject == o.baseObject }) continue
            result += o
        }
        for (o in pool) {
            if (result.size >= n) break
            if (result.any { it.id == o.id }) continue
            if (uniqueBases && result.any { it.baseObject == o.baseObject }) continue
            result += o
        }
        return result
    }
}
