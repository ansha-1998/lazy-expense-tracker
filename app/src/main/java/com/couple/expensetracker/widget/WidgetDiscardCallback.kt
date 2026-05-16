package com.couple.expensetracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class WidgetDiscardCallback : ActionCallback {

    companion object {
        val KEY_TXN_ID = ActionParameters.Key<String>("txn_id")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val txnId = parameters[KEY_TXN_ID] ?: return

        // Instantly hide the row — triggers immediate recompose before DB write completes
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[KEY_HIDDEN_IDS] ?: ""
            val updated = if (current.isBlank()) txnId else "$current,$txnId"
            prefs.toMutablePreferences().also { it[KEY_HIDDEN_IDS] = updated }
        }

        // Delete from DB then full refresh
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        ep.transactionRepository().discardTransaction(txnId)
        ExpenseWidget().update(context, glanceId)
    }
}
