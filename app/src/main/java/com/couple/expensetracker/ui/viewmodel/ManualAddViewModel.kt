package com.couple.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ManualAddViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    val categories: StateFlow<Set<String>> = prefs.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_CATEGORIES)

    fun saveTransaction(
        date: Long,
        amount: Double,
        paymentType: String,
        bankName: String,
        last4OrRef: String,
        tag: String,
        category: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val username = prefs.myUsername.first().ifBlank { "me" }
            val txn = TransactionEntity(
                id = UUID.randomUUID().toString(),
                date = date,
                amount = amount,
                paymentType = paymentType,
                bankName = bankName,
                last4OrRef = last4OrRef,
                tag = tag,
                addedBy = username,
                source = "Manual",
                lastModified = System.currentTimeMillis(),
                syncStatus = "PENDING",
                category = category
            )
            repository.addTransaction(txn)
            onSuccess()
        }
    }
}
