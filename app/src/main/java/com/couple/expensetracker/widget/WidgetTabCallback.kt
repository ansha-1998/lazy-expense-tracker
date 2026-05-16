package com.couple.expensetracker.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

class WidgetTabCallback : ActionCallback {

    companion object {
        val KEY_TAB = ActionParameters.Key<String>("tab_key")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val tab = parameters[KEY_TAB] ?: TAB_UNCLASSIFIED
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            prefs.toMutablePreferences().also { it[KEY_SELECTED_TAB] = tab }
        }
        ExpenseWidget().update(context, glanceId)
    }
}
