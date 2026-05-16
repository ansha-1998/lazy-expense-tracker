package com.couple.expensetracker

import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test

class DriveJsonSerializationTest {

    private val gson = Gson()

    @Test
    fun `TransactionEntity serializes and deserializes correctly`() {
        val txn = TransactionEntity(
            id = "abc-123",
            date = 1746700000000L,
            amount = 2500.0,
            paymentType = "UPI",
            bankName = "HDFC",
            last4OrRef = "412345678901",
            tag = "Combined",
            addedBy = "me",
            source = "SMS",
            lastModified = 1746700001000L,
            syncStatus = "SYNCED"
        )
        val json = gson.toJson(txn)
        val restored = gson.fromJson(json, TransactionEntity::class.java)
        assertEquals(txn.id, restored.id)
        assertEquals(txn.amount, restored.amount, 0.01)
        assertEquals(txn.tag, restored.tag)
        assertEquals(txn.syncStatus, restored.syncStatus)
    }

    @Test
    fun `MonthlySummaryEntity serializes and deserializes correctly`() {
        val summary = MonthlySummaryEntity(
            monthKey = "2026-05",
            personalTotal = 1000.0,
            combinedTotal = 2000.0,
            otherTotal = 500.0,
            grandTotal = 3500.0,
            lastUpdated = 1746700000000L
        )
        val json = gson.toJson(summary)
        val restored = gson.fromJson(json, MonthlySummaryEntity::class.java)
        assertEquals(summary.monthKey, restored.monthKey)
        assertEquals(summary.grandTotal, restored.grandTotal, 0.01)
        assertEquals(summary.combinedTotal, restored.combinedTotal, 0.01)
    }

    @Test
    fun `List of TransactionEntity serializes and deserializes`() {
        val list = listOf(
            TransactionEntity("id1", 1746700000000L, 100.0, "UPI", "HDFC", "ref1", "Personal", "me", "SMS", 1746700000000L, "PENDING"),
            TransactionEntity("id2", 1746700000001L, 200.0, "Card", "SBI", "5678", "Combined", "me", "Manual", 1746700000001L, "SYNCED")
        )
        val json = gson.toJson(list)
        val type = object : TypeToken<List<TransactionEntity>>() {}.type
        val restored: List<TransactionEntity> = gson.fromJson(json, type)
        assertEquals(2, restored.size)
        assertEquals("id1", restored[0].id)
        assertEquals("id2", restored[1].id)
        assertEquals(200.0, restored[1].amount, 0.01)
    }

    @Test
    fun `Empty list serializes to empty JSON array`() {
        val json = gson.toJson(emptyList<TransactionEntity>())
        assertEquals("[]", json)
    }
}
