package com.pandre.mileage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pandre.mileage.R
import com.pandre.mileage.domain.Amounts
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.SyncStatus
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun sortFuelHistory(entries: List<FuelEntry>): List<FuelEntry> =
    entries.sortedWith(compareByDescending<FuelEntry> { it.fuelDate }.thenBy { it.id })

internal fun groupFuelHistory(entries: List<FuelEntry>): Map<YearMonth, List<FuelEntry>> =
    sortFuelHistory(entries).groupBy { YearMonth.parse(it.fuelDate.take(7)) }

@Composable
fun FuelHistoryScreen(carName: String, entries: List<FuelEntry>, onBack: () -> Unit) {
    val groups = remember(entries) { groupFuelHistory(entries) }
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    stringResource(R.string.fuel_history_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(carName, style = MaterialTheme.typography.titleMedium)
                if (entries.isEmpty()) Text(stringResource(R.string.no_entries))
                LazyColumn(
                    modifier = Modifier.weight(1f).testTag("full-fuel-history"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { (month, monthEntries) ->
                        item(key = "month-$month") {
                            Text(
                                month.format(monthFormat),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        items(monthEntries, key = { "entry-${it.id}" }) { FuelEntryCard(it) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FuelEntryCard(entry: FuelEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.entry_heading, entry.fuelDate, entry.odometerKm),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.entry_amounts,
                    Amounts.decimal(entry.litersHundredths),
                    Amounts.decimal(entry.totalPriceCents),
                )
            )
            Text(
                stringResource(
                    R.string.entry_status,
                    Amounts.unitPrice(entry.totalPriceCents, entry.litersHundredths),
                    entry.status.name.lowercase(),
                )
            )
            entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private val sampleHistory =
    listOf("2026-11-10", "2026-11-01", "2026-10-15", "2025-11-04").mapIndexed { index, date ->
        FuelEntry(
            "preview-$index",
            "preview-car",
            date,
            54000L - index * 500,
            4000,
            6800,
            "preview-user",
            SyncStatus.SYNCED,
        )
    }

@Preview(name = "History grouped by month", showBackground = true)
@Composable
private fun FuelHistoryScreenPreview() {
    FuelHistoryScreen("Family car", sampleHistory, onBack = {})
}

@Preview(name = "Fueling card", showBackground = true)
@Composable
private fun FuelEntryCardPreview() {
    MaterialTheme { FuelEntryCard(sampleHistory.first()) }
}

@Preview(name = "Empty history", showBackground = true)
@Composable
private fun EmptyFuelHistoryPreview() {
    FuelHistoryScreen("Family car", emptyList(), onBack = {})
}
