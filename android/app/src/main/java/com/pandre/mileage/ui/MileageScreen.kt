package com.pandre.mileage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandre.mileage.R
import com.pandre.mileage.domain.Amounts
import com.pandre.mileage.domain.Car
import com.pandre.mileage.domain.FuelEntry
import com.pandre.mileage.domain.SyncStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MileageScreen(
    model: MileageViewModel,
    initiallySignedIn: Boolean,
    signIn: (() -> Unit, (String) -> Unit) -> Unit,
) {
    var signedIn by remember { mutableStateOf(initiallySignedIn) }
    var authError by remember { mutableStateOf<String?>(null) }
    val state by model.state.collectAsStateWithLifecycle()
    MileageContent(
        state,
        signedIn,
        authError,
        onSignIn = {
            signIn(
                {
                    signedIn = true
                    authError = null
                },
                { authError = it },
            )
        },
        onLoadCars = model::load,
        onSelectCar = model::select,
        onRefresh = model::refresh,
        onOpenEntry = model::openEntry,
        onDismissEntry = model::dismissEntry,
        onSave = model::add,
    )
}

@Composable
fun MileageContent(
    state: MileageUiState,
    signedIn: Boolean,
    authError: String? = null,
    onSignIn: () -> Unit,
    onLoadCars: () -> Unit,
    onSelectCar: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenEntry: () -> Unit,
    onDismissEntry: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    val cars = state.cars
    val selected = state.selectedCarId
    val entries = state.entries
    val message = state.message
    val busy = state.loading
    val blocked = state.blocked
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.app_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    stringResource(R.string.app_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!signedIn || blocked) {
                    Text(stringResource(R.string.sign_in_prompt))
                    Button(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
                    authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                if (!state.editing)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (signedIn && !blocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onLoadCars, enabled = !busy) {
                            Text(stringResource(R.string.refresh_cars))
                        }
                        if (selected != null)
                            OutlinedButton(onClick = onRefresh, enabled = !busy) {
                                Text(stringResource(R.string.refresh_history))
                            }
                    }
                    if (cars.isEmpty() && !busy && message == null)
                        Text(stringResource(R.string.no_cars))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(cars, key = { it.id }) { car ->
                            FilterChip(
                                selected = selected == car.id,
                                onClick = { onSelectCar(car.id) },
                                label = { Text(car.displayName) },
                            )
                        }
                    }
                    if (selected != null) {
                        Text(
                            state.refreshedAt?.let {
                                stringResource(
                                    R.string.updated,
                                    DateTimeFormatter.ofPattern("dd MMM HH:mm")
                                        .withZone(ZoneId.of("Europe/Lisbon"))
                                        .format(Instant.ofEpochMilli(it)),
                                )
                            } ?: stringResource(R.string.never_refreshed)
                        )
                        Button(onClick = onOpenEntry) { Text(stringResource(R.string.add_fueling)) }
                        Text(
                            stringResource(
                                R.string.summary,
                                Amounts.decimal(state.totals.litersHundredths),
                                Amounts.decimal(state.totals.totalPriceCents),
                            )
                        )
                        if (entries.isEmpty() && !busy && message == null)
                            Text(stringResource(R.string.no_entries))
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            stringResource(
                                                R.string.entry_heading,
                                                entry.fuelDate,
                                                entry.odometerKm,
                                            ),
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
                                                Amounts.unitPrice(
                                                    entry.totalPriceCents,
                                                    entry.litersHundredths,
                                                ),
                                                entry.status.name.lowercase(),
                                            )
                                        )
                                        entry.error?.let {
                                            Text(it, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.editing)
                FuelDialog(busy, message, onDismiss = onDismissEntry, onSave = onSave)
        }
    }
}

@Composable
private fun FuelDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var date by rememberSaveable {
        mutableStateOf(LocalDate.now(ZoneId.of("Europe/Lisbon")).toString())
    }
    var odometer by rememberSaveable { mutableStateOf("") }
    var liters by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    val price = runCatching {
        Amounts.unitPrice(Amounts.parseHundredths(total), Amounts.parseHundredths(liters))
    }
        .getOrDefault("—")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_fueling)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    date,
                    { date = it },
                    label = { Text(stringResource(R.string.fuel_date)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    odometer,
                    { odometer = it },
                    label = { Text(stringResource(R.string.odometer)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    liters,
                    { liters = it },
                    label = { Text(stringResource(R.string.liters)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    total,
                    { total = it },
                    label = { Text(stringResource(R.string.total_paid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text(stringResource(R.string.unit_price, price))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(date, odometer, liters, total) }, enabled = !busy) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Preview(name = "Fueling form", group = "Fueling", showBackground = true)
@Composable
private fun FuelDialogPreview() {
    MaterialTheme {
        FuelDialog(busy = false, error = null, onDismiss = {}, onSave = { _, _, _, _ -> })
    }
}

@Preview(name = "Fueling validation error", group = "Fueling", showBackground = true)
@Composable
private fun FuelDialogErrorPreview() {
    MaterialTheme {
        FuelDialog(
            busy = false,
            error = "Enter a valid quantity of liters.",
            onDismiss = {},
            onSave = { _, _, _, _ -> },
        )
    }
}

@Preview(name = "Sign in", group = "Mileage", showBackground = true)
@Composable
private fun SignInPreview() {
    PreviewMileageContent(signedIn = false)
}

@Preview(name = "Sign-in error", group = "Mileage", showBackground = true)
@Composable
private fun SignInErrorPreview() {
    PreviewMileageContent(signedIn = false, authError = "Could not sign in. Please try again.")
}

@Preview(name = "No cars", group = "Mileage", showBackground = true)
@Composable
private fun NoCarsPreview() {
    PreviewMileageContent()
}

@Preview(name = "Loading", group = "Mileage", showBackground = true)
@Composable
private fun LoadingPreview() {
    PreviewMileageContent(MileageUiState(loading = true))
}

@Preview(name = "Empty history", group = "Mileage", showBackground = true)
@Composable
private fun EmptyHistoryPreview() {
    PreviewMileageContent(previewHistory.copy(entries = emptyList()))
}

@Preview(name = "Fuel history", group = "Mileage", showBackground = true, heightDp = 1000)
@Composable
private fun FuelHistoryPreview() {
    PreviewMileageContent(previewHistory)
}

@Preview(name = "Connection error", group = "Mileage", showBackground = true)
@Composable
private fun ConnectionErrorPreview() {
    PreviewMileageContent(previewHistory.copy(message = "Could not connect. Please try again."))
}

@Composable
private fun PreviewMileageContent(
    state: MileageUiState = MileageUiState(),
    signedIn: Boolean = true,
    authError: String? = null,
) {
    MileageContent(
        state = state,
        signedIn = signedIn,
        authError = authError,
        onSignIn = {},
        onLoadCars = {},
        onSelectCar = {},
        onRefresh = {},
        onOpenEntry = {},
        onDismissEntry = {},
        onSave = { _, _, _, _ -> },
    )
}

private val previewHistory =
    MileageUiState(
        cars = listOf(Car("family-car", "Family car"), Car("city-car", "City car")),
        selectedCarId = "family-car",
        refreshedAt = Instant.parse("2026-11-10T18:30:00Z").toEpochMilli(),
        entries =
            listOf(
                previewEntry("pending", "2026-11-10", 54200, SyncStatus.PENDING),
                previewEntry(
                    "failed",
                    "2026-11-04",
                    53700,
                    SyncStatus.FAILED,
                    "Odometer reading is invalid.",
                ),
                previewEntry("synced", "2026-10-28", 53200, SyncStatus.SYNCED),
            ),
    )

private fun previewEntry(
    id: String,
    date: String,
    odometerKm: Long,
    status: SyncStatus,
    error: String? = null,
) =
    FuelEntry(
        id = id,
        carId = "family-car",
        fuelDate = date,
        odometerKm = odometerKm,
        litersHundredths = 4000,
        totalPriceCents = 6800,
        createdByUid = "preview-user",
        status = status,
        error = error,
    )
