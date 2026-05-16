package com.couple.expensetracker

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UnclassifiedScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun emptyState_showsNoTransactionsText() {
        composeRule.onNodeWithText("No unclassified transactions").assertExists()
    }

    @Test
    fun unclassifiedTab_isVisibleOnLaunch() {
        composeRule.onNodeWithText("Unclassified").assertExists()
    }

    @Test
    fun bottomNavigation_hasThreeTabs() {
        composeRule.onNodeWithText("Transactions").assertExists()
        composeRule.onNodeWithText("Summary").assertExists()
    }

    @Test
    fun navigateToTransactions_showsFilterBar() {
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.onNodeWithText("All").assertExists()
    }

    @Test
    fun navigateToSummary_showsMonthDisplay() {
        composeRule.onNodeWithText("Summary").performClick()
        composeRule.onNodeWithText("MY EXPENSES").assertExists()
    }
}
