package com.trendscout.core.io

import com.trendscout.core.model.Countries
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.TrendType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Source-neutral record produced by CSV / JSON importers and merged into the library by URL. */
data class ImportedItem(
    val url: String,
    val title: String? = null,
    val authorHandle: String? = null,
    val authorFollowers: Long? = null,
    val authorCountry: String? = null,
    val publishedAt: Long? = null,
    val hashtags: List<String> = emptyList(),
    val soundId: String? = null,
    val soundTitle: String? = null,
    val category: String? = null,
    val formatTags: List<String> = emptyList(),
    val trendTypes: Set<TrendType> = emptySet(),
    val thumbnailUrl: String? = null,
    val popularIn: List<Pair<String, Int?>> = emptyList(),
    val popularBasis: RegionBasis = RegionBasis.DATA_FEED,
    val capturedAt: Long? = null,
    val views: Long? = null,
    val likes: Long? = null,
    val comments: Long? = null,
    val shares: Long? = null,
)

data class ImportError(val line: Int, val message: String)

data class ImportResult(val items: List<ImportedItem>, val errors: List<ImportError>, val sourceName: String? = null)

object DateParser {
    /** Accepts ISO-8601 instant, ISO local date-time / date (treated as UTC), or epoch seconds / millis. */
    fun parse(raw: String?): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        s.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(s).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }
}

/**
 * CSV import for statistics the user is allowed to provide (own exports, manual tracking sheets).
 * Header names are case-insensitive; only `url` is required. See docs/DATA_FORMATS.md.
 */
object CsvImporter {

    fun parse(text: String): ImportResult {
        val rows = splitRows(text.removePrefix("﻿"))
        if (rows.isEmpty()) return ImportResult(emptyList(), listOf(ImportError(0, "empty file")))
        val header = rows.first().map { it.trim().lowercase() }
        val idx = header.withIndex().associate { it.value to it.index }
        if ("url" !in idx) return ImportResult(emptyList(), listOf(ImportError(1, "missing 'url' column")))

        val items = mutableListOf<ImportedItem>()
        val errors = mutableListOf<ImportError>()
        rows.drop(1).forEachIndexed { i, row ->
            val line = i + 2
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun col(name: String): String? = idx[name]?.let { row.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun num(name: String): Long? {
                val v = col(name) ?: return null
                return parseCount(v) ?: run { errors += ImportError(line, "bad number in '$name': $v"); null }
            }
            val rawUrl = col("url")
            val parsed = rawUrl?.let { TikTokUrl.parse(it) }
            if (parsed == null) {
                errors += ImportError(line, "not a TikTok URL: ${rawUrl ?: ""}")
                return@forEachIndexed
            }
            val country = col("author_country")?.uppercase()?.takeIf { Countries.isValid(it) }
            val popular = col("popular_in")?.split(';', '|', ' ')?.map { it.trim().uppercase() }
                ?.filter { Countries.isValid(it) }?.map { it to null } ?: emptyList()
            items += ImportedItem(
                url = parsed.canonicalUrl,
                title = col("title"),
                authorHandle = col("author")?.removePrefix("@") ?: parsed.handle,
                authorFollowers = num("author_followers"),
                authorCountry = country,
                publishedAt = DateParser.parse(col("published_at"))
                    ?: parsed.videoId?.let { TikTokUrl.publishedAtFromId(it) },
                hashtags = col("hashtags")?.split(';', '|', ',', ' ')?.map { it.trim().removePrefix("#") }?.filter { it.isNotEmpty() } ?: emptyList(),
                soundTitle = col("sound"),
                category = col("category"),
                formatTags = col("format")?.split(';', '|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                popularIn = popular,
                popularBasis = RegionBasis.USER_OBSERVATION,
                capturedAt = DateParser.parse(col("captured_at")),
                views = num("views"),
                likes = num("likes"),
                comments = num("comments"),
                shares = num("shares"),
            )
        }
        return ImportResult(items, errors)
    }

    /** "12.5K", "1,2M", "3 400" -> long. */
    fun parseCount(raw: String): Long? {
        val s = raw.trim().replace(" ", "").replace(" ", "").uppercase()
        if (s.isEmpty()) return null
        val mult = when (s.last()) { 'K' -> 1_000.0; 'M' -> 1_000_000.0; 'B' -> 1_000_000_000.0; else -> 1.0 }
        val body = if (mult != 1.0) s.dropLast(1) else s
        val normalized = if (mult != 1.0) body.replace(',', '.') else body.replace(",", "")
        val d = normalized.toDoubleOrNull() ?: return null
        if (d < 0) return null
        return (d * mult).toLong()
    }

    /** RFC-4180-ish CSV splitter (quotes, escaped quotes, CRLF); also accepts ';' as delimiter. */
    fun splitRows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull() ?: return emptyList()
        val delim = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delim -> { row.add(cell.toString()); cell.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }
}

// ---------------- JSON feed (own backend / licensed provider) ----------------

@Serializable
data class FeedDocument(
    val source: FeedSourceInfo? = null,
    val items: List<FeedItemDto> = emptyList(),
)

@Serializable
data class FeedSourceInfo(
    val name: String? = null,
    val license: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class FeedItemDto(
    val url: String,
    val title: String? = null,
    val author: String? = null,
    @SerialName("author_followers") val authorFollowers: Long? = null,
    @SerialName("author_country") val authorCountry: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val hashtags: List<String> = emptyList(),
    val sound: FeedSoundDto? = null,
    val category: String? = null,
    val formats: List<String> = emptyList(),
    @SerialName("trend_types") val trendTypes: List<String> = emptyList(),
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("popular_in") val popularIn: List<FeedRegionDto> = emptyList(),
    val stats: FeedStatsDto? = null,
)

@Serializable
data class FeedSoundDto(val id: String? = null, val title: String? = null)

@Serializable
data class FeedRegionDto(val country: String, val rank: Int? = null)

@Serializable
data class FeedStatsDto(
    @SerialName("captured_at") val capturedAt: String? = null,
    val views: Long? = null,
    val likes: Long? = null,
    val comments: Long? = null,
    val shares: Long? = null,
)

object JsonFeedParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(text: String): ImportResult {
        val doc = try {
            json.decodeFromString(FeedDocument.serializer(), text)
        } catch (e: Exception) {
            return ImportResult(emptyList(), listOf(ImportError(0, "invalid JSON: ${e.message?.take(200)}")))
        }
        val errors = mutableListOf<ImportError>()
        val items = doc.items.mapIndexedNotNull { i, d ->
            val parsed = TikTokUrl.parse(d.url)
            if (parsed == null) {
                errors += ImportError(i + 1, "not a TikTok URL: ${d.url}")
                return@mapIndexedNotNull null
            }
            ImportedItem(
                url = parsed.canonicalUrl,
                title = d.title,
                authorHandle = d.author?.removePrefix("@") ?: parsed.handle,
                authorFollowers = d.authorFollowers,
                authorCountry = d.authorCountry?.uppercase()?.takeIf { Countries.isValid(it) },
                publishedAt = DateParser.parse(d.publishedAt) ?: parsed.videoId?.let { TikTokUrl.publishedAtFromId(it) },
                hashtags = d.hashtags.map { it.removePrefix("#") },
                soundId = d.sound?.id,
                soundTitle = d.sound?.title,
                category = d.category,
                formatTags = d.formats,
                trendTypes = d.trendTypes.mapNotNull { t -> TrendType.entries.firstOrNull { it.name.equals(t, true) } }.toSet(),
                thumbnailUrl = d.thumbnailUrl,
                popularIn = d.popularIn.filter { Countries.isValid(it.country) }.map { it.country.uppercase() to it.rank },
                popularBasis = RegionBasis.DATA_FEED,
                capturedAt = DateParser.parse(d.stats?.capturedAt),
                views = d.stats?.views,
                likes = d.stats?.likes,
                comments = d.stats?.comments,
                shares = d.stats?.shares,
            )
        }
        return ImportResult(items, errors, doc.source?.name)
    }
}
