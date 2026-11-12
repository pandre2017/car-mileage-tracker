package com.pandre.mileage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pandre.mileage.platform.GoogleAccountSignIn
import com.pandre.mileage.ui.MileageScreen
import com.pandre.mileage.ui.MileageViewModel

class MainActivity : ComponentActivity() {
    // The manifest guarantees this process uses MileageApplication.
    private val app
        get() = application as MileageApplication

    private val googleSignIn by lazy { GoogleAccountSignIn(this, app.auth) }
    private val model: MileageViewModel by viewModels {
        viewModelFactory {
            initializer {
                MileageViewModel(
                    app.observeCars,
                    app.observeCarHistory,
                    app.refreshCars,
                    app.refreshCarHistory,
                    app.addFuelEntry,
                    createSavedStateHandle(),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MileageScreen(model, app.auth?.currentUser != null) { success, failure ->
                googleSignIn.signIn(
                    {
                        success()
                        model.load()
                    },
                    failure,
                )
            }
        }
        if (app.auth?.currentUser != null) model.load()
    }
}
