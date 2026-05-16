package com.couple.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.couple.expensetracker.data.db.entities.TransactionEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionEntity,
    categories: List<String>,
    onSave: (amount: Double, bankName: String, category: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("%.2f".format(transaction.amount)) }
    var bankNameText by remember { mutableStateOf(transaction.bankName) }
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var amountError by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    supportingText = if (amountError) ({ Text("Enter a valid amount") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bankNameText,
                    onValueChange = { bankNameText = it },
                    label = { Text("Bank / Source") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category picker — inline list avoids nested-popup clipping in AlertDialog
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Category (optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        CategoryRow(
                            label = "None",
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null }
                        )
                        categories.sorted().forEach { cat ->
                            CategoryRow(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", "").toDoubleOrNull()
                if (amount == null || amount <= 0) { amountError = true; return@TextButton }
                onSave(amount, bankNameText.trim().ifBlank { transaction.bankName }, selectedCategory)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CategoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
