package com.example.os_app.screens

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptDefaultsAndTotalsTest {
    @Test
    fun discountDefaultsToZero() {
        assertEquals("0", DEFAULT_DISCOUNT_DIGITS)
    }

    @Test
    fun receiptDateUsesCurrentLocalDateFormat() {
        val today = LocalDate.now()
        val expected = "${today.dayOfMonth}.${today.monthValue}.${today.year}"

        assertEquals(expected, formatReceiptDate())
    }

    @Test
    fun receiptDateFormatterDoesNotHardcodeADate() {
        assertEquals("5.11.2026", formatReceiptDate(LocalDate.of(2026, 11, 5)))
        assertEquals("9.2.2027", formatReceiptDate(LocalDate.of(2027, 2, 9)))
    }

    @Test
    fun zeroDiscountLeavesSubtotalUnchanged() {
        val receipt = receipt(discount = 0, delivery = 0)

        assertEquals(100L, receipt.subtotal)
        assertEquals(100L, receipt.total)
    }

    @Test
    fun manuallyEnteredDiscountIsSubtractedOnceAndDeliveryAdded() {
        val receipt = receipt(discount = 10, delivery = 5)

        assertEquals(100L, receipt.subtotal)
        assertEquals(95L, receipt.total)
    }

    private fun receipt(discount: Long, delivery: Long) = ReceiptPrintData(
        customer = "Test",
        phone = "",
        date = "",
        address = "",
        items = listOf(ReceiptPrintLine("Item", quantity = 2, unitPrice = 50)),
        discount = discount,
        delivery = delivery
    )
}
