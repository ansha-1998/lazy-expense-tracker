package com.couple.expensetracker.data.repository

import android.content.Context
import com.couple.expensetracker.data.db.dao.MonthlySummaryDao
import com.couple.expensetracker.data.db.dao.PartnerTransactionDao
import com.couple.expensetracker.data.db.dao.TransactionDao
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.sync.SyncWorker
import com.couple.expensetracker.util.DateUtils
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.couple.expensetracker.widget.ExpenseWidget
import com.couple.expensetracker.widget.KEY_HIDDEN_IDS
import com.couple.expensetracker.widget.KEY_PENDING_TXNS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val summaryDao: MonthlySummaryDao,
    private val partnerTransactionDao: PartnerTransactionDao,
    @ApplicationContext private val context: Context
) {
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val insertMutex = Mutex()

    private val _autoSyncTrigger = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val autoSyncTrigger: SharedFlow<Unit> = _autoSyncTrigger.asSharedFlow()

    // Lets other repositories (e.g. settlements) piggyback on the same debounced auto-sync
    // instead of each needing their own trigger/scheduling mechanism.
    fun triggerAutoSync() { _autoSyncTrigger.tryEmit(Unit) }

    // A shared expense is the one thing the partner is actively waiting to see, so it skips the
    // 60s debounce and runs as its own background task immediately (independent of any screen
    // being open). Rapid successive shared additions collapse into a single sync via unique work
    // + REPLACE — no need to run the whole thing once per transaction when one sync covers all of them.
    private fun triggerInstantSyncIfShared(tag: String) {
        if (tag != "Combined") return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun getUnclassified(addedBy: String): Flow<List<TransactionEntity>> =
        transactionDao.getByTag("Unclassified", addedBy)

    fun getAll(addedBy: String): Flow<List<TransactionEntity>> =
        transactionDao.getAll(addedBy)

    fun getByTag(tag: String, addedBy: String): Flow<List<TransactionEntity>> =
        transactionDao.getByTag(tag, addedBy)

    fun getAllByMonth(monthKey: String, addedBy: String): Flow<List<TransactionEntity>> =
        transactionDao.getAllByMonth(monthKey, addedBy)

    fun getByTagAndMonth(tag: String, monthKey: String, addedBy: String): Flow<List<TransactionEntity>> =
        transactionDao.getByTagAndMonth(tag, monthKey, addedBy)

    suspend fun getById(id: String): TransactionEntity? = transactionDao.getById(id)

    suspend fun addTransaction(entity: TransactionEntity): Boolean {
        val inserted = insertMutex.withLock {
            if (entity.source != "Manual" && isDuplicate(entity)) return@withLock false
            transactionDao.insert(entity)
            true
        }
        if (!inserted) return false
        recalculateSummary(DateUtils.toMonthKey(entity.date), entity.addedBy)
        _autoSyncTrigger.tryEmit(Unit)
        triggerInstantSyncIfShared(entity.tag)
        widgetScope.launch {
            val ids = GlanceAppWidgetManager(context).getGlanceIds(ExpenseWidget::class.java)
            if (ids.isEmpty()) return@launch
            // Step 1: write to Glance state → instant composable re-render, item appears immediately
            val entry = "${entity.id}|${"%.0f".format(entity.amount)}|${entity.bankName}"
            for (id in ids) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    val current = prefs[KEY_PENDING_TXNS] ?: ""
                    val updated = if (current.isBlank()) entry else "$current\n$entry"
                    prefs.toMutablePreferences().also { it[KEY_PENDING_TXNS] = updated }
                }
            }
            // Step 2: full DB refresh in background — real data replaces the pending entry
            for (id in ids) ExpenseWidget().update(context, id)
        }
        return true
    }

    suspend fun isDuplicate(entity: TransactionEntity): Boolean {
        if (!entity.rawMessage.isNullOrBlank()) {
            return transactionDao.countByRawMessage(entity.rawMessage) > 0
        }
        val since = System.currentTimeMillis() - 5 * 60 * 1000L
        return transactionDao.countRecentBySignature(entity.amount, entity.bankName, entity.last4OrRef, since) > 0
    }

    suspend fun updateTag(id: String, tag: String) {
        val entity = transactionDao.getById(id) ?: return
        transactionDao.updateTag(id, tag, System.currentTimeMillis())
        recalculateSummary(DateUtils.toMonthKey(entity.date), entity.addedBy)
        _autoSyncTrigger.tryEmit(Unit)
        triggerInstantSyncIfShared(tag)
        refreshWidget()
    }

    suspend fun editTransaction(entity: TransactionEntity) {
        val old = transactionDao.getById(entity.id)
        transactionDao.update(entity)
        recalculateSummary(DateUtils.toMonthKey(entity.date), entity.addedBy)
        if (old != null && DateUtils.toMonthKey(old.date) != DateUtils.toMonthKey(entity.date)) {
            recalculateSummary(DateUtils.toMonthKey(old.date), old.addedBy)
        }
        refreshWidget()
    }

    suspend fun discardTransaction(id: String) {
        val entity = transactionDao.getById(id) ?: return
        transactionDao.deleteById(id)
        recalculateSummary(DateUtils.toMonthKey(entity.date), entity.addedBy)
        _autoSyncTrigger.tryEmit(Unit)
        refreshWidget()
    }

    suspend fun getPendingOrFailed(): List<TransactionEntity> = transactionDao.getPendingOrFailed()

    suspend fun getAllOnce(addedBy: String): List<TransactionEntity> = transactionDao.getAllOnce(addedBy)

    suspend fun markSynced(ids: List<String>) = transactionDao.markSynced(ids)

    suspend fun markFailed(ids: List<String>) = transactionDao.markFailed(ids)

    suspend fun getTransactionsForMonth(monthKey: String, addedBy: String): List<TransactionEntity> =
        transactionDao.getByMonth(monthKey, addedBy)

    suspend fun getCategoryBreakdownForMonth(monthKey: String, addedBy: String): Map<String, Double> =
        transactionDao.getCategorySumsForMonth(monthKey, addedBy)
            .associate { it.category to it.total }

    suspend fun getAllTimeCategoryBreakdown(addedBy: String): Map<String, Double> =
        transactionDao.getAllTimeCategorySums(addedBy)
            .associate { it.category to it.total }

    suspend fun getCategoryBreakdownByTagForMonth(monthKey: String, addedBy: String, tag: String): Map<String, Double> =
        transactionDao.getCategorySumsByTagForMonth(monthKey, addedBy, tag)
            .associate { it.category to it.total }

    suspend fun getAllTimeCategoryBreakdownByTag(addedBy: String, tag: String): Map<String, Double> =
        transactionDao.getAllTimeCategorySumsByTag(addedBy, tag)
            .associate { it.category to it.total }

    suspend fun getPartnerTransactionsForMonth(monthKey: String): List<PartnerTransactionEntity> =
        partnerTransactionDao.getByMonthOnce(monthKey)

    suspend fun getCombinedPartnerTransactionsForMonth(monthKey: String, username: String): List<PartnerTransactionEntity> =
        partnerTransactionDao.getCombinedByPersonAndMonthOnce(username, monthKey)

    fun getCombinedPartnerTransactionsFlow(monthKey: String): Flow<List<PartnerTransactionEntity>> =
        partnerTransactionDao.getCombinedByMonth(monthKey)

    suspend fun getAllTimeCombinedPartnerTotal(username: String): Double =
        partnerTransactionDao.getAllTimeCombinedTotalByPerson(username) ?: 0.0

    suspend fun purgeOldTransactions() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val oldTxns = transactionDao.getOlderThan(cutoff)
        val monthAndUser = oldTxns.map { Pair(DateUtils.toMonthKey(it.date), it.addedBy) }.distinct()
        for ((mk, addedBy) in monthAndUser) {
            if (summaryDao.getByMonthOnce(mk) == null) {
                recalculateSummary(mk, addedBy)
            }
        }
        transactionDao.deleteOlderThan(cutoff)
        partnerTransactionDao.deleteOlderThan(cutoff)
    }

    private suspend fun refreshWidget() {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(ExpenseWidget::class.java)
        if (ids.isEmpty()) return
        for (id in ids) {
            // Wipe stale optimistic state so classified/discarded items can't leak back
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                prefs.toMutablePreferences().also {
                    it.remove(KEY_PENDING_TXNS)
                    it.remove(KEY_HIDDEN_IDS)
                }
            }
            ExpenseWidget().update(context, id)
        }
    }

    private suspend fun recalculateSummary(monthKey: String, addedBy: String) {
        val personal = transactionDao.sumByMonthAndTag(monthKey, addedBy, "Personal") ?: 0.0
        val combined = transactionDao.sumByMonthAndTag(monthKey, addedBy, "Combined") ?: 0.0
        val other = transactionDao.sumByMonthAndTag(monthKey, addedBy, "Other") ?: 0.0
        val summary = MonthlySummaryEntity(
            monthKey = monthKey,
            personalTotal = personal,
            combinedTotal = combined,
            otherTotal = other,
            grandTotal = personal + combined + other,
            lastUpdated = System.currentTimeMillis()
        )
        summaryDao.upsert(summary)
    }
}
