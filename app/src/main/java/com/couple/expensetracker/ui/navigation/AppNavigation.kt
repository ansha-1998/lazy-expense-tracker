package com.couple.expensetracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.*
import androidx.navigation.compose.*
import com.couple.expensetracker.ui.screens.*

sealed class Screen(val route: String) {
    object Unclassified : Screen("unclassified")
    object Transactions : Screen("transactions")
    object Summary : Screen("summary")
    object Settings : Screen("settings")
    object ManualAdd : Screen("manual_add")
    object DiscardConfirm : Screen("discard_confirm/{txnId}/{amount}/{bankName}/{last4OrRef}") {
        fun createRoute(txnId: String, amount: Double, bankName: String, last4OrRef: String) =
            "discard_confirm/$txnId/$amount/${bankName.ifBlank { "_" }}/${last4OrRef.ifBlank { "_" }}"
    }
}

data class BottomNavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Unclassified, "Unclassified", Icons.Default.Inbox),
    BottomNavItem(Screen.Transactions, "Transactions", Icons.Default.List),
    BottomNavItem(Screen.Summary, "Summary", Icons.Default.BarChart)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    startRoute: String = Screen.Unclassified.route,
    externalNavEvent: ExternalNavEvent? = null,
    onNavEventConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    LaunchedEffect(externalNavEvent) {
        externalNavEvent?.let { event ->
            when (event) {
                is ExternalNavEvent.OpenDiscardConfirm -> {
                    navController.navigate(
                        Screen.DiscardConfirm.createRoute(
                            event.txnId, event.amount, event.bankName, event.last4OrRef
                        )
                    )
                    onNavEventConsumed()
                }
                is ExternalNavEvent.OpenUnclassified -> {
                    navController.navigate(Screen.Unclassified.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = false }
                        launchSingleTop = true
                    }
                    onNavEventConsumed()
                }
            }
        }
    }

    val showBottomBar = currentRoute in listOf(
        Screen.Unclassified.route,
        Screen.Transactions.route,
        Screen.Summary.route
    )

    Scaffold(
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = { Text("Expense Tracker") },
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            Icon(Icons.Default.Settings, "Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                navController.navigate(item.screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Unclassified.route) { UnclassifiedScreen() }
            composable(Screen.Transactions.route) {
                TransactionsScreen(onNavigateToAdd = { navController.navigate(Screen.ManualAdd.route) })
            }
            composable(Screen.Summary.route) { SummaryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.ManualAdd.route) {
                ManualAddScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(
                route = Screen.DiscardConfirm.route,
                arguments = listOf(
                    navArgument("txnId") { type = NavType.StringType },
                    navArgument("amount") { type = NavType.FloatType },
                    navArgument("bankName") { type = NavType.StringType },
                    navArgument("last4OrRef") { type = NavType.StringType }
                )
            ) { backStack ->
                val txnId = backStack.arguments?.getString("txnId") ?: ""
                val amount = backStack.arguments?.getFloat("amount")?.toDouble() ?: 0.0
                val bankName = backStack.arguments?.getString("bankName")?.let { if (it == "_") "" else it } ?: ""
                val last4 = backStack.arguments?.getString("last4OrRef")?.let { if (it == "_") "" else it } ?: ""
                DiscardConfirmationScreen(
                    transactionId = txnId,
                    amount = amount,
                    bankName = bankName,
                    last4OrRef = last4,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

sealed class ExternalNavEvent {
    data class OpenDiscardConfirm(
        val txnId: String,
        val amount: Double,
        val bankName: String,
        val last4OrRef: String
    ) : ExternalNavEvent()

    object OpenUnclassified : ExternalNavEvent()
}
