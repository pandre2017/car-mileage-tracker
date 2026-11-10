package com.pandre.mileage

import android.app.Application
import androidx.room.Room
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.pandre.mileage.data.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MileageApplication : Application() {
    lateinit var repository: MileageRepository
    var auth: FirebaseAuth? = null
        private set
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank() && BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()) {
            val app = FirebaseApp.initializeApp(this, FirebaseOptions.Builder().setApiKey(BuildConfig.FIREBASE_API_KEY).setApplicationId(BuildConfig.FIREBASE_APP_ID).setProjectId(BuildConfig.FIREBASE_PROJECT_ID).build())
            auth = FirebaseAuth.getInstance(app)
        }
        val db = Room.databaseBuilder(this, LocalDatabase::class.java, "mileage.db").build()
        val api = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).addConverterFactory(GsonConverterFactory.create()).build().create(MileageApi::class.java)
        repository = MileageRepository(db.dao(), api, auth)
    }
    fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(this).enqueueUniqueWork("fuel-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
class SyncWorker(context: android.content.Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MileageApplication
        if (app.auth?.currentUser == null) return Result.failure()
        return try { if (app.repository.sync()) Result.retry() else Result.success() }
        catch (error: retrofit2.HttpException) { if (error.code() in listOf(401, 403)) Result.failure() else Result.retry() }
        catch (error: java.io.IOException) { Result.retry() }
        catch (error: com.google.firebase.FirebaseNetworkException) { Result.retry() }
        catch (error: com.google.firebase.auth.FirebaseAuthException) { Result.failure() }
        catch (error: IllegalStateException) { Result.failure() }
    }
}
