package com.example.tweakly.ui.editor

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.utils.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class EditorFilter(val label: String) {
    NONE("Оригинал"), GRAYSCALE("Ч/Б"), SEPIA("Сепия"),
    VINTAGE("Винтаж"), COLD("Холодный"), WARM("Тёплый"), VIVID("Яркий")
}

data class DrawPath(val points: List<Pair<Float,Float>>, val color: Color, val strokeWidth: Float)
data class TextOverlay(val text: String, val x: Float, val y: Float, val color: Color, val size: Float)
data class EditorSnapshot(val filter: EditorFilter, val rotation: Float, val brightness: Float, val contrast: Float, val drawPaths: List<DrawPath>, val texts: List<TextOverlay>)

data class EditorState(
    val originalBitmap: Bitmap? = null, val displayBitmap: Bitmap? = null,
    val isLoading: Boolean = true, val filter: EditorFilter = EditorFilter.NONE,
    val rotation: Float = 0f, val brightness: Float = 0f, val contrast: Float = 1f,
    val drawPaths: List<DrawPath> = emptyList(), val texts: List<TextOverlay> = emptyList(),
    val isSaving: Boolean = false, val savedUri: Uri? = null, val error: String? = null,
    val drawColor: Color = Color.Red, val drawStroke: Float = 8f,
    val isDrawMode: Boolean = false, val isTextMode: Boolean = false,
    val undoStack: List<EditorSnapshot> = emptyList()
)

@HiltViewModel
class PhotoEditorViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    fun loadImage(context: Context, uri: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.update { it.copy(isLoading = true) }
        val bmp = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.let { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        _state.update { it.copy(originalBitmap = bmp, displayBitmap = bmp?.copy(Bitmap.Config.ARGB_8888, true), isLoading = false) }
        applyEdits()
    }

    fun setFilter(f: EditorFilter) { pushUndo(); _state.update { it.copy(filter = f) }; applyEdits() }
    fun rotate(d: Float = 90f)     { pushUndo(); _state.update { it.copy(rotation = (it.rotation + d) % 360f) }; applyEdits() }
    fun autoCorrect()              { pushUndo(); _state.update { it.copy(brightness = 0.1f, contrast = 1.15f) }; applyEdits() }
    fun setBrightness(v: Float)    { _state.update { it.copy(brightness = v) }; applyEdits() }
    fun setContrast(v: Float)      { _state.update { it.copy(contrast = v) }; applyEdits() }
    fun addDrawPath(p: DrawPath)   { pushUndo(); _state.update { it.copy(drawPaths = it.drawPaths + p) }; applyEdits() }
    fun addText(t: TextOverlay)    { pushUndo(); _state.update { it.copy(texts = it.texts + t) }; applyEdits() }
    fun setDrawColor(c: Color)     = _state.update { it.copy(drawColor = c) }
    fun setDrawStroke(s: Float)    = _state.update { it.copy(drawStroke = s) }
    fun toggleDraw()               = _state.update { it.copy(isDrawMode = !it.isDrawMode, isTextMode = false) }
    fun toggleText()               = _state.update { it.copy(isTextMode = !it.isTextMode, isDrawMode = false) }

    fun undo() {
        val stack = _state.value.undoStack; if (stack.isEmpty()) return
        val s = stack.last()
        _state.update { it.copy(filter=s.filter, rotation=s.rotation, brightness=s.brightness, contrast=s.contrast, drawPaths=s.drawPaths, texts=s.texts, undoStack=stack.dropLast(1)) }
        applyEdits()
    }

    private fun pushUndo() {
        val s = _state.value
        _state.update { it.copy(undoStack = (it.undoStack + EditorSnapshot(s.filter,s.rotation,s.brightness,s.contrast,s.drawPaths,s.texts)).takeLast(20)) }
    }

    private fun applyEdits() = viewModelScope.launch(Dispatchers.Default) {
        val s = _state.value; val src = s.originalBitmap ?: return@launch
        var bmp = filterBitmap(src, s.filter, s.brightness, s.contrast)
        if (s.rotation != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(s.rotation) }, true)
        if (s.drawPaths.isNotEmpty() || s.texts.isNotEmpty()) bmp = overlayBitmap(bmp, s.drawPaths, s.texts)
        _state.update { it.copy(displayBitmap = bmp) }
    }

    private fun filterBitmap(src: Bitmap, f: EditorFilter, b: Float, c: Float): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix(); val bv = b * 255f
        cm.set(floatArrayOf(c,0f,0f,0f,bv, 0f,c,0f,0f,bv, 0f,0f,c,0f,bv, 0f,0f,0f,1f,0f))
        when (f) {
            EditorFilter.GRAYSCALE -> cm.postConcat(ColorMatrix().also { it.setSaturation(0f) })
            EditorFilter.SEPIA -> cm.postConcat(ColorMatrix(floatArrayOf(0.393f,0.769f,0.189f,0f,0f,0.349f,0.686f,0.168f,0f,0f,0.272f,0.534f,0.131f,0f,0f,0f,0f,0f,1f,0f)))
            EditorFilter.VINTAGE -> cm.setSaturation(0.6f)
            EditorFilter.COLD -> cm.postConcat(ColorMatrix(floatArrayOf(0.8f,0f,0f,0f,0f,0f,0.9f,0f,0f,0f,0f,0f,1.2f,0f,0f,0f,0f,0f,1f,0f)))
            EditorFilter.WARM -> cm.postConcat(ColorMatrix(floatArrayOf(1.2f,0f,0f,0f,10f,0f,1f,0f,0f,5f,0f,0f,0.8f,0f,0f,0f,0f,0f,1f,0f)))
            EditorFilter.VIVID -> cm.setSaturation(1.8f)
            EditorFilter.NONE -> {}
        }
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private fun overlayBitmap(src: Bitmap, paths: List<DrawPath>, texts: List<TextOverlay>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true); val c = Canvas(out)
        paths.forEach { path ->
            val p = Paint().apply { color=path.color.toArgb(); strokeWidth=path.strokeWidth*src.width/1000f; isAntiAlias=true; style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
            path.points.zipWithNext { a, b -> c.drawLine(a.first*src.width,a.second*src.height,b.first*src.width,b.second*src.height,p) }
        }
        texts.forEach { t ->
            c.drawText(t.text, t.x*src.width, t.y*src.height, Paint().apply { color=t.color.toArgb(); textSize=t.size*src.height/100f; isAntiAlias=true })
        }
        return out
    }

    fun save(context: Context, originalName: String) = viewModelScope.launch {
        val bmp = _state.value.displayBitmap ?: return@launch
        _state.update { it.copy(isSaving = true) }
        val uri = ImageUtils.saveBitmapToGallery(context, bmp, originalName.substringBeforeLast(".") + "_edited.jpg")
        _state.update { it.copy(isSaving=false, savedUri=uri, error=if(uri==null)"Не удалось сохранить" else null) }
    }

    fun clearSaved() = _state.update { it.copy(savedUri = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
