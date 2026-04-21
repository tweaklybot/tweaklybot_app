package com.example.tweakly.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.repository.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val isLoggedIn = authRepository.currentUser.map { it != null }

    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.signInWithGoogle(account)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true) }
                .onFailure { _uiState.value = AuthUiState(error = it.message) }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        if (!validateInputs(email, password)) return
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.signInWithEmail(email, password)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true) }
                .onFailure { _uiState.value = AuthUiState(error = it.message) }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        if (!validateInputs(email, password)) return
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            authRepository.registerWithEmail(email, password)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true) }
                .onFailure { _uiState.value = AuthUiState(error = it.message) }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isBlank() -> { _uiState.value = AuthUiState(error = "Email не может быть пустым"); false }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { _uiState.value = AuthUiState(error = "Неверный формат email"); false }
            password.length < 6 -> { _uiState.value = AuthUiState(error = "Пароль должен содержать минимум 6 символов"); false }
            else -> true
        }
    }
}
