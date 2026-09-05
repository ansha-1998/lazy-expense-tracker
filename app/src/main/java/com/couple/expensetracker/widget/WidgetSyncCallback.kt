package com.couple.expensetracker.widget

import android.accounts.Account
import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class WidgetSyncCallback : ActionCallback {

    companion object {
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive"
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val ep = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java
        )
        val prefs = ep.appPreferences()
        val driveSync = ep.driveSync()
        val connectivity = ep.connectivityObserver()

        if (!connectivity.isConnected.value) return

        val email           = prefs.googleAccountEmail.first().ifBlank { return }
        val folderId        = prefs.driveFolderId.first().ifBlank { return }
        val myUsername      = prefs.myUsername.first().ifBlank { return }
        val sharedUsernames = prefs.getSharedUsernamesOnce()

        val token = withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.getToken(context, Account(email, "com.google"), DRIVE_SCOPE)
            } catch (e: UserRecoverableAuthException) {
                // Cannot start re-auth from widget; user must open the app
                ""
            } catch (e: Exception) {
                ""
            }
        }
        if (token.isBlank()) return

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
            p.toMutablePreferences().also { it[KEY_SYNCING] = true }
        }
        ExpenseWidget().update(context, glanceId)  // push loading state to screen before sync starts
        try {
            driveSync.sync(token, folderId, myUsername, sharedUsernames)
        } finally {
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { p ->
                p.toMutablePreferences().also { it[KEY_SYNCING] = false }
            }
            ExpenseWidget().update(context, glanceId)
        }
    }
}
