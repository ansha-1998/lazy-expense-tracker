package com.couple.expensetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.db.dao.PartnerTransactionDao
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
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

    val myTransactions: StateFlow<List<TransactionEntity>> = combine(
        prefs.myUsername,
        _selectedFilter
    ) { username, filter -> Pair(username.ifBlank { "me" }, filter) }
        .flatMapLatest { (addedBy, filter) ->
            if (filter == "All") repository.getAll(addedBy)
            else repository.getByTag(filter, addedBy)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val partnerCombined: StateFlow<List<PartnerTransactionEntity>> = _selectedFilter
        .flatMapLatest { filter ->
            if (filter == "Combined") partnerTransactionDao.getCombined()
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: String) { _selectedFilter.value = filter }

    fun updateTag(id: String, tag: String) {
        viewModelScope.launch { repository.updateTag(id, tag) }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { repository.discardTransaction(id) }
    }

    fun editTransaction(id: String, amount: Double, bankName: String, category: String?) {
        viewModelScope.launch {
            val entity = repository.getById(id) ?: return@launch
            repository.editTransaction(entity.copy(amount = amount, bankName = bankName, category = category, lastModified = System.currentTimeMillis(), syncStatus = "PENDING"))
        }
    }
}
