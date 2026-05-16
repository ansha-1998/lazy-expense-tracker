package com.couple.expensetracker.data.sync

import com.couple.expensetracker.data.db.dao.PartnerSummaryDao
import com.couple.expensetracker.data.db.dao.PartnerTransactionDao
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.SummaryRepository
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.util.ConnectivityObserver
import com.couple.expensetracker.util.DateUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val prefs: AppPreferences,
    private val connectivity: ConnectivityObserver,
    private val gson: Gson
) {
    private val cutoff get() = DateUtils.sixMonthsCutoff()

    suspend fun sync(accessToken: String, folderId: String, myUsername: String, partnerUsername: String, fullSync: Boolean = true): Boolean {
        if (!connectivity.isConnected.value) return false
        val auth = "Bearer $accessToken"

        return try {
            uploadTransactions(auth, folderId, myUsername)
            uploadSummary(auth, folderId, myUsername)
            if (fullSync) {
                downloadPartnerTransactions(auth, folderId, partnerUsername)
            } else {
                downloadPartnerTransactionsIncremental(auth, folderId, partnerUsername)
            }
            downloadPartnerSummary(auth, folderId, partnerUsername)
            prefs.setLastSynced(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
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
        prefs.setPartnerTxnFileModifiedTime(file.modifiedTime)
    }

    private suspend fun downloadPartnerTransactionsIncremental(auth: String, folderId: String, partnerUsername: String) {
        val fileName = "${partnerUsername}_transactions.json"
        val file = findFile(auth, folderId, fileName) ?: return
        val knownModifiedTime = prefs.getPartnerTxnFileModifiedTimeOnce()
        if (file.modifiedTime.isNotBlank() && file.modifiedTime == knownModifiedTime) return
        val response = api.downloadFile(auth, file.id)
        if (!response.isSuccessful) return
        val body = response.body()?.string() ?: return
        val type = object : TypeToken<List<PartnerTransactionEntity>>() {}.type
        val all: List<PartnerTransactionEntity> = gson.fromJson(body, type)
        val localMax = partnerTransactionDao.getMaxLastModified(partnerUsername) ?: 0L
        val newOrUpdated = all.filter { it.lastModified > localMax }
        if (newOrUpdated.isNotEmpty()) {
            partnerTransactionDao.insertAll(newOrUpdated)
        }
        prefs.setPartnerTxnFileModifiedTime(file.modifiedTime)
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
