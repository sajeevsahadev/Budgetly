package com.budgetly.utils

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class CurrencyFormatterTest {

    @Test
    fun `format zero amount`() {
        val result = CurrencyFormatter.formatCompact(0.0)
        assertEquals("₹0", result)
    }

    @Test
    fun `format amount under 1000`() {
        val result = CurrencyFormatter.formatCompact(750.0)
        assertEquals("₹750", result)
    }

    @Test
    fun `format amount in thousands`() {
        val result = CurrencyFormatter.formatCompact(5500.0)
        assertEquals("₹5.5K", result)
    }

    @Test
    fun `format amount in lakhs`() {
        val result = CurrencyFormatter.formatCompact(150000.0)
        assertEquals("₹1.5L", result)
    }

    @Test
    fun `format exactly 1 lakh`() {
        val result = CurrencyFormatter.formatCompact(100000.0)
        assertEquals("₹1.0L", result)
    }

    @Test
    fun `format exactly 1000`() {
        val result = CurrencyFormatter.formatCompact(1000.0)
        assertEquals("₹1.0K", result)
    }
}

class DateUtilsTest {

    @Test
    fun `startOfMonth returns first day at midnight`() {
        val start = DateUtils.startOfMonth(1, 2024)
        val cal = Calendar.getInstance().apply { time = start }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.MONTH)) // January = 0
        assertEquals(2024, cal.get(Calendar.YEAR))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `endOfMonth returns last day at 23:59:59`() {
        val end = DateUtils.endOfMonth(1, 2024) // January 2024
        val cal = Calendar.getInstance().apply { time = end }
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `endOfMonth handles February correctly`() {
        val end = DateUtils.endOfMonth(2, 2024) // Feb 2024 (leap year)
        val cal = Calendar.getInstance().apply { time = end }
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH)) // 2024 is leap year
    }

    @Test
    fun `endOfMonth handles non-leap February`() {
        val end = DateUtils.endOfMonth(2, 2023) // Feb 2023
        val cal = Calendar.getInstance().apply { time = end }
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `daysUntil future date is positive`() {
        val future = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }.time
        val days = DateUtils.daysUntil(future)
        assertTrue("Days until future should be positive", days > 0)
        assertTrue("Should be roughly 7 days", days in 6..8)
    }

    @Test
    fun `daysUntil past date is negative`() {
        val past = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -3)
        }.time
        val days = DateUtils.daysUntil(past)
        assertTrue("Days until past should be negative", days < 0)
    }

    @Test
    fun `isOverdue returns true for past date`() {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.time
        assertTrue(DateUtils.isOverdue(yesterday))
    }

    @Test
    fun `isOverdue returns false for future date`() {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        assertFalse(DateUtils.isOverdue(tomorrow))
    }

    @Test
    fun `month names array has 12 entries`() {
        assertEquals(12, DateUtils.MONTH_NAMES.size)
        assertEquals("Jan", DateUtils.MONTH_NAMES[0])
        assertEquals("Dec", DateUtils.MONTH_NAMES[11])
    }
}
