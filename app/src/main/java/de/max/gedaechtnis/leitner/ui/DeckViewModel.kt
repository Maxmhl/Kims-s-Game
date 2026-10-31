package de.max.gedaechtnis.leitner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.max.gedaechtnis.data.Card
import de.max.gedaechtnis.data.Clock
import de.max.gedaechtnis.data.Deck
import de.max.gedaechtnis.data.LeitnerRepository
import de.max.gedaechtnis.data.SettingsRepository
import de.max.gedaechtnis.kim.engine.Balancing
import de.max.gedaechtnis.leitner.engine.CsvImport
import de.max.gedaechtnis.leitner.engine.Direction
import de.max.gedaechtnis.leitner.engine.LeitnerMode
import de.max.gedaechtnis.leitner.engine.Scheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckUiState(
    val decks: List<Deck> = emptyList(),
    val selected: Deck? = null,
    val cards: List<Card> = emptyList(),
    /** Anzahl Einträge je Fach (Index 0 = Fach 1). */
    val boxCounts: List<Int> = emptyList(),
    val intervals: List<Int> = emptyList(),
    val due: Int = 0,
    val total: Int = 0,
)

sealed interface ImportMessage {
    data class Done(val imported: Int, val duplicates: Int, val invalid: Int) : ImportMessage
    data object Failed : ImportMessage
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeckViewModel @Inject constructor(
    private val repo: LeitnerRepository,
    private val settings: SettingsRepository,
    private val scheduler: Scheduler,
    private val clock: Clock,
) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)
    private val _message = MutableStateFlow<ImportMessage?>(null)
    val message: StateFlow<ImportMessage?> = _message

    val state: StateFlow<DeckUiState> = combine(repo.deckList, selectedId, settings.settings) { decks, sel, st ->
        val id = sel ?: st.lastDeckId
        decks to (decks.firstOrNull { it.id == id } ?: decks.firstOrNull())
    }.flatMapLatest { (decks, deck) ->
        val cardsFlow: Flow<List<Card>> = deck?.let { repo.cards(it.id) } ?: flowOf(emptyList())
        cardsFlow.combine(flowOf(clock.today())) { cards, today ->
            if (deck == null) return@combine DeckUiState(decks = decks)
            val spec = scheduler.spec(deck.mode)
            val visible = cards.filter { it.direction == Direction.FORWARD || deck.mode == LeitnerMode.STRICT }
            val counts = (1..spec.boxes).map { b -> visible.count { it.box.coerceIn(1, spec.boxes) == b } }
            DeckUiState(
                decks = decks, selected = deck, cards = cards, boxCounts = counts, intervals = spec.intervals,
                due = visible.count { it.nextDue <= today }, total = visible.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckUiState())

    fun select(id: Long) { selectedId.value = id; viewModelScope.launch { settings.setLastDeck(id) } }

    fun createDeck(name: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = repo.createDeck(name, scheduler.let { _ -> LeitnerMode.STRICT })
        select(id)
    }

    fun rename(name: String) = viewModelScope.launch { state.value.selected?.let { if (name.isNotBlank()) repo.renameDeck(it.id, name) } }
    fun deleteDeck() = viewModelScope.launch { state.value.selected?.let { repo.deleteDeck(it.id); selectedId.value = null } }
    fun setMode(mode: LeitnerMode) = viewModelScope.launch { state.value.selected?.let { repo.setMode(it.id, mode) } }

    fun saveCard(existing: Card?, front: String, back: String, imageUri: String?) = viewModelScope.launch {
        val deck = state.value.selected ?: return@launch
        if (front.isBlank() || back.isBlank()) return@launch
        if (existing == null) repo.addCard(deck.id, front, back, imageUri) else repo.editCard(existing, front, back, imageUri)
    }

    fun deleteCard(card: Card) = viewModelScope.launch { repo.deleteCard(card) }

    /** Liest CSV-Bytes (UTF-8) und legt die Karten an; Duplikate werden übersprungen. */
    fun importCsv(bytes: ByteArray?) = viewModelScope.launch {
        val deck = state.value.selected
        if (bytes == null || deck == null) { _message.value = ImportMessage.Failed; return@launch }
        val result = CsvImport.parse(bytes, repo.existingFronts(deck.id))
        val n = repo.importCsv(deck.id, result)
        _message.value = ImportMessage.Done(n, result.duplicates, result.invalid)
    }

    fun clearMessage() { _message.value = null }
}
