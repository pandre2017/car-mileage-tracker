package com.pandre.mileage.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandre.mileage.domain.Amounts
import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.FuelTotals
import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.usecase.AddFuelEntryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarHistoryUseCase
import com.pandre.mileage.domain.usecase.ObserveCarsUseCase
import com.pandre.mileage.domain.usecase.RefreshCarHistoryUseCase
import com.pandre.mileage.domain.usecase.RefreshCarsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MileageUiState(
    val cars: List<Car> = emptyList(),
    val selectedCarId: String? = null,
    val entries: List<FuelEntry> = emptyList(),
    val refreshedAt: Long? = null,
    val loading: Boolean = false,
    val message: String? = null,
    val blocked: Boolean = false,
    val editing: Boolean = false,
) {
    val totals: FuelTotals
        get() = FuelTotals.from(entries)
}

class MileageViewModel(
    private val observeCars: ObserveCarsUseCase,
    private val observeCarHistory: ObserveCarHistoryUseCase,
    private val refreshCars: RefreshCarsUseCase,
    private val refreshCarHistory: RefreshCarHistoryUseCase,
    private val addFuelEntry: AddFuelEntryUseCase,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(MileageUiState(selectedCarId = savedState["carId"]))
    val state = mutableState.asStateFlow()
    private var historyJob: Job? = null
    private var operationJob: Job? = null
    private var operationVersion = 0

    init {
        viewModelScope.launch {
            observeCars().collect { cars ->
                mutableState.update { it.copy(cars = cars) }
            }
        }
        state.value.selectedCarId?.let(::observeHistory)
    }

    fun load() = runOperation {
        refreshCars()
        state.value.selectedCarId?.let { refreshCarHistory(it) }
        mutableState.update { it.copy(blocked = false) }
    }

    fun select(id: String) {
        if (id != state.value.selectedCarId) {
            savedState["carId"] = id
            mutableState.update {
                it.copy(
                    selectedCarId = id,
                    entries = emptyList(),
                    refreshedAt = null,
                    editing = false,
                )
            }
            observeHistory(id)
        }
        refresh()
    }

    fun refresh() = runOperation {
        state.value.selectedCarId?.let { refreshCarHistory(it) }
        mutableState.update { it.copy(blocked = false) }
    }

    fun openEntry() {
        mutableState.update { it.copy(editing = true, message = null) }
    }

    fun dismissEntry() {
        if (!state.value.loading) mutableState.update { it.copy(editing = false, message = null) }
    }

    fun add(date: String, odometer: String, liters: String, total: String) {
        if (state.value.loading) return
        runOperation {
            val carId = state.value.selectedCarId ?: error("Select a car.")
            addFuelEntry(
                NewFuelEntry(
                    carId,
                    date,
                    odometer.toLongOrNull() ?: error("Enter a whole odometer reading."),
                    Amounts.parseHundredths(liters),
                    Amounts.parseHundredths(total),
                )
            )
            mutableState.update { it.copy(editing = false) }
        }
    }

    private fun observeHistory(carId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            observeCarHistory(carId).collect { (entries, time) ->
                mutableState.update {
                    if (it.selectedCarId == carId) it.copy(entries = entries, refreshedAt = time)
                    else it
                }
            }
        }
    }

    private fun runOperation(block: suspend () -> Unit) {
        val version = ++operationVersion
        operationJob?.cancel()
        mutableState.update { it.copy(loading = true, message = null) }
        operationJob = viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val problem = (error as? MileageException)?.problem
                val text =
                    when (problem) {
                        MileageProblem.ACCESS_DENIED ->
                            "Access is not configured. Ask the administrator to provision your Google account and car."
                        MileageProblem.UNAUTHENTICATED ->
                            "Your session needs renewal. Sign in again with the same Google account."
                        MileageProblem.CONNECTION ->
                            "Could not connect. Cached records and pending entries are kept."
                        MileageProblem.SERVER ->
                            "The server could not complete the request. Please try again."
                        null -> error.message ?: "Please try again."
                    }
                mutableState.update {
                    it.copy(
                        message = text,
                        blocked =
                            it.blocked ||
                                problem == MileageProblem.ACCESS_DENIED ||
                                problem == MileageProblem.UNAUTHENTICATED,
                    )
                }
            } finally {
                if (version == operationVersion) mutableState.update { it.copy(loading = false) }
            }
        }
    }
}
