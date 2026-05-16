package com.couple.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnclassifiedViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    val transactions: StateFlow<List<TransactionEntity>> = prefs.myUsername
        .flatMapLatest { username ->
            val addedBy = username.ifBlank { "me" }
            repository.getUnclassified(addedBy)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun classify(id: String, tag: String, category: String?) {
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            repository.editTransaction(
                entity.copy(
                    tag = tag,
                    category = category,
                    lastModified = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
            )
        }
    }

    fun editTransaction(id: String, amount: Double, bankName: String, category: String?) {
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            repository.editTransaction(
                entity.copy(
                    amount = amount,
                    bankName = bankName,
                    category = category,
                    lastModified = System.currentTimeMillis(),
                    syncStatus = "PENDING"
                )
            )
        }
    }

    fun discard(id: String) {
        viewModelScope.launch { repository.discardTransaction(id) }
    }
}
