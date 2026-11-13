package com.pandre.mileage.domain

import kotlinx.coroutines.flow.Flow

data class Car(val id: String, val displayName: String, val estimatedFullTankRangeKm: Long? = null)

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED,
}

data class FuelEntry(
    val id: String,
    val carId: String,
    val fuelDate: String,
    val odometerKm: Long,
    val litersHundredths: Long,
    val totalPriceCents: Long,
    val createdByUid: String,
    val status: SyncStatus,
    val error: String? = null,
)

data class NewFuelEntry(
    val carId: String,
    val fuelDate: String,
    val odometerKm: Long,
    val litersHundredths: Long,
    val totalPriceCents: Long,
) {
    fun validate() {
        require(java.time.LocalDate.parse(fuelDate).toString() == fuelDate) {
            "Use a valid date in YYYY-MM-DD format."
        }
        require(odometerKm in 0..10_000_000) { "Enter a valid odometer reading." }
        require(litersHundredths in 1..100_000) { "Enter a valid quantity of liters." }
        require(totalPriceCents in 1..10_000_000) { "Enter a valid total price." }
    }
}

enum class MileageProblem {
    UNAUTHENTICATED,
    ACCESS_DENIED,
    CONNECTION,
    SERVER,
}

class MileageException(val problem: MileageProblem, cause: Throwable? = null) :
    Exception(problem.name, cause)

interface MileageRepository {
    fun observeCars(): Flow<List<Car>>

    fun observeEntries(carId: String): Flow<List<FuelEntry>>

    fun observeRefreshTime(carId: String): Flow<Long?>

    suspend fun loadCars()

    suspend fun refresh(carId: String)

    suspend fun add(entry: NewFuelEntry)

    suspend fun sync(): Boolean
}
