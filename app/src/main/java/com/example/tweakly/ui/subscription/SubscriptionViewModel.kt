package com.example.tweakly.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class SubscriptionUiState(
    val isPremium: Boolean = false,
    val expiresAt: Long = 0L,
    val usedStorageGB: Double = 0.0,
    val storagePercent: Float = 0f,
    val isLoading: Boolean = false,
    val message: String? = null
) {
    val expireDate: String
        get() = if (expiresAt > 0)
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(expiresAt))
        else "Бессрочно"
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val repo: SubscriptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionUiState())
    val state: StateFlow<SubscriptionUiState> = _state.asStateFlow()

    init {
        repo.state.onEach { sub ->
            _state.update {
                it.copy(
                    isPremium      = sub.isPremium,
                    expiresAt      = sub.expiresAt,
                    usedStorageGB  = sub.usedStorageGB,
                    storagePercent = sub.storageUsedPercent
                )
            }
        }.launchIn(viewModelScope)
    }

    /** In production: integrate with Google Play Billing here */
    fun subscribe() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            // TODO: launch Google Play Billing flow
            // For now show a placeholder message
            _state.update { it.copy(
                isLoading = false,
                message = "Оплата через Google Play будет добавлена в релизной версии"
            )}
        }
    }

    /** Activate 7-day demo premium for testing */
    fun activateDemoPremium() {
        viewModelScope.launch {
            val expiry = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
            repo.activatePremium(expiry)
            _state.update { it.copy(message = "Demo Premium активирован на 7 дней!") }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}
