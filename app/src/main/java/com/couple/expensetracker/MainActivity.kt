package com.couple.expensetracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.notification.NotificationHelper
import com.couple.expensetracker.ui.navigation.AppNavigation
import com.couple.expensetracker.ui.navigation.ExternalNavEvent
import com.couple.expensetracker.ui.theme.ExpenseTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var prefs: AppPreferences

    private var pendingNavEvent: ExternalNavEvent? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch { transactionRepository.purgeOldTransactions() }

        requestRequiredPermissions()
        handleIncomingIntent(intent)

        setContent {
            ExpenseTrackerTheme {
                var navEvent by remember { mutableStateOf(pendingNavEvent) }

                AppNavigation(
                    externalNavEvent = navEvent,
                    onNavEventConsumed = { navEvent = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            "com.couple.expensetracker.DISCARD_CONFIRM" -> {
                val txnId = intent.getStringExtra(NotificationHelper.EXTRA_TRANSACTION_ID) ?: return
                val amount = intent.getDoubleExtra(NotificationHelper.EXTRA_AMOUNT, 0.0)
                val bankName = intent.getStringExtra(NotificationHelper.EXTRA_BANK_NAME) ?: ""
                val last4 = intent.getStringExtra(NotificationHelper.EXTRA_LAST4) ?: ""
                pendingNavEvent = ExternalNavEvent.OpenDiscardConfirm(txnId, amount, bankName, last4)
            }
            "com.couple.expensetracker.OPEN_UNCLASSIFIED" -> {
                pendingNavEvent = ExternalNavEvent.OpenUnclassified
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }
}
