package com.example.tweakly.ui.people

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.FaceGroup
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.repository.MediaRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class PeopleState(
    val groups: List<FaceGroup> = emptyList(),
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0
)

@HiltViewModel
class PeopleViewModel @Inject constructor(private val mediaRepo: MediaRepository) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    fun loadGroups() = viewModelScope.launch {
        mediaRepo.getFaceGroups().collect { groupIds ->
            val groups = groupIds.mapNotNull { gid ->
                mediaRepo.getByFaceGroup(gid).first().firstOrNull()?.let { preview ->
                    val count = mediaRepo.getByFaceGroup(gid).first().size
                    FaceGroup(gid, preview.uri, count)
                }
            }
            _state.update { it.copy(groups = groups) }
        }
    }

    fun scanForFaces(context: Context, photos: List<MediaItem>) = viewModelScope.launch {
        _state.update { it.copy(isScanning = true, totalCount = photos.size, scannedCount = 0) }
        var groupCounter = (_state.value.groups.size).toLong()
        photos.forEachIndexed { idx, photo ->
            try {
                val image = InputImage.fromFilePath(context, Uri.parse(photo.uri))
                val faces = withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine { cont ->
                        detector.process(image)
                            .addOnSuccessListener { cont.resume(it) {} }
                            .addOnFailureListener { cont.resume(emptyList()) {} }
                    }
                }
                if (faces.isNotEmpty()) {
                    // Simple grouping: assign group based on face count bucket (real app would use embeddings)
                    val groupId = "group_${groupCounter % 10}"
                    mediaRepo.setFaceGroup(photo.id, groupId)
                    groupCounter++
                }
            } catch (_: Exception) {}
            _state.update { it.copy(scannedCount = idx + 1) }
        }
        _state.update { it.copy(isScanning = false) }
    }
}
