package com.couple.expensetracker.receiver

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.notification.NotificationHelper
import com.couple.expensetracker.util.SMSParser
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class BankNotificationListener : NotificationListenerService() {

    // NotificationListenerService is bound by the system before Hilt's component tree is ready,
    // so we use EntryPoint instead of @AndroidEntryPoint / @Inject.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun repository(): TransactionRepository
        fun prefs(): AppPreferences
        fun notificationHelper(): NotificationHelper
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ep: ListenerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ListenerEntryPoint::class.java)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MESSAGING_PACKAGES) return

        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: return
        val body = messagingStyleText(extras)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        if (body.length < 20) return

        Log.d("BankNotifListener", "candidate from=$sender body=${body.take(60)}")

        scope.launch {
            val prefs = ep.prefs()
            val repository = ep.repository()
            val notificationHelper = ep.notificationHelper()

            val keywords = prefs.getFilterKeywordsOnce()
            val exclusions = prefs.getExclusionKeywordsOnce()
            val parsed = SMSParser.parse(sender, body, keywords, exclusions) ?: return@launch
            Log.d("BankNotifListener", "parsed=$parsed")

            val username = prefs.myUsername.first().ifBlank { "me" }
            val txn = TransactionEntity(
                id = UUID.randomUUID().toString(),
                date = sbn.postTime,
                amount = parsed.amount,
                paymentType = parsed.paymentType,
                bankName = parsed.bankName,
                last4OrRef = parsed.last4OrRef,
                tag = "Unclassified",
                addedBy = username,
                source = "RCS",
                lastModified = System.currentTimeMillis(),
                syncStatus = "PENDING",
                rawMessage = body
            )
            val added = repository.addTransaction(txn)
            if (added) notificationHelper.showTransactionNotification(txn)
        }
    }

    private fun messagingStyleText(extras: android.os.Bundle): String? {
        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            )
        } else null
        return messages?.lastOrNull()?.text?.toString()
    }

    companion object {
        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",  // Google Messages — handles SMS + RCS
            "com.android.mms",                    // AOSP stock SMS
            "com.samsung.android.messaging",      // Samsung Messages
            "com.miui.sms",                       // Xiaomi MIUI
            "com.oppo.mms",                       // OPPO
            "com.vivo.message",                   // Vivo
            "com.oneplus.mms"                     // OnePlus
        )

        fun isGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val cn = ComponentName(context, BankNotificationListener::class.java)
            return flat.split(":").any { it == cn.flattenToString() }
        }
    }
}
