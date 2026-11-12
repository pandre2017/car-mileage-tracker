package com.pandre.mileage.domain

import com.pandre.mileage.domain.usecase.AddFuelEntryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarHistoryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarsUseCase
import com.pandre.mileage.domain.usecase.RefreshCarHistoryUseCase
import com.pandre.mileage.domain.usecase.RefreshCarsUseCase
import com.pandre.mileage.domain.usecase.SyncOutcome
import com.pandre.mileage.domain.usecase.SyncPendingFuelEntriesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UseCasesTest {
    private val repository = RecordingRepository()
    private val events
        get() = repository.events

    private val scheduler = SyncScheduler { events += "schedule" }
    private val input = NewFuelEntry("car", "2026-11-01", 1200, 4237, 7520)

    @Test
    fun validSavePrecedesScheduling() = runTest {
        AddFuelEntryUseCase(repository, scheduler)(input)
        assertEquals(listOf("save", "schedule"), events)
        assertEquals(input, repository.saved)
    }

    @Test
    fun invalidInputNeverTouchesRepositoryOrScheduler() = runTest {
        try {
            AddFuelEntryUseCase(repository, scheduler)(input.copy(litersHundredths = 0))
            fail("Expected validation")
        } catch (expected: IllegalArgumentException) {
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun failedSaveDoesNotScheduleOrHideFailure() = runTest {
        val failure = MileageException(MileageProblem.CONNECTION)
        repository.failure = failure
        try {
            AddFuelEntryUseCase(repository, scheduler)(input)
            fail("Expected failure")
        } catch (error: MileageException) {
            assertTrue(error === failure)
        }
        assertFalse(events.contains("schedule"))
    }

    @Test
    fun cancellationIsPropagatedWithoutScheduling() = runTest {
        repository.failure = CancellationException("fixture")
        try {
            AddFuelEntryUseCase(repository, scheduler)(input)
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            assertFalse(events.contains("schedule"))
        }
    }

    @Test
    fun successfulRefreshesRecoverPendingUploads() = runTest {
        RefreshCarsUseCase(repository, scheduler)()
        RefreshCarHistoryUseCase(repository, scheduler)("car")
        assertEquals(listOf("cars", "schedule", "history:car", "schedule"), events)
    }

    @Test
    fun failedRefreshDoesNotSchedule() = runTest {
        repository.failure = MileageException(MileageProblem.ACCESS_DENIED)
        try {
            RefreshCarsUseCase(repository, scheduler)()
            fail("Expected failure")
        } catch (expected: MileageException) {
            assertFalse(events.contains("schedule"))
        }
    }

    @Test
    fun observationCombinesHistoryWithLastSuccessfulRefresh() = runTest {
        repository.cars.value = listOf(Car("car", "Family car"))
        assertEquals("car", ObserveCarsUseCase(repository)().first().single().id)
        repository.time.value = 1234L
        val history = ObserveCarHistoryUseCase(repository)("car").first()
        assertTrue(history.entries.isEmpty())
        assertEquals(1234L, history.refreshedAt)
        assertEquals("car", repository.observedCar)
    }

    @Test
    fun syncReturnsExplicitCompletionOrRetryOutcome() = runTest {
        val sync = SyncPendingFuelEntriesUseCase(repository)
        assertEquals(SyncOutcome.COMPLETE, sync())
        repository.retry = true
        assertEquals(SyncOutcome.RETRY, sync())
    }
}

private class RecordingRepository : MileageRepository {
    val events = mutableListOf<String>()
    val cars = MutableStateFlow(emptyList<Car>())
    val time = MutableStateFlow<Long?>(null)
    var failure: Exception? = null
    var saved: NewFuelEntry? = null
    var observedCar: String? = null
    var retry = false

    override fun observeCars() = cars

    override fun observeEntries(carId: String): MutableStateFlow<List<FuelEntry>> {
        observedCar = carId
        return MutableStateFlow(emptyList())
    }

    override fun observeRefreshTime(carId: String) = time

    override suspend fun loadCars() {
        failure?.let { throw it }
        events += "cars"
    }

    override suspend fun refresh(carId: String) {
        failure?.let { throw it }
        events += "history:$carId"
    }

    override suspend fun add(entry: NewFuelEntry) {
        failure?.let { throw it }
        saved = entry
        events += "save"
    }

    override suspend fun sync() = retry
}
