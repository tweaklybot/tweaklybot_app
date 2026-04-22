package com.example.tweakly.data.model

/**
 * Subscription tiers and their limits.
 *
 * FREE:
 *   - 1 GB cloud storage
 *   - Uploads only on WiFi
 *   - Compressed quality (60%)
 *   - No video upload
 *   - Basic editor (crop + rotate only)
 *   - OCR: 10 scans/day
 *
 * PREMIUM:
 *   - Unlimited storage
 *   - Upload on any network
 *   - Original quality (100%)
 *   - Video upload up to 500 MB
 *   - Full editor + AI features
 *   - Unlimited OCR / QR
 */
enum class SubscriptionTier { FREE, PREMIUM }

data class PlanLimits(
    val storageGB: Int,              // -1 = unlimited
    val uploadQuality: Int,          // JPEG % 1-100
    val videoUploadEnabled: Boolean,
    val maxVideoSizeMB: Int,
    val forcedWifiOnly: Boolean,
    val ocrScansPerDay: Int,         // -1 = unlimited
    val fullEditorEnabled: Boolean,
    val aiSuggestionsEnabled: Boolean
)

object Plans {
    val FREE = PlanLimits(
        storageGB = 1,
        uploadQuality = 60,
        videoUploadEnabled = false,
        maxVideoSizeMB = 0,
        forcedWifiOnly = true,
        ocrScansPerDay = 10,
        fullEditorEnabled = false,
        aiSuggestionsEnabled = false
    )

    val PREMIUM = PlanLimits(
        storageGB = -1,
        uploadQuality = 100,
        videoUploadEnabled = true,
        maxVideoSizeMB = 500,
        forcedWifiOnly = false,
        ocrScansPerDay = -1,
        fullEditorEnabled = true,
        aiSuggestionsEnabled = true
    )

    fun forTier(tier: SubscriptionTier) = when (tier) {
        SubscriptionTier.FREE    -> FREE
        SubscriptionTier.PREMIUM -> PREMIUM
    }
}
