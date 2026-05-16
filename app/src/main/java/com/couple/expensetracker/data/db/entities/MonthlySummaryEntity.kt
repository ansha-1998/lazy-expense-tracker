package com.couple.expensetracker.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_summary",
    indices = [Index(value = ["monthKey"], unique = true)]
)
data class MonthlySummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val monthKey: String,
    val personalTotal: Double,
    val combinedTotal: Double,
    val otherTotal: Double,
    val grandTotal: Double,
    val lastUpdated: Long
)
