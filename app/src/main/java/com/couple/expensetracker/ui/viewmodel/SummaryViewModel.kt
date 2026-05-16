package com.couple.expensetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.SummaryRepository
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val transactionRepository: TransactionRepository,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _monthKey = MutableStateFlow(DateUtils.currentMonthKey())
    val monthKey: StateFlow<String> = _monthKey

    val mySummary: StateFlow<MonthlySummaryEntity?> = _monthKey
        .flatMapLatest { summaryRepository.getMySummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val partnerSummary: StateFlow<PartnerSummaryEntity?> = _monthKey
        .flatMapLatest { summaryRepository.getPartnerSummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastSynced: StateFlow<Long> = prefs.lastSynced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Monthly category breakdown — recalculates when month changes
    private val _categoryBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    val categoryBreakdown: StateFlow<Map<String, Double>> = _categoryBreakdown

    // All-time category breakdown
    private val _allTimeCategoryBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    val allTimeCategoryBreakdown: StateFlow<Map<String, Double>> = _allTimeCategoryBreakdown

    // All-time aggregated summary (summed from monthly_summary table)
    private val _allTimeMySummary = MutableStateFlow<MonthlySummaryEntity?>(null)
    val allTimeMySummary: StateFlow<MonthlySummaryEntity?> = _allTimeMySummary

    private val _allTimePartnerCombinedTotal = MutableStateFlow(0.0)
    val allTimePartnerCombinedTotal: StateFlow<Double> = _allTimePartnerCombinedTotal

    private val _csvShareIntent = MutableSharedFlow<Intent>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val csvShareIntent: SharedFlow<Intent> = _csvShareIntent

    private val _csvError = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val csvError: SharedFlow<String> = _csvError

    init {
        loadMonthlyCategoryBreakdown()
        loadAllTimeData()
        viewModelScope.launch {
            summaryRepository.getAllMySummaries().collect { loadAllTimeSummary(it) }
        }
    }

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun goToPreviousMonth() {
        _monthKey.value = DateUtils.previousMonth(_monthKey.value)
        loadMonthlyCategoryBreakdown()
    }

    fun goToNextMonth() {
        val next = DateUtils.nextMonth(_monthKey.value)
        if (next <= DateUtils.currentMonthKey()) {
            _monthKey.value = next
            loadMonthlyCategoryBreakdown()
        }
    }

    private fun loadMonthlyCategoryBreakdown() {
        viewModelScope.launch {
            val username = prefs.myUsername.first().ifBlank { "me" }
            _categoryBreakdown.value = transactionRepository.getCategoryBreakdownForMonth(_monthKey.value, username)
        }
    }

    private fun loadAllTimeData() {
        viewModelScope.launch {
            val username = prefs.myUsername.first().ifBlank { "me" }
            _allTimeCategoryBreakdown.value = transactionRepository.getAllTimeCategoryBreakdown(username)
            val partnerSummaries = summaryRepository.getAllPartnerSummariesOnce()
            _allTimePartnerCombinedTotal.value = partnerSummaries.sumOf { it.combinedTotal }
        }
    }

    private fun loadAllTimeSummary(summaries: List<MonthlySummaryEntity>) {
        if (summaries.isEmpty()) {
            _allTimeMySummary.value = null
            return
        }
        val personal = summaries.sumOf { it.personalTotal }
        val combined = summaries.sumOf { it.combinedTotal }
        val other = summaries.sumOf { it.otherTotal }
        _allTimeMySummary.value = MonthlySummaryEntity(
            monthKey = "all",
            personalTotal = personal,
            combinedTotal = combined,
            otherTotal = other,
            grandTotal = personal + combined + other,
            lastUpdated = System.currentTimeMillis()
        )
        // Refresh all-time category breakdown whenever summaries update
        viewModelScope.launch {
            val username = prefs.myUsername.first().ifBlank { "me" }
            _allTimeCategoryBreakdown.value = transactionRepository.getAllTimeCategoryBreakdown(username)
            val partnerSummaries = summaryRepository.getAllPartnerSummariesOnce()
            _allTimePartnerCombinedTotal.value = partnerSummaries.sumOf { it.combinedTotal }
        }
    }

    fun exportCsv(context: Context) {
        viewModelScope.launch {
            try {
                val monthKey = _monthKey.value
                val username = prefs.myUsername.first().ifBlank { "me" }

                val myTxns = transactionRepository.getTransactionsForMonth(monthKey, username)
                val partnerTxns = transactionRepository.getPartnerTransactionsForMonth(monthKey)

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sb = StringBuilder()
                sb.appendLine("Date,Amount,Category,Tag,PaymentType,BankName,Reference,AddedBy")

                for (txn in myTxns) {
                    sb.appendLine(
                        "${sdf.format(Date(txn.date))}," +
                        "${txn.amount}," +
                        "${txn.category ?: ""}," +
                        "${txn.tag}," +
                        "${txn.paymentType}," +
                        "\"${txn.bankName}\"," +
                        "${txn.last4OrRef}," +
                        "${txn.addedBy}"
                    )
                }
                for (txn in partnerTxns) {
                    sb.appendLine(
                        "${sdf.format(Date(txn.date))}," +
                        "${txn.amount}," +
                        "," +
                        "${txn.tag}," +
                        "${txn.paymentType}," +
                        "\"${txn.bankName}\"," +
                        "${txn.last4OrRef}," +
                        "${txn.addedBy}"
                    )
                }

                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
                    val file = File(dir, "expenses_${monthKey}.csv")
                    file.writeText(sb.toString())
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Expenses $monthKey")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _csvShareIntent.emit(Intent.createChooser(intent, "Share CSV"))
            } catch (e: Exception) {
                _csvError.emit("Export failed: ${e.message}")
            }
        }
    }
}
