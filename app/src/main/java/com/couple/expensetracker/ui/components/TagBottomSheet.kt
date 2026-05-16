package com.couple.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBottomSheet(
    onDismiss: () -> Unit,
    onTag: (String) -> Unit,
    onDiscard: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    amountText: String = "",
    rawMessage: String? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (amountText.isNotBlank()) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Text(
                text = "Tag as",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TagButton(label = "Personal", onClick = { onTag("Personal"); onDismiss() })
            TagButton(label = "Combined", onClick = { onTag("Combined"); onDismiss() })
            TagButton(label = "Other", onClick = { onTag("Other"); onDismiss() })
            if (onEdit != null) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = { onDismiss(); onEdit() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit Details / Category")
                }
            }
            if (onDiscard != null) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onDismiss(); onDiscard() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard")
                }
            }
            if (!rawMessage.isNullOrBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (expanded) "Hide Source SMS" else "View Source SMS")
                }
                if (expanded) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = rawMessage,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label)
    }
}
