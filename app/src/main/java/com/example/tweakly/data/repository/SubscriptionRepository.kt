package com.example.tweakly.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.tweakly.data.model.Plans
import com.example.tweakly.data.model.PlanLimits
import com.example.tweakly.data.model.SubscriptionTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subDataStore by preferencesDataStore("tweakly_subscription")

data class SubscriptionState(
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val expiresAt: Long = 0L,
    val usedStorageBytes: Long = 0L,
    val ocrScansToday: Int = 0,
    val ocrScanDate: String = "",        // "yyyy-MM-dd"
    val totalUploaded: Int = 0
) {
    val limits: PlanLimits get() = Plans.forTier(tier)

    val isPremium get() = tier == SubscriptionTier.PREMIUM &&
            (expiresAt == -1L || expiresAt > System.currentTimeMillis())

    val usedStorageGB get() = usedStorageBytes / (1024.0 * 1024.0 * 1024.0)

    val storageUsedPercent: Float
        get() {
            if (limits.storageGB == -1) return 0f
            val maxBytes = limits.storageGB.toLong() * 1024 * 1024 * 1024
            return (usedStorageBytes.toFloat() / maxBytes).coerceIn(0f, 1f)
        }

    val isStorageFull: Boolean
        get() {
            if (limits.storageGB == -1) return false
            return usedStorageBytes >= limits.storageGB.toLong() * 1024 * 1024 * 1024
        }

    fun ocrAllowed(): Boolean {
        if (limits.ocrScansPerDay == -1) return true
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return ocrScanDate != today || ocrScansToday < limits.ocrScansPerDay
    }
}

@Singleton
class SubscriptionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TIER          = stringPreferencesKey("sub_tier")
        val EXPIRES_AT    = longPreferencesKey("sub_expires")
        val USED_BYTES    = longPreferencesKey("used_bytes")
        val OCR_TODAY     = intPreferencesKey("ocr_today")
        val OCR_DATE      = stringPreferencesKey("ocr_date")
        val TOTAL_UPLOADS = intPreferencesKey("total_uploads")
    }

    val state: Flow<SubscriptionState> = context.subDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            SubscriptionState(
                tier           = runCatching { SubscriptionTier.valueOf(p[Keys.TIER] ?: "FREE") }
                                     .getOrDefault(SubscriptionTier.FREE),
                expiresAt      = p[Keys.EXPIRES_AT] ?: 0L,
                usedStorageBytes = p[Keys.USED_BYTES] ?: 0L,
                ocrScansToday  = p[Keys.OCR_TODAY] ?: 0,
                ocrScanDate    = p[Keys.OCR_DATE] ?: "",
                totalUploaded  = p[Keys.TOTAL_UPLOADS] ?: 0
            )
        }

    /** Activate premium. Pass expiresAt = -1L for lifetime. */
    suspend fun activatePremium(expiresAt: Long = -1L) {
        context.subDataStore.edit { p ->
            p[Keys.TIER] = SubscriptionTier.PREMIUM.name
            p[Keys.EXPIRES_AT] = expiresAt
        }
    }

    suspend fun addUsedBytes(bytes: Long) {
        context.subDataStore.edit { p ->
            p[Keys.USED_BYTES] = (p[Keys.USED_BYTES] ?: 0L) + bytes
        }
    }

    suspend fun recordOcrScan() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        context.subDataStore.edit { p ->
            val lastDate = p[Keys.OCR_DATE] ?: ""
            p[Keys.OCR_DATE] = today
            p[Keys.OCR_TODAY] = if (lastDate == today) (p[Keys.OCR_TODAY] ?: 0) + 1 else 1
        }
    }

    suspend fun recordUpload() {
        context.subDataStore.edit { p ->
            p[Keys.TOTAL_UPLOADS] = (p[Keys.TOTAL_UPLOADS] ?: 0) + 1
        }
    }

    /** For demo/testing: reset to free */
    suspend fun resetToFree() {
        context.subDataStore.edit { p ->
            p[Keys.TIER] = SubscriptionTier.FREE.name
            p[Keys.EXPIRES_AT] = 0L
        }
    }
}
