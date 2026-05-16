package com.couple.expensetracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.text.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.couple.expensetracker.data.db.entities.MonthlySummaryEntity
import com.couple.expensetracker.data.db.entities.PartnerSummaryEntity
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.util.DateUtils
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

val KEY_SELECTED_TAB = stringPreferencesKey("selected_tab")
val KEY_HIDDEN_IDS = stringPreferencesKey("hidden_ids")
val KEY_PENDING_TXNS = stringPreferencesKey("pending_txns")
val KEY_SYNCING = booleanPreferencesKey("syncing")
const val TAB_UNCLASSIFIED = "unclassified"
const val TAB_ALL = "all"
const val TAB_SUMMARY = "summary"

private val Purple = Color(0xFF6650A4)
private val PurpleLight = Color(0xFFD0BCFF)
private val Green = Color(0xFF4CAF50)
private val Blue = Color(0xFF2196F3)
private val Orange = Color(0xFFFF9800)
private val Red = Color(0xFFCF6679)
private val RedSurface = Color(0xFF4D1F25)
private val TextPrimary = ColorProvider(Color(0xFFE6E1E5))
private val TextSecondary = ColorProvider(Color(0xFFCAC4D0))
private val TextMuted = ColorProvider(Color(0xFF938F99))
private val DividerColor = Color(0xFF49454F)

class ExpenseWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val txnRepo = ep.transactionRepository()
        val summaryRepo = ep.summaryRepository()
        val prefs = ep.appPreferences()

        val username = prefs.myUsername.first().ifBlank { "me" }
        val monthKey = DateUtils.currentMonthKey()

        // Run all queries in parallel — cuts data-loading time from ~5x to ~1x query time
        var unclassified: List<TransactionEntity> = emptyList()
        var allTransactions: List<TransactionEntity> = emptyList()
        var mySummary: MonthlySummaryEntity? = null
        var partnerSummary: PartnerSummaryEntity? = null
        var lastSynced = 0L
        coroutineScope {
            val dUnclassified = async { txnRepo.getUnclassified(username).first() }
            val dAll          = async { txnRepo.getAll(username).first() }
            val dMySummary    = async { summaryRepo.getMySummary(monthKey).first() }
            val dPartner      = async { summaryRepo.getPartnerSummary(monthKey).first() }
            val dLastSynced   = async { prefs.lastSynced.first() }
            unclassified   = dUnclassified.await()
            allTransactions = dAll.await()
            mySummary      = dMySummary.await()
            partnerSummary = dPartner.await()
            lastSynced     = dLastSynced.await()
        }

        provideContent {
            val widgetPrefs = currentState<Preferences>()
            val selectedTab = widgetPrefs[KEY_SELECTED_TAB] ?: TAB_UNCLASSIFIED
            val isSyncing = widgetPrefs[KEY_SYNCING] ?: false
            val hiddenIds = widgetPrefs[KEY_HIDDEN_IDS]
                ?.split(",")?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()

            // Optimistic pending items: show immediately without waiting for full DB refresh
            // Use allTransactions so classified txns no longer leak back as pending entries
            val realIds = allTransactions.map { it.id }.toSet()
            val pendingTxns = widgetPrefs[KEY_PENDING_TXNS]
                ?.split("\n")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { entry ->
                    val p = entry.split("|")
                    if (p.size >= 3 && p[0] !in realIds && p[0] !in hiddenIds)
                        TransactionEntity(
                            id = p[0], amount = p[1].toDoubleOrNull() ?: 0.0,
                            bankName = p[2], date = 0, tag = "Unclassified",
                            addedBy = username, paymentType = "", last4OrRef = "",
                            source = "", lastModified = 0, syncStatus = ""
                        )
                    else null
                }
                ?: emptyList()

            GlanceTheme {
                WidgetContent(
                    selectedTab = selectedTab,
                    isSyncing = isSyncing,
                    unclassified = (pendingTxns + unclassified).filter { it.id !in hiddenIds },
                    allTransactions = allTransactions,
                    mySummary = mySummary,
                    partnerSummary = partnerSummary,
                    lastSynced = lastSynced
                )
            }
        }
    }
}

@Composable
private fun WidgetContent(
    selectedTab: String,
    isSyncing: Boolean,
    unclassified: List<TransactionEntity>,
    allTransactions: List<TransactionEntity>,
    mySummary: MonthlySummaryEntity?,
    partnerSummary: PartnerSummaryEntity?,
    lastSynced: Long
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        TabRow(selectedTab = selectedTab, unclassifiedCount = unclassified.size, lastSynced = lastSynced, isSyncing = isSyncing)

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
                .padding(vertical = 4.dp)
        ) {}

        val contentModifier = GlanceModifier.defaultWeight().fillMaxWidth()
        when (selectedTab) {
            TAB_UNCLASSIFIED -> UnclassifiedContent(transactions = unclassified, modifier = contentModifier)
            TAB_ALL -> AllTransactionsContent(transactions = allTransactions, modifier = contentModifier)
            else -> SummaryContent(
                mySummary = mySummary,
                partnerSummary = partnerSummary,
                lastSynced = lastSynced,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun TabRow(selectedTab: String, unclassifiedCount: Int, lastSynced: Long, isSyncing: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabButton(
            label = if (unclassifiedCount > 0) "Todo ($unclassifiedCount)" else "Todo",
            isSelected = selectedTab == TAB_UNCLASSIFIED,
            tabValue = TAB_UNCLASSIFIED,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        TabButton(
            label = "All",
            isSelected = selectedTab == TAB_ALL,
            tabValue = TAB_ALL,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(4.dp))
        TabButton(
            label = "Summary",
            isSelected = selectedTab == TAB_SUMMARY,
            tabValue = TAB_SUMMARY,
            modifier = GlanceModifier.defaultWeight()
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .background(if (isSyncing) Color(0xFF3D2D6E) else Purple)
                .cornerRadius(6.dp)
                .clickable(actionRunCallback<WidgetSyncCallback>()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSyncing) "…" else "↻",
                style = TextStyle(
                    color = ColorProvider(if (isSyncing) Color(0xFF9A8DBF) else Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun TabButton(label: String, isSelected: Boolean, tabValue: String, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .background(if (isSelected) Purple else Color.Transparent)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(
                actionRunCallback<WidgetTabCallback>(
                    actionParametersOf(WidgetTabCallback.KEY_TAB to tabValue)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) ColorProvider(Color.White) else ColorProvider(PurpleLight),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
private fun UnclassifiedContent(transactions: List<TransactionEntity>, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
    if (transactions.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "All clear — no unclassified transactions",
                style = TextStyle(color = TextMuted, fontSize = 13.sp)
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(transactions) { txn ->
                UnclassifiedRow(txn = txn)
            }
        }
    }
}

@Composable
private fun AllTransactionsContent(transactions: List<TransactionEntity>, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
    if (transactions.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No transactions yet",
                style = TextStyle(color = TextMuted, fontSize = 13.sp)
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(transactions) { txn ->
                AllTransactionRow(txn = txn)
            }
        }
    }
}

@Composable
private fun AllTransactionRow(txn: TransactionEntity) {
    val tagColor = when (txn.tag) {
        "Personal" -> Green
        "Combined" -> Blue
        "Other" -> Orange
        else -> Purple
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight().padding(end = 4.dp)) {
            Text(
                text = "₹${"%.0f".format(txn.amount)}",
                style = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = txn.bankName,
                style = TextStyle(color = TextSecondary, fontSize = 11.sp),
                maxLines = 1
            )
        }
        Box(
            modifier = GlanceModifier
                .background(tagColor.copy(alpha = 0.30f))
                .cornerRadius(4.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = txn.tag,
                style = TextStyle(color = ColorProvider(tagColor), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun UnclassifiedRow(txn: TransactionEntity) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().padding(end = 4.dp)
        ) {
            Text(
                text = "₹${"%.0f".format(txn.amount)}",
                style = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = txn.bankName,
                style = TextStyle(color = TextSecondary, fontSize = 11.sp),
                maxLines = 1
            )
        }

        TagChip(label = "P", txnId = txn.id, tag = "Personal", tint = Green)
        Spacer(modifier = GlanceModifier.width(3.dp))
        TagChip(label = "C", txnId = txn.id, tag = "Combined", tint = Blue)
        Spacer(modifier = GlanceModifier.width(3.dp))
        TagChip(label = "O", txnId = txn.id, tag = "Other", tint = Orange)
        Spacer(modifier = GlanceModifier.width(3.dp))

        Box(
            modifier = GlanceModifier
                .size(26.dp)
                .background(RedSurface)
                .cornerRadius(4.dp)
                .clickable(
                    actionRunCallback<WidgetDiscardCallback>(
                        actionParametersOf(WidgetDiscardCallback.KEY_TXN_ID to txn.id)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                style = TextStyle(color = ColorProvider(Red), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun TagChip(label: String, txnId: String, tag: String, tint: Color) {
    Box(
        modifier = GlanceModifier
            .size(26.dp)
            .background(tint.copy(alpha = 0.30f))
            .cornerRadius(4.dp)
            .clickable(
                actionRunCallback<WidgetTagCallback>(
                    actionParametersOf(
                        WidgetTagCallback.KEY_TXN_ID to txnId,
                        WidgetTagCallback.KEY_TAG to tag
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(tint), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun SummaryContent(
    mySummary: MonthlySummaryEntity?,
    partnerSummary: PartnerSummaryEntity?,
    lastSynced: Long,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize()
) {
    val myCombined = mySummary?.combinedTotal ?: 0.0
    val partnerCombined = partnerSummary?.combinedTotal ?: 0.0
    val eachOwes = (myCombined + partnerCombined) / 2.0

    Column(modifier = modifier) {
        SummaryRow("Personal", mySummary?.personalTotal)
        SummaryRow("Combined", mySummary?.combinedTotal)
        SummaryRow("Other", mySummary?.otherTotal)
        SummaryRow("Total", mySummary?.grandTotal, bold = true)

        Spacer(modifier = GlanceModifier.height(4.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor)
        ) {}
        Spacer(modifier = GlanceModifier.height(4.dp))

        SummaryRow("Partner Combined", if (partnerSummary != null) partnerCombined else null)
        SummaryRow("Each owes", eachOwes, bold = true)

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(
            text = "Synced: ${DateUtils.formatLastSynced(lastSynced)}",
            style = TextStyle(color = TextMuted, fontSize = 10.sp)
        )
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double?, bold: Boolean = false) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (bold) TextPrimary else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            ),
            modifier = GlanceModifier.defaultWeight()
        )
        Text(
            text = if (amount != null) "₹${"%.0f".format(amount)}" else "—",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}
