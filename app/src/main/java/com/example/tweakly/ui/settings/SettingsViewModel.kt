package com.example.tweakly.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.UserInfo
import com.example.tweakly.data.remote.TweaklyApi
import com.example.tweakly.data.repository.AppSettings
import com.example.tweakly.data.repository.AuthRepository
import com.example.tweakly.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: UserInfo? = null,
    val settings: AppSettings = AppSettings(),
    val serverOnline: Boolean? = null,   // null = checking
    val repoName: String? = null,
    val repoUrl: String? = null,
    val repoExists: Boolean = false,
    val isCreatingRepo: Boolean = false,
    val isSyncing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settingsRepo: SettingsRepository,
    private val api: TweaklyApi
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        // Observe settings from DataStore — every change auto-saves
        settingsRepo.settings
            .onEach { s -> _state.update { it.copy(settings = s) } }
            .launchIn(viewModelScope)

        // Observe current user
        authRepo.currentUser
            .onEach { user -> _state.update { it.copy(user = user) } }
            .launchIn(viewModelScope)

        checkServer()
        loadRepoInfo()
    }

    // ── Settings mutations (all immediately persisted to DataStore) ──────────
    fun setWifiOnly(v: Boolean)     = viewModelScope.launch { settingsRepo.setWifiOnly(v) }
    fun setAutoSync(v: Boolean)     = viewModelScope.launch { settingsRepo.setAutoSync(v) }
    fun setUploadQuality(v: Int)    = viewModelScope.launch { settingsRepo.setUploadQuality(v) }

    // ── Server ───────────────────────────────────────────────────────────────
    fun checkServer() = viewModelScope.launch {
        _state.update { it.copy(serverOnline = null) }
        try {
            val r = api.healthCheck()
            _state.update { it.copy(serverOnline = r.status == "ok") }
        } catch (_: Exception) {
            _state.update { it.copy(serverOnline = false) }
        }
    }

    // ── Repo ─────────────────────────────────────────────────────────────────
    fun loadRepoInfo() = viewModelScope.launch {
        try {
            val r = api.getRepoInfo()
            _state.update { it.copy(
                repoExists = r.exists,
                repoName = r.repo?.name ?: r.repoName,
                repoUrl  = r.repo?.url
            )}
        } catch (_: Exception) {}
    }

    fun createRepo() = viewModelScope.launch {
        _state.update { it.copy(isCreatingRepo = true) }
        try {
            // Refresh token before API call
            authRepo.refreshToken()
            val r = api.createRepo()
            if (r.success) {
                _state.update { it.copy(
                    repoExists = true,
                    repoName = r.repo?.name,
                    repoUrl  = r.repo?.url,
                    message  = if (r.created) "Репозиторий создан!" else "Репозиторий уже существует"
                )}
            } else {
                _state.update { it.copy(message = r.message ?: "Не удалось создать репозиторий") }
            }
        } catch (e: Exception) {
            _state.update { it.copy(message = "Ошибка: ${e.message?.take(80)}") }
        } finally {
            _state.update { it.copy(isCreatingRepo = false) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun signOut() {
        authRepo.signOut()
        viewModelScope.launch { settingsRepo.setGuestMode(false) }
    }
}
