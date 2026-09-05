package com.couple.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.couple.expensetracker.data.db.AppDatabase
import com.couple.expensetracker.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "expense_tracker.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideMonthlySummaryDao(db: AppDatabase): MonthlySummaryDao = db.monthlySummaryDao()

    @Provides
    fun providePartnerTransactionDao(db: AppDatabase): PartnerTransactionDao = db.partnerTransactionDao()

    @Provides
    fun providePartnerSummaryDao(db: AppDatabase): PartnerSummaryDao = db.partnerSummaryDao()

    @Provides
    fun provideSettlementDao(db: AppDatabase): SettlementDao = db.settlementDao()
}
