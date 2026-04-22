package com.example.tweakly.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.MediaItem
import com.example.tweakly.data.model.MediaType
import com.example.tweakly.data.repository.AuthRepository
import com.example.tweakly.data.repository.MediaRepository
import com.example.tweakly.data.repository.SettingsRepository
import com.example.tweakly.data.repository.SubscriptionRepository
import com.example.tweakly.ui.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class GalleryTab { ALL, PHOTOS, VIDEOS, SCREENSHOTS }

data class GalleryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val tab: GalleryTab = GalleryTab.ALL,
    val groupedMedia: Map<String, List<MediaItem>> = emptyMap(),
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val isGuestMode: Boolean = false,
    val isPremium: Boolean = false,
    val storagePercent: Float = 0f,
    val showStorageWarning: Boolean = false
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepo: MediaRepository,
    private val authRepo: AuthRepository,
    private val syncManager: SyncManager,
    private val subscriptionRepo: SubscriptionRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    private val _tab   = MutableStateFlow(GalleryTab.ALL)
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        // Auth + settings state
        combine(settingsRepo.settings, authRepo.currentUser) { settings, user ->
            _state.update { it.copy(isLoggedIn = user != null, isGuestMode = settings.isGuestMode) }
        }.launchIn(viewModelScope)

        // Subscription state
        subscriptionRepo.state.onEach { sub ->
            val warn = !sub.isPremium && sub.storageUsedPercent > 0.8f
            _state.update { it.copy(
                isPremium = sub.isPremium,
                storagePercent = sub.storageUsedPercent,
                showStorageWarning = warn
            )}
        }.launchIn(viewModelScope)

        // Media filtered by active tab
        combine(mediaRepo.getAll(), _tab) { items, tab ->
            val filtered = when (tab) {
                GalleryTab.ALL         -> items
                GalleryTab.PHOTOS      -> items.filter { it.mediaType == MediaType.PHOTO }
                GalleryTab.VIDEOS      -> items.filter { it.mediaType == MediaType.VIDEO }
                GalleryTab.SCREENSHOTS -> items.filter { it.mediaType == MediaType.SCREENSHOT }
            }
            _state.update { it.copy(
                isLoading    = false,
                tab          = tab,
                groupedMedia = filtered.groupBy { fmtDate(it.dateTaken) }
            )}
        }.catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
         .launchIn(viewModelScope)

        loadMedia()
    }

    fun loadMedia() = viewModelScope.launch {
        _state.update { it.copy(isLoading = it.groupedMedia.isEmpty()) }
        runCatching { mediaRepo.loadFromMediaStore() }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(isLoading = false) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isRefreshing = true) }
        runCatching { mediaRepo.loadFromMediaStore() }
        _state.update { it.copy(isRefreshing = false) }
    }

    fun syncAll() = viewModelScope.launch {
        syncManager.enqueueAllPending()
    }

    fun setTab(tab: GalleryTab) { _tab.value = tab }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun fmtDate(ts: Long): String {
        if (ts == 0L) return "Без даты"
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().also { it.timeInMillis = ts }
        return when {
            sameDay(now, cal)      -> "Сегодня"
            isYesterday(now, cal)  -> "Вчера"
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale("ru")).format(Date(ts))
            else ->
                SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(ts))
        }
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, other: Calendar): Boolean {
        val y = Calendar.getInstance().also { it.timeInMillis = now.timeInMillis; it.add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(y, other)
    }
}
