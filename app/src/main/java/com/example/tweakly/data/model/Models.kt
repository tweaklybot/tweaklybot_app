package com.example.tweakly.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

// ── Network ───────────────────────────────────────────────────────────────────
@Serializable data class HealthResponse(val status: String, val version: String? = null, val timestamp: String? = null)
@Serializable data class CreateRepoRequest(val repoName: String? = null)
@Serializable data class RepoData(val name: String, val full_name: String? = null, val url: String, val private: Boolean = true, val size: Int = 0, val created_at: String? = null)
@Serializable data class RepoResponse(val success: Boolean, val created: Boolean = false, val message: String? = null, val repo: RepoData? = null)
@Serializable data class RepoInfoResponse(val exists: Boolean, val repoName: String? = null, val repo: RepoData? = null)
@Serializable data class UploadContentData(val path: String? = null, val sha: String? = null, val name: String? = null, val size: Long? = null, val download_url: String? = null)
@Serializable data class UploadResponse(val success: Boolean, val content: UploadContentData? = null, val message: String? = null)
@Serializable data class DeleteFileRequest(val path: String, val sha: String, val commitMessage: String = "Delete via Tweakly")
@Serializable data class DeleteResponse(val success: Boolean, val message: String? = null)
@Serializable data class RemoteFile(val name: String, val path: String, val sha: String? = null, val size: Long = 0, val type: String = "file", val download_url: String? = null)
@Serializable data class FilesResponse(val success: Boolean, val files: List<RemoteFile> = emptyList(), val count: Int = 0)
@Serializable data class UserMeResponse(val uid: String, val email: String? = null, val name: String? = null, val repoName: String? = null, val repoExists: Boolean = false, val repoUrl: String? = null)

// ── UI — @Immutable so Compose skips recomposition when data unchanged ────────
enum class MediaType { PHOTO, VIDEO, SCREENSHOT }
enum class SyncStatusUi { PENDING, SYNCED, FAILED, NOT_SYNCED }
enum class SortOrder { DATE_DESC, DATE_ASC, SIZE_DESC, NAME_ASC }

@Immutable
data class MediaItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val duration: Long = 0L,
    val mediaType: MediaType = MediaType.PHOTO,
    val syncStatus: SyncStatusUi = SyncStatusUi.NOT_SYNCED,
    val remotePath: String? = null,
    val isFavorite: Boolean = false,
    val faceGroupId: String? = null
)

@Immutable
data class FaceGroup(val groupId: String, val previewUri: String, val count: Int)

data class UserInfo(val uid: String, val email: String?, val displayName: String?, val photoUrl: String?)
