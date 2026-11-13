package com.pandre.mileage

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.pandre.mileage.data.DefaultMileageRepository
import com.pandre.mileage.data.FirebaseAuthSession
import com.pandre.mileage.data.LocalDatabase
import com.pandre.mileage.data.MileageApi
import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.usecase.AddFuelEntryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarHistoryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarStatisticsUseCase
import com.pandre.mileage.domain.usecase.ObserveCarsUseCase
import com.pandre.mileage.domain.usecase.RefreshCarHistoryUseCase
import com.pandre.mileage.domain.usecase.RefreshCarsUseCase
import com.pandre.mileage.domain.usecase.SyncPendingFuelEntriesUseCase
import com.pandre.mileage.platform.ApiDiagnostics
import com.pandre.mileage.platform.MileageWorkerFactory
import com.pandre.mileage.platform.WorkManagerSyncScheduler
import com.pandre.mileage.platform.logOperationFailure
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MileageApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(MileageWorkerFactory(syncPendingFuelEntries))
                .build()

    private val scheduler by lazy { WorkManagerSyncScheduler(this) }
    val observeCarStatistics by lazy { ObserveCarStatisticsUseCase(repository) }
    val observeCars by lazy { ObserveCarsUseCase(repository) }
    val observeCarHistory by lazy {
        ObserveCarHistoryUseCase(repository)
    }
    val refreshCars by lazy {
        RefreshCarsUseCase(repository, scheduler)
    }
    val refreshCarHistory by lazy {
        RefreshCarHistoryUseCase(repository, scheduler)
    }
    val addFuelEntry by lazy {
        AddFuelEntryUseCase(repository, scheduler)
    }
    val syncPendingFuelEntries by lazy {
        SyncPendingFuelEntriesUseCase(repository)
    }

    val auth: FirebaseAuth? by lazy { createAuthentication() }
    private val repository: MileageRepository by lazy {
        createRepository()
    }

    private fun createAuthentication(): FirebaseAuth? {
        if (
            BuildConfig.FIREBASE_API_KEY.isBlank() ||
                BuildConfig.FIREBASE_APP_ID.isBlank() ||
                BuildConfig.FIREBASE_PROJECT_ID.isBlank()
        )
            return null
        val options =
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .build()
        return FirebaseAuth.getInstance(FirebaseApp.initializeApp(this, options))
    }

    private fun createRepository(): MileageRepository {
        val db =
            Room.databaseBuilder(this, LocalDatabase::class.java, "mileage.db")
                .addMigrations(LocalDatabase.MIGRATION_1_2)
                .build()
        val client = okhttp3.OkHttpClient.Builder().addInterceptor(ApiDiagnostics()).build()
        val api =
            Retrofit.Builder()
                .client(client)
                .baseUrl(BuildConfig.API_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MileageApi::class.java)
        return DefaultMileageRepository(
            db.dao(),
            api,
            FirebaseAuthSession(auth),
            ::logOperationFailure,
        )
    }
}
