package com.pandre.mileage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStatisticsTest {
    private val car = Car("car", "Family car", 650)

    @Test
    fun noHistoryShowsRangeWithoutInventingOdometer() {
        val result = CarStatistics.from(car, emptyList())
        assertEquals(650L, result.initialRangeKm)
        assertNull(result.nextFuelingOdometerKm)
        assertNull(result.consumptionHundredths)
    }

    @Test
    fun oneOrTwoRecordsUseInitialRangeAndExcludeFirstPurchaseFromConsumption() {
        val first = entry(1, 50000, 9000)
        assertEquals(50650L, CarStatistics.from(car, listOf(first)).nextFuelingOdometerKm)
        val result = CarStatistics.from(car, listOf(first, entry(2, 50500, 4000)))
        assertEquals(800L, result.consumptionHundredths)
        assertEquals(51150L, result.nextFuelingOdometerKm)
        assertEquals(0, result.intervalCount)
    }

    @Test
    fun consumptionIsWeightedByDistanceAndPredictionUsesFuelingHabits() {
        val result =
            CarStatistics.from(
                car,
                listOf(entry(3, 51200, 6000), entry(1, 50000, 9999), entry(2, 50400, 3000)),
            )
        assertEquals(750L, result.consumptionHundredths)
        assertEquals(51800L, result.nextFuelingOdometerKm)
        assertEquals(2, result.intervalCount)
    }

    @Test
    fun predictionUsesOnlyLastFiveIntervals() {
        val history =
            listOf(entry(1, 0, 4000)) + (2..7).map { entry(it, 10000L + (it - 2) * 500, 4000) }
        val result = CarStatistics.from(car, history)
        assertEquals(13000L, result.nextFuelingOdometerKm)
        assertEquals(5, result.intervalCount)
    }

    @Test
    fun failedAndOtherCarRecordsAreExcludedAndPendingAreMarked() {
        val result =
            CarStatistics.from(
                car,
                listOf(
                    entry(1, 1000, 4000),
                    entry(2, 1500, 4000).copy(status = SyncStatus.PENDING),
                    entry(3, 3000, 9000).copy(status = SyncStatus.FAILED),
                    entry(4, 9000, 9000).copy(carId = "other"),
                ),
            )
        assertEquals(800L, result.consumptionHundredths)
        assertEquals(2150L, result.nextFuelingOdometerKm)
        assertTrue(result.includesPending)
    }

    @Test
    fun missingOrInvalidRangeDoesNotBlockLearnedPrediction() {
        assertNull(
            CarStatistics.from(
                    car.copy(estimatedFullTankRangeKm = null),
                    listOf(entry(1, 1000, 4000)),
                )
                .nextFuelingOdometerKm
        )
        assertNull(
            CarStatistics.from(car.copy(estimatedFullTankRangeKm = 0), emptyList()).initialRangeKm
        )
        val result =
            CarStatistics.from(
                car.copy(estimatedFullTankRangeKm = null),
                (1..3).map { entry(it, it * 500L, 4000) },
            )
        assertEquals(2000L, result.nextFuelingOdometerKm)
    }

    @Test
    fun nonIncreasingOdometerOrDuplicateDateDoesNotProduceMisleadingEstimate() {
        for (second in listOf(entry(2, 1000, 4000), entry(2, 900, 4000), entry(1, 1500, 4000))) {
            val result = CarStatistics.from(car, listOf(entry(1, 1000, 4000), second))
            assertTrue(result.inconsistentHistory)
            assertNull(result.consumptionHundredths)
            assertNull(result.nextFuelingOdometerKm)
        }
    }

    @Test
    fun roundsConsumptionAndPredictedOdometer() {
        val result =
            CarStatistics.from(
                car,
                listOf(entry(1, 1000, 4000), entry(2, 1300, 2000), entry(3, 1601, 2000)),
            )
        assertEquals(666L, result.consumptionHundredths)
        assertEquals(1902L, result.nextFuelingOdometerKm)
    }

    private fun entry(day: Int, odometer: Long, liters: Long) =
        FuelEntry(
            "entry-$day",
            "car",
            "2026-11-${day.toString().padStart(2, '0')}",
            odometer,
            liters,
            6800,
            "driver",
            SyncStatus.SYNCED,
        )
}
