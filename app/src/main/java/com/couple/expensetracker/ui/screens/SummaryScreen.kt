package com.couple.expensetracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
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
import com.couple.expensetracker.ui.viewmodel.SummaryViewModel
import com.couple.expensetracker.util.DateUtils
import kotlinx.coroutines.flow.collectLatest

private val PIE_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800), Color(0xFFE91E63),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF795548),
    Color(0xFF607D8B)
)

@Composable
fun SummaryScreen(viewModel: SummaryViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val monthKey by viewModel.monthKey.collectAsState()
    val mySummary by viewModel.mySummary.collectAsState()
    val partnerSummary by viewModel.partnerSummary.collectAsState()
    val lastSynced by viewModel.lastSynced.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val allTimeCategoryBreakdown by viewModel.allTimeCategoryBreakdown.collectAsState()
    val allTimeMySummary by viewModel.allTimeMySummary.collectAsState()
    val allTimePartnerCombinedTotal by viewModel.allTimePartnerCombinedTotal.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.csvError.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        viewModel.csvShareIntent.collectLatest { intent ->
            context.startActivity(intent)
        }
    }

    val isPartnerStale = remember(lastSynced) {
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
                    partnerSummary = partnerSummary,
                    lastSynced = lastSynced,
                    isPartnerStale = isPartnerStale,
                    categoryBreakdown = categoryBreakdown,
                    onPrevious = viewModel::goToPreviousMonth,
                    onNext = viewModel::goToNextMonth,
                    onExportCsv = { viewModel.exportCsv(context) }
                )
            } else {
                TotalTab(
                    allTimeMySummary = allTimeMySummary,
                    allTimePartnerCombinedTotal = allTimePartnerCombinedTotal,
                    categoryBreakdown = allTimeCategoryBreakdown,
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
    partnerSummary: PartnerSummaryEntity?,
    lastSynced: Long,
    isPartnerStale: Boolean,
    categoryBreakdown: Map<String, Double>,
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

        Spacer(modifier = Modifier.height(16.dp))

        SummarySection(title = "PARTNER EXPENSES (Combined only)") {
            if (partnerSummary != null) {
                SummaryRow("Partner Combined", partnerSummary.combinedTotal)
            } else {
                Text(
                    text = "—",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isPartnerStale) {
                Text(
                    text = if (lastSynced == 0L) "⚠ Partner data never synced"
                    else "⚠ Partner data not synced · Last synced: ${DateUtils.formatLastSynced(lastSynced)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val myCombined = mySummary?.combinedTotal ?: 0.0
        val partnerCombined = partnerSummary?.combinedTotal ?: 0.0
        val totalCombined = myCombined + partnerCombined
        val eachShare = totalCombined / 2.0
        val myBalance = myCombined - eachShare

        SummarySection(title = "COMBINED SPLIT TOTAL") {
            SummaryRow("My contribution", myCombined)
            SummaryRow("Partner's contribution", partnerCombined)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            SummaryRow("Total combined", totalCombined)
            SummaryRow("Each share", eachShare, bold = true)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            when {
                myBalance > 0.01 -> SummaryRow("Partner owes me", myBalance, bold = true)
                myBalance < -0.01 -> SummaryRow("I owe partner", -myBalance, bold = true)
                else -> SummaryRow("All settled", 0.0, bold = true)
            }
        }

        if (categoryBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryPieSection(breakdown = categoryBreakdown)
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
    allTimePartnerCombinedTotal: Double,
    categoryBreakdown: Map<String, Double>,
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

        Spacer(modifier = Modifier.height(16.dp))

        SummarySection(title = "PARTNER EXPENSES (Combined only)") {
            SummaryRow("Partner Combined", allTimePartnerCombinedTotal)
        }

        Spacer(modifier = Modifier.height(16.dp))

        val myCombined = allTimeMySummary?.combinedTotal ?: 0.0
        val totalCombined = myCombined + allTimePartnerCombinedTotal
        val eachShare = totalCombined / 2.0
        val myBalance = myCombined - eachShare

        SummarySection(title = "COMBINED SPLIT TOTAL") {
            SummaryRow("My contribution", myCombined)
            SummaryRow("Partner's contribution", allTimePartnerCombinedTotal)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            SummaryRow("Total combined", totalCombined)
            SummaryRow("Each share", eachShare, bold = true)
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            when {
                myBalance > 0.01 -> SummaryRow("Partner owes me", myBalance, bold = true)
                myBalance < -0.01 -> SummaryRow("I owe partner", -myBalance, bold = true)
                else -> SummaryRow("All settled", 0.0, bold = true)
            }
        }

        if (categoryBreakdown.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryPieSection(breakdown = categoryBreakdown)
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
private fun CategoryPieSection(breakdown: Map<String, Double>) {
    SummarySection(title = "CATEGORY BREAKDOWN") {
        val total = breakdown.values.sum()
        if (total <= 0.0) return@SummarySection

        val entries = breakdown.entries
            .sortedByDescending { it.value }
            .toList()

        // Pie chart
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

        // Legend
        entries.forEachIndexed { index, entry ->
            val pct = entry.value / total * 100
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .padding(end = 0.dp)
                ) {
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
        Text(
            text = label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = if (amount != null) "₹${"%.2f".format(amount)}" else "—",
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
