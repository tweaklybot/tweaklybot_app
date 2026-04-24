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

    fun signInWithGoogle(account: GoogleSignInAccount) = doAuth {
        authRepo.signInWithGoogle(account)
    }

    fun signInEmail(email: String, password: String) {
        if (!validate(email, password)) return
        doAuth { authRepo.signInWithEmail(email, password) }
    }

    fun registerEmail(email: String, password: String) {
        if (!validate(email, password)) return
        doAuth { authRepo.registerWithEmail(email, password) }
    }

    fun continueAsGuest() = viewModelScope.launch {
        settingsRepo.setGuestMode(true)
        settingsRepo.setSkipOnboarding(true)
        _state.value = AuthState(success = true)
    }

    fun setError(msg: String) { _state.update { it.copy(error = msg) } }
    fun clearError() { _state.update { it.copy(error = null) } }

    private fun doAuth(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _state.value = AuthState(isLoading = true)
            block()
                .onSuccess {
                    // Mark onboarding done so next launch goes straight to gallery
                    settingsRepo.setSkipOnboarding(true)
                    _state.value = AuthState(success = true)
                }
                .onFailure { _state.value = AuthState(error = friendlyError(it.message)) }
        }
    }

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
        "WRONG_PASSWORD" in msg || "invalid credential" in msg || "INVALID_LOGIN_CREDENTIALS" in msg ->
            "Неверный email или пароль"
        "EMAIL_EXISTS" in msg || "email-already-in-use" in msg -> "Этот email уже зарегистрирован"
        "WEAK_PASSWORD" in msg -> "Пароль слишком простой (минимум 6 символов)"
        "USER_NOT_FOUND" in msg -> "Аккаунт не найден. Зарегистрируйтесь"
        "network" in msg.lowercase() || "Unable to resolve" in msg -> "Нет соединения с сетью"
        "SIGN_IN_CANCELLED" in msg || "12501" in msg -> null
        "10:" in msg -> "Google Sign-In: добавьте SHA-1 в Firebase Console"
        else -> msg.take(120)
    }
}
