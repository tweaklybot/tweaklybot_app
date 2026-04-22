package com.example.tweakly.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.UserInfo
import com.example.tweakly.data.remote.TweaklyApi
import com.example.tweakly.data.repository.AppSettings
import com.example.tweakly.data.repository.AuthRepository
import com.example.tweakly.data.repository.SettingsRepository
import com.example.tweakly.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val user: UserInfo? = null,
    val settings: AppSettings = AppSettings(),
    val serverOnline: Boolean? = null,
    val repoName: String? = null,
    val repoUrl: String? = null,
    val repoExists: Boolean = false,
    val isCreatingRepo: Boolean = false,
    val isPremium: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settingsRepo: SettingsRepository,
    private val subscriptionRepo: SubscriptionRepository,
    private val api: TweaklyApi
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        settingsRepo.settings
            .onEach { s -> _state.update { it.copy(settings = s) } }
            .launchIn(viewModelScope)

        authRepo.currentUser
            .onEach { u -> _state.update { it.copy(user = u) } }
            .launchIn(viewModelScope)

        subscriptionRepo.state
            .onEach { sub -> _state.update { it.copy(isPremium = sub.isPremium) } }
            .launchIn(viewModelScope)

        checkServer()
        loadRepoInfo()
    }

    fun setWifiOnly(v: Boolean)   = viewModelScope.launch { settingsRepo.setWifiOnly(v) }
    fun setAutoSync(v: Boolean)   = viewModelScope.launch { settingsRepo.setAutoSync(v) }
    fun setUploadQuality(v: Int)  = viewModelScope.launch { settingsRepo.setUploadQuality(v) }

    fun checkServer() = viewModelScope.launch {
        _state.update { it.copy(serverOnline = null) }
        val ok = try { api.healthCheck().status == "ok" } catch (_: Exception) { false }
        _state.update { it.copy(serverOnline = ok) }
    }

    fun loadRepoInfo() = viewModelScope.launch {
        try {
            val r = api.getRepoInfo()
            _state.update { it.copy(
                repoExists = r.exists,
                repoName   = r.repo?.name ?: r.repoName,
                repoUrl    = r.repo?.url
            )}
        } catch (_: Exception) {}
    }

    fun createRepo(name: String = "") = viewModelScope.launch {
        _state.update { it.copy(isCreatingRepo = true) }
        try {
            authRepo.refreshToken()
            val r = api.createRepo(
                com.example.tweakly.data.model.CreateRepoRequest(name.takeIf { it.isNotBlank() })
            )
            if (r.success) {
                _state.update { it.copy(
                    repoExists = true,
                    repoName   = r.repo?.name,
                    repoUrl    = r.repo?.url,
                    message    = if (r.created) "Репозиторий создан!" else "Репозиторий уже существует"
                )}
            } else {
                _state.update { it.copy(message = r.message ?: "Ошибка создания репозитория") }
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
