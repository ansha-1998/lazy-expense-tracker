package com.couple.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.couple.expensetracker.data.db.AppDatabase
import com.couple.expensetracker.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @After
    fun teardown() = db.close()

    private fun makeTxn(
        id: String, tag: String, addedBy: String = "me",
        syncStatus: String = "PENDING", date: Long = System.currentTimeMillis()
    ) = TransactionEntity(
        id = id, date = date, amount = 100.0, paymentType = "UPI",
        bankName = "HDFC", last4OrRef = "ref", tag = tag,
        addedBy = addedBy, source = "SMS",
        lastModified = System.currentTimeMillis(), syncStatus = syncStatus
    )

    @Test
    fun getByTag_returnsOnlyMatchingRows() = runBlocking {
        db.transactionDao().insert(makeTxn("1", "Personal"))
        db.transactionDao().insert(makeTxn("2", "Combined"))
        db.transactionDao().insert(makeTxn("3", "Unclassified"))
        val personal = db.transactionDao().getByTag("Personal", "me").first()
        assertEquals(1, personal.size)
        assertEquals("Personal", personal[0].tag)
    }

    @Test
    fun getAll_returnsAllForAddedBy() = runBlocking {
        db.transactionDao().insert(makeTxn("1", "Personal", "me"))
        db.transactionDao().insert(makeTxn("2", "Combined", "me"))
        db.transactionDao().insert(makeTxn("3", "Personal", "partner"))
        val myTxns = db.transactionDao().getAll("me").first()
        assertEquals(2, myTxns.size)
    }

    @Test
    fun getPendingOrFailed_returnsBothStatuses() = runBlocking {
        db.transactionDao().insert(makeTxn("1", "Personal", syncStatus = "PENDING"))
        db.transactionDao().insert(makeTxn("2", "Combined", syncStatus = "FAILED"))
        db.transactionDao().insert(makeTxn("3", "Other", syncStatus = "SYNCED"))
        val pending = db.transactionDao().getPendingOrFailed()
        assertEquals(2, pending.size)
    }

    @Test
    fun updateTag_changesSyncStatusToPending() = runBlocking {
        db.transactionDao().insert(makeTxn("1", "Unclassified", syncStatus = "SYNCED"))
        db.transactionDao().updateTag("1", "Personal", System.currentTimeMillis())
        val result = db.transactionDao().getById("1")
        assertEquals("Personal", result?.tag)
        assertEquals("PENDING", result?.syncStatus)
    }

    @Test
    fun markSynced_updatesMultipleRows() = runBlocking {
        db.transactionDao().insert(makeTxn("1", "Personal", syncStatus = "PENDING"))
        db.transactionDao().insert(makeTxn("2", "Combined", syncStatus = "PENDING"))
        db.transactionDao().markSynced(listOf("1", "2"))
        assertEquals("SYNCED", db.transactionDao().getById("1")?.syncStatus)
        assertEquals("SYNCED", db.transactionDao().getById("2")?.syncStatus)
    }
}
