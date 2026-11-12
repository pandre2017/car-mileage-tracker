package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.MileageRepository

enum class SyncOutcome {
    COMPLETE,
    RETRY,
}

class SyncPendingFuelEntriesUseCase(private val repository: MileageRepository) {
    suspend operator fun invoke(): SyncOutcome =
        if (repository.sync()) SyncOutcome.RETRY else SyncOutcome.COMPLETE
}
