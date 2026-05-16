package com.couple.expensetracker

import com.couple.expensetracker.util.SMSParser
import org.junit.Assert.*
import org.junit.Test

class SMSParserTest {

    @Test
    fun `HDFC UPI debit SMS parsed correctly`() {
        val body = "Dear Customer, Rs.2500.00 debited from your HDFC Bank Account XX1234 for UPI ref 412345678901 on 08-05-2026."
        val result = SMSParser.parse("HDFCBK", body)
        assertNotNull(result)
        assertEquals(2500.00, result!!.amount, 0.01)
        assertEquals("UPI", result.paymentType)
        assertEquals("HDFC", result.bankName)
        assertEquals("412345678901", result.last4OrRef)
    }

    @Test
    fun `ICICI card SMS parsed correctly`() {
        val body = "ICICI Bank: INR 1,200.50 spent on credit card ending 5678 at AMAZON on 08-MAY-2026."
        val result = SMSParser.parse("ICICIB", body)
        assertNotNull(result)
        assertEquals(1200.50, result!!.amount, 0.01)
        assertEquals("Card", result.paymentType)
        assertEquals("ICICI", result.bankName)
        assertEquals("5678", result.last4OrRef)
    }

    @Test
    fun `SBI card XX pattern parsed correctly`() {
        val body = "SBI Credit Card XX9876 has been used for Rs 500 at SWIGGY on 08/05/2026."
        val result = SMSParser.parse("SBIINB", body)
        assertNotNull(result)
        assertEquals(500.0, result!!.amount, 0.01)
        assertEquals("Card", result.paymentType)
        assertEquals("SBI", result.bankName)
        assertEquals("9876", result.last4OrRef)
    }

    @Test
    fun `Axis UPI payment SMS parsed correctly`() {
        val body = "Your Axis Bank account has been debited with Rs.750.00 by UPI 987654321098 on 08-05-2026."
        val result = SMSParser.parse("AXISBK", body)
        assertNotNull(result)
        assertEquals(750.0, result!!.amount, 0.01)
        assertEquals("UPI", result.paymentType)
        assertEquals("Axis", result.bankName)
    }

    @Test
    fun `Kotak rupee symbol SMS parsed correctly`() {
        val body = "Kotak Bank: ₹3000 debited for UPI ref 111222333444 on 08-May-26."
        val result = SMSParser.parse("KOTAKB", body)
        assertNotNull(result)
        assertEquals(3000.0, result!!.amount, 0.01)
        assertEquals("UPI", result.paymentType)
        assertEquals("Kotak", result.bankName)
    }

    @Test
    fun `SMS without amount returns null`() {
        val body = "Your OTP is 123456. Valid for 10 minutes. Do not share."
        val result = SMSParser.parse("HDFCBK", body)
        assertNull(result)
    }

    @Test
    fun `Malformed SMS returns null`() {
        val body = "Congratulations! You won a prize."
        val result = SMSParser.parse("UNKNOWN", body)
        assertNull(result)
    }

    @Test
    fun `Amount with commas parsed correctly`() {
        val body = "Rs.1,50,000 debited from your account via UPI ref 999888777666."
        val result = SMSParser.parse("HDFCBK", body)
        assertNotNull(result)
        assertEquals(150000.0, result!!.amount, 0.01)
    }

    @Test
    fun `Unknown sender returns raw sender prefix as bank name`() {
        val body = "Rs.500 debited via UPI ref 123456789012."
        val result = SMSParser.parse("UNKNOWNBK", body)
        assertNotNull(result)
        assertEquals("UNKNOWNBK", result!!.bankName.take(9))
    }

    @Test
    fun `Bank name mapping is case-insensitive for sender`() {
        val bankName = SMSParser.parseBankName("hdfcbk")
        assertEquals("HDFC", bankName)
    }

    @Test
    fun `INR prefix parsed correctly`() {
        val body = "INR 899.00 debited from account for NETFLIX via card ending 1234."
        val result = SMSParser.parse("ICICIB", body)
        assertNotNull(result)
        assertEquals(899.0, result!!.amount, 0.01)
        assertEquals("Card", result.paymentType)
    }
}
