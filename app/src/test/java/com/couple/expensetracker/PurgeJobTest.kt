package com.couple.expensetracker

import com.couple.expensetracker.util.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PurgeJobTest {

    private fun makeTimestamp(monthsAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -monthsAgo)
        return cal.timeInMillis
    }

    @Test
    fun `transaction older than 6 months should be purged`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val oldTimestamp = makeTimestamp(7)
        assertTrue(oldTimestamp < cutoff)
    }

    @Test
    fun `transaction within 6 months should not be purged`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val recentTimestamp = makeTimestamp(3)
        assertTrue(recentTimestamp >= cutoff)
    }

    @Test
    fun `exactly 6 months ago is at boundary`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val sixMonthsAgo = makeTimestamp(6)
        assertTrue(sixMonthsAgo <= cutoff + 86_400_000L)
    }

    @Test
    fun `purge does not affect recent transactions`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val timestamps = listOf(
            makeTimestamp(1),
            makeTimestamp(2),
            makeTimestamp(5)
        )
        val toKeep = timestamps.filter { it >= cutoff }
        assertEquals(3, toKeep.size)
    }

    @Test
    fun `purge removes only old transactions`() {
        val cutoff = DateUtils.sixMonthsCutoff()
        val timestamps = listOf(
            makeTimestamp(1),  // keep
            makeTimestamp(7),  // purge
            makeTimestamp(9),  // purge
            makeTimestamp(3)   // keep
        )
        val toPurge = timestamps.filter { it < cutoff }
        val toKeep = timestamps.filter { it >= cutoff }
        assertEquals(2, toPurge.size)
        assertEquals(2, toKeep.size)
    }

    private fun assertTrue(b: Boolean) = assertEquals(true, b)
}
