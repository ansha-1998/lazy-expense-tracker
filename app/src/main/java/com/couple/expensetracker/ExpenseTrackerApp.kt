package com.couple.expensetracker

import android.app.Application
import com.couple.expensetracker.notification.NotificationHelper
import com.couple.expensetracker.util.ConnectivityObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExpenseTrackerApp : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var connectivityObserver: ConnectivityObserver

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannel()
        connectivityObserver.register()
    }
}
