package com.pandre.mileage

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.usecase.SyncPendingFuelEntriesUseCase
import com.pandre.mileage.platform.MileageWorkerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SyncWorkerTest {
    @Test
    fun factoryInjectsUseCaseAndWorkerMapsRetryAndCompletion() = runBlocking {
        val repository = SyncFixture()
        assertEquals(ListenableWorker.Result.success(), worker(repository).doWork())
        repository.retry = true
        assertEquals(ListenableWorker.Result.retry(), worker(repository).doWork())
    }

    @Test
    fun workerDoesNotRetryMissingAccessButRetriesConnectionFailures() = runBlocking {
        val repository = SyncFixture()
        repository.problem = MileageProblem.ACCESS_DENIED
        assertEquals(ListenableWorker.Result.failure(), worker(repository).doWork())
        repository.problem = MileageProblem.CONNECTION
        assertEquals(ListenableWorker.Result.retry(), worker(repository).doWork())
    }

    @Test
    fun cancellationPropagatesOutOfWorker() = runBlocking {
        val repository = SyncFixture().apply { cancelled = true }
        try {
            worker(repository).doWork()
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            /* WorkManager owns cancellation. */
        }
    }

    private fun worker(repository: MileageRepository): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(
                InstrumentationRegistry.getInstrumentation().targetContext
            )
            .setWorkerFactory(MileageWorkerFactory(SyncPendingFuelEntriesUseCase(repository)))
            .build()
}

private class SyncFixture : MileageRepository {
    var retry = false
    var cancelled = false
    var problem: MileageProblem? = null

    override suspend fun sync(): Boolean {
        if (cancelled) throw CancellationException("fixture")
        problem?.let { throw MileageException(it) }
        return retry
    }

    override fun observeCars(): Flow<List<Car>> = error("Not used")

    override fun observeEntries(carId: String): Flow<List<FuelEntry>> = error("Not used")

    override fun observeRefreshTime(carId: String): Flow<Long?> = error("Not used")

    override suspend fun loadCars() = error("Not used")

    override suspend fun refresh(carId: String) = error("Not used")

    override suspend fun add(entry: NewFuelEntry) = error("Not used")
}
