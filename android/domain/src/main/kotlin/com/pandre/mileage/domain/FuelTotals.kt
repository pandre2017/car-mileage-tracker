package com.pandre.mileage.domain

data class FuelTotals(val litersHundredths: Long = 0, val totalPriceCents: Long = 0) {
    companion object {
        /** Pending purchases count locally; permanently rejected records do not. */
        fun from(entries: List<FuelEntry>): FuelTotals {
            val purchases = entries.filter { it.status != SyncStatus.FAILED }
            return FuelTotals(
                purchases.sumOf { it.litersHundredths },
                purchases.sumOf { it.totalPriceCents },
            )
        }
    }
}
