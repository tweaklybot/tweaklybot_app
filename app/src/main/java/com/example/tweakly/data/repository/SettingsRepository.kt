package com.example.tweakly.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tweakly_settings")

data class AppSettings(
    val wifiOnly: Boolean = true,
    val autoSync: Boolean = false,
    val uploadQuality: Int = 85,        // JPEG quality 1-100
    val skipOnboarding: Boolean = false,
    val isGuestMode: Boolean = false
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val WIFI_ONLY     = booleanPreferencesKey("wifi_only")
        val AUTO_SYNC     = booleanPreferencesKey("auto_sync")
        val UPLOAD_QUALITY = intPreferencesKey("upload_quality")
        val SKIP_ONBOARDING = booleanPreferencesKey("skip_onboarding")
        val GUEST_MODE    = booleanPreferencesKey("guest_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            AppSettings(
                wifiOnly       = prefs[Keys.WIFI_ONLY] ?: true,
                autoSync       = prefs[Keys.AUTO_SYNC] ?: false,
                uploadQuality  = prefs[Keys.UPLOAD_QUALITY] ?: 85,
                skipOnboarding = prefs[Keys.SKIP_ONBOARDING] ?: false,
                isGuestMode    = prefs[Keys.GUEST_MODE] ?: false
            )
        }

    suspend fun setWifiOnly(v: Boolean)      = save(Keys.WIFI_ONLY, v)
    suspend fun setAutoSync(v: Boolean)       = save(Keys.AUTO_SYNC, v)
    suspend fun setUploadQuality(v: Int)      = save(Keys.UPLOAD_QUALITY, v.coerceIn(10, 100))
    suspend fun setSkipOnboarding(v: Boolean) = save(Keys.SKIP_ONBOARDING, v)
    suspend fun setGuestMode(v: Boolean)      = save(Keys.GUEST_MODE, v)

    private suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
