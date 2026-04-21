package com.example.tweakly.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.PhotoUiModel
import com.example.tweakly.data.model.SyncStatusUi
import com.example.tweakly.data.repository.PhotoRepository
// import com.google.mlkit.vision.common.InputImage
// import com.google.mlkit.vision.text.TextRecognition
// import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerUiState(
    val photo: PhotoUiModel? = null,
    val isLoading: Boolean = true,
    val ocrResult: String? = null,
    val isOcrLoading: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    fun loadPhoto(photoId: Long) {
        viewModelScope.launch {
            val entity = photoRepository.getPhotoById(photoId)
            if (entity != null) {
                val photo = PhotoUiModel(
                    id = entity.id, uri = entity.uri, displayName = entity.displayName,
                    dateTaken = entity.dateTaken, size = entity.size, width = entity.width,
                    height = entity.height,
                    syncStatus = when (entity.syncStatus) {
                        com.example.tweakly.data.local.entity.SyncStatus.SYNCED -> SyncStatusUi.SYNCED
                        com.example.tweakly.data.local.entity.SyncStatus.FAILED -> SyncStatusUi.FAILED
                        else -> SyncStatusUi.PENDING
                    },
                    remotePath = entity.remotePath
                )
                _uiState.update { it.copy(photo = photo, isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Фото не найдено") }
            }
        }
    }

    fun performOcr(context: Context) {
        // Временно отключено, пока не добавлены зависимости ML Kit
        _uiState.update { it.copy(isOcrLoading = true) }
        viewModelScope.launch {
            // Заглушка
            _uiState.update { 
                it.copy(
                    ocrResult = "Распознавание текста временно недоступно",
                    isOcrLoading = false
                ) 
            }
        }
        /*
        // Рабочий код с ML Kit (раскомментируйте после добавления зависимостей)
        val uri = _uiState.value.photo?.uri ?: return
        _uiState.update { it.copy(isOcrLoading = true) }
        viewModelScope.launch {
            try {
                val image = InputImage.fromFilePath(context, Uri.parse(uri))
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        _uiState.update { it.copy(ocrResult = visionText.text.ifEmpty { "Текст не найден" }, isOcrLoading = false) }
                    }
                    .addOnFailureListener { e ->
                        _uiState.update { it.copy(error = e.message, isOcrLoading = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, isOcrLoading = false) }
            }
        }
        */
    }

    fun sharePhoto(context: Context) {
        val uri = _uiState.value.photo?.uri ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Поделиться фото"))
    }

    fun showDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }
    fun showInfoDialog() = _uiState.update { it.copy(showInfoDialog = true) }
    fun hideInfoDialog() = _uiState.update { it.copy(showInfoDialog = false) }
    fun clearOcrResult() = _uiState.update { it.copy(ocrResult = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
