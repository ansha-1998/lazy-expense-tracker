package com.couple.expensetracker.util

import java.text.Normalizer

object SMSParser {

    data class ParsedSMS(
        val amount: Double,
        val paymentType: String,
        val bankName: String,
        val last4OrRef: String
    )

    private val BANK_SENDER_MAP = mapOf(
        "HDFCBK" to "HDFC",
        "HDFC" to "HDFC",
        "SBIINB" to "SBI",
        "SBIPSG" to "SBI",
        "CBSSBI" to "SBI",
        "SBIUPI" to "SBI",
        "SBICRD" to "SBI",
        "ICICIB" to "ICICI",
        "ICICI" to "ICICI",
        "AXISBK" to "Axis",
        "AXISNB" to "Axis",
        "KOTAKB" to "Kotak",
        "KOTAK" to "Kotak",
        "IDFCFB" to "IDFC First",
        "YESBNK" to "Yes Bank",
        "YESBK" to "Yes Bank",
        "PAYTMB" to "Paytm",
        "PYTMBN" to "Paytm",
        "INDBNK" to "Indian Bank",
        "PNBSMS" to "PNB",
        "PNBSMG" to "PNB",
        "BOIIND" to "Bank of India",
        "CANBNK" to "Canara Bank",
        "CENTBK" to "Central Bank",
        "UNIONB" to "Union Bank",
        "SCBANK" to "Standard Chartered",
        "SCBNK" to "Standard Chartered",
        "INDUSB" to "IndusInd",
        "AUBANK" to "AU Small Finance",
        "FEDBKM" to "Federal Bank",
        "RBLBNK" to "RBL Bank",
        "BAJFIN" to "Bajaj Finance",
        "AMAZON" to "Amazon Pay",
        "HSBC" to "HSBC",
        "HSBCCC" to "HSBC"
    )

    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""debited\s+(?:for|with|by|of)\s+(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(?:spent|payment\s+of|paid)\s+(?:Rs\.?|INR|₹)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9,]+(?:\.[0-9]{1,2})?)\s*(?:debited|charged)""", RegexOption.IGNORE_CASE)
    )

    private val UPI_REF_PATTERN = Regex("""\b(\d{12})\b""")
    private val LAST4_PATTERNS = listOf(
        // "Card ending 0735", "Card ending with 0735", "Card ending in 0735"
        Regex("""[Cc]ard\s+(?:ending\s+(?:(?:in|with)\s+)?|XX+|x+)(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""(?:XX+|x+)(\d{4})"""),
        // "ending 0735", "ending with 0735", "ending in 0735"
        Regex("""ending\s+(?:(?:in|with)\s+)?(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""card\s+(\d{4})""", RegexOption.IGNORE_CASE)
    )

    private val OTP_PATTERNS = Regex(
        """(?i)\b(otp|one[\s\-]?time[\s\-]?password|one[\s\-]?time[\s\-]?pin|verification\s+code|auth(?:entication)?\s+code|login\s+code|access\s+code|passcode\s+is|security\s+code)\b"""
    )

    private val TRANSACTION_KEYWORDS = Regex(
        """\b(?:sent|spent|paid|debited|used|charged|payment|transfer(?:red)?|withdrawn|withdrawal|atm\s*withdrawal|purchase|deducted|mandate|autopay|auto[\s\-]?debit)\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        sender: String,
        body: String,
        customKeywords: Set<String>? = null,
        exclusionKeywords: Set<String>? = null
    ): ParsedSMS? {
        // RCS messages from Indian banks (e.g. SBI Cards) use Unicode Mathematical Alphanumeric
        // Symbols for bold/italic styling — e.g. "𝗌𝗉𝖾𝗇𝗍" instead of ASCII "spent".
        // NFKC normalization maps all such styled characters back to plain ASCII equivalents.
        val normalizedBody = Normalizer.normalize(body, Normalizer.Form.NFKC)

        if (OTP_PATTERNS.containsMatchIn(normalizedBody)) return null

        if (!exclusionKeywords.isNullOrEmpty()) {
            val exclusionRegex = Regex(
                exclusionKeywords.joinToString("|") { """\b${Regex.escape(it)}\b""" },
                RegexOption.IGNORE_CASE
            )
            if (exclusionRegex.containsMatchIn(normalizedBody)) return null
        }

        val keywordRegex = if (customKeywords != null) {
            val pattern = customKeywords.joinToString("|") { """\b${Regex.escape(it)}\b""" }
            Regex(pattern, RegexOption.IGNORE_CASE)
        } else {
            TRANSACTION_KEYWORDS
        }
        if (!keywordRegex.containsMatchIn(normalizedBody)) return null
        val amount = parseAmount(normalizedBody) ?: return null
        val bankName = parseBankName(sender)
        val (paymentType, last4OrRef) = parsePaymentTypeAndRef(normalizedBody)
        return ParsedSMS(
            amount = amount,
            paymentType = paymentType,
            bankName = bankName,
            last4OrRef = last4OrRef
        )
    }

    private fun parseAmount(body: String): Double? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(body) ?: continue
            val raw = match.groupValues[1].replace(",", "")
            return raw.toDoubleOrNull()?.takeIf { it > 0 }
        }
        return null
    }

    fun parseBankName(sender: String): String {
        val upper = sender.uppercase().trim()
        for ((key, name) in BANK_SENDER_MAP) {
            if (upper.contains(key)) return name
        }
        // RCS senders arrive as display names (e.g. "SBI CARDS AND PAYMENT SERVICES")
        if (upper.contains("SBI")) return "SBI"
        if (upper.contains("HDFC")) return "HDFC"
        if (upper.contains("ICICI")) return "ICICI"
        if (upper.contains("AXIS")) return "Axis"
        if (upper.contains("KOTAK")) return "Kotak"
        return sender.take(15)
    }

    private fun parsePaymentTypeAndRef(body: String): Pair<String, String> {
        val lower = body.lowercase()

        // Card number takes priority — check before UPI keyword to handle "CC debited via UPI" messages
        for (pattern in LAST4_PATTERNS) {
            val match = pattern.find(body)
            if (match != null) {
                return Pair("Card", match.groupValues[1])
            }
        }

        val upiRef = UPI_REF_PATTERN.find(body)?.groupValues?.get(1)
        if (lower.contains("upi") || upiRef != null) {
            return Pair("UPI", upiRef ?: "")
        }

        if (lower.contains("neft")) return Pair("NEFT", "")
        if (lower.contains("rtgs")) return Pair("RTGS", "")
        if (lower.contains("imps")) return Pair("IMPS", "")

        if (lower.contains("card") || lower.contains("debit") || lower.contains("credit")) {
            return Pair("Card", "")
        }

        return Pair("Other", "")
    }
}
