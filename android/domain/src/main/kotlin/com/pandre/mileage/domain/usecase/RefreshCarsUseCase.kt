package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.SyncScheduler

class RefreshCarsUseCase(
    private val repository: MileageRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke() {
        repository.loadCars()
        scheduler.schedule()
    }
}
