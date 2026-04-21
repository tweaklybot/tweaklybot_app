package com.example.tweakly.data.model

import kotlinx.serialization.Serializable

// ── Network Models ────────────────────────────────────────────────────────────

@Serializable
data class HealthResponse(val status: String, val message: String? = null)

@Serializable
data class CreateRepoRequest(val repoName: String, val description: String = "Tweakly photo backup")

@Serializable
data class RepoResponse(val success: Boolean, val repoName: String? = null, val url: String? = null, val message: String? = null)

@Serializable
data class RepoInfoResponse(val success: Boolean, val repoName: String? = null, val url: String? = null, val filesCount: Int = 0)

@Serializable
data class UploadResponse(val success: Boolean, val path: String? = null, val url: String? = null, val message: String? = null)

@Serializable
data class DeleteFileRequest(val path: String, val commitMessage: String = "Delete via Tweakly")

@Serializable
data class DeleteResponse(val success: Boolean, val message: String? = null)

@Serializable
data class FilesResponse(val success: Boolean, val files: List<RemoteFile> = emptyList())

@Serializable
data class RemoteFile(val name: String, val path: String, val downloadUrl: String? = null, val size: Long = 0)

// ── UI State Models ────────────────────────────────────────────────────────────

data class PhotoUiModel(
    val id: Long,
    val uri: String,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val syncStatus: SyncStatusUi,
    val remotePath: String?
)

enum class SyncStatusUi { PENDING, SYNCED, FAILED, NOT_SYNCED }

data class UserInfo(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?
)
