package com.pandre.mileage.ui

import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.SyncStatus
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelHistoryTest {
    @Test
    fun sortsByFuelDateRatherThanInputOrderAndKeepsAllStatuses() {
        val entries =
            listOf(
                entry("2025-12-31"),
                entry("2026-02-01", SyncStatus.FAILED),
                entry("2026-01-15", SyncStatus.PENDING),
            )
        assertEquals(
            listOf("2026-02-01", "2026-01-15", "2025-12-31"),
            sortFuelHistory(entries).map { it.fuelDate },
        )
    }

    @Test
    fun groupsByYearAndMonthWithNewestGroupsAndEntriesFirst() {
        val groups =
            groupFuelHistory(
                listOf(
                    entry("2025-11-10"),
                    entry("2026-11-01"),
                    entry("2026-10-03"),
                    entry("2026-11-12"),
                )
            )
        assertEquals(
            listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2025, 11)),
            groups.keys.toList(),
        )
        assertEquals(
            listOf("2026-11-12", "2026-11-01"),
            groups[YearMonth.of(2026, 11)]?.map { it.fuelDate },
        )
    }

    @Test
    fun emptyHistoryHasNoGroups() {
        assertEquals(emptyMap<YearMonth, List<FuelEntry>>(), groupFuelHistory(emptyList()))
    }

    private fun entry(date: String, status: SyncStatus = SyncStatus.SYNCED) =
        FuelEntry(date, "car", date, 1000, 4000, 6800, "test-user", status)
}
