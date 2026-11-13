package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.CarStatistics
import com.pandre.mileage.domain.MileageRepository
import kotlinx.coroutines.flow.combine

class ObserveCarStatisticsUseCase(private val repository: MileageRepository) {
    operator fun invoke(carId: String) =
        combine(repository.observeCars(), repository.observeEntries(carId)) { cars, entries ->
            CarStatistics.from(cars.firstOrNull { it.id == carId }, entries)
        }
}
