package com.example

import android.content.Context
import android.app.backup.BackupManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("BackupWorker", "Starting periodic backup sync/flag registration.")
            BackupManager(applicationContext).dataChanged()
            Log.d("BackupWorker", "Google Auto Backup successfully flagged.")
            Result.success()
        } catch (e: Exception) {
            Log.e("BackupWorker", "Failed to flag backup: ${e.message}", e)
            Result.retry()
        }
    }
}
