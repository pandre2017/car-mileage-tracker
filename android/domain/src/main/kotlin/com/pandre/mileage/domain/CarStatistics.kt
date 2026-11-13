package com.pandre.mileage.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Estimates from purchases and fueling habits, not a measurement of remaining tank fuel. */
data class CarStatistics(
    val consumptionHundredths: Long? = null,
    val nextFuelingOdometerKm: Long? = null,
    val initialRangeKm: Long? = null,
    val intervalCount: Int = 0,
    val includesPending: Boolean = false,
    val inconsistentHistory: Boolean = false,
) {
    companion object {
        fun from(car: Car?, entries: List<FuelEntry>): CarStatistics {
            val history =
                entries
                    .filter { it.carId == car?.id && it.status != SyncStatus.FAILED }
                    .sortedBy { it.fuelDate }
            val range = car?.estimatedFullTankRangeKm?.takeIf { it in 1..10_000_000 }
            val initial =
                CarStatistics(
                    initialRangeKm = range,
                    includesPending = history.any { it.status == SyncStatus.PENDING },
                )
            val intervals = history.zipWithNext()
            if (
                intervals.any { (first, next) ->
                    next.fuelDate <= first.fuelDate || next.odometerKm <= first.odometerKm
                }
            ) {
                return initial.copy(inconsistentHistory = true)
            }
            val latest = history.lastOrNull() ?: return initial
            val distance = latest.odometerKm - history.first().odometerKm
            val liters = history.drop(1).sumOf { it.litersHundredths }
            val consumption =
                if (distance > 0 && liters > 0) {
                    BigDecimal.valueOf(liters)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(distance), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                } else null
            val recentIntervals = intervals.takeLast(5)
            val learned = recentIntervals.size >= 2
            val expectedDistance =
                if (learned) {
                    BigDecimal.valueOf(
                            recentIntervals.sumOf { (first, next) ->
                                next.odometerKm - first.odometerKm
                            }
                        )
                        .divide(
                            BigDecimal.valueOf(recentIntervals.size.toLong()),
                            0,
                            RoundingMode.HALF_UP,
                        )
                        .longValueExact()
                } else range
            return initial.copy(
                consumptionHundredths = consumption,
                nextFuelingOdometerKm = expectedDistance?.let { latest.odometerKm + it },
                intervalCount = if (learned) recentIntervals.size else 0,
            )
        }
    }
}
