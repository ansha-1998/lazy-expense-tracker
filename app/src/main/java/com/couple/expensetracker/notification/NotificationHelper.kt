package com.couple.expensetracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.couple.expensetracker.MainActivity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.receiver.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "expense_channel"
        const val ACTION_TAG_PERSONAL = "com.couple.expensetracker.ACTION_TAG_PERSONAL"
        const val ACTION_TAG_COMBINED = "com.couple.expensetracker.ACTION_TAG_COMBINED"
        const val ACTION_TAG_OTHER = "com.couple.expensetracker.ACTION_TAG_OTHER"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_BANK_NAME = "bank_name"
        const val EXTRA_LAST4 = "last4_or_ref"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expense Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for detected bank transactions"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showTransactionNotification(txn: TransactionEntity) {
        val notificationId = txn.id.hashCode()
        val body = "₹${"%.2f".format(txn.amount)} via ${txn.paymentType} · ${txn.bankName} · ${txn.last4OrRef}"

        val personalIntent = actionIntent(ACTION_TAG_PERSONAL, txn, notificationId)
        val combinedIntent = actionIntent(ACTION_TAG_COMBINED, txn, notificationId)
        val otherIntent = actionIntent(ACTION_TAG_OTHER, txn, notificationId)

        val discardIntent = discardPendingIntent(txn, notificationId)
        val contentIntent = openUnclassifiedPendingIntent(notificationId)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Payment Detected")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Personal", personalIntent)
            .addAction(0, "Combined", combinedIntent)
            .addAction(0, "Other", otherIntent)
            .addAction(0, "Discard", discardIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    private fun actionIntent(action: String, txn: TransactionEntity, notificationId: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TRANSACTION_ID, txn.id)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId + action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openUnclassifiedPendingIntent(notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.couple.expensetracker.OPEN_UNCLASSIFIED"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId + "unclassified".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun discardPendingIntent(txn: TransactionEntity, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.couple.expensetracker.DISCARD_CONFIRM"
            putExtra(EXTRA_TRANSACTION_ID, txn.id)
            putExtra(EXTRA_AMOUNT, txn.amount)
            putExtra(EXTRA_BANK_NAME, txn.bankName)
            putExtra(EXTRA_LAST4, txn.last4OrRef)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationId + "discard".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
