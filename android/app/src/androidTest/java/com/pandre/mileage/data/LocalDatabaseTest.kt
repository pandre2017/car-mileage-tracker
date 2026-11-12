package com.pandre.mileage.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalDatabaseTest {
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
