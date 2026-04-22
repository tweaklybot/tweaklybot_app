package com.example.tweakly.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        try {
            EncryptedSharedPreferences.create(
                context, "tweakly_secure_prefs", masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to plain prefs if encryption fails (e.g. emulator)
            context.getSharedPreferences("tweakly_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String) = prefs.edit().putString(KEY_TOKEN, token).apply()
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun clearToken() = prefs.edit().remove(KEY_TOKEN).apply()

    fun saveRepoName(name: String) = prefs.edit().putString(KEY_REPO, name).apply()
    fun getRepoName(): String? = prefs.getString(KEY_REPO, null)

    companion object {
        private const val KEY_TOKEN = "firebase_id_token"
        private const val KEY_REPO = "repo_name"
    }
}
