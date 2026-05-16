package com.couple.expensetracker.ui.viewmodel

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.couple.expensetracker.data.db.entities.TransactionEntity
import com.couple.expensetracker.data.preferences.AppPreferences
import com.couple.expensetracker.data.repository.TransactionRepository
import com.couple.expensetracker.data.sync.DriveSync
import com.couple.expensetracker.notification.NotificationHelper
import com.couple.expensetracker.util.ConnectivityObserver
import com.couple.expensetracker.util.SMSParser
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val driveSync: DriveSync,
    private val connectivityObserver: ConnectivityObserver,
    private val repository: TransactionRepository,
    private val notificationHelper: NotificationHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive"
        const val DEFAULT_TEST_SENDER = "ICICIB"
        const val DEFAULT_TEST_SMS =
            "Your ICICI Bank Credit Card ending 8000 has been debited for Rs. 179.00 on 09-May-26. If not done by you, call 1800-xxx-xxxx."

        // Invisible Unicode code points that Android keyboards can silently insert
        private val INVISIBLE_CHARS = setOf(
            0x200B, // zero-width space
            0x200C, // zero-width non-joiner
            0x200D, // zero-width joiner
            0xFEFF, // byte-order mark / zero-width no-break space
            0x00AD  // soft hyphen
        )
    }

    val myUsername: StateFlow<String> = prefs.myUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val partnerUsername: StateFlow<String> = prefs.partnerUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val driveFolderId: StateFlow<String> = prefs.driveFolderId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val lastSynced: StateFlow<Long> = prefs.lastSynced
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val googleEmail: StateFlow<String> = prefs.googleAccountEmail
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val filterKeywords: StateFlow<Set<String>> = prefs.filterKeywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_KEYWORDS)

    val exclusionKeywords: StateFlow<Set<String>> = prefs.exclusionKeywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_EXCLUSION_KEYWORDS)

    val categories: StateFlow<Set<String>> = prefs.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPreferences.DEFAULT_CATEGORIES)

    val isOnline: StateFlow<Boolean> = connectivityObserver.isConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _syncMessage = MutableSharedFlow<String>()
    val syncMessage: SharedFlow<String> = _syncMessage

    private val _reAuthIntent = MutableSharedFlow<Intent>()
    val reAuthIntent: SharedFlow<Intent> = _reAuthIntent

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    init {
        refreshNotificationAccess()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            repository.autoSyncTrigger
                .debounce(60_000L)
                .collect { triggerSync(manual = false) }
        }
    }

    fun refreshNotificationAccess() {
        val enabled = AndroidSettings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: ""
        val component = "${context.packageName}/com.couple.expensetracker.receiver.BankNotificationListener"
        _notificationAccessGranted.value = enabled.split(":").any { it == component }
    }

    fun saveMyUsername(value: String) {
        viewModelScope.launch { prefs.setMyUsername(value) }
    }

    fun savePartnerUsername(value: String) {
        viewModelScope.launch { prefs.setPartnerUsername(value) }
    }

    fun saveDriveFolderId(value: String) {
        viewModelScope.launch { prefs.setDriveFolderId(extractFolderId(value)) }
    }

    fun setGoogleEmail(email: String) {
        viewModelScope.launch { prefs.setGoogleAccountEmail(email) }
    }

    fun addFilterKeyword(word: String) {
        val trimmed = word
            .filter { it.code !in INVISIBLE_CHARS }
            .replace(' ', ' ')  // non-breaking space → regular space
            .trim()
            .lowercase()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = prefs.getFilterKeywordsOnce()
            prefs.setFilterKeywords(current + trimmed)
        }
    }

    fun removeFilterKeyword(word: String) {
        viewModelScope.launch {
            val current = prefs.getFilterKeywordsOnce()
            prefs.setFilterKeywords(current - word)
        }
    }

    fun addExclusionKeyword(word: String) {
        val trimmed = word
            .filter { it.code !in INVISIBLE_CHARS }
            .replace(' ', ' ')
            .trim()
            .lowercase()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = prefs.getExclusionKeywordsOnce()
            prefs.setExclusionKeywords(current + trimmed)
        }
    }

    fun removeExclusionKeyword(word: String) {
        viewModelScope.launch {
            val current = prefs.getExclusionKeywordsOnce()
            prefs.setExclusionKeywords(current - word)
        }
    }

    fun addCategory(name: String) {
        val trimmed = name
            .filter { it.code !in INVISIBLE_CHARS }
            .trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val current = prefs.getCategoriesOnce()
            prefs.setCategories(current + trimmed)
        }
    }

    fun removeCategory(name: String) {
        viewModelScope.launch {
            val current = prefs.getCategoriesOnce()
            prefs.setCategories(current - name)
        }
    }

    fun sendTestTransaction(sender: String = DEFAULT_TEST_SENDER, body: String = DEFAULT_TEST_SMS) {
        viewModelScope.launch {
            val keywords = prefs.getFilterKeywordsOnce()
            val exclusions = prefs.getExclusionKeywordsOnce()
            val parsed = SMSParser.parse(sender, body, keywords, exclusions)
            if (parsed == null) {
                _syncMessage.emit("Message did not match — check keywords or amount format")
                return@launch
            }
            val username = prefs.myUsername.first().ifBlank { "me" }
            val txn = TransactionEntity(
                id = UUID.randomUUID().toString(),
                date = System.currentTimeMillis(),
                amount = parsed.amount,
                paymentType = parsed.paymentType,
                bankName = parsed.bankName,
                last4OrRef = parsed.last4OrRef,
                tag = "Unclassified",
                addedBy = username,
                source = "SMS",
                lastModified = System.currentTimeMillis(),
                syncStatus = "PENDING",
                rawMessage = body
            )
            val added = repository.addTransaction(txn)
            if (added) {
                notificationHelper.showTransactionNotification(txn)
                _syncMessage.emit("Test added: ₹${parsed.amount} · ${parsed.bankName} · ${parsed.paymentType}")
            } else {
                _syncMessage.emit("Duplicate — transaction already exists")
            }
        }
    }

    fun syncNow() {
        if (!connectivityObserver.isConnected.value) {
            viewModelScope.launch { _syncMessage.emit("No internet connection") }
            return
        }
        triggerSync(manual = true)
    }

    // Accepts either a full Drive share URL or a bare folder ID
    private fun extractFolderId(input: String): String {
        val match = Regex("folders/([a-zA-Z0-9_-]+)").find(input)
        return match?.groupValues?.get(1) ?: input.trim()
    }

    private fun triggerSync(manual: Boolean = false) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            val folderId = prefs.driveFolderId.first()
            val myUsername = prefs.myUsername.first()
            val partnerUsername = prefs.partnerUsername.first()
            val email = prefs.googleAccountEmail.first()
            if (folderId.isBlank() || myUsername.isBlank() || email.isBlank()) return@launch

            _isSyncing.value = true
            try {
                val token = withContext(Dispatchers.IO) {
                    try {
                        GoogleAuthUtil.getToken(context, Account(email, "com.google"), DRIVE_SCOPE)
                    } catch (e: UserRecoverableAuthException) {
                        e.intent?.let { _reAuthIntent.emit(it) }
                        ""
                    } catch (e: Exception) { "" }
                }
                if (token.isBlank()) {
                    if (manual) _syncMessage.emit("Drive permission needed — please re-sign in")
                    return@launch
                }

                val success = driveSync.sync(token, folderId, myUsername, partnerUsername, fullSync = manual)
                if (manual) {
                    _syncMessage.emit(if (success) "Synced successfully" else "Sync failed. Will retry automatically.")
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
