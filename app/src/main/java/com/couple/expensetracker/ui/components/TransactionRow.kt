package com.couple.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.ui.theme.*
import com.couple.expensetracker.util.DateUtils
import com.couple.expensetracker.util.SplitUtils

private val PERSON_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722)
)

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    showAddedBy: Boolean = false,
    isShared: Boolean = false,
    sharedUsernames: List<String> = emptyList(),
    viewerUsername: String = "me",
    viewerDefaultPct: Double? = null
) {
    val cardColor = if (isShared) PartnerTransactionCardColor else MyTransactionCardColor

    // Colour for the username badge — matches Settings / Summary palette
    val personColor: Color = if (isShared) {
        val idx = sharedUsernames.indexOf(transaction.addedBy)
        if (idx >= 0) PERSON_COLORS[idx % PERSON_COLORS.size]
        else PERSON_COLORS[0]
    } else MaterialTheme.colorScheme.primary

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
                if (isShared) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(personColor, CircleShape)
                        )
                        Text(
                            text = transaction.addedBy,
                            fontSize = 11.sp,
                            color = personColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val isCombined = !showAddedBy && transaction.tag == "Combined"
                val shareAmount: Double?
                val tagLabel = if (isCombined) {
                    val effectiveKey = viewerUsername.ifBlank { "me" }
                    val storedPct = SplitUtils.getSplitPct(transaction.customSplits, effectiveKey, transaction.amount)
                        ?: if (!isShared) SplitUtils.getSplitPct(transaction.customSplits, "me", transaction.amount) else null
                    val actualPct = storedPct ?: viewerDefaultPct ?: 50.0
                    shareAmount = transaction.amount * actualPct / 100.0
                    "Combined ${actualPct.toInt()}%"
                } else {
                    shareAmount = null
                    if (showAddedBy) transaction.addedBy else transaction.tag
                }
                TagChip(tag = tagLabel)
                if (shareAmount != null) {
                    ShareAmountChip(amount = shareAmount)
                }
                if (!transaction.category.isNullOrBlank()) {
                    CategoryChip(category = transaction.category)
                }
            }
        }
    }
}

@Composable
fun TagChip(tag: String) {
    val color = when {
        tag == "Personal" -> PersonalColor
        tag.startsWith("Combined") -> CombinedColor
        tag == "Other" -> OtherColor
        tag == "partner" -> CombinedColor
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
fun ShareAmountChip(amount: Double) {
    Surface(
        color = CombinedColor.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "₹${"%.2f".format(amount)}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = CombinedColor,
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
