package com.trendscout.core.feed

import com.trendscout.core.model.Period
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.model.linksTo
import com.trendscout.core.model.normalizeTag
import com.trendscout.core.model.trendIds
import com.trendscout.core.scoring.MetricsCalculator
import com.trendscout.core.scoring.ScoringContext
import com.trendscout.core.scoring.TrendScore
import com.trendscout.core.scoring.TrendScorer
import com.trendscout.core.scoring.VideoMetrics

enum class SortMode { FASTEST_GROWING, MOST_POPULAR, NEWEST, ENGAGEMENT, TOPIC_MATCH }

/** Which geographic field the country filter applies to. They are never merged silently. */
enum class GeoMode {
    /** Evidence that the content is popular in the country (Creative Center, feed, user observation). */
    POPULAR_IN,
    /** Country where the author is registered / based. */
    AUTHOR_COUNTRY,
    /** Either of the above (cards still show which one matched). */
    EITHER,
}

data class FeedQuery(
    val countries: Set<String> = emptySet(),
    val geoMode: GeoMode = GeoMode.EITHER,
    /** Only items with data activity (a snapshot or being added) inside this window. */
    val period: Period? = null,
    /** Only items published inside this window (publication date must be known). */
    val publishedWithin: Period? = null,
    val categories: Set<String> = emptySet(),
    val minViews: Long? = null,
    val maxViews: Long? = null,
    /** Minimum *measured* views/hour. Items without growth history are excluded when set. */
    val minViewsPerHour: Double? = null,
    val trendTypes: Set<TrendType> = emptySet(),
    val text: String = "",
    val sort: SortMode = SortMode.FASTEST_GROWING,
    val topicKeywords: List<String> = emptyList(),
    val favoritesOnly: Boolean = false,
)

enum class GeoMatch { POPULAR_IN, AUTHOR_COUNTRY }

data class FeedEntry(
    val video: Video,
    val metrics: VideoMetrics,
    val score: TrendScore,
    val topicMatch: Double,
    val geoMatch: Set<GeoMatch>,
)

object FeedEngine {

    fun build(videos: List<Video>, trends: List<Trend>, query: FeedQuery, now: Long): List<FeedEntry> {
        val ctx = ScoringContext(videos, trends, now)
        val keywords = query.topicKeywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val text = query.text.trim().lowercase()

        return videos.asSequence()
            .filter { !query.favoritesOnly || it.favorite }
            .mapNotNull { v ->
                val geo = geoMatch(v, query)
                if (query.countries.isNotEmpty() && geo.isEmpty()) return@mapNotNull null
                val m = MetricsCalculator.compute(v, now)
                if (!passes(v, m, query, now, text)) return@mapNotNull null
                FeedEntry(v, m, TrendScorer.scoreVideo(v, m, ctx), topicMatch(v, keywords), geo)
            }
            .toList()
            .let { sort(it, query.sort) }
    }

    private fun geoMatch(v: Video, q: FeedQuery): Set<GeoMatch> {
        if (q.countries.isEmpty()) return emptySet()
        val out = HashSet<GeoMatch>()
        if (q.geoMode != GeoMode.AUTHOR_COUNTRY && v.popularIn.any { it.countryCode in q.countries }) out += GeoMatch.POPULAR_IN
        if (q.geoMode != GeoMode.POPULAR_IN && v.authorCountry != null && v.authorCountry in q.countries) out += GeoMatch.AUTHOR_COUNTRY
        return out
    }

    private fun passes(v: Video, m: VideoMetrics, q: FeedQuery, now: Long, text: String): Boolean {
        q.period?.let { p ->
            val lastActivity = maxOf(v.addedAt, m.latest?.capturedAt ?: 0L)
            if (lastActivity < now - p.millis) return false
        }
        q.publishedWithin?.let { p ->
            val pub = v.publishedAt ?: return false
            if (pub < now - p.millis) return false
        }
        if (q.categories.isNotEmpty() && (v.category == null || v.category !in q.categories)) return false
        val views = m.latest?.views
        if (q.minViews != null && (views == null || views < q.minViews)) return false
        if (q.maxViews != null && (views == null || views > q.maxViews)) return false
        if (q.minViewsPerHour != null) {
            val vph = m.currentViewsPerHour ?: return false
            if (vph < q.minViewsPerHour) return false
        }
        if (q.trendTypes.isNotEmpty() && !matchesTypes(v, q.trendTypes)) return false
        if (text.isNotEmpty() && !matchesText(v, text)) return false
        return true
    }

    private fun matchesTypes(v: Video, types: Set<TrendType>): Boolean = types.any { t ->
        when (t) {
            TrendType.SOUND -> v.soundKey != null || t in v.trendTypes
            TrendType.HASHTAG -> v.hashtags.isNotEmpty() || t in v.trendTypes
            else -> t in v.trendTypes
        }
    }

    fun matchesText(v: Video, text: String): Boolean {
        val t = text.removePrefix("#")
        return v.title.lowercase().contains(t) ||
            v.hashtags.any { normalizeTag(it).contains(t) } ||
            (v.soundTitle?.lowercase()?.contains(t) == true) ||
            (v.authorHandle?.lowercase()?.contains(t) == true) ||
            v.formatTags.any { it.lowercase().contains(t) } ||
            (v.notes?.lowercase()?.contains(t) == true) ||
            (v.category?.lowercase()?.contains(t) == true)
    }

    /** Share of the user's topic keywords found in the video's text fields. */
    fun topicMatch(v: Video, keywords: List<String>): Double {
        if (keywords.isEmpty()) return 0.0
        val hay = buildString {
            append(v.title.lowercase()).append(' ')
            v.hashtags.forEach { append(normalizeTag(it)).append(' ') }
            v.formatTags.forEach { append(it.lowercase()).append(' ') }
            append(v.soundTitle?.lowercase() ?: "").append(' ')
            append(v.category?.lowercase() ?: "")
        }
        val hits = keywords.count { hay.contains(normalizeTag(it).replace('_', ' ')) || hay.contains(normalizeTag(it)) }
        return hits.toDouble() / keywords.size
    }

    fun sort(list: List<FeedEntry>, mode: SortMode): List<FeedEntry> = when (mode) {
        SortMode.FASTEST_GROWING -> list.sortedWith(
            compareByDescending<FeedEntry> { it.metrics.currentViewsPerHour != null }
                .thenByDescending { it.metrics.currentViewsPerHour ?: it.metrics.avgViewsPerHourSincePublish ?: -1.0 }
        )
        SortMode.MOST_POPULAR -> list.sortedByDescending { it.metrics.latest?.views ?: -1L }
        SortMode.NEWEST -> list.sortedByDescending { it.video.publishedAt ?: it.video.addedAt }
        SortMode.ENGAGEMENT -> list.sortedByDescending { it.metrics.engagementRate ?: -1.0 }
        SortMode.TOPIC_MATCH -> list.sortedWith(
            compareByDescending<FeedEntry> { it.topicMatch }.thenByDescending { it.score.score ?: -1 }
        )
    }
}

data class TrendEntry(
    val trend: Trend,
    val score: TrendScore,
    val videoCount: Int,
    val authors: Int,
    /** Countries with popularity evidence, with the strongest basis. */
    val countries: Map<String, com.trendscout.core.model.RegionBasis>,
    /** Whether a row exists in storage (saved / annotated) or the trend is derived from video tags only. */
    val persisted: Boolean,
)

/** Builds the list of hashtags / sounds / formats from stored trends plus tags found on videos. */
object TrendCatalog {

    fun build(
        videos: List<Video>,
        trends: List<Trend>,
        now: Long,
        countries: Set<String> = emptySet(),
        types: Set<TrendType> = emptySet(),
        text: String = "",
    ): List<TrendEntry> {
        val ctx = ScoringContext(videos, trends, now)
        val byId = LinkedHashMap<String, Pair<Trend, Boolean>>()
        trends.forEach { byId[it.id] = it to true }
        for (v in videos) {
            for (id in v.trendIds()) {
                if (id in byId) continue
                val type = TrendType.valueOf(id.substringBefore(':'))
                val key = id.substringAfter(':')
                val display = when (type) {
                    TrendType.HASHTAG -> v.hashtags.firstOrNull { normalizeTag(it) == key } ?: key
                    TrendType.SOUND -> v.soundTitle ?: key
                    else -> v.formatTags.firstOrNull { normalizeTag(it) == key } ?: key
                }
                byId[id] = Trend(id = id, type = type, name = display.removePrefix("#"), createdAt = v.addedAt) to false
            }
        }
        val q = text.trim().lowercase().removePrefix("#")
        return byId.values.mapNotNull { (t, persisted) ->
            if (types.isNotEmpty() && t.type !in types) return@mapNotNull null
            if (q.isNotEmpty() && !t.name.lowercase().contains(q)) return@mapNotNull null
            val geo = HashMap(ctx.countries(t.id))
            t.popularIn.forEach { o -> geo.merge(o.countryCode, o.basis) { a, b -> if (a.reliability >= b.reliability) a else b } }
            if (countries.isNotEmpty() && geo.keys.none { it in countries }) return@mapNotNull null
            val linked = videos.filter { it.linksTo(t) }
            TrendEntry(
                trend = t,
                score = TrendScorer.scoreTrend(t, linked, ctx, now),
                videoCount = linked.size,
                authors = linked.mapNotNull { it.authorHandle?.lowercase() }.distinct().size,
                countries = geo,
                persisted = persisted,
            )
        }.sortedWith(
            compareByDescending<TrendEntry> { it.score.score ?: -1 }
                .thenByDescending { it.authors }
                .thenByDescending { it.videoCount }
        )
    }
}
