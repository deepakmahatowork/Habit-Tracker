package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Index
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "habits"
)
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val colorHex: String = "#FF6200EE",
    val createdAt: Long = System.currentTimeMillis(),
    val targetDaysPerWeek: Int = 7,  // Default to daily (7 days a week)
    val isArchived: Boolean = false,
    val frequencyAmount: Int = 1,
    val frequencyPeriod: String = "DAILY", // DAILY, WEEKLY, MONTHLY
    val orderIndex: Int = 0
)

@Entity(
    tableName = "habit_completions",
    indices = [Index(value = ["habitId", "dateString"], unique = true)]
)
data class HabitCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val dateString: String, // Format: YYYY-MM-DD
    val timestamp: Long = System.currentTimeMillis()
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE habits ADD COLUMN frequencyAmount INTEGER NOT NULL DEFAULT 1")
        database.execSQL("ALTER TABLE habits ADD COLUMN frequencyPeriod TEXT NOT NULL DEFAULT 'DAILY'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE habits ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
    }
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY orderIndex ASC, createdAt ASC")
    fun getAllActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getAllArchivedHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getHabitById(id: Int): Habit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit): Long

    @Query("UPDATE habits SET isArchived = 1 WHERE id = :id")
    suspend fun archiveHabit(id: Int)

    @Query("UPDATE habits SET isArchived = 0 WHERE id = :id")
    suspend fun restoreHabit(id: Int)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Int)

    @Query("UPDATE habits SET orderIndex = :newOrder WHERE id = :id")
    suspend fun updateHabitOrder(id: Int, newOrder: Int)

    // Completions query
    @Query("SELECT * FROM habit_completions ORDER BY dateString DESC")
    fun getAllCompletions(): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE dateString = :date")
    fun getCompletionsForDate(date: String): Flow<List<HabitCompletion>>

    @Query("SELECT * FROM habit_completions WHERE dateString BETWEEN :startDate AND :endDate")
    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<HabitCompletion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletion): Long

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND dateString = :dateString")
    suspend fun deleteCompletion(habitId: Int, dateString: String)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId")
    suspend fun clearCompletionsForHabit(habitId: Int)
}

@Database(entities = [Habit::class, HabitCompletion::class], version = 3, exportSchema = false)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
}
