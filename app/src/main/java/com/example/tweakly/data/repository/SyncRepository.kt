package com.example.tweakly.data.repository

import com.example.tweakly.data.model.RepoInfoResponse
import com.example.tweakly.data.model.RepoResponse
import com.example.tweakly.data.remote.TweaklyApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(private val api: TweaklyApi) {

    suspend fun checkHealth(): Boolean = try {
        val r = api.healthCheck()
        r.status == "ok"
    } catch (e: Exception) { false }

    suspend fun createRepo(): Result<RepoResponse> = runCatching { api.createRepo() }

    suspend fun getRepoInfo(): Result<RepoInfoResponse> = runCatching { api.getRepoInfo() }
}
