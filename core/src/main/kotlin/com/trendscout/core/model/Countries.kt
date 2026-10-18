package com.trendscout.core.model

import java.util.Locale

/**
 * Country registry. Any ISO 3166-1 alpha-2 code works: names come from the JVM locale data,
 * so adding a market means adding its code, not changing the app.
 */
object Countries {
    /** Markets shown first (in this order). */
    val PRIORITY: List<String> = listOf("US", "GB", "FR", "DE", "JP", "KR", "BR", "MX", "ES", "IT", "CA", "AU")

    /** Additional markets offered in settings; extend freely. */
    val EXTRA: List<String> = listOf(
        "IN", "ID", "PH", "VN", "TH", "MY", "SG", "TR", "PL", "NL", "BE", "SE", "NO", "DK", "FI",
        "PT", "IE", "AT", "CH", "AR", "CL", "CO", "PE", "SA", "AE", "EG", "ZA", "NG", "NZ", "TW", "UA", "KZ",
    )

    val ALL: List<String> get() = PRIORITY + EXTRA

    private val isoCodes: Set<String> = Locale.getISOCountries().toSet()

    fun isValid(code: String): Boolean = code.uppercase() in isoCodes

    fun name(code: String, locale: Locale = Locale.forLanguageTag("ru")): String {
        val n = Locale("", code.uppercase()).getDisplayCountry(locale)
        return n.ifBlank { code.uppercase() }
    }

    /** Regional-indicator emoji flag, e.g. "US" -> 🇺🇸. */
    fun flag(code: String): String {
        val c = code.uppercase()
        if (c.length != 2 || !c.all { it in 'A'..'Z' }) return "🏳"
        val first = Character.codePointAt(c, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(c, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
