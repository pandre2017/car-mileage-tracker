package com.pandre.mileage.domain.usecase

import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.MileageRepository
import kotlinx.coroutines.flow.Flow

class ObserveCarsUseCase(private val repository: MileageRepository) {
    operator fun invoke(): Flow<List<Car>> = repository.observeCars()
}
