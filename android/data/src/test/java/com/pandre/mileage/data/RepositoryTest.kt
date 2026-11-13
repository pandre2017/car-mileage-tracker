package com.pandre.mileage.data

import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.SyncStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RepositoryTest {
    private val session =
        object : AuthSession {
            override val uid = "driver"

            override suspend fun authorization() = "Bearer fixture"
        }

    @Test
    fun dtoAndEntityMappingStayInsideDataBoundary() = runTest {
        val dao = FakeDao()
        val repository = DefaultMileageRepository(dao, FakeApi(), session)
        repository.loadCars()
        repository.refresh("a")
        assertEquals("Car A", repository.observeCars().first().single().displayName)
        assertEquals(650L, repository.observeCars().first().single().estimatedFullTankRangeKm)
        val entry = repository.observeEntries("a").first().single()
        assertEquals(SyncStatus.SYNCED, entry.status)
        assertEquals(4237L, entry.litersHundredths)
        assertEquals("a", entry.carId)
    }

    @Test
    fun retryableUploadKeepsPendingRecordAndReturnsRetry() = runTest {
        val dao = FakeDao()
        val api = FakeApi().apply { uploadFailure = IOException("offline fixture") }
        val repository = DefaultMileageRepository(dao, api, session)
        repository.add(NewFuelEntry("a", "2026-11-01", 1200, 4237, 7520))
        assertTrue(repository.sync())
        assertEquals("PENDING", dao.pending().single().state)
        api.uploadFailure = null
        assertFalse(repository.sync())
        assertTrue(dao.pending().isEmpty())
        assertEquals(1, repository.observeEntries("a").first().size)
    }

    @Test
    fun duplicateLocalDateIsRejectedWithoutOverwriting() = runTest {
        val dao = FakeDao()
        val repository = DefaultMileageRepository(dao, FakeApi(), session)
        val input = NewFuelEntry("a", "2026-11-01", 1200, 4237, 7520)
        repository.add(input)
        try {
            repository.add(input)
            fail("Expected duplicate rejection")
        } catch (expected: IllegalArgumentException) {
            /* expected */
        }
        assertEquals(1, dao.pending().size)
    }

    @Test
    fun networkFailureMapsToDomainAndCancellationPropagates() = runTest {
        val api = FakeApi().apply { profileFailure = IOException("offline fixture") }
        val repository = DefaultMileageRepository(FakeDao(), api, session)
        try {
            repository.loadCars()
            fail("Expected failure")
        } catch (error: MileageException) {
            assertEquals(MileageProblem.CONNECTION, error.problem)
        }
        assertEquals(0, api.carRequests)
        api.profileFailure = CancellationException("cancel fixture")
        try {
            repository.loadCars()
            fail("Expected cancellation")
        } catch (expected: CancellationException) {
            /* expected */
        }
        assertEquals(0, api.carRequests)
    }
}

private class FakeApi : MileageApi {
    var profileFailure: Exception? = null
    var uploadFailure: Exception? = null
    var carRequests = 0
    private var entry =
        FuelDto("remote", "2026-11-01", 1200, 4237, 7520, "driver", "2026-11-01T12:00:00Z")

    override suspend fun me(auth: String): Profile {
        profileFailure?.let { throw it }
        return Profile("driver", "Driver", "fixture@example.test")
    }

    override suspend fun cars(auth: String): List<CarDto> {
        carRequests++
        return listOf(CarDto("a", "Car A", 650))
    }

    override suspend fun history(auth: String, carId: String) = listOf(entry)

    override suspend fun create(
        auth: String,
        carId: String,
        id: String,
        input: FuelInput,
    ): FuelDto {
        uploadFailure?.let { throw it }
        entry = entry.copy(id = id)
        return entry
    }
}

private class FakeDao : MileageDao {
    private val storedCars = MutableStateFlow(emptyList<CarEntity>())
    private val storedEntries = MutableStateFlow(emptyList<EntryEntity>())
    private val refreshes = MutableStateFlow(emptyList<RefreshEntity>())

    override fun cars() = storedCars

    override fun entries(carId: String) = storedEntries.map {
        it.filter { entry -> entry.carId == carId }
    }

    override suspend fun pending() = storedEntries.value.filter { it.state == "PENDING" }

    override suspend fun onDate(carId: String, date: String) =
        storedEntries.value.firstOrNull { it.carId == carId && it.fuelDate == date }

    override fun refreshed(carId: String) = refreshes.map {
        it.firstOrNull { refresh -> refresh.carId == carId }
    }

    override suspend fun putCars(cars: List<CarEntity>) {
        storedCars.value = cars
    }

    override suspend fun clearCars() {
        storedCars.value = emptyList()
    }

    override suspend fun putEntries(entries: List<EntryEntity>) {
        storedEntries.value =
            storedEntries.value.filter { old -> entries.none { it.id == old.id } } + entries
    }

    override suspend fun putRefresh(refresh: RefreshEntity) {
        refreshes.value = refreshes.value.filter { it.carId != refresh.carId } + refresh
    }

    override suspend fun clearSynced(carId: String) {
        storedEntries.value =
            storedEntries.value.filterNot { it.carId == carId && it.state == "SYNCED" }
    }

    override suspend fun status(id: String, state: String, error: String?) {
        storedEntries.value =
            storedEntries.value.map {
                if (it.id == id) it.copy(state = state, error = error) else it
            }
    }
}
