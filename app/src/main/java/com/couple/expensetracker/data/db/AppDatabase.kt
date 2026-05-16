package com.couple.expensetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.couple.expensetracker.data.db.dao.*
import com.couple.expensetracker.data.db.entities.*

@Database(
    entities = [
        TransactionEntity::class,
        MonthlySummaryEntity::class,
        PartnerTransactionEntity::class,
        PartnerSummaryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlySummaryDao(): MonthlySummaryDao
    abstract fun partnerTransactionDao(): PartnerTransactionDao
    abstract fun partnerSummaryDao(): PartnerSummaryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN rawMessage TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
            }
        }
    }
}
