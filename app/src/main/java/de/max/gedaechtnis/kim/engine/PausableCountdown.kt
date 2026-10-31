package de.max.gedaechtnis.kim.engine

/** Countdown mit Pause; die Zeitquelle wird übergeben, damit er testbar bleibt. */
class PausableCountdown(val totalMs: Long, private val now: () -> Long) {
    private var accumulated = 0L
    private var runningSince: Long? = null

    fun start() { if (runningSince == null) runningSince = now() }

    fun pause() {
        runningSince?.let { accumulated += now() - it }
        runningSince = null
    }

    val isRunning: Boolean get() = runningSince != null
    val elapsedMs: Long get() = accumulated + (runningSince?.let { now() - it } ?: 0L)
    val remainingMs: Long get() = (totalMs - elapsedMs).coerceAtLeast(0)
    val isFinished: Boolean get() = elapsedMs >= totalMs
    val fraction: Float get() = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs)
}
