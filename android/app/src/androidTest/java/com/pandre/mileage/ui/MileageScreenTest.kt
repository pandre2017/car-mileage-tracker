package com.pandre.mileage.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.pandre.mileage.R
import com.pandre.mileage.domain.Car
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Screen boundary only. Does not launch the production Activity or contact Firebase. */
class MileageScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun label(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun signedOutScreenOffersGoogleSignIn() {
        var clicked = false
        content(MileageUiState(), false, onSignIn = { clicked = true })
        compose.onNodeWithText(label(R.string.sign_in)).performClick()
        compose.runOnIdle { assertTrue(clicked) }
        compose.onNodeWithText(label(R.string.add_fueling)).assertDoesNotExist()
    }

    @Test
    fun selectingSharedCarForwardsItsStableIdentity() {
        var selected: String? = null
        content(
            MileageUiState(cars = listOf(Car("car-a", "Family car"))),
            onSelect = { selected = it },
        )
        compose.onNodeWithText("Family car").performClick()
        compose.runOnIdle { assertEquals("car-a", selected) }
    }

    @Test
    fun fuelingFormSavesExactInputsAndShowsValidationThenSuccess() {
        val state =
            mutableStateOf(
                MileageUiState(cars = listOf(Car("a", "Family car")), selectedCarId = "a")
            )
        var submitted: List<String>? = null
        compose.setContent {
            MaterialTheme {
                MileageContent(
                    state.value,
                    true,
                    onSignIn = {},
                    onLoadCars = {},
                    onSelectCar = {},
                    onRefresh = {},
                    onOpenEntry = { state.value = state.value.copy(editing = true) },
                    onDismissEntry = { state.value = state.value.copy(editing = false) },
                    onSave = { date, odo, liters, total ->
                        submitted = listOf(date, odo, liters, total)
                        state.value =
                            if (liters == "1.234")
                                state.value.copy(message = "Use at most two decimals")
                            else state.value.copy(editing = false, message = null)
                    },
                )
            }
        }
        compose.onNodeWithText(label(R.string.add_fueling)).performClick()
        compose.onNodeWithText(label(R.string.fuel_date)).performTextReplacement("2026-11-01")
        compose.onNodeWithText(label(R.string.odometer)).performTextInput("1200")
        compose.onNodeWithText(label(R.string.liters)).performTextInput("1.234")
        compose.onNodeWithText(label(R.string.total_paid)).performTextInput("75.20")
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.onNodeWithText("Use at most two decimals").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.liters)).performTextReplacement("42,37")
        compose.onNodeWithText("Price per liter: EUR 1.77").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.save)).performClick()
        compose.onNodeWithText(label(R.string.record_fueling)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf("2026-11-01", "1200", "42,37", "75.20"), submitted)
        }
    }

    @Test
    fun errorCanBeRetriedAndLoadingDisablesRefresh() {
        var refreshed = false
        content(MileageUiState(message = "Could not connect"), onRefreshCars = { refreshed = true })
        compose.onNodeWithText("Could not connect").assertIsDisplayed()
        compose.onNodeWithText(label(R.string.refresh_cars)).performClick()
        compose.runOnIdle { assertTrue(refreshed) }
    }

    @Test
    fun loadingDisablesRefreshAndHidesEmptyMessage() {
        content(MileageUiState(loading = true))
        compose.onNodeWithText(label(R.string.refresh_cars)).assertIsNotEnabled()
        compose.onNodeWithText(label(R.string.no_cars)).assertDoesNotExist()
    }

    private fun content(
        state: MileageUiState,
        signedIn: Boolean = true,
        onSignIn: () -> Unit = {},
        onSelect: (String) -> Unit = {},
        onRefreshCars: () -> Unit = {},
    ) {
        compose.setContent {
            MileageContent(
                state,
                signedIn,
                onSignIn = onSignIn,
                onLoadCars = onRefreshCars,
                onSelectCar = onSelect,
                onRefresh = {},
                onOpenEntry = {},
                onDismissEntry = {},
                onSave = { _, _, _, _ -> },
            )
        }
    }
}
