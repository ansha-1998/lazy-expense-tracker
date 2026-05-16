package com.couple.expensetracker.widget

import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.SummaryRepository
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.data.sync.DriveSync
import com.couple.expensetracker.util.ConnectivityObserver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun transactionRepository(): TransactionRepository
    fun summaryRepository(): SummaryRepository
    fun appPreferences(): AppPreferences
    fun driveSync(): DriveSync
    fun connectivityObserver(): ConnectivityObserver
}
