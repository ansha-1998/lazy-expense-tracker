package com.couple.expensetracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.ui.components.EditTransactionDialog
import com.couple.expensetracker.ui.components.TransactionRow
import com.couple.expensetracker.ui.viewmodel.SettingsViewModel
import com.couple.expensetracker.ui.viewmodel.UnclassifiedViewModel
import com.couple.expensetracker.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnclassifiedScreen(
    viewModel: UnclassifiedViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by settingsViewModel.categories.collectAsState()

    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var showDiscardDialog by remember { mutableStateOf<TransactionEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (transactions.isEmpty()) {
            Text(
                text = "No unclassified transactions",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transactions, key = { it.id }) { txn ->
                    SwipeToDeleteRow(
                        txn = txn,
                        onDelete = { showDiscardDialog = txn },
                        onClick = { selectedTransaction = txn }
                    )
                }
            }
        }
    }

    selectedTransaction?.let { txn ->
        ClassifyBottomSheet(
            transaction = txn,
            categories = categories.sorted(),
            onSave = { tag, category ->
                viewModel.classify(txn.id, tag, category)
                selectedTransaction = null
            },
            onDiscard = {
                selectedTransaction = null
                showDiscardDialog = txn
            },
            onEdit = {
                selectedTransaction = null
                editingTransaction = txn
            },
            onDismiss = { selectedTransaction = null }
        )
    }

    editingTransaction?.let { txn ->
        EditTransactionDialog(
            transaction = txn,
            categories = categories.sorted(),
            onSave = { amount, bankName, category ->
                viewModel.editTransaction(txn.id, amount, bankName, category)
                editingTransaction = null
            },
            onDismiss = { editingTransaction = null }
        )
    }

    showDiscardDialog?.let { txn ->
        DiscardAlertDialog(
            amount = txn.amount,
            bankName = txn.bankName,
            last4OrRef = txn.last4OrRef,
            onConfirm = {
                viewModel.discard(txn.id)
                showDiscardDialog = null
            },
            onDismiss = { showDiscardDialog = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ClassifyBottomSheet(
    transaction: TransactionEntity,
    categories: List<String>,
    onSave: (tag: String, category: String?) -> Unit,
    onDiscard: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTag by remember { mutableStateOf("Personal") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action buttons at the top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Discard") }
                Button(
                    onClick = { onSave(selectedTag, selectedCategory) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
            }

            HorizontalDivider()

            // Transaction info
            Column {
                Text(
                    text = "₹${"%.2f".format(transaction.amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${transaction.bankName} · ${transaction.paymentType}" +
                            if (transaction.last4OrRef.isNotBlank()) " · ${transaction.last4OrRef}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = DateUtils.toDisplayDateTime(transaction.date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tag selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Personal", "Combined", "Other").forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Edit amount / bank
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Edit Amount / Bank")
            }

            // Category selection — inline to avoid popup clipping
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Category (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        CategoryRadioRow(
                            label = "None",
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                        categories.forEach { cat ->
                            CategoryRadioRow(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                        }
                    }
                }
            }

            // Source message — shown by default, long-press to copy
            if (!transaction.rawMessage.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Source Message",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Long press to copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(transaction.rawMessage))
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = transaction.rawMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(
    txn: TransactionEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
        positionalThreshold = { it * 0.4f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_bg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        TransactionRow(transaction = txn, onClick = onClick)
    }
}

@Composable
fun DiscardAlertDialog(
    amount: Double,
    bankName: String,
    last4OrRef: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Transaction?") },
        text = {
            Text(
                "₹${"%.2f".format(amount)} · $bankName" +
                        (if (last4OrRef.isNotBlank()) " · $last4OrRef" else "") +
                        " will be permanently deleted."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
