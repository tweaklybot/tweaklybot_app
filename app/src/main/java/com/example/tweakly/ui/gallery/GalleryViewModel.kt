package com.example.tweakly.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tweakly.data.model.*
import com.example.tweakly.data.repository.*
import com.example.tweakly.ui.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class GalleryTab { ALL, PHOTOS, VIDEOS, SCREENSHOTS, FAVORITES, PEOPLE }

sealed class GalleryListItem {
    data class Header(val date: String) : GalleryListItem()
    data class Cell(val media: MediaItem) : GalleryListItem()
    data class PeopleGroupCell(val group: FaceGroup) : GalleryListItem()
}

@androidx.compose.runtime.Stable
data class GalleryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val tab: GalleryTab = GalleryTab.ALL,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val flatItems: List<GalleryListItem> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isSelectMode: Boolean = false,
    val error: String? = null,
    val snackbarMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isGuestMode: Boolean = false,
    val isPremium: Boolean = false,
    val storagePercent: Float = 0f,
    val showStorageWarning: Boolean = false
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel @Inject constructor(
    private val mediaRepo: MediaRepository,
    private val authRepo: AuthRepository,
    private val syncManager: SyncManager,
    private val subscriptionRepo: SubscriptionRepository,
    settingsRepo: SettingsRepository
) : ViewModel() {

    private val _tab   = MutableStateFlow(GalleryTab.ALL)
    private val _sort  = MutableStateFlow(SortOrder.DATE_DESC)
    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    init {
        combine(settingsRepo.settings, authRepo.currentUser) { s, u ->
            _state.update { it.copy(isLoggedIn = u != null, isGuestMode = s.isGuestMode) }
        }.launchIn(viewModelScope)

        subscriptionRepo.state.onEach { sub ->
            _state.update { it.copy(
                isPremium = sub.isPremium,
                storagePercent = sub.storageUsedPercent,
                showStorageWarning = !sub.isPremium && sub.storageUsedPercent > 0.8f
            )}
        }.launchIn(viewModelScope)

        // React to tab + sort changes → switch data source
        combine(_tab, _sort) { tab, sort -> Pair(tab, sort) }
            .flatMapLatest { (tab, sort) ->
                when (tab) {
                    GalleryTab.FAVORITES -> mediaRepo.getFavorites()
                    GalleryTab.PEOPLE    -> flowOf(emptyList()) // handled separately
                    else -> {
                        val typeFlow = when (tab) {
                            GalleryTab.PHOTOS      -> mediaRepo.getByType(com.example.tweakly.data.local.entity.DbMediaType.PHOTO)
                            GalleryTab.VIDEOS      -> mediaRepo.getByType(com.example.tweakly.data.local.entity.DbMediaType.VIDEO)
                            GalleryTab.SCREENSHOTS -> mediaRepo.getByType(com.example.tweakly.data.local.entity.DbMediaType.SCREENSHOT)
                            else                   -> mediaRepo.getAll(sort)
                        }
                        typeFlow
                    }
                }
            }
            .onEach { items ->
                val flat = buildFlatList(items)
                _state.update { it.copy(isLoading = false, flatItems = flat, tab = _tab.value, sortOrder = _sort.value) }
            }
            .catch { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
            .launchIn(viewModelScope)

        loadMedia()
    }

    private fun buildFlatList(items: List<MediaItem>): List<GalleryListItem> {
        val flat = mutableListOf<GalleryListItem>()
        var lastDate = ""
        for (item in items) {
            val d = fmtDate(item.dateTaken)
            if (d != lastDate) { flat += GalleryListItem.Header(d); lastDate = d }
            flat += GalleryListItem.Cell(item)
        }
        return flat
    }

    fun loadMedia() = viewModelScope.launch {
        _state.update { it.copy(isLoading = it.flatItems.isEmpty()) }
        runCatching { mediaRepo.loadFromMediaStore() }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
        _state.update { it.copy(isLoading = false) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isRefreshing = true) }
        runCatching { mediaRepo.loadFromMediaStore() }
        _state.update { it.copy(isRefreshing = false) }
    }

    fun setTab(tab: GalleryTab) { _tab.value = tab; exitSelectMode() }
    fun setSortOrder(sort: SortOrder) { _sort.value = sort }
    fun syncAll() = viewModelScope.launch { syncManager.enqueueAllPending() }

    // ── Multi-select ─────────────────────────────────────────────────────────
    fun enterSelectMode(id: Long) {
        _state.update { it.copy(isSelectMode = true, selectedIds = setOf(id)) }
    }
    fun toggleSelect(id: Long) {
        _state.update { s ->
            val newSet = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
            s.copy(selectedIds = newSet, isSelectMode = newSet.isNotEmpty())
        }
    }
    fun selectAll() {
        val allIds = _state.value.flatItems.filterIsInstance<GalleryListItem.Cell>().map { it.media.id }.toSet()
        _state.update { it.copy(selectedIds = allIds, isSelectMode = true) }
    }
    fun exitSelectMode() = _state.update { it.copy(isSelectMode = false, selectedIds = emptySet()) }

    fun deleteSelected(context: android.content.Context) = viewModelScope.launch {
        val ids = _state.value.selectedIds.toList()
        ids.forEach { id -> mediaRepo.deleteLocal(id) }
        exitSelectMode()
        _state.update { it.copy(snackbarMessage = "Удалено ${ids.size} файлов") }
    }

    fun syncSelected() = viewModelScope.launch {
        val ids = _state.value.selectedIds.toList()
        ids.forEach { id -> syncManager.enqueueUpload(id) }
        exitSelectMode()
        _state.update { it.copy(snackbarMessage = "Добавлено в очередь синхронизации: ${ids.size}") }
    }

    // ── Favorites ────────────────────────────────────────────────────────────
    fun toggleFavorite(item: MediaItem) = viewModelScope.launch {
        mediaRepo.toggleFavorite(item.id, item.isFavorite)
    }

    fun clearError()   = _state.update { it.copy(error = null) }
    fun clearSnackbar() = _state.update { it.copy(snackbarMessage = null) }

    private fun fmtDate(ts: Long): String {
        if (ts == 0L) return "Без даты"
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().also { it.timeInMillis = ts }
        return when {
            sameDay(now, cal)     -> "Сегодня"
            isYesterday(now, cal) -> "Вчера"
            now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMMM", Locale("ru")).format(Date(ts))
            else -> SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(ts))
        }
    }
    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    private fun isYesterday(now: Calendar, other: Calendar): Boolean {
        val y = Calendar.getInstance().also { it.timeInMillis = now.timeInMillis; it.add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(y, other)
    }
}
