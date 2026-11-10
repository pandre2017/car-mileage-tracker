package com.pandre.mileage.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.time.LocalDate
import java.util.UUID

class MileageRepository(val dao: MileageDao, private val api: MileageApi, private val auth: FirebaseAuth?) {
    private val syncLock = Mutex()
    val uid get() = auth?.currentUser?.uid
    private suspend fun token(): String = "Bearer " + (auth?.currentUser?.getIdToken(false)?.await()?.token ?: error("Sign in to continue."))
    suspend fun loadCars() { api.me(token()); dao.replaceCars(api.cars(token())) }
    suspend fun refresh(carId: String) = syncLock.withLock { refreshUnlocked(carId) }
    private suspend fun refreshUnlocked(carId: String) {
        val remote = api.history(token(), carId)
        dao.replaceHistory(carId, remote.map { it.entity(carId) })
    }
    suspend fun add(carId: String, date: String, odometer: Long, liters: Long, cents: Long) {
        require(LocalDate.parse(date).toString() == date) { "Use a valid date in YYYY-MM-DD format." }
        require(odometer in 0..10_000_000 && liters in 1..100_000 && cents in 1..10_000_000) { "Check the odometer and amounts." }
        require(dao.onDate(carId, date) == null) { "This car already has a record on that date." }
        val owner = uid ?: error("Sign in to continue.")
        dao.putEntries(listOf(EntryEntity(UUID.randomUUID().toString(), carId, date, odometer, liters, cents, owner)))
    }
    suspend fun sync(): Boolean = syncLock.withLock {
        var retry = false
        for (entry in dao.pending()) {
            if (entry.createdByUid != uid) error("Sign in with the account that created pending entries.")
            try {
                val result = api.create(token(), entry.carId, entry.id, FuelInput(entry.fuelDate, entry.odometerKm, entry.litersHundredths, entry.totalPriceCents))
                dao.putEntries(listOf(result.entity(entry.carId)))
                refreshUnlocked(entry.carId)
            } catch (error: HttpException) {
                when (error.code()) {
                    400, 409 -> dao.status(entry.id, "FAILED", error.response()?.errorBody()?.string()?.let { body -> runCatching { com.google.gson.JsonParser.parseString(body).asJsonObject["message"].asString }.getOrNull() } ?: "Check this record.")
                    401, 403 -> throw error
                    else -> if (error.code() >= 500 || error.code() == 429) retry = true else dao.status(entry.id, "FAILED", "Request rejected (${error.code()}).")
                }
            } catch (error: java.io.IOException) { retry = true }
        }
        retry
    }
}
