package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.SyncScheduler

class RefreshCarHistoryUseCase(
    private val repository: MileageRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(carId: String) {
        require(carId.isNotBlank()) { "Select a car." }
        repository.refresh(carId)
        scheduler.schedule()
    }
}
