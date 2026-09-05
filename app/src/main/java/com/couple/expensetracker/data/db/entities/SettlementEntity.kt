package com.couple.expensetracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settlements")
data class SettlementEntity(
    @PrimaryKey val id: String,
    val monthKey: String,
    val payer: String,     // username who paid (direction of money flow)
    val receiver: String,  // username who received (direction of money flow)
    val amount: Double,    // always positive
    val markedBy: String,  // username who tapped "Settle Up" — either payer or receiver can
    val createdAt: Long
)
