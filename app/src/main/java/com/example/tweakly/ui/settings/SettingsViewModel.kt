package com.example.tweakly.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.UserInfo
import com.example.tweakly.data.remote.TweaklyApi
import com.example.tweakly.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userInfo: UserInfo? = null,
    val serverStatus: String = "Проверка...",
    val isServerOnline: Boolean = false,
    val autoSync: Boolean = false,
    val wifiOnly: Boolean = true,
    val repoInfo: String = "",
    val isLoading: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: TweaklyApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(userInfo = authRepository.getCurrentUserInfo()) }
        checkServerHealth()
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            try {
                val response = api.healthCheck()
                _uiState.update { it.copy(serverStatus = "Онлайн: ${response.status}", isServerOnline = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(serverStatus = "Недоступен", isServerOnline = false) }
            }
        }
    }

    fun loadRepoInfo() {
        viewModelScope.launch {
            try {
                val info = api.getRepoInfo()
                if (info.success) {
                    _uiState.update { it.copy(repoInfo = info.repoName ?: "") }
                }
            } catch (_: Exception) {}
        }
    }

    fun createRepo(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = api.createRepo(com.example.tweakly.data.model.CreateRepoRequest(name))
                if (response.success) {
                    _uiState.update { it.copy(repoInfo = response.repoName ?: name) }
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleAutoSync(value: Boolean) = _uiState.update { it.copy(autoSync = value) }
    fun toggleWifiOnly(value: Boolean) = _uiState.update { it.copy(wifiOnly = value) }

    fun signOut() = authRepository.signOut()
}
