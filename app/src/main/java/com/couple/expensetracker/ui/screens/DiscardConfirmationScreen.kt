package com.couple.expensetracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.couple.expensetracker.ui.viewmodel.UnclassifiedViewModel

@Composable
fun DiscardConfirmationScreen(
    transactionId: String,
    amount: Double,
    bankName: String,
    last4OrRef: String,
    onNavigateBack: () -> Unit,
    viewModel: UnclassifiedViewModel = hiltViewModel()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Discard Transaction?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "₹${"%.2f".format(amount)} · $bankName" +
                            (if (last4OrRef.isNotBlank()) " · $last4OrRef" else "") +
                            " will be permanently deleted. This cannot be undone."
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f)
                    ) { Text("Cancel") }
                    Button(
                        onClick = {
                            viewModel.discard(transactionId)
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Confirm Discard") }
                }
            }
        }
    }
}
