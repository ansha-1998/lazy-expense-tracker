package com.couple.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.couple.expensetracker.ui.viewmodel.ManualAddViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddScreen(
    onNavigateBack: () -> Unit,
    viewModel: ManualAddViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()

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
                    viewModel.saveTransaction(
                        date = date,
                        amount = amount,
                        paymentType = paymentType,
                        bankName = bankName,
                        last4OrRef = last4OrRef,
                        tag = tag,
                        category = category,
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
