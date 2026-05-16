package com.couple.expensetracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        val KEY_MY_USERNAME = stringPreferencesKey("my_username")
        val KEY_PARTNER_USERNAME = stringPreferencesKey("partner_username")
        val KEY_DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val KEY_LAST_SYNCED = longPreferencesKey("last_synced")
        val KEY_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val KEY_FILTER_KEYWORDS = stringPreferencesKey("filter_keywords")
        val KEY_EXCLUSION_KEYWORDS = stringPreferencesKey("exclusion_keywords")
        val KEY_PARTNER_TXN_FILE_MODIFIED_TIME = stringPreferencesKey("partner_txn_file_modified_time")
        val KEY_CATEGORIES = stringPreferencesKey("categories")

        val DEFAULT_CATEGORIES: Set<String> = setOf(
            "Grocery",
            "Entertainment",
            "Electronics & Gadgets",
            "Bills, EMIs & Subscriptions",
            "Household Utilities",
            "Food & Beverages",
            "Clothing & Fashion",
            "Transportation"
        )

        val DEFAULT_KEYWORDS: Set<String> = setOf(
            "sent", "spent", "paid", "debited", "used", "charged", "payment",
            "transfer", "transferred", "withdrawn", "withdrawal", "atm withdrawal",
            "purchase", "deducted", "mandate", "autopay", "auto-debit"
        )

        val DEFAULT_EXCLUSION_KEYWORDS: Set<String> = setOf("offer", "loan")
    }

    val myUsername: Flow<String> = context.dataStore.data.map { it[KEY_MY_USERNAME] ?: "" }
    val partnerUsername: Flow<String> = context.dataStore.data.map { it[KEY_PARTNER_USERNAME] ?: "" }
    val driveFolderId: Flow<String> = context.dataStore.data.map { it[KEY_DRIVE_FOLDER_ID] ?: "" }
    val lastSynced: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_SYNCED] ?: 0L }
    val googleAccountEmail: Flow<String> = context.dataStore.data.map { it[KEY_GOOGLE_ACCOUNT_EMAIL] ?: "" }
    val filterKeywords: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_FILTER_KEYWORDS]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: DEFAULT_KEYWORDS
    }

    val categories: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CATEGORIES]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: DEFAULT_CATEGORIES
    }

    val exclusionKeywords: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_EXCLUSION_KEYWORDS]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: DEFAULT_EXCLUSION_KEYWORDS
    }

    suspend fun setMyUsername(value: String) {
        context.dataStore.edit { it[KEY_MY_USERNAME] = value }
    }

    suspend fun setPartnerUsername(value: String) {
        context.dataStore.edit { it[KEY_PARTNER_USERNAME] = value }
    }

    suspend fun setDriveFolderId(value: String) {
        context.dataStore.edit { it[KEY_DRIVE_FOLDER_ID] = value }
    }

    suspend fun setLastSynced(value: Long) {
        context.dataStore.edit { it[KEY_LAST_SYNCED] = value }
    }

    suspend fun setGoogleAccountEmail(value: String) {
        context.dataStore.edit { it[KEY_GOOGLE_ACCOUNT_EMAIL] = value }
    }

    suspend fun getMyUsernameOnce(): String =
        context.dataStore.data.map { it[KEY_MY_USERNAME] ?: "" }.first()

    suspend fun getPartnerUsernameOnce(): String =
        context.dataStore.data.map { it[KEY_PARTNER_USERNAME] ?: "" }.first()

    suspend fun getDriveFolderIdOnce(): String =
        context.dataStore.data.map { it[KEY_DRIVE_FOLDER_ID] ?: "" }.first()

    suspend fun setFilterKeywords(keywords: Set<String>) {
        context.dataStore.edit { it[KEY_FILTER_KEYWORDS] = keywords.joinToString("|") }
    }

    suspend fun getFilterKeywordsOnce(): Set<String> = filterKeywords.first()

    suspend fun setExclusionKeywords(keywords: Set<String>) {
        context.dataStore.edit { it[KEY_EXCLUSION_KEYWORDS] = keywords.joinToString("|") }
    }

    suspend fun getExclusionKeywordsOnce(): Set<String> = exclusionKeywords.first()

    suspend fun setCategories(cats: Set<String>) {
        context.dataStore.edit { it[KEY_CATEGORIES] = cats.joinToString("|") }
    }

    suspend fun getCategoriesOnce(): Set<String> = categories.first()

    suspend fun setPartnerTxnFileModifiedTime(value: String) {
        context.dataStore.edit { it[KEY_PARTNER_TXN_FILE_MODIFIED_TIME] = value }
    }

    suspend fun getPartnerTxnFileModifiedTimeOnce(): String =
        context.dataStore.data.map { it[KEY_PARTNER_TXN_FILE_MODIFIED_TIME] ?: "" }.first()
}
