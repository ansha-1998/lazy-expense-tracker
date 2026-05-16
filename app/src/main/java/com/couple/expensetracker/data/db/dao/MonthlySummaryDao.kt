package com.couple.expensetracker.data.db.dao

import androidx.room.*
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: MonthlySummaryEntity)

    @Query("SELECT * FROM monthly_summary WHERE monthKey = :monthKey")
    fun getByMonth(monthKey: String): Flow<MonthlySummaryEntity?>

    @Query("SELECT * FROM monthly_summary WHERE monthKey = :monthKey")
    suspend fun getByMonthOnce(monthKey: String): MonthlySummaryEntity?

    @Query("SELECT * FROM monthly_summary ORDER BY monthKey DESC")
    fun getAll(): Flow<List<MonthlySummaryEntity>>

    @Query("SELECT * FROM monthly_summary ORDER BY monthKey DESC")
    suspend fun getAllOnce(): List<MonthlySummaryEntity>
}
