package com.couple.expensetracker.data.db.dao

import androidx.room.*
import com.couple.expensetracker.data.db.entities.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settlement: SettlementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settlements: List<SettlementEntity>)

    @Query("SELECT * FROM settlements WHERE monthKey = :monthKey")
    suspend fun getByMonth(monthKey: String): List<SettlementEntity>

    // Reactive version used only to trigger a recompute when the table changes underneath us
    // (e.g. a sync downloads a partner's settlement) — Room re-emits this automatically on writes.
    @Query("SELECT * FROM settlements WHERE monthKey = :monthKey")
    fun getByMonthFlow(monthKey: String): Flow<List<SettlementEntity>>

    // Settlements I created locally (I tapped Settle Up) — these are the ones I "own" and upload to sync
    @Query("SELECT * FROM settlements WHERE markedBy = :username")
    suspend fun getAllAuthoredBy(username: String): List<SettlementEntity>
}
