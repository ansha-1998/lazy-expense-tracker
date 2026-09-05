package com.couple.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.db.dao.PartnerTransactionDao
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val partnerTransactionDao: PartnerTransactionDao,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter

    private val _monthKey = MutableStateFlow(DateUtils.currentMonthKey())
    val monthKey: StateFlow<String> = _monthKey

    val myTransactions: StateFlow<List<TransactionEntity>> = combine(
        prefs.myUsername,
        _selectedFilter,
        _monthKey
    ) { username, filter, month -> Triple(username.ifBlank { "me" }, filter, month) }
        .flatMapLatest { (addedBy, filter, month) ->
            if (filter == "All") repository.getAllByMonth(month, addedBy)
            else repository.getByTagAndMonth(filter, month, addedBy)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val partnerCombined: StateFlow<List<PartnerTransactionEntity>> = combine(
        _selectedFilter,
        _monthKey
    ) { filter, month -> Pair(filter, month) }
        .flatMapLatest { (filter, month) ->
            if (filter == "Combined") partnerTransactionDao.getCombinedByMonth(month)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: String) { _selectedFilter.value = filter }

    fun goToPreviousMonth() {
        _monthKey.value = DateUtils.previousMonth(_monthKey.value)
    }

    fun goToNextMonth() {
        val next = DateUtils.nextMonth(_monthKey.value)
        if (next <= DateUtils.currentMonthKey()) _monthKey.value = next
    }

    fun updateTag(id: String, tag: String) {
        viewModelScope.launch { repository.updateTag(id, tag) }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.discardTransaction(id) }
    }

    fun editTransaction(id: String, amount: Double, bankName: String, category: String?, customSplits: String?) {
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            repository.editTransaction(entity.copy(
                amount = amount,
                bankName = bankName,
                category = category,
                customSplits = customSplits,
                lastModified = System.currentTimeMillis(),
                syncStatus = "PENDING"
            ))
        }
    }
}
