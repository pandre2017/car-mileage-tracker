package com.pandre.mileage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.usecase.SyncOutcome
import com.pandre.mileage.domain.usecase.SyncPendingFuelEntriesUseCase
import kotlinx.coroutines.CancellationException

class SyncWorker(
    context: Context,
    parameters: WorkerParameters,
    private val syncPendingFuelEntries: SyncPendingFuelEntriesUseCase,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            when (syncPendingFuelEntries()) {
                SyncOutcome.RETRY -> Result.retry()
                SyncOutcome.COMPLETE -> Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: MileageException) {
            if (
                error.problem == MileageProblem.CONNECTION || error.problem == MileageProblem.SERVER
            )
                Result.retry()
            else Result.failure()
        } catch (error: IllegalStateException) {
            Result.failure()
        }
    }
}
