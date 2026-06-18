package com.example

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.data.HabitDatabase
import com.example.data.HabitRepository
import java.util.concurrent.TimeUnit

class HabitTrackerApplication : Application() {

    val database: HabitDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            HabitDatabase::class.java,
            "habit_tracker_database"
        )
        .addMigrations(com.example.data.MIGRATION_1_2, com.example.data.MIGRATION_2_3)
        .build()
    }

    val repository: HabitRepository by lazy {
        HabitRepository(database.habitDao())
    }

    override fun onCreate() {
        super.onCreate()
        scheduleBackupJob()
    }

    private fun scheduleBackupJob() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // Run backup flag registration every 3 hours
            val backupWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "GoogleCloudBackupSyncJob",
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing to avoid resetting timer
                backupWorkRequest
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
