package com.example.tweakly.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.model.SyncStatusUi
import com.example.tweakly.data.repository.MediaRepository
import com.example.tweakly.data.repository.MediaRepository.Companion.toUi
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewerState(
    val item: MediaItem? = null,
    val isLoading: Boolean = true,
    val ocrResult: String? = null,
    val qrResult: String? = null,
    val isOcrRunning: Boolean = false,
    val isQrRunning: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val mediaRepo: MediaRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    fun load(id: Long) = viewModelScope.launch {
        val entity = mediaRepo.getById(id)
        if (entity != null) {
            _state.update { it.copy(item = entity.toUi(), isLoading = false) }
        } else {
            _state.update { it.copy(isLoading = false, error = "Файл не найден") }
        }
    }

    // ── OCR ─────────────────────────────────────────────────────────────────
    fun runOcr(context: Context) {
        val uri = _state.value.item?.uri ?: return
        _state.update { it.copy(isOcrRunning = true) }
        viewModelScope.launch {
            try {
                val image = InputImage.fromFilePath(context, Uri.parse(uri))
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        _state.update {
                            it.copy(
                                ocrResult = result.text.ifBlank { "Текст не обнаружен" },
                                isOcrRunning = false
                            )
                        }
                    }
                    .addOnFailureListener { e ->
                        _state.update { it.copy(error = "OCR: ${e.message}", isOcrRunning = false) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = "OCR: ${e.message}", isOcrRunning = false) }
            }
        }
    }

    // ── QR Scan ──────────────────────────────────────────────────────────────
    fun scanQr(context: Context) {
        val uri = _state.value.item?.uri ?: return
        _state.update { it.copy(isQrRunning = true) }
        viewModelScope.launch {
            try {
                val image = InputImage.fromFilePath(context, Uri.parse(uri))
                BarcodeScanning.getClient()
                    .process(image)
                    .addOnSuccessListener { barcodes ->
                        val result = barcodes.firstOrNull()?.rawValue ?: "QR-код не найден"
                        _state.update { it.copy(qrResult = result, isQrRunning = false) }
                    }
                    .addOnFailureListener { e ->
                        _state.update { it.copy(error = "QR: ${e.message}", isQrRunning = false) }
                    }
            } catch (e: Exception) {
                _state.update { it.copy(error = "QR: ${e.message}", isQrRunning = false) }
            }
        }
    }

    // ── Share ────────────────────────────────────────────────────────────────
    fun share(context: Context) {
        val item = _state.value.item ?: return
        val mimeType = if (item.mediaType == MediaType.VIDEO) "video/*" else "image/*"
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Поделиться"
        ))
    }

    // ── Dialog controls ──────────────────────────────────────────────────────
    fun showDeleteDialog()  = _state.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog()  = _state.update { it.copy(showDeleteDialog = false) }
    fun showInfoDialog()    = _state.update { it.copy(showInfoDialog = true) }
    fun hideInfoDialog()    = _state.update { it.copy(showInfoDialog = false) }
    fun clearOcr()          = _state.update { it.copy(ocrResult = null) }
    fun clearQr()           = _state.update { it.copy(qrResult = null) }
    fun clearError()        = _state.update { it.copy(error = null) }
}
