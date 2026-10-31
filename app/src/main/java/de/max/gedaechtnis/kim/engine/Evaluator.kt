package de.max.gedaechtnis.kim.engine

/** Wertet die Antwort des Spielers gegen die Runde aus. */
class Evaluator(private val catalog: Catalog) {

    fun evaluate(round: Round, answer: Answer): Evaluation {
        val raw = when (round.config.mode) {
            Mode.CHANGED -> changed(round, (answer as? Answer.Positions)?.marked.orEmpty())
            Mode.MISSING -> missing(round, (answer as? Answer.Objects)?.ids.orEmpty())
            Mode.RECONSTRUCTION -> reconstruction(round, (answer as? Answer.Placement)?.placed.orEmpty())
            Mode.FREE_RECALL -> freeRecall(round, (answer as? Answer.Names)?.bases.orEmpty())
            Mode.SEQUENCE -> sequence(round, (answer as? Answer.Order)?.ids.orEmpty())
        }
        val lost = round.config.oneErrorLosesRound && (raw.falseClicks + raw.missed > 0)
        return raw.copy(lost = lost)
    }

    private fun changed(round: Round, marked: Set<Int>): Evaluation {
        val real = round.realChanges
        val realPositions = real.map { it.position }.toSet()
        val details = mutableListOf<Detail>()
        var hits = 0
        var fc = 0
        var missed = 0
        if (real.isEmpty()) {
            // „Keine Änderung“: richtig, wenn nichts markiert wurde.
            if (marked.isEmpty()) {
                return Evaluation(1, 0, 0, emptyList(), false)
            }
        }
        for (c in real) {
            if (c.position in marked) {
                hits++
                details += Detail(c.position, c.type, c.before, c.after, Outcome.FOUND)
            } else {
                missed++
                details += Detail(c.position, c.type, c.before, c.after, Outcome.MISSED)
            }
        }
        for (pos in marked.sorted()) {
            if (pos in realPositions) continue
            fc++
            val fake = round.fakes.firstOrNull { it.position == pos }
            val cell = round.original.getOrNull(pos)
            details += Detail(pos, fake?.type, cell?.obj, cell?.obj, Outcome.FALSE_CLICK)
        }
        return Evaluation(hits, fc, missed, details.sortedBy { it.position }, false)
    }

    private fun missing(round: Round, selected: Set<String>): Evaluation {
        val targets = round.realChanges.filter { it.type == ChangeType.REMOVED }
        val targetIds = targets.mapNotNull { it.before?.id }.toSet()
        var hits = 0
        var missed = 0
        val details = mutableListOf<Detail>()
        for (c in targets) {
            if (c.before?.id in selected) {
                hits++
                details += Detail(c.position, c.type, c.before, null, Outcome.FOUND)
            } else {
                missed++
                details += Detail(c.position, c.type, c.before, null, Outcome.MISSED)
            }
        }
        var fc = 0
        for (id in selected - targetIds) {
            fc++
            details += Detail(-1, null, catalog[id], null, Outcome.FALSE_CLICK)
        }
        return Evaluation(hits, fc, missed, details, false)
    }

    private fun reconstruction(round: Round, placed: Map<Int, String>): Evaluation {
        val cols = round.config.columns
        val exact = round.config.exactPositions
        var hits = 0
        var fc = 0
        val details = mutableListOf<Detail>()
        val correctPlacedPositions = mutableSetOf<Int>()
        val expected = round.testedPositions.associateWith { round.original[it].obj }
        for ((pos, id) in placed.entries.sortedBy { it.key }) {
            val obj = catalog[id]
            val want = expected[pos]
            val ok = want?.id == id || (!exact && obj != null && expected.any { (p, o) ->
                o.id == id && isNeighbour(p, pos, cols)
            })
            if (ok) {
                hits++
                correctPlacedPositions += expected.entries.first { it.value.id == id }.key
                details += Detail(pos, null, obj, obj, Outcome.FOUND)
            } else {
                fc++
                details += Detail(pos, null, want, obj, Outcome.FALSE_CLICK)
            }
        }
        var missed = 0
        for ((pos, obj) in expected) {
            if (pos !in correctPlacedPositions && placed.values.none { it == obj.id }) {
                missed++
                details += Detail(pos, null, obj, null, Outcome.MISSED)
            }
        }
        return Evaluation(hits, fc, missed, details.sortedBy { it.position }, false)
    }

    private fun isNeighbour(a: Int, b: Int, cols: Int): Boolean {
        val dr = Math.abs(a / cols - b / cols)
        val dc = Math.abs(a % cols - b % cols)
        return dr <= 1 && dc <= 1
    }

    private fun freeRecall(round: Round, named: Set<String>): Evaluation {
        val targets = round.original.map { it.obj.baseObject }.toSet()
        var hits = 0
        var missed = 0
        var fc = 0
        val details = mutableListOf<Detail>()
        for ((i, cell) in round.original.withIndex()) {
            if (cell.obj.baseObject in named) {
                hits++
                details += Detail(i, null, cell.obj, cell.obj, Outcome.FOUND)
            } else {
                missed++
                details += Detail(i, null, cell.obj, null, Outcome.MISSED)
            }
        }
        for (b in named - targets) {
            fc++
            details += Detail(-1, null, null, null, Outcome.FALSE_CLICK, label = catalog.label(b))
        }
        return Evaluation(hits, fc, missed, details, false)
    }

    private fun sequence(round: Round, given: List<String>): Evaluation {
        val expected = if (round.config.reverseSequence) round.sequence.reversed() else round.sequence
        var hits = 0
        var fc = 0
        var missed = 0
        val details = mutableListOf<Detail>()
        for ((i, want) in expected.withIndex()) {
            val got = given.getOrNull(i)
            when {
                got == null -> { missed++; details += Detail(i, null, want, null, Outcome.MISSED) }
                got == want.id -> { hits++; details += Detail(i, null, want, want, Outcome.FOUND) }
                else -> { fc++; details += Detail(i, null, want, catalog[got], Outcome.FALSE_CLICK) }
            }
        }
        return Evaluation(hits, fc, missed, details, false)
    }
}
