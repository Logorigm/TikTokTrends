package com.trendscout.core.scoring

import com.trendscout.core.model.Period
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.Video
import kotlin.math.abs

private const val HOUR = 3_600_000.0

/** Growth between two real snapshots. */
data class GrowthWindow(
    val fromAt: Long,
    val toAt: Long,
    val viewsPerHour: Double?,
    val likesPerHour: Double?,
    val commentsPerHour: Double?,
) {
    val hours: Double get() = (toAt - fromAt) / HOUR
}

/**
 * Numbers derived from a video's snapshots. Every field is nullable: a value is only produced when
 * the underlying data exists. In particular, [currentViewsPerHour] requires at least two snapshots;
 * with a single snapshot only [avgViewsPerHourSincePublish] (a lifetime average) is available.
 */
data class VideoMetrics(
    val snapshotCount: Int,
    val latest: StatSnapshot?,
    val ageHours: Double?,
    val avgViewsPerHourSincePublish: Double?,
    val lastWindow: GrowthWindow?,
    val previousWindow: GrowthWindow?,
    /** Approximate growth over standard periods, only where snapshots actually cover that span. */
    val periodGrowth: Map<Period, GrowthWindow>,
    val engagementRate: Double?,
    /** Change of engagement rate (fraction) between the first and the latest snapshot. */
    val engagementRateChange: Double?,
    /** Latest views divided by author followers. >1 means the video went beyond the author's audience. */
    val breakoutRatio: Double?,
) {
    val currentViewsPerHour: Double? get() = lastWindow?.viewsPerHour
    val hasMeasuredGrowth: Boolean get() = currentViewsPerHour != null

    /** Ratio of the last interval's view rate to the previous one. */
    val acceleration: Double?
        get() {
            val cur = lastWindow?.viewsPerHour ?: return null
            val prev = previousWindow?.viewsPerHour ?: return null
            if (prev <= 0.0) return null
            return cur / prev
        }
}

object MetricsCalculator {

    fun compute(video: Video, now: Long): VideoMetrics {
        val snaps = video.snapshots.sortedBy { it.capturedAt }
        val latest = snaps.lastOrNull()
        val age = video.publishedAt?.let { (now - it) / HOUR }?.takeIf { it >= 0 }

        val avgSincePublish = if (latest?.views != null && video.publishedAt != null) {
            val hours = (latest.capturedAt - video.publishedAt) / HOUR
            if (hours >= 1.0) latest.views / hours else null
        } else null

        val withViews = snaps.filter { it.views != null }
        val last = if (withViews.size >= 2) window(withViews[withViews.size - 2], withViews.last()) else null
        val prev = if (withViews.size >= 3) window(withViews[withViews.size - 3], withViews[withViews.size - 2]) else null

        val periodGrowth = buildMap {
            val end = withViews.lastOrNull()
            if (end != null) {
                for (p in Period.entries) {
                    val target = end.capturedAt - p.millis
                    val start = withViews.filter { it.capturedAt < end.capturedAt }
                        .minByOrNull { abs(it.capturedAt - target) }
                    if (start != null && abs(start.capturedAt - target) <= p.millis / 2) {
                        window(start, end)?.let { put(p, it) }
                    }
                }
            }
        }

        val engagementRate = latest?.let { s ->
            val v = s.views ?: return@let null
            val e = s.engagement ?: return@let null
            if (v > 0) e.toDouble() / v else null
        }
        val firstWithEngagement = snaps.firstOrNull { it.views != null && it.views > 0 && it.engagement != null }
        val engagementChange = if (engagementRate != null && firstWithEngagement != null && firstWithEngagement !== latest) {
            engagementRate - firstWithEngagement.engagement!!.toDouble() / firstWithEngagement.views!!
        } else null

        val breakout = if (latest?.views != null && video.authorFollowers != null && video.authorFollowers > 0) {
            latest.views.toDouble() / video.authorFollowers
        } else null

        return VideoMetrics(
            snapshotCount = snaps.size,
            latest = latest,
            ageHours = age,
            avgViewsPerHourSincePublish = avgSincePublish,
            lastWindow = last,
            previousWindow = prev,
            periodGrowth = periodGrowth,
            engagementRate = engagementRate,
            engagementRateChange = engagementChange,
            breakoutRatio = breakout,
        )
    }

    private fun window(a: StatSnapshot, b: StatSnapshot): GrowthWindow? {
        val hours = (b.capturedAt - a.capturedAt) / HOUR
        if (hours <= 0.05) return null
        fun rate(x: Long?, y: Long?): Double? = if (x != null && y != null) (y - x) / hours else null
        return GrowthWindow(
            fromAt = a.capturedAt,
            toAt = b.capturedAt,
            viewsPerHour = rate(a.views, b.views),
            likesPerHour = rate(a.likes, b.likes),
            commentsPerHour = rate(a.comments, b.comments),
        )
    }
}
