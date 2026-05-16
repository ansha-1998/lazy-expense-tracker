package com.couple.expensetracker.data.repository

import com.couple.expensetracker.data.db.dao.MonthlySummaryDao
import com.couple.expensetracker.data.db.dao.PartnerSummaryDao
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val summaryDao: MonthlySummaryDao,
    private val partnerSummaryDao: PartnerSummaryDao
) {
    fun getMySummary(monthKey: String): Flow<MonthlySummaryEntity?> =
        summaryDao.getByMonth(monthKey)

    fun getPartnerSummary(monthKey: String): Flow<PartnerSummaryEntity?> =
        partnerSummaryDao.getByMonth(monthKey)

    suspend fun getAllOnce(): List<MonthlySummaryEntity> = summaryDao.getAllOnce()

    fun getAllMySummaries(): Flow<List<MonthlySummaryEntity>> = summaryDao.getAll()

    suspend fun getAllPartnerSummariesOnce(): List<PartnerSummaryEntity> = partnerSummaryDao.getAllOnce()
}
