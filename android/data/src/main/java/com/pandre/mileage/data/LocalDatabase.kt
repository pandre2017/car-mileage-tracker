package com.pandre.mileage.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cars")
data class CarEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val estimatedFullTankRangeKm: Long? = null,
)

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val carId: String,
    val fuelDate: String,
    val odometerKm: Long,
    val litersHundredths: Long,
    val totalPriceCents: Long,
    val createdByUid: String,
    val state: String = "PENDING",
    val error: String? = null,
)

@Entity(tableName = "refreshes")
data class RefreshEntity(@PrimaryKey val carId: String, val refreshedAt: Long)

@Dao
interface MileageDao {
    @Query("SELECT * FROM cars ORDER BY displayName") fun cars(): Flow<List<CarEntity>>

    @Query("SELECT * FROM entries WHERE carId = :carId ORDER BY fuelDate DESC")
    fun entries(carId: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE state = 'PENDING' ORDER BY fuelDate")
    suspend fun pending(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE carId = :carId AND fuelDate = :date LIMIT 1")
    suspend fun onDate(carId: String, date: String): EntryEntity?

    @Query("SELECT * FROM refreshes WHERE carId = :carId")
    fun refreshed(carId: String): Flow<RefreshEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCars(cars: List<CarEntity>)

    @Query("DELETE FROM cars") suspend fun clearCars()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntries(entries: List<EntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putRefresh(refresh: RefreshEntity)

    @Query("DELETE FROM entries WHERE carId = :carId AND state = 'SYNCED'")
    suspend fun clearSynced(carId: String)

    @Query("UPDATE entries SET state = :state, error = :error WHERE id = :id")
    suspend fun status(id: String, state: String, error: String?)

    @Transaction
    suspend fun replaceCars(cars: List<CarEntity>) {
        clearCars()
        putCars(cars)
    }

    @Transaction
    suspend fun replaceHistory(carId: String, entries: List<EntryEntity>) {
        clearSynced(carId)
        putEntries(entries)
        putRefresh(RefreshEntity(carId, System.currentTimeMillis()))
    }
}

@Database(
    entities = [CarEntity::class, EntryEntity::class, RefreshEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): MileageDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE cars ADD COLUMN estimatedFullTankRangeKm INTEGER")
                }
            }
    }
}
