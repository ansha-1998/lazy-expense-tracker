package com.couple.expensetracker.data.sync

import com.couple.expensetracker.data.db.dao.PartnerSummaryDao
import com.couple.expensetracker.data.db.dao.PartnerTransactionDao
import com.couple.expensetracker.data.db.dao.SettlementDao
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.SettlementEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.SummaryRepository
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.util.ConnectivityObserver
import com.couple.expensetracker.util.DateUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveSync @Inject constructor(
    private val api: DriveApiService,
    private val transactionRepository: TransactionRepository,
    private val summaryRepository: SummaryRepository,
    private val partnerTransactionDao: PartnerTransactionDao,
    private val partnerSummaryDao: PartnerSummaryDao,
    private val settlementDao: SettlementDao,
    private val prefs: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val gson: Gson
) {
    private val cutoff get() = DateUtils.sixMonthsCutoff()
    private val syncMutex = Mutex()

    // Single source of truth for "is a sync running right now" — shared by the manual Sync Now
    // button AND the background SyncWorker, so the UI reflects a sync regardless of what
    // triggered it, and the two never run concurrently against Drive.
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    suspend fun sync(
        accessToken: String,
        folderId: String,
        myUsername: String,
        sharedUsernames: List<String>,
        fullSync: Boolean = true
    ): Boolean {
        if (!connectivity.isConnected.value) return false
        if (syncMutex.isLocked) return false
        return syncMutex.withLock {
            _isSyncing.value = true
            try {
                val auth = "Bearer $accessToken"
                uploadTransactions(auth, folderId, myUsername)
                uploadSummary(auth, folderId, myUsername)
                uploadSettlements(auth, folderId, myUsername)
                if (fullSync) {
                    sharedUsernames.forEach { username ->
                        downloadPartnerTransactions(auth, folderId, username)
                    }
                } else {
                    sharedUsernames.forEach { username ->
                        downloadPartnerTransactionsIncremental(auth, folderId, username)
                    }
                }
                sharedUsernames.forEach { username ->
                    downloadPartnerSummary(auth, folderId, username)
                    downloadPartnerSettlements(auth, folderId, username)
                }
                prefs.setLastSynced(System.currentTimeMillis())
                true
            } catch (e: Exception) {
                false
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun uploadTransactions(auth: String, folderId: String, username: String) {
        val pending = transactionRepository.getPendingOrFailed()
        val all = transactionRepository.getAllOnce(username)
        val filtered = all.filter { it.date >= cutoff }
        val ids = pending.map { it.id }

        val json = gson.toJson(filtered)
        val fileName = "${username}_transactions.json"
        upsertFile(auth, folderId, fileName, json)
        if (ids.isNotEmpty()) transactionRepository.markSynced(ids)
    }

    private suspend fun uploadSummary(auth: String, folderId: String, username: String) {
        val summaries = summaryRepository.getAllOnce()
        val json = gson.toJson(summaries)
        upsertFile(auth, folderId, "${username}_summary.json", json)
    }

    private suspend fun downloadPartnerTransactions(auth: String, folderId: String, partnerUsername: String) {
        val fileName = "${partnerUsername}_transactions.json"
        val file = findFile(auth, folderId, fileName) ?: return
        val response = api.downloadFile(auth, file.id)
        if (!response.isSuccessful) return
        val body = response.body()?.string() ?: return
        val type = object : TypeToken<List<PartnerTransactionEntity>>() {}.type
        val transactions: List<PartnerTransactionEntity> = gson.fromJson(body, type)
        partnerTransactionDao.replaceAll(partnerUsername, transactions)
        prefs.setSharedTxnFileModifiedTime(partnerUsername, file.modifiedTime)
    }

    private suspend fun downloadPartnerTransactionsIncremental(auth: String, folderId: String, partnerUsername: String) {
        val fileName = "${partnerUsername}_transactions.json"
        val file = findFile(auth, folderId, fileName) ?: return
        val knownModifiedTime = prefs.getSharedTxnFileModifiedTimeOnce(partnerUsername)
        if (file.modifiedTime.isNotBlank() && file.modifiedTime == knownModifiedTime) return
        val response = api.downloadFile(auth, file.id)
        if (!response.isSuccessful) return
        val body = response.body()?.string() ?: return
        val type = object : TypeToken<List<PartnerTransactionEntity>>() {}.type
        val all: List<PartnerTransactionEntity> = gson.fromJson(body, type)
        // replaceAll handles both new/updated records AND deletions (removes transactions
        // no longer present in partner's file, e.g. when partner discards a transaction)
        partnerTransactionDao.replaceAll(partnerUsername, all)
        prefs.setSharedTxnFileModifiedTime(partnerUsername, file.modifiedTime)
    }

    private suspend fun downloadPartnerSummary(auth: String, folderId: String, partnerUsername: String) {
        val fileName = "${partnerUsername}_summary.json"
        val file = findFile(auth, folderId, fileName) ?: return
        val response = api.downloadFile(auth, file.id)
        if (!response.isSuccessful) return
        val body = response.body()?.string() ?: return
        val type = object : TypeToken<List<PartnerSummaryEntity>>() {}.type
        val summaries: List<PartnerSummaryEntity> = gson.fromJson(body, type)
        partnerSummaryDao.upsertAll(summaries)
    }

    // I only upload settlements where I was the receiver (the one who confirmed the payment) —
    // that's the single source of truth for that settlement; the payer's device just downloads it.
    private suspend fun uploadSettlements(auth: String, folderId: String, username: String) {
        val authored = settlementDao.getAllAuthoredBy(username)
        val json = gson.toJson(authored)
        upsertFile(auth, folderId, "${username}_settlements.json", json)
    }

    private suspend fun downloadPartnerSettlements(auth: String, folderId: String, partnerUsername: String) {
        val fileName = "${partnerUsername}_settlements.json"
        val file = findFile(auth, folderId, fileName) ?: return
        val response = api.downloadFile(auth, file.id)
        if (!response.isSuccessful) return
        val body = response.body()?.string() ?: return
        val type = object : TypeToken<List<SettlementEntity>>() {}.type
        val settlements: List<SettlementEntity> = gson.fromJson(body, type)
        settlementDao.insertAll(settlements)
    }

    private suspend fun findFile(auth: String, folderId: String, name: String): DriveFile? {
        val q = "'$folderId' in parents and name='$name' and trashed=false"
        val result = api.listFiles(auth, q)
        return result.files.firstOrNull()
    }

    private suspend fun upsertFile(auth: String, folderId: String, fileName: String, json: String) {
        val existingId = findFile(auth, folderId, fileName)?.id
        val contentType = "application/json".toMediaType()
        val jsonBody = json.toRequestBody(contentType)
        val contentPart = MultipartBody.Part.createFormData("media", fileName, jsonBody)

        if (existingId != null) {
            val metaJson = """{"name":"$fileName"}"""
            val metaPart = MultipartBody.Part.createFormData(
                "metadata", null,
                metaJson.toRequestBody("application/json".toMediaType())
            )
            api.updateFile(auth, existingId, metaPart, contentPart)
        } else {
            val metaJson = """{"name":"$fileName","parents":["$folderId"]}"""
            val metaPart = MultipartBody.Part.createFormData(
                "metadata", null,
                metaJson.toRequestBody("application/json".toMediaType())
            )
            api.createFile(auth, metaPart, contentPart)
        }
    }
}
