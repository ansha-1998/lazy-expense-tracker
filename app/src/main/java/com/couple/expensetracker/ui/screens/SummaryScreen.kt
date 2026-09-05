package com.couple.expensetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.couple.expensetracker.ui.components.MonthPicker
import com.couple.expensetracker.ui.viewmodel.CategoryFilter
import com.couple.expensetracker.ui.viewmodel.SharedPersonData
import com.couple.expensetracker.ui.viewmodel.SummaryViewModel
import com.couple.expensetracker.util.DateUtils
import kotlinx.coroutines.flow.collectLatest

private val PIE_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
    Color(0xFF607D8B)
)

// Colors assigned to shared persons by index (index 0 = first shared person)
private val PERSON_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722)
)

@Composable
fun SummaryScreen(viewModel: SummaryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val monthKey by viewModel.monthKey.collectAsState()
    val mySummary by viewModel.mySummary.collectAsState()
    val lastSynced by viewModel.lastSynced.collectAsState()
    val categoryBreakdown by viewModel.filteredCategoryBreakdown.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val allTimeCategoryBreakdown by viewModel.filteredAllTimeCategoryBreakdown.collectAsState()
    val allTimeCategoryFilter by viewModel.allTimeCategoryFilter.collectAsState()
    val allTimeMySummary by viewModel.allTimeMySummary.collectAsState()
    val sharedPersonsData by viewModel.sharedPersonsData.collectAsState()
    val myBalance by viewModel.myBalance.collectAsState()
    val allTimeSharedPersonsData by viewModel.allTimeSharedPersonsData.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.csvError.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        viewModel.csvShareIntent.collectLatest { intent -> context.startActivity(intent) }
    }

    val isSharedStale = remember(lastSynced) {
        lastSynced == 0L || (System.currentTimeMillis() - lastSynced) > 24 * 60 * 60 * 1000L
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Monthly") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Total") }
                )
            }

            if (selectedTab == 0) {
                MonthlyTab(
                    monthKey = monthKey,
                    mySummary = mySummary,
                    sharedPersonsData = sharedPersonsData,
                    myBalance = myBalance,
                    lastSynced = lastSynced,
                    isSharedStale = isSharedStale,
                    categoryBreakdown = categoryBreakdown,
                    categoryFilter = categoryFilter,
                    onCategoryFilterChange = viewModel::setCategoryFilter,
                    onSettleUp = viewModel::settleUp,
                    onPrevious = viewModel::goToPreviousMonth,
                    onNext = viewModel::goToNextMonth,
                    onExportCsv = { viewModel.exportCsv(context) }
                )
            } else {
                TotalTab(
                    allTimeMySummary = allTimeMySummary,
                    allTimeSharedPersonsData = allTimeSharedPersonsData,
                    categoryBreakdown = allTimeCategoryBreakdown,
                    categoryFilter = allTimeCategoryFilter,
                    onCategoryFilterChange = viewModel::setAllTimeCategoryFilter,
                    lastSynced = lastSynced
                )
            }
        }
    }
}

@Composable
private fun MonthlyTab(
    monthKey: String,
    mySummary: MonthlySummaryEntity?,
    sharedPersonsData: List<SharedPersonData>,
    myBalance: Double,
    lastSynced: Long,
    isSharedStale: Boolean,
    categoryBreakdown: Map<String, Double>,
    categoryFilter: CategoryFilter,
    onCategoryFilterChange: (CategoryFilter) -> Unit,
    onSettleUp: (username: String, balance: Double, amount: Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        MonthPicker(
            currentMonthKey = monthKey,
            onPrevious = onPrevious,
            onNext = onNext
        )

        Divider()

        SummarySection(title = "MY EXPENSES") {
            SummaryRow("Personal", mySummary?.personalTotal)
            SummaryRow("Combined", mySummary?.combinedTotal)
            SummaryRow("Other", mySummary?.otherTotal)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            SummaryRow("My Total", mySummary?.grandTotal, bold = true)
        }

        if (sharedPersonsData.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            val myCombined = mySummary?.combinedTotal ?: 0.0
            val totalCombined = myCombined + sharedPersonsData.sumOf { it.combinedTotal }

            SummarySection(title = "COMBINED SPLIT") {
                var splitDetailsExpanded by remember { mutableStateOf(false) }
                var settleUpTarget by remember { mutableStateOf<SharedPersonData?>(null) }

                // ── Settlement rows first (always visible) ──────────────
                sharedPersonsData.forEach { person ->
                    when {
                        person.balance < -0.01 ->
                            SettlementRowWithButton(
                                label = "${person.username} owes me",
                                amount = -person.balance,
                                isOwedToMe = true,
                                settledThisMonth = person.settledThisMonth,
                                lastSettledBy = person.lastSettledBy,
                                onSettleUp = { settleUpTarget = person }
                            )
                        person.balance > 0.01 ->
                            SettlementRowWithButton(
                                label = "I owe ${person.username}",
                                amount = person.balance,
                                isOwedToMe = false,
                                settledThisMonth = person.settledThisMonth,
                                lastSettledBy = person.lastSettledBy,
                                onSettleUp = { settleUpTarget = person }
                            )
                        else ->
                            SettlementRow("Settled with ${person.username}", 0.0, isOwedToMe = null, settledThisMonth = person.settledThisMonth, lastSettledBy = person.lastSettledBy)
                    }
                }

                // ── Settle Up confirmation dialog ────────────────────────
                settleUpTarget?.let { target ->
                    val iOweThem = target.balance > 0
                    val displayAmount = kotlin.math.abs(target.balance)
                    var amountText by remember(target) { mutableStateOf("%.2f".format(displayAmount)) }
                    val enteredAmount = amountText.toDoubleOrNull()
                    val isValid = enteredAmount != null && enteredAmount > 0.0

                    AlertDialog(
                        onDismissRequest = { settleUpTarget = null },
                        title = { Text("Settle Up") },
                        text = {
                            Column {
                                Text(
                                    if (iOweThem)
                                        "How much did you pay ${target.username}?"
                                    else
                                        "How much did you receive from ${target.username}?"
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = amountText,
                                    onValueChange = { amountText = it },
                                    label = { Text("Amount") },
                                    prefix = { Text("₹") },
                                    singleLine = true,
                                    isError = !isValid,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Outstanding balance: ₹${"%.2f".format(displayAmount)}. This records the payment as settled — it will not be added as an expense.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = isValid,
                                onClick = {
                                    onSettleUp(target.username, target.balance, enteredAmount!!)
                                    settleUpTarget = null
                                }
                            ) { Text("Confirm") }
                        },
                        dismissButton = {
                            TextButton(onClick = { settleUpTarget = null }) { Text("Cancel") }
                        }
                    )
                }
                if (sharedPersonsData.size > 1) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    when {
                        myBalance > 0.01 -> SettlementRow("Net: others owe me", myBalance, isOwedToMe = true)
                        myBalance < -0.01 -> SettlementRow("Net: I owe others", -myBalance, isOwedToMe = false)
                        else -> SettlementRow("Net: All settled", 0.0, isOwedToMe = null)
                    }
                }

                // ── Collapsible details ──────────────────────────────────
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { splitDetailsExpanded = !splitDetailsExpanded }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (splitDetailsExpanded) "▲" else "▼",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (splitDetailsExpanded) {
                    val myShare = myCombined - myBalance
                    SummaryRow("My contribution", myCombined)
                    SummaryRow("My share", myShare)

                    sharedPersonsData.forEachIndexed { idx, person ->
                        val color = PERSON_COLORS[idx % PERSON_COLORS.size]
                        val personShare = person.combinedTotal - person.balance
                        Divider(modifier = Modifier.padding(vertical = 2.dp))
                        ColoredPersonRow("${person.username}'s contribution", person.combinedTotal, color)
                        ColoredPersonRow("${person.username}'s share", personShare, color)
                        if (person.settledThisMonth > 0.01) {
                            ColoredPersonRow("${person.username}'s settled amount", person.settledThisMonth, color)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    SummaryRow("Total combined", totalCombined, bold = true)
                }
            }
        }

        if (sharedPersonsData.isNotEmpty() && isSharedStale) {
            Text(
                text = if (lastSynced == 0L) "⚠ Shared data never synced"
                else "⚠ Last synced: ${DateUtils.formatLastSynced(lastSynced)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (categoryBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryPieSection(
                breakdown = categoryBreakdown,
                filter = categoryFilter,
                onFilterChange = onCategoryFilterChange
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onExportCsv,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Download CSV")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Last synced: ${DateUtils.formatLastSynced(lastSynced)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun TotalTab(
    allTimeMySummary: MonthlySummaryEntity?,
    allTimeSharedPersonsData: List<SharedPersonData>,
    categoryBreakdown: Map<String, Double>,
    categoryFilter: CategoryFilter,
    onCategoryFilterChange: (CategoryFilter) -> Unit,
    lastSynced: Long
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "ALL TIME",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        SummarySection(title = "MY EXPENSES") {
            SummaryRow("Personal", allTimeMySummary?.personalTotal)
            SummaryRow("Combined", allTimeMySummary?.combinedTotal)
            SummaryRow("Other", allTimeMySummary?.otherTotal)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            SummaryRow("My Total", allTimeMySummary?.grandTotal, bold = true)
        }

        if (allTimeSharedPersonsData.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            val myCombined = allTimeMySummary?.combinedTotal ?: 0.0
            val totalSharedCombined = allTimeSharedPersonsData.sumOf { it.combinedTotal }
            val totalCombined = myCombined + totalSharedCombined

            SummarySection(title = "COMBINED TOTAL") {
                SummaryRow("My contribution", myCombined)
                allTimeSharedPersonsData.forEachIndexed { idx, person ->
                    val color = PERSON_COLORS[idx % PERSON_COLORS.size]
                    ColoredPersonRow("${person.username}'s contribution", person.combinedTotal, color)
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                SummaryRow("Total combined", totalCombined, bold = true)
            }
        }

        if (categoryBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryPieSection(
                breakdown = categoryBreakdown,
                filter = categoryFilter,
                onFilterChange = onCategoryFilterChange
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Totals computed from stored monthly summaries · Last synced: ${DateUtils.formatLastSynced(lastSynced)}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun CategoryPieSection(
    breakdown: Map<String, Double>,
    filter: CategoryFilter,
    onFilterChange: (CategoryFilter) -> Unit
) {
    SummarySection(title = "CATEGORY BREAKDOWN") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilterChange(f) },
                    label = {
                        Text(
                            text = when (f) {
                                CategoryFilter.Total -> "Total"
                                CategoryFilter.Personal -> "Personal"
                                CategoryFilter.Shared -> "Shared"
                                CategoryFilter.Other -> "Other"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        val total = breakdown.values.sum()
        if (total <= 0.0) {
            Text(
                text = "No transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            return@SummarySection
        }

        val entries = breakdown.entries
            .sortedByDescending { it.value }
            .toList()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                var startAngle = -90f
                entries.forEachIndexed { index, entry ->
                    val sweep = (entry.value / total * 360f).toFloat()
                    drawArc(
                        color = PIE_COLORS[index % PIE_COLORS.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweep
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        entries.forEachIndexed { index, entry ->
            val pct = entry.value / total * 100
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(12.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = PIE_COLORS[index % PIE_COLORS.size])
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.key,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${"%.1f".format(pct)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "₹${"%.0f".format(entry.value)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double?, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(
            text = if (amount != null) "₹${"%.2f".format(amount)}" else "—",
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettlementRowWithButton(
    label: String,
    amount: Double,
    isOwedToMe: Boolean,
    settledThisMonth: Double = 0.0,
    lastSettledBy: String? = null,
    onSettleUp: () -> Unit
) {
    val color = if (isOwedToMe) Color(0xFF2E7D32) else Color(0xFFC62828)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(text = "₹${"%.2f".format(amount)}", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.bodyMedium)
            if (settledThisMonth > 0.01) {
                Text(
                    text = "Settled so far: ₹${"%.2f".format(settledThisMonth)}" +
                        (lastSettledBy?.let { " · marked by $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(
            onClick = onSettleUp,
            modifier = Modifier.padding(start = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Settle Up", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// Settlement row: green when someone owes me, red when I owe, neutral when settled
@Composable
private fun SettlementRow(label: String, amount: Double, isOwedToMe: Boolean?, settledThisMonth: Double = 0.0, lastSettledBy: String? = null) {
    val color = when (isOwedToMe) {
        true  -> Color(0xFF2E7D32)   // dark green — money coming to me
        false -> Color(0xFFC62828)   // dark red   — money I owe
        null  -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontWeight = FontWeight.Bold, color = color)
            Text(
                text = if (isOwedToMe == null) "✓ Settled" else "₹${"%.2f".format(amount)}",
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        if (settledThisMonth > 0.01) {
            Text(
                text = "Settled so far: ₹${"%.2f".format(settledThisMonth)}" +
                    (lastSettledBy?.let { " · marked by $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

// Full-width row with colored person name dot + label
@Composable
private fun ColoredPersonRow(label: String, amount: Double, color: Color, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text = label,
                color = color,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "₹${"%.2f".format(amount)}",
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
