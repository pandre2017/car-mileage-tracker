package com.pandre.mileage.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.gson.JsonParser
import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.MileageException
import com.pandre.mileage.domain.MileageProblem
import com.pandre.mileage.domain.MileageRepository
import com.pandre.mileage.domain.NewFuelEntry
import com.pandre.mileage.domain.SyncStatus
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class DefaultMileageRepository(
    private val dao: MileageDao,
    private val api: MileageApi,
    private val session: AuthSession,
    private val reportFailure: (String, Exception) -> Unit = { _, _ -> },
) : MileageRepository {
    private val syncLock = Mutex()

    override fun observeCars() = dao.cars().map { cars -> cars.map { Car(it.id, it.displayName) } }

    override fun observeEntries(carId: String) =
        dao.entries(carId).map { entries ->
            entries.map { entry ->
                FuelEntry(
                    entry.id,
                    entry.carId,
                    entry.fuelDate,
                    entry.odometerKm,
                    entry.litersHundredths,
                    entry.totalPriceCents,
                    entry.createdByUid,
                    SyncStatus.valueOf(entry.state),
                    entry.error,
                )
            }
        }

    override fun observeRefreshTime(carId: String) = dao.refreshed(carId).map { it?.refreshedAt }

    override suspend fun loadCars() = withDomainErrors {
        api.me(session.authorization())
        dao.replaceCars(api.cars(session.authorization()).map { CarEntity(it.id, it.displayName) })
    }

    override suspend fun refresh(carId: String) = withDomainErrors {
        syncLock.withLock { refreshUnlocked(carId) }
    }

    private suspend fun refreshUnlocked(carId: String) {
        val remote = api.history(session.authorization(), carId)
        dao.replaceHistory(carId, remote.map { it.entity(carId) })
    }

    override suspend fun add(entry: NewFuelEntry) = withDomainErrors {
        entry.validate()
        // Serialize local check + insertion with refresh and sync so duplicate taps cannot race.
        syncLock.withLock {
            require(dao.onDate(entry.carId, entry.fuelDate) == null) {
                "This car already has a record on that date."
            }
            val owner = session.uid ?: throw MileageException(MileageProblem.UNAUTHENTICATED)
            dao.putEntries(
                listOf(
                    EntryEntity(
                        UUID.randomUUID().toString(),
                        entry.carId,
                        entry.fuelDate,
                        entry.odometerKm,
                        entry.litersHundredths,
                        entry.totalPriceCents,
                        owner,
                    )
                )
            )
        }
    }

    override suspend fun sync(): Boolean = withDomainErrors {
        syncLock.withLock {
            var retry = false
            for (entry in dao.pending()) {
                check(entry.createdByUid == session.uid) {
                    "Sign in with the account that created pending entries."
                }
                try {
                    val result =
                        api.create(
                            session.authorization(),
                            entry.carId,
                            entry.id,
                            FuelInput(
                                entry.fuelDate,
                                entry.odometerKm,
                                entry.litersHundredths,
                                entry.totalPriceCents,
                            ),
                        )
                    dao.putEntries(listOf(result.entity(entry.carId)))
                    refreshUnlocked(entry.carId)
                } catch (error: HttpException) {
                    reportFailure("background sync", error)
                    when (error.code()) {
                        400,
                        409 -> dao.status(entry.id, "FAILED", serverMessage(error))
                        401,
                        403 -> throw error
                        else ->
                            if (error.code() >= 500 || error.code() == 429) retry = true
                            else
                                dao.status(
                                    entry.id,
                                    "FAILED",
                                    "Request rejected (${error.code()}).",
                                )
                    }
                } catch (error: IOException) {
                    reportFailure("background sync", error)
                    retry = true
                }
            }
            retry
        }
    }

    private fun serverMessage(error: HttpException): String =
        runCatching {
            JsonParser.parseString(error.response()?.errorBody()?.string())
                .asJsonObject["message"]
                .asString
        }
            .getOrNull() ?: "Check this record."

    private suspend fun <T> withDomainErrors(block: suspend () -> T): T =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpException) {
            reportFailure("repository", error)
            val problem =
                when (error.code()) {
                    401 -> MileageProblem.UNAUTHENTICATED
                    403 -> MileageProblem.ACCESS_DENIED
                    else -> MileageProblem.SERVER
                }
            throw MileageException(problem, error)
        } catch (error: IOException) {
            throw MileageException(MileageProblem.CONNECTION, error)
        } catch (error: FirebaseNetworkException) {
            throw MileageException(MileageProblem.CONNECTION, error)
        } catch (error: FirebaseAuthException) {
            throw MileageException(MileageProblem.UNAUTHENTICATED, error)
        }
}
