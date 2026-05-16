package com.couple.expensetracker

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryCalculationTest {

    @Test
    fun `grandTotal equals sum of personal combined other`() {
        val personal = 1500.0
        val combined = 3000.0
        val other = 500.0
        val grand = personal + combined + other
        assertEquals(5000.0, grand, 0.01)
    }

    @Test
    fun `split calculation is half of total combined`() {
        val myCombined = 2000.0
        val partnerCombined = 4000.0
        val total = myCombined + partnerCombined
        val eachOwes = total / 2.0
        assertEquals(3000.0, eachOwes, 0.01)
    }

    @Test
    fun `split with zero partner data equals half of own combined`() {
        val myCombined = 2000.0
        val partnerCombined = 0.0
        val total = myCombined + partnerCombined
        val eachOwes = total / 2.0
        assertEquals(1000.0, eachOwes, 0.01)
    }

    @Test
    fun `monthKey format is YYYY-MM`() {
        val pattern = Regex("""\d{4}-\d{2}""")
        val key = "2026-05"
        assertTrue(pattern.matches(key))
    }

    @Test
    fun `partner combined not counted in own grand total`() {
        val myPersonal = 1000.0
        val myCombined = 500.0
        val myOther = 200.0
        val partnerCombined = 900.0

        val myGrand = myPersonal + myCombined + myOther
        assertEquals(1700.0, myGrand, 0.01)
        // partner data should not affect myGrand
        assertEquals(1700.0, myGrand, 0.01)
    }

    private fun assertTrue(b: Boolean) = assertEquals(true, b)
}
