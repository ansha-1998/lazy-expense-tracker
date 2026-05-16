package com.couple.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.couple.expensetracker.util.DateUtils

@Composable
fun MonthPicker(
    currentMonthKey: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val isCurrentMonth = currentMonthKey == DateUtils.currentMonthKey()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = DateUtils.toMonthDisplay(currentMonthKey),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(
            onClick = onNext,
            enabled = !isCurrentMonth
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}
