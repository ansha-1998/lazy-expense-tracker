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
import com.couple.expensetracker.ui.components.TagBottomSheet
import com.couple.expensetracker.ui.components.TransactionRow
import com.couple.expensetracker.ui.viewmodel.SettingsViewModel
import com.couple.expensetracker.ui.viewmodel.TransactionsViewModel

private fun PartnerTransactionEntity.toTransactionEntity() = TransactionEntity(
    id = id, date = date, amount = amount, paymentType = paymentType,
    bankName = bankName, last4OrRef = last4OrRef, tag = tag,
    addedBy = addedBy, source = source, lastModified = lastModified, syncStatus = syncStatus,
    rawMessage = null
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
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    val isOnline by settingsViewModel.isOnline.collectAsState()
    val categories by settingsViewModel.categories.collectAsState()
    var selectedTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var combinedSubTab by remember { mutableStateOf(0) } // 0 = Me, 1 = Partner

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
                            text = { Text("Partner") }
                        )
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (selectedFilter == "Combined") {
                        if (combinedSubTab == 0) {
                            items(myTransactions, key = { it.id }) { txn ->
                                TransactionRow(
                                    transaction = txn,
                                    onClick = { selectedTransaction = txn }
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
                                            text = "No partner transactions",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                items(partnerCombined, key = { "p_${it.id}" }) { txn ->
                                    TransactionRow(
                                        transaction = txn.toTransactionEntity(),
                                        onClick = {},
                                        isPartner = true
                                    )
                                }
                            }
                        }
                    } else {
                        items(myTransactions, key = { it.id }) { txn ->
                            TransactionRow(
                                transaction = txn,
                                onClick = { selectedTransaction = txn }
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
}
