package com.pandre.mileage

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Screen and Room tests never initialize the production Firebase/WorkManager graph. */
class MileageTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        loader: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(loader, Application::class.java.name, context)
}
