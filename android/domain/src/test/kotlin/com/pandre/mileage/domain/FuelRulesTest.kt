package com.pandre.mileage.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelRulesTest {
    @Test
    fun totalsIncludePendingAndSyncedButExcludeRejectedEntries() {
        fun entry(status: SyncStatus) =
            FuelEntry(status.name, "car", "2026-11-01", 1200, 1000, 1500, "driver", status)
        assertEquals(FuelTotals(2000, 3000), FuelTotals.from(SyncStatus.entries.map(::entry)))
    }

    @Test
    fun unitPriceUsesHalfUpRoundingAtTheBoundary() {
        assertEquals("1.01", Amounts.unitPrice(201, 200))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLitersCannotBeEntered() {
        Amounts.parseHundredths("0.00")
    }

    @Test(expected = java.time.format.DateTimeParseException::class)
    fun impossibleCalendarDateCannotBeSaved() {
        NewFuelEntry("car", "2026-02-30", 1200, 4237, 7520).validate()
    }
}
