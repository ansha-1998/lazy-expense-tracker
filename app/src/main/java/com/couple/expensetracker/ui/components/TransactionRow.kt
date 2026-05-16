package com.couple.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.ui.theme.*
import com.couple.expensetracker.util.DateUtils

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    showAddedBy: Boolean = false,
    isPartner: Boolean = false
) {
    val cardColor = if (isPartner) PartnerTransactionCardColor else MyTransactionCardColor
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "₹${"%.2f".format(transaction.amount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "${transaction.bankName} · ${transaction.paymentType}" +
                            if (transaction.last4OrRef.isNotBlank()) " · ${transaction.last4OrRef}" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateUtils.toDisplayDateTime(transaction.date),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TagChip(tag = if (showAddedBy) transaction.addedBy else transaction.tag)
                if (!transaction.category.isNullOrBlank()) {
                    CategoryChip(category = transaction.category)
                }
            }
        }
    }
}

@Composable
fun TagChip(tag: String) {
    val color = when (tag) {
        "Personal" -> PersonalColor
        "Combined" -> CombinedColor
        "Other" -> OtherColor
        "partner" -> CombinedColor
        else -> UnclassifiedColor
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CategoryChip(category: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun SourceBadge(source: String) {
    val color = if (source == "SMS") MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = source,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            fontSize = 10.sp,
            color = color
        )
    }
}
