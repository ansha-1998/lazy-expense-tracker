package com.couple.expensetracker.data.db.dao

import androidx.room.*
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerSummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(summaries: List<PartnerSummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: PartnerSummaryEntity)

    @Query("SELECT * FROM partner_summary WHERE monthKey = :monthKey")
    fun getByMonth(monthKey: String): Flow<PartnerSummaryEntity?>

    @Query("SELECT * FROM partner_summary WHERE monthKey = :monthKey")
    suspend fun getByMonthOnce(monthKey: String): PartnerSummaryEntity?

    @Query("SELECT * FROM partner_summary ORDER BY monthKey DESC")
    suspend fun getAllOnce(): List<PartnerSummaryEntity>
}
