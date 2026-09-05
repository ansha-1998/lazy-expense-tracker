package com.couple.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.couple.expensetracker.data.db.entities.PartnerTransactionEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.ui.components.EditTransactionDialog
import com.couple.expensetracker.ui.components.MonthPicker
import com.couple.expensetracker.ui.components.TagBottomSheet
import com.couple.expensetracker.ui.components.TransactionRow
import com.couple.expensetracker.ui.viewmodel.SettingsViewModel
import com.couple.expensetracker.ui.viewmodel.TransactionsViewModel

private fun PartnerTransactionEntity.toTransactionEntity() = TransactionEntity(
    id = id, date = date, amount = amount, paymentType = paymentType,
    bankName = bankName, last4OrRef = last4OrRef, tag = tag,
    addedBy = addedBy, source = source, lastModified = lastModified, syncStatus = syncStatus,
    rawMessage = rawMessage, customSplits = customSplits
)

private val FILTERS = listOf("All", "Personal", "Combined", "Other")

@Composable
fun TransactionsScreen(
    onNavigateToAdd: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val myTransactions by viewModel.myTransactions.collectAsState()
    val partnerCombined by viewModel.partnerCombined.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val monthKey by viewModel.monthKey.collectAsState()
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    val isOnline by settingsViewModel.isOnline.collectAsState()
    val categories by settingsViewModel.categories.collectAsState()
    val myUsername by settingsViewModel.myUsername.collectAsState()
    val sharedUsernames by settingsViewModel.sharedUsernames.collectAsState()
    val sharePercentages by settingsViewModel.sharePercentages.collectAsState()
    val totalPersons = (1 + sharedUsernames.size).coerceAtLeast(1)
    val viewerDefaultPct = sharePercentages["me"] ?: (100.0 / totalPersons)
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var selectedSharedTransaction by remember { mutableStateOf<PartnerTransactionEntity?>(null) }
    var combinedSubTab by remember { mutableStateOf(0) } // 0 = Me, 1 = Shared

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScrollableTabRow(
                    selectedTabIndex = FILTERS.indexOf(selectedFilter).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp
                ) {
                    FILTERS.forEachIndexed { _, filter ->
                        Tab(
                            selected = selectedFilter == filter,
                            onClick = {
                                viewModel.setFilter(filter)
                                combinedSubTab = 0
                            },
                            text = { Text(filter) }
                        )
                    }
                }

                MonthPicker(
                    currentMonthKey = monthKey,
                    onPrevious = viewModel::goToPreviousMonth,
                    onNext = viewModel::goToNextMonth
                )

                if (selectedFilter == "Combined") {
                    TabRow(selectedTabIndex = combinedSubTab) {
                        Tab(
                            selected = combinedSubTab == 0,
                            onClick = { combinedSubTab = 0 },
                            text = { Text("Me") }
                        )
                        Tab(
                            selected = combinedSubTab == 1,
                            onClick = { combinedSubTab = 1 },
                            text = { Text("Shared") }
                        )
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (selectedFilter == "Combined") {
                        if (combinedSubTab == 0) {
                            items(myTransactions, key = { it.id }) { txn ->
                                TransactionRow(
                                    transaction = txn,
                                    onClick = { selectedTransaction = txn },
                                    viewerUsername = myUsername,
                                    viewerDefaultPct = viewerDefaultPct
                                )
                            }
                        } else {
                            if (partnerCombined.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No shared transactions",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(partnerCombined, key = { "p_${it.id}" }) { txn ->
                                    TransactionRow(
                                        transaction = txn.toTransactionEntity(),
                                        onClick = { selectedSharedTransaction = txn },
                                        isShared = true,
                                        sharedUsernames = sharedUsernames,
                                        viewerUsername = myUsername,
                                        viewerDefaultPct = viewerDefaultPct
                                    )
                                }
                            }
                        }
                    } else {
                        items(myTransactions, key = { it.id }) { txn ->
                            TransactionRow(
                                transaction = txn,
                                onClick = { selectedTransaction = txn },
                                viewerUsername = myUsername,
                                viewerDefaultPct = viewerDefaultPct
                            )
                        }
                    }
                }
            }

            if (selectedFilter == "Combined") {
                FloatingActionButton(
                    onClick = { if (isOnline && !isSyncing) settingsViewModel.syncNow() },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                }
            }
        }
    }

    // Bottom sheet for own transactions (full edit/tag/discard)
    selectedTransaction?.let { txn ->
        TagBottomSheet(
            onDismiss = { selectedTransaction = null },
            onTag = { tag -> viewModel.updateTag(txn.id, tag) },
            onEdit = { editingTransaction = txn },
            onDiscard = { viewModel.deleteTransaction(txn.id) },
            amountText = "₹${"%.2f".format(txn.amount)} · ${txn.bankName}",
            rawMessage = txn.rawMessage
        )
    }

    // Read-only bottom sheet for shared person transactions (shows source message only)
    selectedSharedTransaction?.let { txn ->
        TagBottomSheet(
            onDismiss = { selectedSharedTransaction = null },
            onTag = {},
            amountText = "₹${"%.2f".format(txn.amount)} · ${txn.bankName} · ${txn.addedBy}",
            rawMessage = txn.rawMessage,
            readOnly = true
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
}
