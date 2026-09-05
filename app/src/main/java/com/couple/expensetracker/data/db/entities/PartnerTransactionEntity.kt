package com.couple.expensetracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "partner_transactions")
data class PartnerTransactionEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val amount: Double,
    val paymentType: String,
    val bankName: String,
    val last4OrRef: String,
    val tag: String,
    val addedBy: String,
    val source: String,
    val lastModified: Long,
    val syncStatus: String,
    val rawMessage: String? = null,
    val customSplits: String? = null,
    val category: String? = null
)
