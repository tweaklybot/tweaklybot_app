package com.example.tweakly.data.repository

import com.example.tweakly.data.model.UserInfo
import com.example.tweakly.data.remote.TokenManager
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val tokenManager: TokenManager
) {
    val currentUser: Flow<UserInfo?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.let {
                UserInfo(it.uid, it.email, it.displayName, it.photoUrl?.toString())
            })
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun isLoggedIn(): Boolean = firebaseAuth.currentUser != null

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<UserInfo> {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val user = result.user!!
            refreshAndSaveToken()
            Result.success(UserInfo(user.uid, user.email, user.displayName, user.photoUrl?.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val user = result.user!!
            refreshAndSaveToken()
            Result.success(UserInfo(user.uid, user.email, user.displayName, user.photoUrl?.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, password: String): Result<UserInfo> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user!!
            refreshAndSaveToken()
            Result.success(UserInfo(user.uid, user.email, user.displayName, user.photoUrl?.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAndSaveToken() {
        firebaseAuth.currentUser?.getIdToken(true)?.await()?.token?.let {
            tokenManager.saveToken(it)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        tokenManager.clearToken()
    }

    fun getCurrentUserInfo(): UserInfo? {
        return firebaseAuth.currentUser?.let {
            UserInfo(it.uid, it.email, it.displayName, it.photoUrl?.toString())
        }
    }
}
