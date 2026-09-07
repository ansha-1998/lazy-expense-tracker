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
        val KEY_SHARED_USERNAMES = stringPreferencesKey("shared_usernames")
        val KEY_SHARE_PERCENTAGES = stringPreferencesKey("share_percentages")
        val KEY_DRIVE_FOLDER_ID = stringPreferencesKey("drive_folder_id")
        val KEY_LAST_SYNCED = longPreferencesKey("last_synced")
        val KEY_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val KEY_FILTER_KEYWORDS = stringPreferencesKey("filter_keywords")
        val KEY_EXCLUSION_KEYWORDS = stringPreferencesKey("exclusion_keywords")
        val KEY_PARTNER_TXN_FILE_MODIFIED_TIME = stringPreferencesKey("partner_txn_file_modified_time")
        val KEY_CATEGORIES = stringPreferencesKey("categories")
        val KEY_AUTO_SYNC_TIME_MINUTES = intPreferencesKey("auto_sync_time_minutes")

        // Default to noon until the user picks their own time in Settings — each partner should
        // choose a different time to avoid both devices hitting Drive at the same moment.
        const val DEFAULT_AUTO_SYNC_TIME_MINUTES = 12 * 60

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

    // Minutes since midnight (0-1439) — when the daily background auto-sync should run.
    val autoSyncTimeMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_AUTO_SYNC_TIME_MINUTES] ?: DEFAULT_AUTO_SYNC_TIME_MINUTES
    }

    // Shared usernames list — migrates legacy single partner_username on first read
    val sharedUsernames: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val stored = prefs[KEY_SHARED_USERNAMES]
        if (stored != null) {
            stored.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            val legacy = prefs[KEY_PARTNER_USERNAME] ?: ""
            if (legacy.isNotBlank()) listOf(legacy) else emptyList()
        }
    }

    // Share percentages: "me:50.0|john:30.0|jane:20.0"
    val sharePercentages: Flow<Map<String, Double>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHARE_PERCENTAGES]
            ?.split("|")
            ?.mapNotNull { pair ->
                val idx = pair.lastIndexOf(':')
                if (idx > 0) pair.substring(0, idx) to (pair.substring(idx + 1).toDoubleOrNull() ?: 0.0)
                else null
            }
            ?.toMap()
            ?: emptyMap()
    }

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

    suspend fun setSharedUsernames(usernames: List<String>) {
        context.dataStore.edit { it[KEY_SHARED_USERNAMES] = usernames.joinToString("|") }
    }

    suspend fun setSharePercentages(percentages: Map<String, Double>) {
        context.dataStore.edit {
            it[KEY_SHARE_PERCENTAGES] = percentages.entries.joinToString("|") { (k, v) -> "$k:$v" }
        }
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

    suspend fun setAutoSyncTimeMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_AUTO_SYNC_TIME_MINUTES] = minutes }
    }

    suspend fun getAutoSyncTimeMinutesOnce(): Int = autoSyncTimeMinutes.first()

    suspend fun getMyUsernameOnce(): String =
        context.dataStore.data.map { it[KEY_MY_USERNAME] ?: "" }.first()

    suspend fun getPartnerUsernameOnce(): String =
        context.dataStore.data.map { it[KEY_PARTNER_USERNAME] ?: "" }.first()

    suspend fun getSharedUsernamesOnce(): List<String> = sharedUsernames.first()

    suspend fun getSharePercentagesOnce(): Map<String, Double> = sharePercentages.first()

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

    // Per-shared-person modified time tracking
    suspend fun getSharedTxnFileModifiedTimeOnce(username: String): String =
        context.dataStore.data.map { it[stringPreferencesKey("shared_txn_modified_$username")] ?: "" }.first()

    suspend fun setSharedTxnFileModifiedTime(username: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey("shared_txn_modified_$username")] = value }
    }

    suspend fun setPartnerTxnFileModifiedTime(value: String) {
        context.dataStore.edit { it[KEY_PARTNER_TXN_FILE_MODIFIED_TIME] = value }
    }

    suspend fun getPartnerTxnFileModifiedTimeOnce(): String =
        context.dataStore.data.map { it[KEY_PARTNER_TXN_FILE_MODIFIED_TIME] ?: "" }.first()
}
