package com.couple.expensetracker.data.db.dao

import androidx.room.*
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PartnerTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(transactions: List<PartnerTransactionEntity>)

    @Query("DELETE FROM partner_transactions WHERE addedBy = :addedBy")
    abstract suspend fun deleteByUser(addedBy: String)

    open suspend fun replaceAll(addedBy: String, transactions: List<PartnerTransactionEntity>) {
        deleteByUser(addedBy)
        insertAll(transactions)
    }

    @Query("""
        SELECT * FROM partner_transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        ORDER BY date DESC
    """)
    abstract fun getByMonth(monthKey: String): Flow<List<PartnerTransactionEntity>>

    @Query("SELECT * FROM partner_transactions WHERE tag = 'Combined' ORDER BY date DESC")
    abstract fun getCombined(): Flow<List<PartnerTransactionEntity>>

    @Query("""
        SELECT * FROM partner_transactions
        WHERE tag = 'Combined'
        AND strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        ORDER BY date DESC
    """)
    abstract fun getCombinedByMonth(monthKey: String): Flow<List<PartnerTransactionEntity>>

    @Query("DELETE FROM partner_transactions WHERE date < :cutoff")
    abstract suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM partner_transactions ORDER BY date DESC")
    abstract suspend fun getAllOnce(): List<PartnerTransactionEntity>

    @Query("""
        SELECT * FROM partner_transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        ORDER BY date DESC
    """)
    abstract suspend fun getByMonthOnce(monthKey: String): List<PartnerTransactionEntity>

    @Query("SELECT MAX(lastModified) FROM partner_transactions WHERE addedBy = :addedBy")
    abstract suspend fun getMaxLastModified(addedBy: String): Long?

    @Query("""
        SELECT * FROM partner_transactions
        WHERE addedBy = :addedBy AND tag = 'Combined'
        AND strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        ORDER BY date DESC
    """)
    abstract suspend fun getCombinedByPersonAndMonthOnce(addedBy: String, monthKey: String): List<PartnerTransactionEntity>

    @Query("SELECT SUM(amount) FROM partner_transactions WHERE addedBy = :addedBy AND tag = 'Combined'")
    abstract suspend fun getAllTimeCombinedTotalByPerson(addedBy: String): Double?

    @Query("""
        SELECT SUM(amount) FROM partner_transactions
        WHERE addedBy = :addedBy AND tag = 'Combined'
        AND strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
    """)
    abstract fun getCombinedTotalByPersonAndMonth(addedBy: String, monthKey: String): Flow<Double?>
}
