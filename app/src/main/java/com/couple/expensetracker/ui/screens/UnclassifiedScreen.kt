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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.text.KeyboardOptions
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.ui.components.EditTransactionDialog
import com.couple.expensetracker.ui.components.TransactionRow
import com.couple.expensetracker.ui.viewmodel.SettingsViewModel
import com.couple.expensetracker.ui.viewmodel.UnclassifiedViewModel
import com.couple.expensetracker.util.DateUtils
import com.couple.expensetracker.util.SplitUtils
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnclassifiedScreen(
    viewModel: UnclassifiedViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val transactions by viewModel.transactions.collectAsState()
    val myUsername by settingsViewModel.myUsername.collectAsState()
    val categories by settingsViewModel.categories.collectAsState()
    val sharedUsernames by settingsViewModel.sharedUsernames.collectAsState()
    val sharePercentages by settingsViewModel.sharePercentages.collectAsState()

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
            myUsername = myUsername,
            categories = categories.sorted(),
            sharedUsernames = sharedUsernames,
            defaultPercentages = sharePercentages,
            onSave = { tag, category, customSplits ->
                viewModel.classify(txn.id, tag, category, customSplits)
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
            sharedUsernames = sharedUsernames,
            defaultPercentages = sharePercentages,
            myUsername = myUsername,
            onSave = { amount, bankName, category, customSplits ->
                viewModel.editTransaction(txn.id, amount, bankName, category, customSplits)
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
    myUsername: String,
    categories: List<String>,
    sharedUsernames: List<String>,
    defaultPercentages: Map<String, Double>,
    onSave: (tag: String, category: String?, customSplits: String?) -> Unit,
    onDiscard: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTag by remember { mutableStateOf("Personal") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showCategorySection by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val txnAmount = transaction.amount
    val allPersons = remember(sharedUsernames) { listOf("me") + sharedUsernames }
    val lastPerson = remember(allPersons) { allPersons.last() }
    val editablePersons = remember(allPersons) { if (allPersons.size > 1) allPersons.dropLast(1) else allPersons }
    val showSplitOption = sharedUsernames.isNotEmpty()

    var showSplitSection by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("amount") }

    val initialAmounts = remember(allPersons, defaultPercentages, txnAmount) {
        val totalPersons = allPersons.size
        val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
        allPersons.associateWith { p -> txnAmount * (defaultPercentages[p] ?: defaultEqualPct) / 100.0 }
    }

    val splitAmountTexts = remember(initialAmounts) {
        mutableStateMapOf<String, String>().also { map ->
            initialAmounts.forEach { (k, v) -> map[k] = "%.2f".format(v) }
        }
    }

    val splitPctInputTexts = remember(initialAmounts, txnAmount, allPersons) {
        mutableStateMapOf<String, String>().also { map ->
            editablePersons.forEach { p ->
                val pct = defaultPercentages[p] ?: (100.0 / allPersons.size)
                map[p] = "%.1f".format(pct)
            }
        }
    }

    val recomputeLast: () -> Unit = {
        if (allPersons.size >= 2) {
            val othersSum = editablePersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
            splitAmountTexts[lastPerson] = "%.2f".format(txnAmount - othersSum)
        }
    }

    val splitSum = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
    val lastAmt = splitAmountTexts[lastPerson]?.toDoubleOrNull() ?: 0.0
    val splitValid = abs(splitSum - txnAmount) < 0.02 && lastAmt >= -0.01

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Discard") }
                Button(
                    onClick = {
                        val customSplits: String? = if (selectedTag == "Combined" && showSplitOption) {
                            if (showSplitSection) {
                                if (lastAmt < -0.01) return@Button
                                SplitUtils.buildSplitsJson(myUsername, allPersons, splitAmountTexts, txnAmount)
                            } else {
                                SplitUtils.buildDefaultSplitsJson(myUsername, allPersons, defaultPercentages, txnAmount)
                            }
                        } else null
                        onSave(selectedTag, selectedCategory, customSplits)
                    },
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
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Amount / Bank")
            }

            // Category — collapsible dropdown
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCategorySection = !showCategorySection },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Category (optional)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (selectedCategory != null) {
                                Text(
                                    text = selectedCategory!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = if (showCategorySection) "▲" else "▼",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (showCategorySection) {
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider()
                        CategoryRadioRow(label = "None", selected = selectedCategory == null, onClick = { selectedCategory = null })
                        categories.forEach { cat ->
                            CategoryRadioRow(label = cat, selected = selectedCategory == cat, onClick = { selectedCategory = cat })
                        }
                    }
                }
            }

            // Custom split — only when Combined + shared persons exist
            if (selectedTag == "Combined" && showSplitOption) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSplitSection = !showSplitSection
                                    if (showSplitSection) recomputeLast()
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Split (optional)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (showSplitSection) "▲ Hide" else "▼ Show",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (showSplitSection) {
                            Spacer(Modifier.height(8.dp))

                            // Mode toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = splitMode == "amount",
                                    onClick = { splitMode = "amount" },
                                    label = { Text("₹ Amount") }
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = splitMode == "pct",
                                    onClick = {
                                        if (splitMode == "amount") {
                                            editablePersons.forEach { p ->
                                                val amt = splitAmountTexts[p]?.toDoubleOrNull() ?: 0.0
                                                val pct = if (txnAmount > 0) amt / txnAmount * 100.0 else 100.0 / allPersons.size
                                                splitPctInputTexts[p] = "%.1f".format(pct)
                                            }
                                        }
                                        splitMode = "pct"
                                    },
                                    label = { Text("% Split") }
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            allPersons.forEach { person ->
                                val isLast = person == lastPerson
                                val amtStr = splitAmountTexts[person] ?: "0.00"
                                val amt = amtStr.toDoubleOrNull() ?: 0.0
                                val pct = if (txnAmount > 0) amt / txnAmount * 100.0 else 0.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (person == "me") "Me" else person,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (isLast) {
                                            Text(
                                                text = "auto",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (splitMode == "amount") {
                                        if (!isLast) {
                                            OutlinedTextField(
                                                value = amtStr,
                                                onValueChange = { v ->
                                                    splitAmountTexts[person] = v
                                                    recomputeLast()
                                                },
                                                modifier = Modifier.width(90.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                prefix = { Text("₹", style = MaterialTheme.typography.bodySmall) }
                                            )
                                        } else {
                                            Surface(
                                                modifier = Modifier.width(90.dp).height(48.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "₹${"%.2f".format(amt)}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            modifier = Modifier.width(52.dp).height(48.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${"%.0f".format(pct)}%",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    } else {
                                        val pctInputStr = if (!isLast) splitPctInputTexts[person] ?: "0.0"
                                                          else "%.1f".format(pct)
                                        if (!isLast) {
                                            OutlinedTextField(
                                                value = pctInputStr,
                                                onValueChange = { v ->
                                                    splitPctInputTexts[person] = v
                                                    val p = v.toDoubleOrNull() ?: 0.0
                                                    splitAmountTexts[person] = "%.2f".format(txnAmount * p / 100.0)
                                                    recomputeLast()
                                                },
                                                modifier = Modifier.width(80.dp),
                                                singleLine = true,
                                                textStyle = MaterialTheme.typography.bodySmall,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                suffix = { Text("%", style = MaterialTheme.typography.bodySmall) }
                                            )
                                        } else {
                                            Surface(
                                                modifier = Modifier.width(80.dp).height(48.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${"%.1f".format(pct)}%",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        Surface(
                                            modifier = Modifier.width(72.dp).height(48.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "₹${"%.0f".format(amt)}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            Surface(
                                color = if (splitValid) Color(0xFF4CAF50).copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (!splitValid && lastAmt < -0.01)
                                               "⚠ ${if (lastPerson == "me") "Me" else lastPerson} goes negative — reduce others"
                                           else
                                               "Total: ₹${"%.2f".format(splitSum)} / ₹${"%.2f".format(txnAmount)}" +
                                                   if (splitValid) " ✓" else " ⚠ must match",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (splitValid) Color(0xFF4CAF50)
                                            else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            TextButton(
                                onClick = {
                                    val totalPersons = allPersons.size
                                    val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
                                    editablePersons.forEach { p ->
                                        val pct2 = defaultPercentages[p] ?: defaultEqualPct
                                        splitAmountTexts[p] = "%.2f".format(txnAmount * pct2 / 100.0)
                                        splitPctInputTexts[p] = "%.1f".format(pct2)
                                    }
                                    recomputeLast()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Reset to Default Split")
                            }
                        }
                    }
                }
            }

            // Source message
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
                                onLongClick = { clipboardManager.setText(AnnotatedString(transaction.rawMessage)) }
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
