package com.pandre.mileage.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandre.mileage.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MileageViewModel(private val repository: MileageRepository, private val scheduleSync: () -> Unit) : ViewModel() {
    val cars = repository.dao.cars().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selected = MutableStateFlow<String?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries = selected.flatMapLatest { if (it == null) flowOf(emptyList()) else repository.dao.entries(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val refreshed = selected.flatMapLatest { if (it == null) flowOf(null) else repository.dao.refreshed(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val message = MutableStateFlow<String?>(null)
    val blocked = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    fun load() = run { action { repository.loadCars(); blocked.value = false; scheduleSync() } }
    fun select(id: String) { selected.value = id; refresh() }
    fun refresh() = run { action { selected.value?.let { repository.refresh(it) }; blocked.value = false; scheduleSync() } }
    fun add(date: String, odometer: String, liters: String, total: String, done: () -> Unit) {
        action {
            val carId = selected.value ?: error("Select a car.")
            repository.add(carId, date, odometer.toLongOrNull() ?: error("Enter a whole odometer reading."), com.pandre.mileage.domain.Amounts.parseHundredths(liters), com.pandre.mileage.domain.Amounts.parseHundredths(total))
            scheduleSync(); done()
        }
    }
    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try { block(); message.value = null }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (error is retrofit2.HttpException && error.code() in listOf(401, 403)) blocked.value = true
                message.value = when {
                    error is retrofit2.HttpException && error.code() == 403 -> "Access is not configured. Ask the administrator to provision your Google account and car."
                    error is retrofit2.HttpException && error.code() == 401 -> "Your session needs renewal. Sign in again with the same Google account."
                    error is java.io.IOException -> "Could not connect. Cached records and pending entries are kept."
                    else -> error.message ?: "Please try again."
                }
            } finally { busy.value = false }
        }
    }
}
