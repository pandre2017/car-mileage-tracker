package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.MileageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class CarHistory(val entries: List<FuelEntry>, val refreshedAt: Long?)

class ObserveCarHistoryUseCase(private val repository: MileageRepository) {
    operator fun invoke(carId: String): Flow<CarHistory> {
        require(carId.isNotBlank()) { "Select a car." }
        return combine(repository.observeEntries(carId), repository.observeRefreshTime(carId)) {
            entries,
            time ->
            CarHistory(entries, time)
        }
    }
}
