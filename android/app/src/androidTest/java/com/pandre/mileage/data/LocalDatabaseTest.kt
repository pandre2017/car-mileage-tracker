package com.pandre.mileage.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalDatabaseTest {
    @Test
    fun versionOneMigrationPreservesCarsPendingRecordsAndRefreshTimes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL(
                "CREATE TABLE cars (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL)"
            )
            old.execSQL(
                "CREATE TABLE entries (id TEXT NOT NULL PRIMARY KEY, carId TEXT NOT NULL, fuelDate TEXT NOT NULL, odometerKm INTEGER NOT NULL, litersHundredths INTEGER NOT NULL, totalPriceCents INTEGER NOT NULL, createdByUid TEXT NOT NULL, state TEXT NOT NULL, error TEXT)"
            )
            old.execSQL(
                "CREATE TABLE refreshes (carId TEXT NOT NULL PRIMARY KEY, refreshedAt INTEGER NOT NULL)"
            )
            old.execSQL("INSERT INTO cars VALUES ('car', 'Family car')")
            old.execSQL(
                "INSERT INTO entries VALUES ('pending', 'car', '2026-11-01', 1000, 4000, 6800, 'driver', 'PENDING', NULL)"
            )
            old.execSQL("INSERT INTO refreshes VALUES ('car', 1234)")
            old.version = 1
        }
        val db =
            Room.databaseBuilder(context, LocalDatabase::class.java, name)
                .addMigrations(LocalDatabase.MIGRATION_1_2)
                .build()
        try {
            val dao = db.dao()
            assertEquals(CarEntity("car", "Family car"), dao.cars().first().single())
            assertEquals("pending", dao.pending().single().id)
            assertEquals(1234L, dao.refreshed("car").first()?.refreshedAt)
            dao.replaceCars(listOf(CarEntity("car", "Family car", 650)))
            assertEquals(650L, dao.cars().first().single().estimatedFullTankRangeKm)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun refreshReplacesSyncedHistoryAndPreservesPendingAndFailed() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build()
        try {
            val dao = db.dao()
            fun entry(id: String, state: String) =
                EntryEntity(id, "car", "2026-11-01", 1000, 4237, 7520, "driver", state)
            dao.putEntries(
                listOf(
                    entry("old", "SYNCED"),
                    entry("pending", "PENDING"),
                    entry("failed", "FAILED"),
                )
            )
            dao.replaceHistory("car", listOf(entry("remote", "SYNCED")))
            assertEquals(
                setOf("pending", "failed", "remote"),
                dao.entries("car").first().map { it.id }.toSet(),
            )
            assertNotNull(dao.refreshed("car").first())
            dao.replaceHistory("car", listOf(entry("pending", "SYNCED")))
            val rows = dao.entries("car").first()
            assertEquals(2, rows.size)
            assertEquals("SYNCED", rows.first { it.id == "pending" }.state)
        } finally {
            db.close()
        }
    }
}
