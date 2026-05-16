package com.couple.expensetracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.couple.expensetracker.data.db.AppDatabase
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SummaryDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun upsertSummary_insertsNewMonthKey() = runBlocking {
        val s = MonthlySummaryEntity(monthKey = "2026-05", personalTotal = 1000.0,
            combinedTotal = 500.0, otherTotal = 200.0, grandTotal = 1700.0,
            lastUpdated = System.currentTimeMillis())
        db.monthlySummaryDao().upsert(s)
        val result = db.monthlySummaryDao().getByMonthOnce("2026-05")
        assertNotNull(result)
        assertEquals(1700.0, result!!.grandTotal, 0.01)
    }

    @Test
    fun upsertSummary_updatesExistingMonthKey() = runBlocking {
        val s1 = MonthlySummaryEntity(monthKey = "2026-04", personalTotal = 100.0,
            combinedTotal = 200.0, otherTotal = 0.0, grandTotal = 300.0,
            lastUpdated = System.currentTimeMillis())
        db.monthlySummaryDao().upsert(s1)
        val s2 = s1.copy(personalTotal = 500.0, grandTotal = 700.0)
        db.monthlySummaryDao().upsert(s2)
        val result = db.monthlySummaryDao().getByMonthOnce("2026-04")
        assertEquals(500.0, result!!.personalTotal, 0.01)
    }

    @Test
    fun partnerSummaryUpsert_bulkInsertsAndUpdates() = runBlocking {
        val summaries = listOf(
            PartnerSummaryEntity(monthKey = "2026-05", personalTotal = 0.0,
                combinedTotal = 1500.0, otherTotal = 0.0, grandTotal = 1500.0,
                lastUpdated = System.currentTimeMillis()),
            PartnerSummaryEntity(monthKey = "2026-04", personalTotal = 0.0,
                combinedTotal = 2000.0, otherTotal = 0.0, grandTotal = 2000.0,
                lastUpdated = System.currentTimeMillis())
        )
        db.partnerSummaryDao().upsertAll(summaries)
        val may = db.partnerSummaryDao().getByMonthOnce("2026-05")
        val april = db.partnerSummaryDao().getByMonthOnce("2026-04")
        assertEquals(1500.0, may!!.combinedTotal, 0.01)
        assertEquals(2000.0, april!!.combinedTotal, 0.01)
    }

    @Test
    fun missingSummary_returnsNull() = runBlocking {
        val result = db.monthlySummaryDao().getByMonthOnce("2020-01")
        assertNull(result)
    }
}
