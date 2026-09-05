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
        PartnerSummaryEntity::class,
        SettlementEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun monthlySummaryDao(): MonthlySummaryDao
    abstract fun partnerTransactionDao(): PartnerTransactionDao
    abstract fun partnerSummaryDao(): PartnerSummaryDao
    abstract fun settlementDao(): SettlementDao

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
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE partner_transactions ADD COLUMN rawMessage TEXT")
                db.execSQL("ALTER TABLE partner_transactions ADD COLUMN customSplits TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN customSplits TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE partner_transactions ADD COLUMN category TEXT")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settlements (
                        id TEXT NOT NULL PRIMARY KEY,
                        monthKey TEXT NOT NULL,
                        withUser TEXT NOT NULL,
                        balanceAdjustment REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        // Settlement schema changed from a viewer-relative signed adjustment to an explicit
        // payer/receiver/amount record (needed so settlements can sync between both partners'
        // devices unambiguously). Pre-release feature with no real settlement data yet, so the
        // old table is simply recreated rather than migrated row-by-row.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS settlements")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settlements (
                        id TEXT NOT NULL PRIMARY KEY,
                        monthKey TEXT NOT NULL,
                        payer TEXT NOT NULL,
                        receiver TEXT NOT NULL,
                        amount REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        // Dropped the receiver-only restriction — either partner can now mark a settlement, so we
        // track who actually tapped "Settle Up" separately from the payer/receiver money-flow direction.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS settlements")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS settlements (
                        id TEXT NOT NULL PRIMARY KEY,
                        monthKey TEXT NOT NULL,
                        payer TEXT NOT NULL,
                        receiver TEXT NOT NULL,
                        amount REAL NOT NULL,
                        markedBy TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
