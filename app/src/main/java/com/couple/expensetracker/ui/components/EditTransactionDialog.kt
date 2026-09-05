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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.util.SplitUtils
import org.json.JSONObject
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionEntity,
    categories: List<String>,
    sharedUsernames: List<String> = emptyList(),
    defaultPercentages: Map<String, Double> = emptyMap(),
    myUsername: String = "me",
    onSave: (amount: Double, bankName: String, category: String?, customSplits: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("%.2f".format(transaction.amount)) }
    var bankNameText by remember { mutableStateOf(transaction.bankName) }
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var amountError by remember { mutableStateOf(false) }
    var showSplitSection by remember { mutableStateOf(transaction.customSplits != null) }
    var splitMode by remember { mutableStateOf("amount") } // "amount" or "pct"

    val txnAmount = amountText.replace(",", "").toDoubleOrNull() ?: transaction.amount
    val allPersons = remember(sharedUsernames) { listOf("me") + sharedUsernames }
    val showSplitOption = transaction.tag == "Combined" && sharedUsernames.isNotEmpty()

    val lastPerson = remember(allPersons) { allPersons.last() }
    val editablePersons = remember(allPersons) { if (allPersons.size > 1) allPersons.dropLast(1) else allPersons }

    val initialAmounts = remember(transaction.customSplits, sharedUsernames, defaultPercentages, txnAmount) {
        val totalPersons = allPersons.size
        val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
        if (!transaction.customSplits.isNullOrBlank()) {
            allPersons.associateWith { p ->
                // New format uses actual username; old format uses "me" for adder's share
                val key = if (p == "me") myUsername.ifBlank { "me" } else p
                SplitUtils.getSplitAmount(transaction.customSplits, key)
                    ?: SplitUtils.getSplitAmount(transaction.customSplits, "me").takeIf { p == "me" }
                    ?: (txnAmount * (defaultPercentages[p] ?: defaultEqualPct) / 100.0)
            }
        } else {
            allPersons.associateWith { p ->
                txnAmount * (defaultPercentages[p] ?: defaultEqualPct) / 100.0
            }
        }
    }

    val splitAmountTexts = remember(initialAmounts) {
        mutableStateMapOf<String, String>().also { map ->
            initialAmounts.forEach { (k, v) -> map[k] = "%.2f".format(v) }
        }
    }

    val splitPctInputTexts = remember(initialAmounts, txnAmount, allPersons) {
        mutableStateMapOf<String, String>().also { map ->
            editablePersons.forEach { p ->
                val amt = initialAmounts[p] ?: 0.0
                val pct = if (txnAmount > 0) amt / txnAmount * 100.0 else 100.0 / allPersons.size
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

    LaunchedEffect(txnAmount) {
        if (!showSplitSection) return@LaunchedEffect
        val prevTotal = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
        if (prevTotal > 0.0 && abs(prevTotal - txnAmount) > 0.01) {
            editablePersons.forEach { p ->
                val current = splitAmountTexts[p]?.toDoubleOrNull() ?: 0.0
                splitAmountTexts[p] = "%.2f".format(current / prevTotal * txnAmount)
            }
            recomputeLast()
        }
    }

    val splitSum = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
    val lastAmt = splitAmountTexts[lastPerson]?.toDoubleOrNull() ?: 0.0
    val splitValid = abs(splitSum - txnAmount) < 0.02 && lastAmt >= -0.01

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

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "Category (optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        CategoryRow(label = "None", selected = selectedCategory == null, onClick = { selectedCategory = null })
                        categories.sorted().forEach { cat ->
                            CategoryRow(label = cat, selected = selectedCategory == cat, onClick = { selectedCategory = cat })
                        }
                    }
                }

                if (showSplitOption) {
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
                                            // pct mode
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(",", "").toDoubleOrNull()
                if (amount == null || amount <= 0) { amountError = true; return@TextButton }

                if (showSplitOption && showSplitSection) {
                    val lastAmtCheck = splitAmountTexts[lastPerson]?.toDoubleOrNull() ?: 0.0
                    if (lastAmtCheck < -0.01) return@TextButton
                }

                val customSplits = if (showSplitOption && showSplitSection) {
                    SplitUtils.buildSplitsJson(myUsername, allPersons, splitAmountTexts, amount)
                } else if (showSplitOption) {
                    SplitUtils.buildDefaultSplitsJson(myUsername, allPersons, defaultPercentages, amount)
                } else {
                    null
                }

                onSave(amount, bankNameText.trim().ifBlank { transaction.bankName }, selectedCategory, customSplits)
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
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
