package com.pandre.mileage

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.pandre.mileage.ui.*

class MainActivity : ComponentActivity() {
    private val app get() = application as MileageApplication
    private val model: MileageViewModel by viewModels { object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = MileageViewModel(app.repository, app::enqueueSync) as T
    } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MileageScreen(model, app.auth?.currentUser != null, ::signIn) }
        if (app.auth?.currentUser != null) model.load()
    }
    private fun signIn(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (app.auth == null || BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) { onError("Configure Firebase and the Google web client ID. See android/README.md."); return }
        lifecycleScope.launch {
            var stage = "Google credential request"
            try {
                val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()).build()
                val credential = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, request).credential
                stage = "Google credential parsing"
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val preferences = getSharedPreferences("installation", MODE_PRIVATE)
                val previous = preferences.getString("ownerUid", null) ?: app.auth?.currentUser?.uid
                stage = "Firebase authentication"
                val result = app.auth?.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null))?.await()
                if (previous != null && result?.user?.uid != previous) {
                    app.auth?.signOut()
                    onError("This installation belongs to another account. Clear app data to switch.")
                } else {
                    preferences.edit().putString("ownerUid", result?.user?.uid).apply()
                    onSuccess(); model.load()
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                val code = when (error) {
                    is androidx.credentials.exceptions.GetCredentialException -> error.type
                    is com.google.firebase.auth.FirebaseAuthException -> error.errorCode
                    else -> error.javaClass.simpleName
                }
                // Log only classification metadata, never tokens, credentials or exception payloads.
                Log.w("MileageAuth", "Sign-in failed: stage=$stage; class=${error.javaClass.simpleName}; code=$code")
                val explanation = when (error) {
                    is androidx.credentials.exceptions.NoCredentialException -> "No matching Google credential. Check the device account and OAuth package/signing configuration."
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> "Google sign-in was cancelled or dismissed."
                    else -> "Sign-in failed during $stage ($code). Check Logcat with tag MileageAuth."
                }
                onError(explanation)
            }
        }
    }
}
