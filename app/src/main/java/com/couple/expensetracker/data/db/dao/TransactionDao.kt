package com.couple.expensetracker.data.db.dao

import androidx.room.*
import com.couple.expensetracker.data.db.entities.CategorySum
import com.couple.expensetracker.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM transactions WHERE addedBy = :addedBy ORDER BY date DESC")
    fun getAll(addedBy: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE tag = :tag AND addedBy = :addedBy ORDER BY date DESC")
    fun getByTag(tag: String, addedBy: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy ORDER BY date DESC
    """)
    fun getAllByMonth(monthKey: String, addedBy: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE tag = :tag
        AND strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy ORDER BY date DESC
    """)
    fun getByTagAndMonth(tag: String, monthKey: String, addedBy: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy
    """)
    suspend fun getByMonth(monthKey: String, addedBy: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncStatus IN ('PENDING', 'FAILED')")
    suspend fun getPendingOrFailed(): List<TransactionEntity>

    @Query("UPDATE transactions SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("UPDATE transactions SET syncStatus = 'FAILED' WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<String>)

    @Query("UPDATE transactions SET tag = :tag, lastModified = :lastModified, syncStatus = 'PENDING' WHERE id = :id")
    suspend fun updateTag(id: String, tag: String, lastModified: Long)

    @Query("SELECT * FROM transactions WHERE date < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy AND tag = :tag
    """)
    suspend fun sumByMonthAndTag(monthKey: String, addedBy: String, tag: String): Double?

    @Query("SELECT * FROM transactions WHERE addedBy = :addedBy ORDER BY date DESC")
    suspend fun getAllOnce(addedBy: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE rawMessage = :rawMessage")
    suspend fun countByRawMessage(rawMessage: String): Int

    @Query("""
        SELECT COALESCE(category, 'Uncategorized') as category, SUM(amount) as total
        FROM transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy
        GROUP BY COALESCE(category, 'Uncategorized')
    """)
    suspend fun getCategorySumsForMonth(monthKey: String, addedBy: String): List<CategorySum>

    @Query("""
        SELECT COALESCE(category, 'Uncategorized') as category, SUM(amount) as total
        FROM transactions
        WHERE addedBy = :addedBy
        GROUP BY COALESCE(category, 'Uncategorized')
    """)
    suspend fun getAllTimeCategorySums(addedBy: String): List<CategorySum>

    @Query("""
        SELECT COALESCE(category, 'Uncategorized') as category, SUM(amount) as total
        FROM transactions
        WHERE strftime('%Y-%m', date / 1000, 'unixepoch') = :monthKey
        AND addedBy = :addedBy
        AND tag = :tag
        GROUP BY COALESCE(category, 'Uncategorized')
    """)
    suspend fun getCategorySumsByTagForMonth(monthKey: String, addedBy: String, tag: String): List<CategorySum>

    @Query("""
        SELECT COALESCE(category, 'Uncategorized') as category, SUM(amount) as total
        FROM transactions
        WHERE addedBy = :addedBy
        AND tag = :tag
        GROUP BY COALESCE(category, 'Uncategorized')
    """)
    suspend fun getAllTimeCategorySumsByTag(addedBy: String, tag: String): List<CategorySum>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE amount = :amount
        AND bankName = :bankName
        AND last4OrRef = :last4OrRef
        AND lastModified > :since
    """)
    suspend fun countRecentBySignature(
        amount: Double,
        bankName: String,
        last4OrRef: String,
        since: Long
    ): Int
}
