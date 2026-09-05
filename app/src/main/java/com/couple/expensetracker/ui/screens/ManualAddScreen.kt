package com.couple.expensetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.couple.expensetracker.ui.viewmodel.ManualAddViewModel
import com.couple.expensetracker.util.SplitUtils
import java.util.*
import kotlin.math.abs

private val PERSON_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManualAddViewModel = hiltViewModel()
) {
    val myUsername by viewModel.myUsername.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val sharedUsernames by viewModel.sharedUsernames.collectAsState()
    val defaultPercentages by viewModel.sharePercentages.collectAsState()

    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var amountText by remember { mutableStateOf("") }
    var paymentType by remember { mutableStateOf("UPI") }
    var bankName by remember { mutableStateOf("") }
    var last4OrRef by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("Personal") }
    var category by remember { mutableStateOf<String?>(null) }

    var amountError by remember { mutableStateOf(false) }
    var bankError by remember { mutableStateOf(false) }

    var showPaymentDropdown by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Split state
    val txnAmount = amountText.toDoubleOrNull() ?: 0.0
    val allPersons = remember(sharedUsernames) { listOf("me") + sharedUsernames }
    val lastPerson = remember(allPersons) { allPersons.last() }
    val editablePersons = remember(allPersons) { if (allPersons.size > 1) allPersons.dropLast(1) else allPersons }
    val showSplitOption = tag == "Combined" && sharedUsernames.isNotEmpty()

    var showSplitSection by remember { mutableStateOf(false) }
    var splitMode by remember { mutableStateOf("amount") }

    val splitAmountTexts = remember(allPersons, defaultPercentages) {
        val totalPersons = allPersons.size
        val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
        mutableStateMapOf<String, String>().also { map ->
            allPersons.forEach { p ->
                val pct = defaultPercentages[p] ?: defaultEqualPct
                map[p] = "%.2f".format(txnAmount * pct / 100.0)
            }
        }
    }

    val splitPctInputTexts = remember(allPersons, defaultPercentages) {
        val totalPersons = allPersons.size
        val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
        mutableStateMapOf<String, String>().also { map ->
            editablePersons.forEach { p ->
                map[p] = "%.1f".format(defaultPercentages[p] ?: defaultEqualPct)
            }
        }
    }

    val recomputeLast: () -> Unit = {
        if (allPersons.size >= 2) {
            val othersSum = editablePersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
            splitAmountTexts[lastPerson] = "%.2f".format(txnAmount - othersSum)
        }
    }

    // When split section first opens, initialize amounts if they're all still zero
    LaunchedEffect(showSplitSection) {
        if (!showSplitSection || txnAmount <= 0.0) return@LaunchedEffect
        val currentTotal = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
        if (currentTotal < 0.01) {
            val totalPersons = allPersons.size
            val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
            editablePersons.forEach { p ->
                val pct = defaultPercentages[p] ?: defaultEqualPct
                splitAmountTexts[p] = "%.2f".format(txnAmount * pct / 100.0)
            }
            recomputeLast()
        }
    }

    // When amount changes, scale editable persons proportionally and recompute last
    LaunchedEffect(txnAmount) {
        if (!showSplitSection || txnAmount <= 0.0) return@LaunchedEffect
        val prevTotal = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
        if (prevTotal > 0.0) {
            editablePersons.forEach { p ->
                val current = splitAmountTexts[p]?.toDoubleOrNull() ?: 0.0
                splitAmountTexts[p] = "%.2f".format(current / prevTotal * txnAmount)
            }
        } else {
            // first time: distribute by default percentages
            val totalPersons = allPersons.size
            val defaultEqualPct = if (totalPersons > 0) 100.0 / totalPersons else 100.0
            editablePersons.forEach { p ->
                val pct = defaultPercentages[p] ?: defaultEqualPct
                splitAmountTexts[p] = "%.2f".format(txnAmount * pct / 100.0)
            }
        }
        recomputeLast()
    }

    val splitSum = allPersons.sumOf { splitAmountTexts[it]?.toDoubleOrNull() ?: 0.0 }
    val lastAmt = splitAmountTexts[lastPerson]?.toDoubleOrNull() ?: 0.0
    val splitValid = abs(splitSum - txnAmount) < 0.02 && lastAmt >= -0.01

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = date)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Transaction") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) { Text("Cancel") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Date: ${formatDate(date)}")
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it; amountError = false },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountError,
                supportingText = if (amountError) ({ Text("Enter a valid amount") }) else null,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = showPaymentDropdown,
                onExpandedChange = { showPaymentDropdown = it }
            ) {
                OutlinedTextField(
                    value = paymentType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showPaymentDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showPaymentDropdown,
                    onDismissRequest = { showPaymentDropdown = false }
                ) {
                    listOf("UPI", "Card").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { paymentType = type; showPaymentDropdown = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it; bankError = false },
                label = { Text("Bank Name") },
                isError = bankError,
                supportingText = if (bankError) ({ Text("Enter bank name") }) else null,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = last4OrRef,
                onValueChange = { last4OrRef = it },
                label = { Text("Last 4 digits / UPI Ref") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = showTagDropdown,
                onExpandedChange = { showTagDropdown = it }
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tag") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showTagDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showTagDropdown,
                    onDismissRequest = { showTagDropdown = false }
                ) {
                    listOf("Personal", "Combined", "Other").forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = { tag = t; showTagDropdown = false }
                        )
                    }
                }
            }

            // Custom split — shown when tag == Combined and shared persons exist
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

                            allPersons.forEachIndexed { idx, person ->
                                val isLast = person == lastPerson
                                val personColor = if (person == "me") MaterialTheme.colorScheme.primary
                                                  else PERSON_COLORS[(idx - 1) % PERSON_COLORS.size]
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
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(personColor, CircleShape)
                                            )
                                            Text(
                                                text = if (person == "me") "Me" else person,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = personColor
                                            )
                                        }
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

            ExposedDropdownMenuBox(
                expanded = showCategoryDropdown,
                onExpandedChange = { showCategoryDropdown = it }
            ) {
                OutlinedTextField(
                    value = category ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(showCategoryDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = showCategoryDropdown,
                    onDismissRequest = { showCategoryDropdown = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = { category = null; showCategoryDropdown = false }
                    )
                    categories.sorted().forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = { category = cat; showCategoryDropdown = false }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) { amountError = true; return@Button }
                    if (bankName.isBlank()) { bankError = true; return@Button }

                    val customSplits: String? = if (showSplitOption) {
                        if (showSplitSection) {
                            if (lastAmt < -0.01) return@Button
                            SplitUtils.buildSplitsJson(myUsername, allPersons, splitAmountTexts, amount)
                        } else {
                            SplitUtils.buildDefaultSplitsJson(myUsername, allPersons, defaultPercentages, amount)
                        }
                    } else null

                    viewModel.saveTransaction(
                        date = date,
                        amount = amount,
                        paymentType = paymentType,
                        bankName = bankName,
                        last4OrRef = last4OrRef,
                        tag = tag,
                        category = category,
                        customSplits = customSplits,
                        onSuccess = onNavigateBack
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "%02d %s %04d".format(
        cal.get(Calendar.DAY_OF_MONTH),
        listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[cal.get(Calendar.MONTH)],
        cal.get(Calendar.YEAR)
    )
}
