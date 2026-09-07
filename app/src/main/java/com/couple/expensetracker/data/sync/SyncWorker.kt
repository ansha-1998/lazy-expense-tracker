package com.couple.expensetracker.data.sync

import android.accounts.Account
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.couple.expensetracker.data.preferences.AppPreferences
import com.google.android.gms.auth.GoogleAuthUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Runs a sync independent of any screen/ViewModel being alive — used to push a shared
// (Combined-tagged) transaction to Drive right away instead of waiting for the debounced
// auto-sync in SettingsViewModel, which only runs while the Settings screen has been opened.
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveSync: DriveSync,
    private val prefs: AppPreferences
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive"
        const val UNIQUE_WORK_NAME = "shared_txn_sync"
        const val KEY_FULL_SYNC = "full_sync"
    }

    override suspend fun doWork(): Result {
        val folderId = prefs.driveFolderId.first()
        val myUsername = prefs.myUsername.first()
        val sharedUsernames = prefs.getSharedUsernamesOnce()
        val email = prefs.googleAccountEmail.first()
        // Always re-arm the next noon/midnight sync, regardless of what triggered this run —
        // keeps the twice-daily chain alive even if the app process is otherwise never opened.
        DailySyncScheduler.scheduleNext(applicationContext)
        if (folderId.isBlank() || myUsername.isBlank() || email.isBlank()) return Result.success()

        val token = withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(applicationContext, Account(email, "com.google"), DRIVE_SCOPE)
            } catch (e: Exception) {
                null
            }
        } ?: return Result.retry()

        val fullSync = inputData.getBoolean(KEY_FULL_SYNC, false)
        val success = driveSync.sync(token, folderId, myUsername, sharedUsernames, fullSync = fullSync)
        return if (success) Result.success() else Result.retry()
    }
}
