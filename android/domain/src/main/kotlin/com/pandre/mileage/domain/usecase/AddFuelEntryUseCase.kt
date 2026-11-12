package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.SyncScheduler

class AddFuelEntryUseCase(
    private val repository: MileageRepository,
    private val scheduler: SyncScheduler,
) {
    suspend operator fun invoke(entry: NewFuelEntry) {
        require(entry.carId.isNotBlank()) { "Select a car." }
        entry.validate()
        repository.add(entry)
        scheduler.schedule()
    }
}
