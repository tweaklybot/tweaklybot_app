package com.example.tweakly.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.repository.AuthRepository
import com.example.tweakly.data.repository.SettingsRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // Expose login state for auto-login
    val isLoggedIn: StateFlow<Boolean> = authRepo.currentUser
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepo.isLoggedIn())

    fun signInWithGoogle(account: GoogleSignInAccount) = launch {
        authRepo.signInWithGoogle(account)
            .onSuccess { _state.value = AuthState(success = true) }
            .onFailure { _state.value = AuthState(error = friendlyError(it.message)) }
    }

    fun signInEmail(email: String, password: String) {
        if (!validate(email, password)) return
        launch { authRepo.signInWithEmail(email, password)
            .onSuccess { _state.value = AuthState(success = true) }
            .onFailure { _state.value = AuthState(error = friendlyError(it.message)) }
        }
    }

    fun registerEmail(email: String, password: String) {
        if (!validate(email, password)) return
        launch { authRepo.registerWithEmail(email, password)
            .onSuccess { _state.value = AuthState(success = true) }
            .onFailure { _state.value = AuthState(error = friendlyError(it.message)) }
        }
    }

    fun continueAsGuest() = viewModelScope.launch {
        settingsRepo.setGuestMode(true)
        settingsRepo.setSkipOnboarding(true)
        _state.value = AuthState(success = true)
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    private fun validate(email: String, password: String): Boolean {
        val err = when {
            email.isBlank() -> "Введите email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Неверный формат email"
            password.length < 6 -> "Пароль минимум 6 символов"
            else -> null
        }
        if (err != null) _state.value = AuthState(error = err)
        return err == null
    }

    private fun friendlyError(msg: String?) = when {
        msg == null -> "Неизвестная ошибка"
        "INVALID_EMAIL" in msg || "badly formatted" in msg -> "Неверный формат email"
        "WRONG_PASSWORD" in msg || "invalid credential" in msg -> "Неверный email или пароль"
        "EMAIL_EXISTS" in msg -> "Этот email уже зарегистрирован"
        "WEAK_PASSWORD" in msg -> "Пароль слишком простой"
        "network" in msg.lowercase() -> "Нет соединения с сетью"
        "SIGN_IN_CANCELLED" in msg -> null  // user cancelled
        else -> msg.take(100)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            block()
        }
    }
}
