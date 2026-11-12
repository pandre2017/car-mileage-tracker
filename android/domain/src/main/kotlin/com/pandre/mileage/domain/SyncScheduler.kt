package com.pandre.mileage.domain

/** Schedules durable upload work without exposing WorkManager to domain or presentation. */
fun interface SyncScheduler {
    fun schedule()
}
