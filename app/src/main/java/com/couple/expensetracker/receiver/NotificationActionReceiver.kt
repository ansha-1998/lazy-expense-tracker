package com.couple.expensetracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TransactionRepository
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(NotificationHelper.EXTRA_TRANSACTION_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        val tag = when (intent.action) {
            NotificationHelper.ACTION_TAG_PERSONAL -> "Personal"
            NotificationHelper.ACTION_TAG_COMBINED -> "Combined"
            NotificationHelper.ACTION_TAG_OTHER -> "Other"
            else -> return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                repository.updateTag(transactionId, tag)
                notificationHelper.cancelNotification(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
