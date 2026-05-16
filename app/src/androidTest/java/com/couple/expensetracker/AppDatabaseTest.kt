package com.couple.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.couple.expensetracker.data.db.AppDatabase
import com.couple.expensetracker.data.db.entities.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun insertAndReadTransaction() = runBlocking {
        val txn = TransactionEntity(
            id = "t1", date = System.currentTimeMillis(), amount = 500.0,
            paymentType = "UPI", bankName = "HDFC", last4OrRef = "ref1",
            tag = "Unclassified", addedBy = "me", source = "SMS",
            lastModified = System.currentTimeMillis(), syncStatus = "PENDING"
        )
        db.transactionDao().insert(txn)
        val result = db.transactionDao().getById("t1")
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.01)
    }

    @Test
    fun upsertMonthlySummary_replacesExisting() = runBlocking {
        val summary1 = MonthlySummaryEntity(monthKey = "2026-05", personalTotal = 1000.0,
            combinedTotal = 500.0, otherTotal = 200.0, grandTotal = 1700.0,
            lastUpdated = System.currentTimeMillis())
        db.monthlySummaryDao().upsert(summary1)

        val summary2 = summary1.copy(personalTotal = 2000.0, grandTotal = 2700.0)
        db.monthlySummaryDao().upsert(summary2)

        val result = db.monthlySummaryDao().getByMonthOnce("2026-05")
        assertNotNull(result)
        assertEquals(2000.0, result!!.personalTotal, 0.01)
    }

    @Test
    fun partnerTransaction_conflictResolution_higherLastModifiedWins() = runBlocking {
        val old = PartnerTransactionEntity(
            id = "pt1", date = 1000L, amount = 100.0, paymentType = "UPI",
            bankName = "SBI", last4OrRef = "ref", tag = "Combined",
            addedBy = "partner", source = "SMS", lastModified = 1000L, syncStatus = "SYNCED"
        )
        val newer = old.copy(amount = 200.0, lastModified = 2000L)

        db.partnerTransactionDao().upsertAll(listOf(old))
        db.partnerTransactionDao().upsertAll(listOf(newer))

        val result = db.partnerTransactionDao().getAllOnce()
        assertEquals(1, result.size)
        assertEquals(200.0, result[0].amount, 0.01)
    }

    @Test
    fun deleteTransaction_removesRow() = runBlocking {
        val txn = TransactionEntity(
            id = "del1", date = System.currentTimeMillis(), amount = 300.0,
            paymentType = "Card", bankName = "ICICI", last4OrRef = "1234",
            tag = "Unclassified", addedBy = "me", source = "SMS",
            lastModified = System.currentTimeMillis(), syncStatus = "PENDING"
        )
        db.transactionDao().insert(txn)
        db.transactionDao().deleteById("del1")
        assertNull(db.transactionDao().getById("del1"))
    }
}
