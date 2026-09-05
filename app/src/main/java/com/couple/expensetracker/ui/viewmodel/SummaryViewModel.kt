package com.couple.expensetracker.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.dao.SettlementDao
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.SettlementEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.SummaryRepository
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.util.DateUtils
import com.couple.expensetracker.util.SplitUtils
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
import java.util.UUID
import javax.inject.Inject

enum class CategoryFilter { Total, Personal, Shared, Other }

data class SharedPersonData(
    val username: String,
    val combinedTotal: Double,
    val balance: Double,  // negative = they owe me, positive = I owe them
    val settledThisMonth: Double = 0.0,  // total amount settled so far this month (always positive)
    val lastSettledBy: String? = null    // username who most recently marked a settlement with this partner
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val summaryRepository: SummaryRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementDao: SettlementDao,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _monthKey = MutableStateFlow(DateUtils.currentMonthKey())
    val monthKey: StateFlow<String> = _monthKey

    val mySummary: StateFlow<MonthlySummaryEntity?> = _monthKey
        .flatMapLatest { summaryRepository.getMySummary(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastSynced: StateFlow<Long> = prefs.lastSynced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _categoryFilter = MutableStateFlow(CategoryFilter.Total)
    val categoryFilter: StateFlow<CategoryFilter> = _categoryFilter

    private val _allTimeCategoryFilter = MutableStateFlow(CategoryFilter.Total)
    val allTimeCategoryFilter: StateFlow<CategoryFilter> = _allTimeCategoryFilter

    private val _categoryBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _categoryBreakdownPersonal = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _categoryBreakdownShared = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _categoryBreakdownOther = MutableStateFlow<Map<String, Double>>(emptyMap())

    val filteredCategoryBreakdown: StateFlow<Map<String, Double>> = combine(
        _categoryFilter, _categoryBreakdown, _categoryBreakdownPersonal, _categoryBreakdownShared, _categoryBreakdownOther
    ) { filter, total, personal, shared, other ->
        when (filter) {
            CategoryFilter.Total -> total
            CategoryFilter.Personal -> personal
            CategoryFilter.Shared -> shared
            CategoryFilter.Other -> other
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _allTimeCategoryBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _allTimePersonalBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _allTimeSharedBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _allTimeOtherBreakdown = MutableStateFlow<Map<String, Double>>(emptyMap())

    val filteredAllTimeCategoryBreakdown: StateFlow<Map<String, Double>> = combine(
        _allTimeCategoryFilter, _allTimeCategoryBreakdown, _allTimePersonalBreakdown, _allTimeSharedBreakdown, _allTimeOtherBreakdown
    ) { filter, total, personal, shared, other ->
        when (filter) {
            CategoryFilter.Total -> total
            CategoryFilter.Personal -> personal
            CategoryFilter.Shared -> shared
            CategoryFilter.Other -> other
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _allTimeMySummary = MutableStateFlow<MonthlySummaryEntity?>(null)
    val allTimeMySummary: StateFlow<MonthlySummaryEntity?> = _allTimeMySummary

    // Per-person shared data for the monthly view (combined total + balance per person)
    private val _sharedPersonsData = MutableStateFlow<List<SharedPersonData>>(emptyList())
    val sharedPersonsData: StateFlow<List<SharedPersonData>> = _sharedPersonsData

    // My overall balance for the month (positive = others owe me, negative = I owe others)
    private val _myBalance = MutableStateFlow(0.0)
    val myBalance: StateFlow<Double> = _myBalance

    // All-time per-person shared totals
    private val _allTimeSharedPersonsData = MutableStateFlow<List<SharedPersonData>>(emptyList())
    val allTimeSharedPersonsData: StateFlow<List<SharedPersonData>> = _allTimeSharedPersonsData

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
        // Reactively reload shared balance whenever my summary, partner transactions, or
        // settlements change (covers: month navigation, local deletes, local settle-ups, and
        // incoming sync data — including a partner's settlement confirmation arriving via Drive)
        viewModelScope.launch {
            _monthKey.flatMapLatest { mk ->
                combine(
                    summaryRepository.getMySummary(mk),
                    transactionRepository.getCombinedPartnerTransactionsFlow(mk),
                    settlementDao.getByMonthFlow(mk)
                ) { _, _, _ -> Unit }
            }.debounce(50).collectLatest { loadMonthlySharedData() }
        }
    }

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun setCategoryFilter(filter: CategoryFilter) { _categoryFilter.value = filter }
    fun setAllTimeCategoryFilter(filter: CategoryFilter) { _allTimeCategoryFilter.value = filter }

    // Either partner can mark a settlement (payer or receiver) — this now syncs properly, so
    // there's no need to restrict who can confirm it. We still record payer/receiver (the actual
    // money-flow direction, from currentBalance's sign) and separately markedBy (who tapped this).
    fun settleUp(personUsername: String, currentBalance: Double, settledAmount: Double) {
        if (kotlin.math.abs(currentBalance) < 0.01 || settledAmount <= 0.0) return
        viewModelScope.launch {
            val myUsername = prefs.myUsername.first().ifBlank { "me" }
            val iOweThem = currentBalance > 0
            settlementDao.insert(
                SettlementEntity(
                    id = UUID.randomUUID().toString(),
                    monthKey = _monthKey.value,
                    payer = if (iOweThem) myUsername else personUsername,
                    receiver = if (iOweThem) personUsername else myUsername,
                    amount = settledAmount,
                    markedBy = myUsername,
                    createdAt = System.currentTimeMillis()
                )
            )
            // Without this, the settlement only exists locally until some unrelated transaction
            // edit happens to schedule a sync — the partner would never see it get confirmed.
            transactionRepository.triggerAutoSync()
            loadMonthlySharedData()
        }
    }

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
            val mk = _monthKey.value
            _categoryBreakdown.value = transactionRepository.getCategoryBreakdownForMonth(mk, username)
            _categoryBreakdownPersonal.value = transactionRepository.getCategoryBreakdownByTagForMonth(mk, username, "Personal")
            _categoryBreakdownShared.value = transactionRepository.getCategoryBreakdownByTagForMonth(mk, username, "Combined")
            _categoryBreakdownOther.value = transactionRepository.getCategoryBreakdownByTagForMonth(mk, username, "Other")
        }
    }

    private fun loadMonthlySharedData() {
        viewModelScope.launch {
            val monthKey = _monthKey.value
            val myUsername = prefs.myUsername.first().ifBlank { "me" }
            val sharedUsernames = prefs.getSharedUsernamesOnce()

            if (sharedUsernames.isEmpty()) {
                _sharedPersonsData.value = emptyList()
                _myBalance.value = 0.0
                return@launch
            }

            // My Combined transactions for the month
            val myTxns = transactionRepository.getTransactionsForMonth(monthKey, myUsername)
                .filter { it.tag == "Combined" }

            // Each shared person's Combined transactions
            val sharedTxnsMap = sharedUsernames.associateWith { username ->
                transactionRepository.getCombinedPartnerTransactionsForMonth(monthKey, username)
            }

            // My paid amount
            val myPaid = myTxns.sumOf { it.amount }

            // My share from own transactions:
            //   new format → key = myUsername; old format → key = "me"; fallback = 50%
            var myShareTotal = 0.0
            for (txn in myTxns) {
                myShareTotal += SplitUtils.viewerAmount(txn.customSplits, myUsername, fallbackToMe = true, txn.amount)
            }
            // My share from partner transactions:
            //   partner stored my username as the key (both old and new format)
            for (pTxns in sharedTxnsMap.values) {
                for (txn in pTxns) {
                    myShareTotal += SplitUtils.viewerAmount(txn.customSplits, myUsername, fallbackToMe = false, txn.amount)
                }
            }
            // All settlements this month that involve me, regardless of which side confirmed them
            val monthSettlements = settlementDao.getByMonth(monthKey)
                .filter { it.payer == myUsername || it.receiver == myUsername }

            // Per-shared-person balance (with settlement adjustments)
            val sharedData = sharedUsernames.map { pUsername ->
                val pTxns = sharedTxnsMap[pUsername] ?: emptyList()
                val pPaid = pTxns.sumOf { it.amount }

                var pShareTotal = 0.0
                // Partner's share from my transactions: look for their username
                for (txn in myTxns) {
                    pShareTotal += SplitUtils.viewerAmount(txn.customSplits, pUsername, fallbackToMe = false, txn.amount)
                }
                // Partner's share from their own transactions:
                //   new format → key = pUsername; old format → key = "me"; fallback = 50%
                for (txn in pTxns) {
                    pShareTotal += SplitUtils.viewerAmount(txn.customSplits, pUsername, fallbackToMe = true, txn.amount)
                }
                // Partner's share from other partners' transactions
                for ((otherUsername, otherTxns) in sharedTxnsMap) {
                    if (otherUsername == pUsername) continue
                    for (txn in otherTxns) {
                        pShareTotal += SplitUtils.viewerAmount(txn.customSplits, pUsername, fallbackToMe = false, txn.amount)
                    }
                }
                val rawBalance = pPaid - pShareTotal
                val withPartner = monthSettlements.filter {
                    (it.payer == pUsername && it.receiver == myUsername) ||
                    (it.payer == myUsername && it.receiver == pUsername)
                }
                // pUsername paying me moves my "they owe me" balance toward 0 (adds, since it's negative).
                // Me paying pUsername moves my "I owe them" balance toward 0 (subtracts, since it's positive).
                val adjustment = withPartner.sumOf { s -> if (s.payer == pUsername) s.amount else -s.amount }
                val settledTotal = withPartner.sumOf { it.amount }
                val lastSettledBy = withPartner.maxByOrNull { it.createdAt }?.markedBy
                SharedPersonData(pUsername, pPaid, rawBalance + adjustment, settledTotal, lastSettledBy)
            }
            _sharedPersonsData.value = sharedData
            // Re-derive my balance from adjusted per-person values (balance signs are inverted: negative person.balance = they owe me)
            _myBalance.value = -sharedData.sumOf { it.balance }
        }
    }

    private fun loadAllTimeData() {
        viewModelScope.launch {
            val username = prefs.myUsername.first().ifBlank { "me" }
            _allTimeCategoryBreakdown.value = transactionRepository.getAllTimeCategoryBreakdown(username)
            _allTimePersonalBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(username, "Personal")
            _allTimeSharedBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(username, "Combined")
            _allTimeOtherBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(username, "Other")

            val sharedUsernames = prefs.getSharedUsernamesOnce()
            val allTimeShared = sharedUsernames.map { u ->
                SharedPersonData(
                    username = u,
                    combinedTotal = transactionRepository.getAllTimeCombinedPartnerTotal(u),
                    balance = 0.0  // all-time balance not computed (too expensive)
                )
            }
            _allTimeSharedPersonsData.value = allTimeShared
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
        viewModelScope.launch {
            val u = prefs.myUsername.first().ifBlank { "me" }
            _allTimeCategoryBreakdown.value = transactionRepository.getAllTimeCategoryBreakdown(u)
            _allTimePersonalBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(u, "Personal")
            _allTimeSharedBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(u, "Combined")
            _allTimeOtherBreakdown.value = transactionRepository.getAllTimeCategoryBreakdownByTag(u, "Other")
            val sharedUsernames = prefs.getSharedUsernamesOnce()
            _allTimeSharedPersonsData.value = sharedUsernames.map { name ->
                SharedPersonData(name, transactionRepository.getAllTimeCombinedPartnerTotal(name), 0.0)
            }
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
                        "${txn.category ?: ""}," +
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
