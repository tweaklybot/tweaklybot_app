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
            trySend(auth.currentUser?.toUserInfo())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    fun isLoggedIn() = firebaseAuth.currentUser != null
    fun currentUserInfo() = firebaseAuth.currentUser?.toUserInfo()

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<UserInfo> = runCatching {
        val cred = GoogleAuthProvider.getCredential(account.idToken, null)
        val res = firebaseAuth.signInWithCredential(cred).await()
        refreshToken()
        res.user!!.toUserInfo()
    }

    suspend fun signInWithEmail(email: String, password: String): Result<UserInfo> = runCatching {
        val res = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        refreshToken()
        res.user!!.toUserInfo()
    }

    suspend fun registerWithEmail(email: String, password: String): Result<UserInfo> = runCatching {
        val res = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        refreshToken()
        res.user!!.toUserInfo()
    }

    /** Call once on startup to ensure token is fresh and saved */
    suspend fun refreshTokenIfLoggedIn() {
        if (isLoggedIn()) refreshToken()
    }

    suspend fun refreshToken() {
        firebaseAuth.currentUser?.getIdToken(true)?.await()?.token?.let {
            tokenManager.saveToken(it)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
        tokenManager.clearToken()
    }

    private fun com.google.firebase.auth.FirebaseUser.toUserInfo() =
        UserInfo(uid, email, displayName, photoUrl?.toString())
}
