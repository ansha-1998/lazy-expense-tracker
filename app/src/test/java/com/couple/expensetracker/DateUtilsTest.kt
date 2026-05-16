package com.couple.expensetracker

import com.couple.expensetracker.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class DateUtilsTest {

    @Test
    fun `toMonthKey returns YYYY-MM format`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MAY, 8)
        val key = DateUtils.toMonthKey(cal.timeInMillis)
        assertEquals("2026-05", key)
    }

    @Test
    fun `toDisplayDate returns DD MMM YYYY format`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MAY, 8)
        val display = DateUtils.toDisplayDate(cal.timeInMillis)
        assertEquals("08 May 2026", display)
    }

    @Test
    fun `toMonthDisplay returns MMM YYYY`() {
        val display = DateUtils.toMonthDisplay("2026-05")
        assertEquals("May 2026", display)
    }

    @Test
    fun `previousMonth goes back one month`() {
        assertEquals("2026-04", DateUtils.previousMonth("2026-05"))
        assertEquals("2025-12", DateUtils.previousMonth("2026-01"))
    }

    @Test
    fun `nextMonth goes forward one month`() {
        assertEquals("2026-06", DateUtils.nextMonth("2026-05"))
        assertEquals("2026-01", DateUtils.nextMonth("2025-12"))
    }

    @Test
    fun `currentMonthKey matches expected format`() {
        val key = DateUtils.currentMonthKey()
        val pattern = Regex("""\d{4}-\d{2}""")
        assertTrue(pattern.matches(key))
    }

    @Test
    fun `sixMonthsCutoff is 6 months in the past`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val now = System.currentTimeMillis()
        val sixMonthsMs = 6L * 30 * 24 * 60 * 60 * 1000
        assertTrue(cutoff > now - sixMonthsMs - 86_400_000L)
        assertTrue(cutoff < now)
    }

    @Test
    fun `formatLastSynced returns Never for zero`() {
        assertEquals("Never", DateUtils.formatLastSynced(0L))
    }

    private fun assertTrue(b: Boolean) = assertEquals(true, b)
}
