package com.couple.expensetracker.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    private val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
    private val displayFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val displayDateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    private val monthDisplayFmt = SimpleDateFormat("MMM yyyy", Locale.US)

    fun toMonthKey(timestamp: Long): String = monthKeyFmt.format(Date(timestamp))

    fun toDisplayDate(timestamp: Long): String = displayFmt.format(Date(timestamp))

    fun toDisplayDateTime(timestamp: Long): String = displayDateTimeFmt.format(Date(timestamp))

    fun toMonthDisplay(monthKey: String): String {
        return try {
            val date = monthKeyFmt.parse(monthKey) ?: return monthKey
            monthDisplayFmt.format(date)
        } catch (e: Exception) {
            monthKey
        }
    }

    fun currentMonthKey(): String = monthKeyFmt.format(Date())

    fun previousMonth(monthKey: String): String {
        return try {
            val cal = Calendar.getInstance()
            cal.time = monthKeyFmt.parse(monthKey) ?: return monthKey
            cal.add(Calendar.MONTH, -1)
            monthKeyFmt.format(cal.time)
        } catch (e: Exception) {
            monthKey
        }
    }

    fun nextMonth(monthKey: String): String {
        return try {
            val cal = Calendar.getInstance()
            cal.time = monthKeyFmt.parse(monthKey) ?: return monthKey
            cal.add(Calendar.MONTH, 1)
            monthKeyFmt.format(cal.time)
        } catch (e: Exception) {
            monthKey
        }
    }

    fun sixMonthsCutoff(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -6)
        return cal.timeInMillis
    }

    fun formatLastSynced(timestamp: Long): String {
        if (timestamp == 0L) return "Never"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000}m ago"
            diff < 86_400_000 -> "${diff / 3_600_000}h ago"
            else -> displayFmt.format(Date(timestamp))
        }
    }
}
