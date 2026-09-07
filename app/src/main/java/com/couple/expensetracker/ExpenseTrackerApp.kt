package com.couple.expensetracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.couple.expensetracker.data.sync.DailySyncScheduler
import com.couple.expensetracker.notification.NotificationHelper
import com.couple.expensetracker.util.ConnectivityObserver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ExpenseTrackerApp : Application(), Configuration.Provider {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannel()
        connectivityObserver.register()
        // Bootstraps the configured daily sync chain — idempotent (REPLACE), so this just confirms
        // the next occurrence is scheduled correctly every time the process starts.
        appScope.launch { DailySyncScheduler.scheduleNext(this@ExpenseTrackerApp) }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
