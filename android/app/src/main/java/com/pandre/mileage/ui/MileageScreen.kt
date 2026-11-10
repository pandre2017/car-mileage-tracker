package com.pandre.mileage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandre.mileage.domain.Amounts
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MileageScreen(model: MileageViewModel, initiallySignedIn: Boolean, signIn: (() -> Unit, (String) -> Unit) -> Unit) {
    var signedIn by remember { mutableStateOf(initiallySignedIn) }
    var authError by remember { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    val cars by model.cars.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val entries by model.entries.collectAsStateWithLifecycle()
    val refreshed by model.refreshed.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val blocked by model.blocked.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Car Mileage", style = MaterialTheme.typography.headlineLarge)
                Text("Shared cars · fuel history", style = MaterialTheme.typography.bodyLarge)
                if (!signedIn || blocked) {
                    Text("Sign in with your provisioned Google account.")
                    Button(onClick = { signIn({ signedIn = true; authError = null }, { authError = it }) }) { Text("Sign in with Google") }
                    authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (signedIn && !blocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.load() }, enabled = !busy) { Text("Refresh cars") }
                        if (selected != null) OutlinedButton(onClick = { model.refresh() }, enabled = !busy) { Text("Refresh history") }
                    }
                    if (cars.isEmpty()) Text("No shared cars yet. Ask the administrator to provision them.")
                    cars.forEach { car ->
                        FilterChip(selected = selected == car.id, onClick = { model.select(car.id) }, label = { Text(car.displayName) })
                    }
                    if (selected != null) {
                        Text(refreshed?.let { "Updated " + DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.of("Europe/Lisbon")).format(Instant.ofEpochMilli(it.refreshedAt)) } ?: "History has not been refreshed yet")
                        Button(onClick = { adding = true }) { Text("Add fueling") }
                        val counted = entries.filter { it.state != "FAILED" }
                        Text("${Amounts.decimal(counted.sumOf { it.litersHundredths })} L purchased · EUR ${Amounts.decimal(counted.sumOf { it.totalPriceCents })}")
                        if (entries.isEmpty()) Text("No fuel records. Add your first fueling.")
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            items(entries, key = { it.id }) { entry ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${entry.fuelDate} · ${entry.odometerKm} km", style = MaterialTheme.typography.titleMedium)
                                        Text("${Amounts.decimal(entry.litersHundredths)} L · EUR ${Amounts.decimal(entry.totalPriceCents)}")
                                        Text("EUR ${Amounts.unitPrice(entry.totalPriceCents, entry.litersHundredths)}/L · ${entry.state.lowercase()}")
                                        entry.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (adding) FuelDialog(busy, message, onDismiss = { adding = false }, onSave = { date, odo, liters, total -> model.add(date, odo, liters, total) { adding = false } })
        }
    }
}

@Composable
private fun FuelDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("Europe/Lisbon")).toString()) }
    var odometer by rememberSaveable { mutableStateOf("") }
    var liters by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    val price = runCatching { Amounts.unitPrice(Amounts.parseHundredths(total), Amounts.parseHundredths(liters)) }.getOrDefault("—")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Record fueling") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(date, { date = it }, label = { Text("Date · YYYY-MM-DD") }, singleLine = true)
            OutlinedTextField(odometer, { odometer = it }, label = { Text("Odometer · km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            OutlinedTextField(liters, { liters = it }, label = { Text("Liters") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(total, { total = it }, label = { Text("Total paid · EUR") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Text("Price per liter: EUR $price")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { onSave(date, odometer, liters, total) }, enabled = !busy) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
