package com.couple.expensetracker.util

import org.json.JSONObject

object SplitUtils {

    /**
     * New format: {"alice": {"pct": 50.0, "amount": 250.0}, "bob": {"pct": 50.0, "amount": 250.0}}
     * Uses actual usernames so both devices can look up by their own username without ambiguity.
     *
     * allPersons uses "me" as UI key for the adder — replaced with myUsername when building JSON.
     */
    fun buildSplitsJson(
        myUsername: String,
        allPersons: List<String>,
        amounts: Map<String, String>,
        totalAmount: Double
    ): String {
        val obj = JSONObject()
        allPersons.forEach { person ->
            val key = if (person == "me") myUsername.ifBlank { "me" } else person
            val amt = amounts[person]?.toDoubleOrNull() ?: 0.0
            val pct = if (totalAmount > 0) amt / totalAmount * 100.0 else 0.0
            obj.put(key, JSONObject().apply {
                put("pct", pct)
                put("amount", amt)
            })
        }
        return obj.toString()
    }

    fun buildDefaultSplitsJson(
        myUsername: String,
        allPersons: List<String>,
        defaultPercentages: Map<String, Double>,
        totalAmount: Double
    ): String {
        val n = allPersons.size.coerceAtLeast(1)
        val equalPct = 100.0 / n
        val obj = JSONObject()
        allPersons.forEach { person ->
            val key = if (person == "me") myUsername.ifBlank { "me" } else person
            val pct = defaultPercentages[person] ?: equalPct
            val amt = totalAmount * pct / 100.0
            obj.put(key, JSONObject().apply {
                put("pct", pct)
                put("amount", amt)
            })
        }
        return obj.toString()
    }

    /**
     * Returns the share *amount* for [key] from customSplits.
     * Handles new nested format {"key": {"pct":X, "amount":Y}} and old flat format {"key": amount}.
     * Returns null if key not found or json is blank.
     */
    fun getSplitAmount(json: String?, key: String): Double? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            if (!obj.has(key)) return null
            when (val v = obj.get(key)) {
                is JSONObject -> v.optDouble("amount").takeUnless { it.isNaN() }
                is Number -> v.toDouble()
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /**
     * Returns the share *percentage* for [key] from customSplits.
     * New format: reads stored "pct" directly.
     * Old flat format: computes pct from amount / totalAmount.
     */
    fun getSplitPct(json: String?, key: String, totalAmount: Double): Double? {
        if (json.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(json)
            if (!obj.has(key)) return null
            when (val v = obj.get(key)) {
                is JSONObject -> v.optDouble("pct").takeUnless { it.isNaN() }
                is Number -> if (totalAmount > 0) v.toDouble() / totalAmount * 100.0 else null
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /**
     * Viewer's percentage to display on a transaction chip.
     * - Tries [viewerKey] first (actual username — works for both new and old shared-txn format).
     * - If not found and [fallbackToMe] (own transactions): tries "me" key (old own-txn format).
     * - Falls back to [defaultPct] (configured default from Settings).
     * - Ultimate fallback: 50%.
     */
    fun viewerPct(
        json: String?,
        viewerKey: String,
        fallbackToMe: Boolean,
        totalAmount: Double,
        defaultPct: Double?
    ): Int {
        val pct = getSplitPct(json, viewerKey, totalAmount)
            ?: (if (fallbackToMe) getSplitPct(json, "me", totalAmount) else null)
            ?: defaultPct
            ?: 50.0
        return pct.toInt()
    }

    /**
     * Viewer's share *amount* for balance calculation.
     * - Tries [viewerKey] (actual username).
     * - If not found and [fallbackToMe]: tries "me" key (old own-txn format).
     * - Falls back to 50% of [totalAmount] for old transactions with no split data.
     */
    fun viewerAmount(
        json: String?,
        viewerKey: String,
        fallbackToMe: Boolean,
        totalAmount: Double
    ): Double {
        return getSplitAmount(json, viewerKey)
            ?: (if (fallbackToMe) getSplitAmount(json, "me") else null)
            ?: (totalAmount * 0.5)
    }
}
