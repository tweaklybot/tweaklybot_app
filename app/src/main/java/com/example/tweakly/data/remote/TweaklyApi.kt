package com.example.tweakly.data.remote

import com.example.tweakly.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface TweaklyApi {

    @GET("health")
    suspend fun healthCheck(): HealthResponse

    @GET("api/user/me")
    suspend fun getMe(): UserMeResponse

    @POST("api/repo/create")
    suspend fun createRepo(@Body request: CreateRepoRequest = CreateRepoRequest()): RepoResponse

    @GET("api/repo/info")
    suspend fun getRepoInfo(): RepoInfoResponse

    @Multipart
    @POST("api/repo/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("path") path: RequestBody,
        @Part("commitMessage") commitMessage: RequestBody
    ): UploadResponse

    @DELETE("api/repo/file")
    suspend fun deleteFile(@Body request: DeleteFileRequest): DeleteResponse

    @GET("api/repo/files")
    suspend fun getFiles(@Query("path") path: String = ""): FilesResponse
}
