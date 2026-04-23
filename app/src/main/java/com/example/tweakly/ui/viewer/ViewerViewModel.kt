package com.example.tweakly.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.local.dao.MediaDao
import com.example.tweakly.data.local.entity.SyncStatus
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.model.SyncStatusUi
import com.example.tweakly.data.repository.MediaRepository
import com.example.tweakly.data.repository.MediaRepository.Companion.mapToUi
import com.example.tweakly.utils.ImageUtils
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
    val deleteSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val mediaRepo: MediaRepository,
    private val mediaDao: MediaDao
) : ViewModel() {

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    fun load(id: Long) = viewModelScope.launch {
        val entity = mediaRepo.getById(id)
        if (entity != null) {
            _state.update { it.copy(item = entity.mapToUi(), isLoading = false) }
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
                            it.copy(ocrResult = result.text.ifBlank { "Текст не обнаружен" },
                                isOcrRunning = false)
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

    // ── QR ───────────────────────────────────────────────────────────────────
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

    // ── Delete from device ────────────────────────────────────────────────────
    fun deleteMedia(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>? = null
    ) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val uri = Uri.parse(item.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: request deletion via system dialog
                val pendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(uri)
                )
                launcher?.launch(IntentSenderRequest.Builder(pendingIntent).build())
            } else {
                // API 24-29: direct delete via ContentResolver
                val deleted = ImageUtils.deleteMediaFile(context, uri)
                if (deleted) {
                    mediaDao.deleteById(item.id)
                    _state.update { it.copy(deleteSuccess = true) }
                } else {
                    _state.update { it.copy(error = "Не удалось удалить файл") }
                }
            }
        }
    }

    /** Called after API 30+ system delete dialog returns OK */
    fun onDeleteConfirmed() {
        val id = _state.value.item?.id ?: return
        viewModelScope.launch {
            mediaDao.deleteById(id)
            _state.update { it.copy(deleteSuccess = true) }
        }
    }

    // ── Share ────────────────────────────────────────────────────────────────
    fun share(context: Context) {
        val item = _state.value.item ?: return
        val mime = if (item.mediaType == MediaType.VIDEO) "video/*" else "image/*"
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Поделиться"
        ))
    }

    fun showDeleteDialog()  = _state.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog()  = _state.update { it.copy(showDeleteDialog = false) }
    fun showInfoDialog()    = _state.update { it.copy(showInfoDialog = true) }
    fun hideInfoDialog()    = _state.update { it.copy(showInfoDialog = false) }
    fun clearOcr()          = _state.update { it.copy(ocrResult = null) }
    fun clearQr()           = _state.update { it.copy(qrResult = null) }
    fun clearError()        = _state.update { it.copy(error = null) }
}
