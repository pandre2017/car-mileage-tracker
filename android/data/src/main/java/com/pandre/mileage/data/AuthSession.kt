package com.pandre.mileage.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

interface AuthSession {
    val uid: String?

    suspend fun authorization(): String
}

class FirebaseAuthSession(private val auth: FirebaseAuth?) : AuthSession {
    override val uid: String?
        get() = auth?.currentUser?.uid

    override suspend fun authorization(): String {
        val token =
            auth?.currentUser?.getIdToken(false)?.await()?.token
                ?: throw com.pandre.mileage.domain.MileageException(
                    com.pandre.mileage.domain.MileageProblem.UNAUTHENTICATED
                )
        return "Bearer $token"
    }
}
