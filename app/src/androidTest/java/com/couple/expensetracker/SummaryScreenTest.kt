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
class SummaryScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        composeRule.onNodeWithText("Summary").performClick()
    }

    @Test
    fun summaryScreen_showsMyExpensesSection() {
        composeRule.onNodeWithText("MY EXPENSES").assertExists()
    }

    @Test
    fun summaryScreen_showsPartnerSection() {
        composeRule.onNodeWithText("PARTNER EXPENSES (Combined only)").assertExists()
    }

    @Test
    fun summaryScreen_showsCombinedSplitSection() {
        composeRule.onNodeWithText("COMBINED SPLIT TOTAL").assertExists()
    }

    @Test
    fun summaryScreen_showsLastSyncedLabel() {
        composeRule.onNodeWithText("Last synced:", substring = true).assertExists()
    }

    @Test
    fun monthPicker_previousButton_isPresent() {
        composeRule.onNodeWithContentDescription("Previous month").assertExists()
    }

    @Test
    fun neverSynced_showsWarningText() {
        composeRule.onNodeWithText("⚠ Partner data never synced").assertExists()
    }
}
