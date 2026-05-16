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
class ManualAddScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    private fun navigateToManualAdd() {
        composeRule.onNodeWithText("Transactions").performClick()
        composeRule.onNodeWithContentDescription("Add transaction").performClick()
    }

    @Test
    fun manualAddScreen_hasRequiredFields() {
        navigateToManualAdd()
        composeRule.onNodeWithText("Amount (₹)").assertExists()
        composeRule.onNodeWithText("Bank Name").assertExists()
        composeRule.onNodeWithText("Tag").assertExists()
    }

    @Test
    fun save_withEmptyAmount_showsValidationError() {
        navigateToManualAdd()
        composeRule.onNodeWithText("Bank Name").performTextInput("HDFC")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Enter a valid amount").assertExists()
    }

    @Test
    fun save_withEmptyBankName_showsValidationError() {
        navigateToManualAdd()
        composeRule.onNodeWithText("Amount (₹)").performTextInput("500")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Enter bank name").assertExists()
    }

    @Test
    fun cancel_navigatesBack() {
        navigateToManualAdd()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Transactions").assertExists()
    }
}
