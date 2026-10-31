package de.max.gedaechtnis.core

import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject
import javax.inject.Singleton

/** Kurze Rückmeldungstöne ohne Audiodateien und ohne Berechtigung. */
@Singleton
class Sounds @Inject constructor() {
    private val generator: ToneGenerator? by lazy {
        runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
    }

    fun correct() { generator?.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
    fun wrong() { generator?.startTone(ToneGenerator.TONE_PROP_NACK, 160) }
    fun finished() { generator?.startTone(ToneGenerator.TONE_PROP_BEEP, 90) }
}
