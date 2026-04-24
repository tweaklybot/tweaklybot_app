package com.example.tweakly.ui.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ViewerState(
    val item: MediaItem? = null,
    val allItems: List<MediaItem> = emptyList(),
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

    // Load current item + all siblings for pager swipe
    fun loadAll(id: Long) = viewModelScope.launch {
        _state.update { it.copy(isLoading = true) }
        val allEntities = mediaDao.getAllSynced()
        val allItems = allEntities.map { it.mapToUi() }
        val current = allItems.firstOrNull { it.id == id }
        _state.update { it.copy(item = current, allItems = allItems, isLoading = false) }
    }

    fun loadCurrent(id: Long) = viewModelScope.launch {
        val entity = mediaRepo.getById(id)
        entity?.let { _state.update { s -> s.copy(item = it.mapToUi()) } }
    }

    // ── OCR ──────────────────────────────────────────────────────────────────
    fun runOcr(context: Context) {
        val uri = _state.value.item?.uri ?: return
        if (_state.value.isOcrRunning) return
        _state.update { it.copy(isOcrRunning = true, ocrResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsedUri = Uri.parse(uri)
                // Try file-based first, fall back to bitmap
                val image = try {
                    InputImage.fromFilePath(context, parsedUri)
                } catch (e: Exception) {
                    val bmp = android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(parsedUri))
                    if (bmp != null) InputImage.fromBitmap(bmp, 0) else throw e
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                withContext(Dispatchers.Main) {
                    recognizer.process(image)
                        .addOnSuccessListener { result ->
                            val text = result.text.trim()
                            _state.update {
                                it.copy(ocrResult = text.ifEmpty { "Текст не обнаружен" }, isOcrRunning = false)
                            }
                        }
                        .addOnFailureListener { e ->
                            _state.update { it.copy(error = "OCR: ${e.message}", isOcrRunning = false) }
                        }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "OCR ошибка: ${e.message}", isOcrRunning = false) }
            }
        }
    }

    // ── QR ────────────────────────────────────────────────────────────────────
    fun scanQr(context: Context) {
        val uri = _state.value.item?.uri ?: return
        if (_state.value.isQrRunning) return
        _state.update { it.copy(isQrRunning = true, qrResult = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsedUri = Uri.parse(uri)
                val image = try {
                    InputImage.fromFilePath(context, parsedUri)
                } catch (e: Exception) {
                    val bmp = android.graphics.BitmapFactory.decodeStream(
                        context.contentResolver.openInputStream(parsedUri))
                    if (bmp != null) InputImage.fromBitmap(bmp, 0) else throw e
                }

                // Scan all barcode formats
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = BarcodeScanning.getClient(options)

                withContext(Dispatchers.Main) {
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val result = barcodes.firstOrNull()?.rawValue ?: "QR-код / штрих-код не найден"
                            _state.update { it.copy(qrResult = result, isQrRunning = false) }
                        }
                        .addOnFailureListener { e ->
                            _state.update { it.copy(error = "QR: ${e.message}", isQrRunning = false) }
                        }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "QR ошибка: ${e.message}", isQrRunning = false) }
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    fun deleteMedia(context: Context, launcher: ActivityResultLauncher<IntentSenderRequest>? = null) {
        val item = _state.value.item ?: return
        viewModelScope.launch {
            val uri = Uri.parse(item.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pi = android.provider.MediaStore.createDeleteRequest(
                        context.contentResolver, listOf(uri))
                    launcher?.launch(IntentSenderRequest.Builder(pi).build())
                } catch (e: Exception) {
                    // Fallback for some devices
                    val deleted = ImageUtils.deleteMediaFile(context, uri)
                    if (deleted) { mediaDao.deleteById(item.id); _state.update { it.copy(deleteSuccess = true) } }
                    else _state.update { it.copy(error = "Не удалось удалить файл") }
                }
            } else {
                val deleted = ImageUtils.deleteMediaFile(context, uri)
                if (deleted) { mediaDao.deleteById(item.id); _state.update { it.copy(deleteSuccess = true) } }
                else _state.update { it.copy(error = "Не удалось удалить файл") }
            }
        }
    }

    fun onDeleteConfirmed() {
        val id = _state.value.item?.id ?: return
        viewModelScope.launch {
            mediaDao.deleteById(id)
            _state.update { it.copy(deleteSuccess = true) }
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────
    fun share(context: Context) {
        val item = _state.value.item ?: return
        val mime = if (item.mediaType == MediaType.VIDEO) "video/*" else "image/*"
        context.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Поделиться"))
    }

    fun showDeleteDialog()  = _state.update { it.copy(showDeleteDialog = true) }
    fun hideDeleteDialog()  = _state.update { it.copy(showDeleteDialog = false) }
    fun showInfoDialog()    = _state.update { it.copy(showInfoDialog = true) }
    fun hideInfoDialog()    = _state.update { it.copy(showInfoDialog = false) }
    fun clearOcr()          = _state.update { it.copy(ocrResult = null) }
    fun clearQr()           = _state.update { it.copy(qrResult = null) }
    fun clearError()        = _state.update { it.copy(error = null) }
}
