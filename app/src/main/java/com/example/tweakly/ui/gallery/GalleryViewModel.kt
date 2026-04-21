package com.example.tweakly.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.PhotoUiModel
import com.example.tweakly.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class GalleryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val photosByDate: Map<String, List<PhotoUiModel>> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState(isLoading = true))
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        observePhotos()
        loadPhotos()
    }

    private fun observePhotos() {
        photoRepository.getPhotos()
            .onEach { photos ->
                val grouped = photos.groupBy { photo ->
                    formatDate(photo.dateTaken)
                }
                _uiState.update { it.copy(isLoading = false, photosByDate = grouped) }
            }
            .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            .launchIn(viewModelScope)
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                photoRepository.loadFromMediaStore()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                photoRepository.loadFromMediaStore()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "Без даты"
        val sdf = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        return sdf.format(Date(timestamp))
    }
}
