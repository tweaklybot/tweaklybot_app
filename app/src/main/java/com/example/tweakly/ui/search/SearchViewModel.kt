package com.example.tweakly.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(val results: List<MediaItem> = emptyList(), val isLoading: Boolean = false)

@HiltViewModel
class SearchViewModel @Inject constructor(private val mediaRepo: MediaRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(isLoading = true) }
            val results = mediaRepo.searchByName(query)
            _state.update { it.copy(results = results, isLoading = false) }
        }
    }

    fun clearResults() { searchJob?.cancel(); _state.value = SearchState() }
}
