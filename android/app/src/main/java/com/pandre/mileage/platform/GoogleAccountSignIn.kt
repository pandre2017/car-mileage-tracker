package com.pandre.mileage.platform

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.pandre.mileage.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Activity-scoped adapter; never stored in a ViewModel or repository. */
class GoogleAccountSignIn(
    private val activity: ComponentActivity,
    private val auth: FirebaseAuth?,
) {
    fun signIn(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (auth == null || BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            onError("Configure Firebase and the Google web client ID. See android/README.md.")
            return
        }
        activity.lifecycleScope.launch {
            var stage = "Google credential request"
            try {
                val request =
                    GetCredentialRequest.Builder()
                        .addCredentialOption(
                            GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                                .build()
                        )
                        .build()
                val credential =
                    CredentialManager.create(activity).getCredential(activity, request).credential
                stage = "Google credential parsing"
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val preferences =
                    activity.getSharedPreferences(
                        "installation",
                        android.content.Context.MODE_PRIVATE,
                    )
                val previous = preferences.getString("ownerUid", null) ?: auth?.currentUser?.uid
                stage = "Firebase authentication"
                val result =
                    auth
                        ?.signInWithCredential(
                            GoogleAuthProvider.getCredential(google.idToken, null)
                        )
                        ?.await()
                if (previous != null && result?.user?.uid != previous) {
                    auth?.signOut()
                    onError(
                        "This installation belongs to another account. Clear app data to switch."
                    )
                } else {
                    preferences.edit().putString("ownerUid", result?.user?.uid).apply()
                    onSuccess()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                val code =
                    when (error) {
                        is androidx.credentials.exceptions.GetCredentialException -> error.type
                        is com.google.firebase.auth.FirebaseAuthException -> error.errorCode
                        else -> error.javaClass.simpleName
                    }
                // Log only classification metadata, never tokens, credentials or exception
                // payloads.
                Log.w(
                    "MileageAuth",
                    "Sign-in failed: stage=$stage; class=${error.javaClass.simpleName}; code=$code",
                )
                val explanation =
                    when (error) {
                        is androidx.credentials.exceptions.NoCredentialException ->
                            "No matching Google credential. Check the device account and OAuth package/signing configuration."
                        is androidx.credentials.exceptions.GetCredentialCancellationException ->
                            "Google sign-in was cancelled or dismissed."
                        else ->
                            "Sign-in failed during $stage ($code). Check Logcat with tag MileageAuth."
                    }
                onError(explanation)
            }
        }
    }
}
