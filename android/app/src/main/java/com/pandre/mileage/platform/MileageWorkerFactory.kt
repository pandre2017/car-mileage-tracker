package com.pandre.mileage.platform

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.pandre.mileage.SyncWorker
import com.pandre.mileage.domain.usecase.SyncPendingFuelEntriesUseCase

class MileageWorkerFactory(private val sync: SyncPendingFuelEntriesUseCase) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name)
            SyncWorker(appContext, workerParameters, sync)
        else null
}
