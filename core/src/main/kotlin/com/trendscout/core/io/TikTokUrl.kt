package com.trendscout.core.io

data class ParsedTikTokUrl(
    val canonicalUrl: String,
    val videoId: String?,
    val handle: String?,
    /** Short links (vm.tiktok.com / vt.tiktok.com) must be resolved by following the redirect. */
    val isShortLink: Boolean,
)

object TikTokUrl {
    private val VIDEO = Regex("""https?://(?:www\.|m\.)?tiktok\.com/@([A-Za-z0-9._-]+)/(?:video|photo)/(\d{8,25})""")
    private val MOBILE_V = Regex("""https?://m\.tiktok\.com/v/(\d{8,25})""")
    private val SHORT = Regex("""https?://(?:vm|vt)\.tiktok\.com/[A-Za-z0-9]+/?""")
    private val TIKTOK_T = Regex("""https?://(?:www\.)?tiktok\.com/t/[A-Za-z0-9]+/?""")
    private val ANY_URL = Regex("""https?://\S+""")

    /** Extracts the first TikTok URL from shared text such as "Look at this! https://vm.tiktok.com/abc/". */
    fun extract(text: String): String? =
        ANY_URL.findAll(text).map { it.value.trimEnd('.', ',', ')', '"', '\'') }.firstOrNull { it.contains("tiktok.com") }

    fun parse(input: String): ParsedTikTokUrl? {
        val url = extract(input.trim()) ?: return null
        VIDEO.find(url)?.let { m ->
            val handle = m.groupValues[1]
            val id = m.groupValues[2]
            return ParsedTikTokUrl("https://www.tiktok.com/@$handle/video/$id", id, handle, false)
        }
        MOBILE_V.find(url)?.let { m -> return ParsedTikTokUrl(url.substringBefore('?'), m.groupValues[1], null, false) }
        if (SHORT.matches(url) || TIKTOK_T.matches(url)) return ParsedTikTokUrl(url, null, null, true)
        return null
    }

    /**
     * TikTok video ids are snowflake-style: the upper 32 bits are the Unix creation time in seconds.
     * Returns epoch millis, or null if the result is not plausible.
     */
    fun publishedAtFromId(videoId: String, now: Long = System.currentTimeMillis()): Long? {
        val id = videoId.toBigIntegerOrNull() ?: return null
        if (id.signum() <= 0) return null
        val seconds = id.shiftRight(32).toLong()
        val millis = seconds * 1000
        val earliest = 1_451_606_400_000L // 2016-01-01
        return if (millis in earliest..(now + 86_400_000L)) millis else null
    }

    fun hashtagsFromText(text: String): List<String> =
        Regex("""#([\p{L}\p{N}_]+)""").findAll(text).map { it.groupValues[1] }.distinctBy { it.lowercase() }.toList()

    fun tagUrl(tag: String): String = "https://www.tiktok.com/tag/" + tag.removePrefix("#").trim()

    fun searchUrl(query: String): String = "https://www.tiktok.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
}
