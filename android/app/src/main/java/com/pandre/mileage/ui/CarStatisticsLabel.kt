package com.pandre.mileage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pandre.mileage.R
import com.pandre.mileage.domain.Amounts
import com.pandre.mileage.domain.CarStatistics

@Composable
internal fun CarStatisticsLabel(statistics: CarStatistics) {
    val consumption = statistics.consumptionHundredths?.let(Amounts::decimal)
    val nextOdometer = statistics.nextFuelingOdometerKm
    val initialRange = statistics.initialRangeKm
    // Give screen readers meaningful labels and context instead of reading the emoji names.
    val description =
        listOfNotNull(
                consumption?.let { stringResource(R.string.estimated_consumption, it) }
                    ?: stringResource(R.string.consumption_needs_history),
                when {
                    statistics.inconsistentHistory ->
                        stringResource(R.string.statistics_invalid_history)
                    nextOdometer != null ->
                        stringResource(R.string.next_fueling_estimate, nextOdometer)
                    initialRange != null -> stringResource(R.string.initial_range, initialRange)
                    else -> stringResource(R.string.prediction_needs_range)
                },
                if (nextOdometer == null) null
                else if (statistics.intervalCount > 0)
                    stringResource(R.string.prediction_intervals, statistics.intervalCount)
                else stringResource(R.string.prediction_initial_assumption),
                if (statistics.includesPending) stringResource(R.string.statistics_pending)
                else null,
                stringResource(R.string.statistics_disclaimer),
            )
            .joinToString(". ")
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.consumption_compact, consumption ?: "—"),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.next_fueling_compact, nextOdometer?.toString() ?: "—"),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(name = "Learned statistics", showBackground = true)
@Composable
private fun LearnedStatisticsPreview() {
    MaterialTheme {
        CarStatisticsLabel(
            CarStatistics(
                consumptionHundredths = 750,
                nextFuelingOdometerKm = 54650,
                intervalCount = 5,
            )
        )
    }
}

@Preview(name = "Initial range, no odometer yet", showBackground = true)
@Composable
private fun InitialStatisticsPreview() {
    MaterialTheme { CarStatisticsLabel(CarStatistics(initialRangeKm = 650)) }
}

@Preview(name = "Missing statistics", showBackground = true)
@Composable
private fun MissingStatisticsPreview() {
    MaterialTheme { CarStatisticsLabel(CarStatistics()) }
}
