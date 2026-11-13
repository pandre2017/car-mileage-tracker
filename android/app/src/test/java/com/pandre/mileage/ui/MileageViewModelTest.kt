package com.pandre.mileage.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.SyncScheduler
import com.pandre.mileage.domain.SyncStatus
import com.pandre.mileage.domain.usecase.AddFuelEntryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarHistoryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarStatisticsUseCase
import com.pandre.mileage.domain.usecase.ObserveCarsUseCase
import com.pandre.mileage.domain.usecase.RefreshCarHistoryUseCase
import com.pandre.mileage.domain.usecase.RefreshCarsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MileageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun selectedCarStatisticsUpdateWhenRangeIsLoadedAndResetForAnotherCar() =
        runTest(dispatcher) {
            val repository = FakeMileageRepository()
            val model = model(repository)
            model.load()
            advanceUntilIdle()
            model.select("a")
            advanceUntilIdle()
            assertEquals(1850L, model.state.value.statistics.nextFuelingOdometerKm)
            model.select("b")
            advanceUntilIdle()
            assertNull(model.state.value.statistics.nextFuelingOdometerKm)
        }

    @Test
    fun loadingFailureAndRetryExposeCompleteImmutableState() =
        runTest(dispatcher) {
            val repository = FakeMileageRepository()
            repository.loadFailure = MileageException(MileageProblem.CONNECTION)
            val model = model(repository)
            model.load()
            assertTrue(model.state.value.loading)
            advanceUntilIdle()
            assertFalse(model.state.value.loading)
            assertTrue(model.state.value.message.orEmpty().contains("Could not connect"))
            repository.loadFailure = null
            model.load()
            advanceUntilIdle()
            assertNull(model.state.value.message)
            assertEquals("Car A", model.state.value.cars.single().displayName)
        }

    @Test
    fun changingCarsCancelsPreviousRefreshAndDropsOldHistory() =
        runTest(dispatcher) {
            val repository = FakeMileageRepository()
            repository.blockFirstRefresh = true
            val saved = SavedStateHandle()
            val model = model(repository, saved)
            model.select("a")
            runCurrent()
            model.select("b")
            advanceUntilIdle()
            assertTrue(repository.firstRefreshCancelled)
            assertEquals("b", saved.get<String>("carId"))
            assertEquals("b", model.state.value.entries.single().carId)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun invalidSaveStaysOpenAndValidSaveSchedulesSyncWithoutUiCallback() =
        runTest(dispatcher) {
            val repository = FakeMileageRepository()
            var scheduled = 0
            val model = createViewModel(repository, SyncScheduler { scheduled++ })
            store.put("vm", model)
            model.select("a")
            advanceUntilIdle()
            model.openEntry()
            model.add("2026-11-01", "1200", "1.234", "75.20")
            advanceUntilIdle()
            assertTrue(model.state.value.editing)
            assertNotNull(model.state.value.message)
            assertNull(repository.added)
            val baseline = scheduled
            model.add("2026-11-01", "1200", "42,37", "75.20")
            advanceUntilIdle()
            assertFalse(model.state.value.editing)
            assertEquals(4237L, repository.added?.litersHundredths)
            assertEquals(baseline + 1, scheduled)
        }

    @Test
    fun clearingViewModelCancelsActiveRefreshWithoutShowingError() =
        runTest(dispatcher) {
            val repository = FakeMileageRepository().apply { blockFirstRefresh = true }
            val model = model(repository)
            model.select("a")
            runCurrent()
            store.clear()
            runCurrent()
            assertTrue(repository.firstRefreshCancelled)
            assertNull(model.state.value.message)
        }

    @Test
    fun permissionFailureBlocksCachedContent() =
        runTest(dispatcher) {
            val repository =
                FakeMileageRepository().apply {
                    loadFailure = MileageException(MileageProblem.ACCESS_DENIED)
                }
            val model = model(repository)
            model.load()
            advanceUntilIdle()
            assertTrue(model.state.value.blocked)
        }

    private fun model(
        repository: FakeMileageRepository,
        saved: SavedStateHandle = SavedStateHandle(),
    ) = createViewModel(repository, SyncScheduler {}, saved).also { store.put("vm", it) }

    private fun createViewModel(
        repository: MileageRepository,
        scheduler: SyncScheduler,
        saved: SavedStateHandle = SavedStateHandle(),
    ) =
        MileageViewModel(
            ObserveCarsUseCase(repository),
            ObserveCarHistoryUseCase(repository),
            RefreshCarsUseCase(repository, scheduler),
            RefreshCarHistoryUseCase(repository, scheduler),
            AddFuelEntryUseCase(repository, scheduler),
            ObserveCarStatisticsUseCase(repository),
            saved,
        )
}

private class FakeMileageRepository : MileageRepository {
    var loadFailure: Exception? = null
    var blockFirstRefresh = false
    var firstRefreshCancelled = false
    var added: NewFuelEntry? = null
    private val cars = MutableStateFlow(emptyList<Car>())

    override fun observeCars() = cars.asStateFlow()

    override fun observeEntries(carId: String) =
        flowOf(
            listOf(
                FuelEntry(carId, carId, "2026-11-01", 1200, 4237, 7520, "driver", SyncStatus.SYNCED)
            )
        )

    override fun observeRefreshTime(carId: String) = flowOf<Long?>(null)

    override suspend fun loadCars() {
        loadFailure?.let { throw it }
        cars.value = listOf(Car("a", "Car A", 650))
    }

    override suspend fun refresh(carId: String) {
        if (carId == "a" && blockFirstRefresh) {
            try {
                awaitCancellation()
            } finally {
                firstRefreshCancelled = true
            }
        }
    }

    override suspend fun add(entry: NewFuelEntry) {
        entry.validate()
        added = entry
    }

    override suspend fun sync() = false
}
