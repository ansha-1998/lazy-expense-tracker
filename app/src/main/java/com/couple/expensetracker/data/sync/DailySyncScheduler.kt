package com.couple.expensetracker.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.couple.expensetracker.data.preferences.AppPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Runs one full sync a day at a user-configurable time (AppPreferences.autoSyncTimeMinutes,
// set in Settings), independent of the app being open. Deliberately a single time rather than a
// fixed schedule shared by both partners — if both devices synced at the same fixed moment, their
// writes to Drive could race each other (e.g. two independent settlements created before either
// side has seen the other's), so each partner should pick a different time.
//
// WorkManager has no "run at this clock time" API, so this schedules a single delayed run to the
// next occurrence of that time, and that run (in SyncWorker.doWork()) re-arms this for the next
// day — a self-sustaining chain rather than a fixed-interval PeriodicWorkRequest, which can't
// guarantee a specific clock time.
object DailySyncScheduler {
    private const val UNIQUE_WORK_NAME = "daily_configured_sync"

    // Cancels the fixed noon/midnight schedule from a prior version of this feature, so it
    // doesn't keep running alongside the new user-configurable one.
    private const val LEGACY_WORK_NAME = "daily_noon_midnight_sync"

    suspend fun scheduleNext(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME)
        val prefs = AppPreferences(context.applicationContext)
        val minutes = prefs.getAutoSyncTimeMinutesOnce()
        val targetHour = minutes / 60
        val targetMinute = minutes % 60

        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val delayMs = next.timeInMillis - now.timeInMillis

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(Data.Builder().putBoolean(SyncWorker.KEY_FULL_SYNC, true).build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
