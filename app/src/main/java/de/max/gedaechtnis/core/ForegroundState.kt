package de.max.gedaechtnis.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ist die App im Vordergrund? Timer pausieren, solange sie es nicht ist. */
@Singleton
class ForegroundState @Inject constructor() {
    private val _foreground = MutableStateFlow(true)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    fun set(value: Boolean) { _foreground.value = value }
}
