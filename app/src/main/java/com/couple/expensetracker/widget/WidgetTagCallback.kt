package com.couple.expensetracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.hilt.android.EntryPointAccessors

class WidgetTagCallback : ActionCallback {

    companion object {
        val KEY_TXN_ID = ActionParameters.Key<String>("txn_id")
        val KEY_TAG    = ActionParameters.Key<String>("tag")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val txnId = parameters[KEY_TXN_ID] ?: return
        val tag   = parameters[KEY_TAG]    ?: return

        // Instantly hide the row by adding to the hidden set — triggers immediate recompose
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val current = prefs[KEY_HIDDEN_IDS] ?: ""
            val updated = if (current.isBlank()) txnId else "$current,$txnId"
            prefs.toMutablePreferences().also { it[KEY_HIDDEN_IDS] = updated }
        }

        // Write to DB then do a full refresh to load correct state
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        ep.transactionRepository().updateTag(txnId, tag)
        ExpenseWidget().update(context, glanceId)
    }
}
