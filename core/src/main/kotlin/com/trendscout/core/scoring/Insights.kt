package com.trendscout.core.scoring

import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.model.linksTo
import com.trendscout.core.model.normalizeTag
import com.trendscout.core.model.trendIds

/**
 * Facts derived strictly from stored data (no AI). The UI phrases them; hypotheses are a separate,
 * clearly labelled layer.
 */
enum class InsightKind {
    BEYOND_AUDIENCE, ACCELERATING, DECELERATING, HIGH_ENGAGEMENT, MANY_AUTHORS,
    MULTI_COUNTRY, SINGLE_COUNTRY, FRESH, TAG_GROWTH, RECURRING_ELEMENTS,
}

data class Insight(val kind: InsightKind, val value: Double? = null, val items: List<String> = emptyList())

data class TagCount(val type: TrendType, val key: String, val display: String, val count: Int)

object Insights {

    fun forVideo(video: Video, m: VideoMetrics, ctx: ScoringContext): List<Insight> = buildList {
        m.breakoutRatio?.takeIf { it >= 3.0 }?.let { add(Insight(InsightKind.BEYOND_AUDIENCE, it)) }
        m.acceleration?.let {
            if (it >= 1.3) add(Insight(InsightKind.ACCELERATING, it))
            else if (it <= 0.7) add(Insight(InsightKind.DECELERATING, it))
        }
        m.engagementRate?.takeIf { it >= 0.08 }?.let { add(Insight(InsightKind.HIGH_ENGAGEMENT, it)) }
        m.ageHours?.takeIf { it <= 72 }?.let { add(Insight(InsightKind.FRESH, it)) }
        val ids = video.trendIds()
        ids.maxByOrNull { ctx.distinctAuthors(it) }?.let { id ->
            val n = ctx.distinctAuthors(id)
            if (n >= 3) add(Insight(InsightKind.MANY_AUTHORS, n.toDouble(), listOf(id.substringAfter(':'))))
        }
        val countries = (video.popularIn.map { it.countryCode } + ids.flatMap { ctx.countries(it).keys }).distinct()
        if (countries.size >= 2) add(Insight(InsightKind.MULTI_COUNTRY, countries.size.toDouble(), countries))
        else if (countries.size == 1) add(Insight(InsightKind.SINGLE_COUNTRY, 1.0, countries))
        ids.mapNotNull { ctx.tagGrowthPerDay(it) }.maxOrNull()?.takeIf { it > 0 }?.let { add(Insight(InsightKind.TAG_GROWTH, it)) }
    }

    fun forTrend(trend: Trend, videos: List<Video>, ctx: ScoringContext): List<Insight> = buildList {
        val linked = videos.filter { it.linksTo(trend) }
        val n = ctx.distinctAuthors(trend.id)
        if (n >= 3) add(Insight(InsightKind.MANY_AUTHORS, n.toDouble(), listOf(trend.name)))
        val countries = (trend.popularIn.map { it.countryCode } + ctx.countries(trend.id).keys).distinct()
        if (countries.size >= 2) add(Insight(InsightKind.MULTI_COUNTRY, countries.size.toDouble(), countries))
        else if (countries.size == 1) add(Insight(InsightKind.SINGLE_COUNTRY, 1.0, countries))
        ScoringContext.postsGrowthPerDay(trend.snapshots)?.takeIf { it > 0 }?.let { add(Insight(InsightKind.TAG_GROWTH, it)) }
        val recurring = recurringFormats(linked).filter { it.count >= 2 }.map { it.display }
        if (recurring.isNotEmpty()) add(Insight(InsightKind.RECURRING_ELEMENTS, recurring.size.toDouble(), recurring.take(5)))
    }

    /** Hashtags and sounds that co-occur with the trend in its linked videos. */
    fun relatedTags(trend: Trend, videos: List<Video>, limit: Int = 12): List<TagCount> {
        val own = trend.id.substringAfter(':')
        val counts = HashMap<Pair<TrendType, String>, Pair<String, Int>>()
        for (v in videos.filter { it.linksTo(trend) }) {
            v.hashtags.forEach { h ->
                val k = TrendType.HASHTAG to normalizeTag(h)
                if (!(trend.type == TrendType.HASHTAG && k.second == own)) {
                    counts[k] = ("#" + h.removePrefix("#")) to ((counts[k]?.second ?: 0) + 1)
                }
            }
            v.soundKey?.let { s ->
                val k = TrendType.SOUND to s
                if (!(trend.type == TrendType.SOUND && s == own)) {
                    counts[k] = (v.soundTitle ?: s) to ((counts[k]?.second ?: 0) + 1)
                }
            }
        }
        return counts.map { (k, v) -> TagCount(k.first, k.second, v.first, v.second) }
            .sortedByDescending { it.count }
            .take(limit)
    }

    /** Format / visual tags repeated across the given videos. */
    fun recurringFormats(videos: List<Video>): List<TagCount> {
        val counts = HashMap<String, Pair<String, Int>>()
        for (v in videos) for (f in v.formatTags.distinctBy(::normalizeTag)) {
            val k = normalizeTag(f)
            counts[k] = f to ((counts[k]?.second ?: 0) + 1)
        }
        return counts.map { (k, v) -> TagCount(TrendType.FORMAT, k, v.first, v.second) }.sortedByDescending { it.count }
    }
}
