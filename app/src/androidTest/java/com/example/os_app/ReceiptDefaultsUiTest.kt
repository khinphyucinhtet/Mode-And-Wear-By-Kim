package com.example.os_app

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.os_app.screens.formatReceiptDate
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptDefaultsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun freshReceiptUsesZeroDiscountAndCurrentDateAndKeepsEditsUntilReopened() {
        openReceipts()
        assertFreshReceiptDefaults()

        composeRule.onNodeWithContentDescription("Discount amount").performTextReplacement("10")
        composeRule.onNodeWithContentDescription("Customer name").performTextInput("Test customer")
        composeRule.onNodeWithContentDescription("Discount amount").assertTextEquals("10")

        composeRule.onNodeWithContentDescription("Back to home").performClick()
        openReceipts()
        assertFreshReceiptDefaults()
    }

    private fun openReceipts() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Receipts").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Receipts").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Discount").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertFreshReceiptDefaults() {
        composeRule.onNodeWithContentDescription("Discount amount").assertTextEquals("0")
        composeRule.onNodeWithContentDescription("Receipt date")
            .assertTextEquals(formatReceiptDate(LocalDate.now()))
    }
}
