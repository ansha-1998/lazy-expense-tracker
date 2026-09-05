package com.couple.expensetracker.ui.screens

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.couple.expensetracker.ui.viewmodel.SettingsViewModel
import com.couple.expensetracker.util.DateUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val PERSON_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
    Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF5722)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val myUsername by viewModel.myUsername.collectAsState()
    val sharedUsernames by viewModel.sharedUsernames.collectAsState()
    val sharePercentages by viewModel.sharePercentages.collectAsState()
    val driveFolderId by viewModel.driveFolderId.collectAsState()
    val lastSynced by viewModel.lastSynced.collectAsState()
    val googleEmail by viewModel.googleEmail.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val filterKeywords by viewModel.filterKeywords.collectAsState()
    val exclusionKeywords by viewModel.exclusionKeywords.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val notificationAccessGranted by viewModel.notificationAccessGranted.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshNotificationAccess() }

    var myUsernameText by remember(myUsername) { mutableStateOf(myUsername) }
    var folderLinkText by remember(driveFolderId) { mutableStateOf(driveFolderId) }
    var newKeywordText by remember { mutableStateOf("") }
    var newExclusionKeywordText by remember { mutableStateOf("") }
    var newCategoryText by remember { mutableStateOf("") }
    var newSharedPersonText by remember { mutableStateOf("") }
    var testSender by remember { mutableStateOf(SettingsViewModel.DEFAULT_TEST_SENDER) }
    var testMessage by remember { mutableStateOf(SettingsViewModel.DEFAULT_TEST_SMS) }

    val snackbarHostState = remember { SnackbarHostState() }

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope("https://www.googleapis.com/auth/drive"))
        .build()

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                account.email?.let { viewModel.setGoogleEmail(it) }
            } catch (e: Exception) { }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncMessage.collectLatest { msg -> snackbarHostState.showSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        viewModel.reAuthIntent.collectLatest { intent -> signInLauncher.launch(intent) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            // Google Account
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google Account", style = MaterialTheme.typography.titleSmall)
                    if (googleEmail.isNotBlank()) {
                        Text(googleEmail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (googleEmail.isBlank()) "Sign in with Google" else "Switch Account")
                    }
                }
            }

            // My Username
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("My Username", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = myUsernameText,
                        onValueChange = { myUsernameText = it },
                        label = { Text("My Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.saveMyUsername(myUsernameText) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save") }
                }
            }

            // Shared Persons
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shared Persons", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Add the usernames of people who share expenses with you. Their Drive files will be synced automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sharedUsernames.isEmpty()) {
                        Text(
                            "No shared persons added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        sharedUsernames.forEachIndexed { index, username ->
                            val dotColor = PERSON_COLORS[index % PERSON_COLORS.size]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                    Text(
                                        username,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dotColor
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeSharedPerson(username) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove $username",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newSharedPersonText,
                            onValueChange = { newSharedPersonText = it },
                            label = { Text("Username") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.addSharedPerson(newSharedPersonText)
                                newSharedPersonText = ""
                            },
                            enabled = newSharedPersonText.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            // Expense Split — shown only when there are shared persons
            if (sharedUsernames.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Expense Split", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Set how Combined expenses are split. Percentages must add up to 100%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Preset buttons — only relevant for exactly 1 shared person
                        if (sharedUsernames.size == 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.applyEqualSplit() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Equal") }
                                OutlinedButton(
                                    onClick = { viewModel.applyPresetSplit(60.0, 40.0) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("60 / 40") }
                                OutlinedButton(
                                    onClick = { viewModel.applyPresetSplit(70.0, 30.0) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("70 / 30") }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.applyEqualSplit() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Reset to Equal Split") }
                        }

                        Divider()

                        // Per-person percentage rows
                        val allPersons = listOf("me") + sharedUsernames
                        allPersons.forEachIndexed { index, username ->
                            val dotColor = if (username == "me") MaterialTheme.colorScheme.primary
                                           else PERSON_COLORS[(index - 1) % PERSON_COLORS.size]
                            val currentPct = sharePercentages[username] ?: 0.0
                            var pctText by remember(currentPct) {
                                mutableStateOf("%.1f".format(currentPct))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                    Text(
                                        text = if (username == "me") "Me" else username,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dotColor
                                    )
                                }
                                OutlinedTextField(
                                    value = pctText,
                                    onValueChange = { v ->
                                        pctText = v
                                        v.toDoubleOrNull()?.let { viewModel.updateSharePercentage(username, it) }
                                    },
                                    modifier = Modifier.width(100.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    suffix = { Text("%", style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }

                        // Total indicator
                        val totalPct = (listOf("me") + sharedUsernames)
                            .sumOf { sharePercentages[it] ?: 0.0 }
                        val isValid = abs(totalPct - 100.0) < 0.5
                        Surface(
                            color = if (isValid) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "Total: ${"%.1f".format(totalPct)}%${if (!isValid) " ⚠ must equal 100%" else " ✓"}",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isValid) Color(0xFF4CAF50)
                                        else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // SMS Filter Keywords
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SMS Filter Keywords", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "An incoming SMS must contain at least one of these words to be captured as a transaction.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        filterKeywords.sorted().forEach { keyword ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(keyword) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeFilterKeyword(keyword) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $keyword",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newKeywordText,
                            onValueChange = { newKeywordText = it },
                            label = { Text("Add keyword") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.addFilterKeyword(newKeywordText)
                                newKeywordText = ""
                            },
                            enabled = newKeywordText.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            // Exclusion Keywords
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SMS Exclusion Keywords", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "If an SMS contains any of these words it will be ignored, even if it matches an inclusion keyword.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        exclusionKeywords.sorted().forEach { keyword ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(keyword) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeExclusionKeyword(keyword) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $keyword",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newExclusionKeywordText,
                            onValueChange = { newExclusionKeywordText = it },
                            label = { Text("Add exclusion keyword") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.addExclusionKeyword(newExclusionKeywordText)
                                newExclusionKeywordText = ""
                            },
                            enabled = newExclusionKeywordText.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            // Expense Categories
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Expense Categories", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Categories available when tagging a transaction. Remove defaults or add your own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.sorted().forEach { cat ->
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text(cat) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removeCategory(cat) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove $cat",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newCategoryText,
                            onValueChange = { newCategoryText = it },
                            label = { Text("Add category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                autoCorrect = false
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.addCategory(newCategoryText)
                                newCategoryText = ""
                            },
                            enabled = newCategoryText.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            // Debug
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Debug", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Edit the sender and message below to test how they parse against your current filter keywords.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        label = { Text("Sender (e.g. ICICIB)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = testMessage,
                        onValueChange = { testMessage = it },
                        label = { Text("SMS body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                    Button(
                        onClick = { viewModel.sendTestTransaction(testSender, testMessage) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Parse & Add Test Transaction") }
                }
            }

            // RCS / Notification Access
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RCS & Push Notifications", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "SBI Cards and some other banks send styled RCS messages instead of plain SMS. " +
                        "Grant Notification Access so the app can capture those too. " +
                        "Regular SMS still works without this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (notificationAccessGranted) {
                        Text(
                            "Notification access granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Enable Notification Access") }
                        TextButton(
                            onClick = { viewModel.refreshNotificationAccess() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Already enabled? Tap to refresh") }
                    }
                }
            }

            // Google Drive Sync
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google Drive Sync", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Paste the link of your shared Drive folder. All shared persons need editor access to the same folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = folderLinkText,
                        onValueChange = { folderLinkText = it },
                        label = { Text("Shared Drive folder link or ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { viewModel.saveDriveFolderId(folderLinkText) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save") }
                    Button(
                        onClick = { viewModel.syncNow() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isOnline && !isSyncing
                    ) {
                        if (isSyncing) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Text("Syncing…")
                            }
                        } else {
                            Text("Sync Now")
                        }
                    }
                    Text(
                        text = "Last synced: ${DateUtils.formatLastSynced(lastSynced)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
