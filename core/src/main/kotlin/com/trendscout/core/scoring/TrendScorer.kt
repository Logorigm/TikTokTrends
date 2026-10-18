package com.trendscout.core.scoring

import com.trendscout.core.model.Period
import com.trendscout.core.model.Reliability
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.Video
import com.trendscout.core.model.linksTo
import com.trendscout.core.model.trendIds
import kotlin.math.log10

/** Each factor the score can use. Weights are heuristic and documented in docs/SCORING.md. */
enum class FactorKind(val weight: Double) {
    /** Views/hour between the two latest snapshots (real growth). */
    VIEW_VELOCITY(0.22),
    /** Lifetime average views/hour since publication (used only when growth was never measured). */
    AVG_VELOCITY(0.08),
    ACCELERATION(0.12),
    ENGAGEMENT_RATE(0.10),
    ENGAGEMENT_GROWTH(0.08),
    FRESHNESS(0.08),
    BREAKOUT(0.14),
    CROSS_AUTHOR(0.10),
    MULTI_COUNTRY(0.08),
    TAG_DYNAMICS(0.08),
}

data class ScoreFactor(
    val kind: FactorKind,
    /** Raw value in natural units (views/hour, ratio, count, hours, %/day...). */
    val raw: Double,
    /** Normalised to 0..1. */
    val normalized: Double,
)

enum class ScoreLabel { PROMISING, WATCH, LOW, INSUFFICIENT_DATA }

enum class ScoreWarning {
    /** Only one snapshot: growth speed is not measured, only a lifetime average (if any). */
    SINGLE_SNAPSHOT,
    NO_STATS,
    NO_PUBLISH_DATE,
    NO_FOLLOWERS,
    /** No evidence about where the content is popular. */
    NO_GEO_DATA,
    /** Geographic evidence is only the user's own observation. */
    GEO_USER_ONLY,
    /** Few related videos in the local data: cross-author / multi-country factors are weak. */
    SMALL_SAMPLE,
}

data class TrendScore(
    /** 0..100, or null when there is not enough data to justify any number. */
    val score: Int?,
    val label: ScoreLabel,
    val reliability: Reliability,
    val factors: List<ScoreFactor>,
    val missing: List<FactorKind>,
    val warnings: List<ScoreWarning>,
    /** Share of total factor weight backed by data, 0..1. */
    val coverage: Double,
)

/**
 * Aggregates computed over the whole local dataset, needed for factors that look beyond one video
 * (repetition across authors, spread across countries, hashtag/sound usage dynamics).
 */
class ScoringContext(
    videos: List<Video>,
    trends: List<Trend>,
    private val now: Long,
) {
    private val authorsByKey = HashMap<String, MutableSet<String>>()
    private val countriesByKey = HashMap<String, MutableMap<String, RegionBasis>>()
    private val trendsById = trends.associateBy { it.id }

    init {
        val horizon = now - Period.D30.millis
        for (v in videos) {
            val ids = v.trendIds()
            val recent = (v.publishedAt ?: v.addedAt) >= horizon
            for (id in ids) {
                if (recent) {
                    authorsByKey.getOrPut(id) { HashSet() }.add(v.authorHandle?.lowercase() ?: "video:${v.id}")
                }
                for (o in v.popularIn) addCountry(id, o.countryCode, o.basis)
            }
        }
        for (t in trends) for (o in t.popularIn) addCountry(t.id, o.countryCode, o.basis)
    }

    private fun addCountry(key: String, code: String, basis: RegionBasis) {
        val m = countriesByKey.getOrPut(key) { HashMap() }
        val prev = m[code]
        if (prev == null || basis.reliability > prev.reliability) m[code] = basis
    }

    fun distinctAuthors(trendId: String): Int = authorsByKey[trendId]?.size ?: 0

    fun countries(trendId: String): Map<String, RegionBasis> = countriesByKey[trendId] ?: emptyMap()

    fun trend(id: String): Trend? = trendsById[id]

    /** Growth of posts per day (fraction) between the two latest trend snapshots of the same country. */
    fun tagGrowthPerDay(trendId: String): Double? = trendsById[trendId]?.let { postsGrowthPerDay(it.snapshots) }

    companion object {
        fun postsGrowthPerDay(snapshots: List<TrendSnapshot>): Double? {
            val best = snapshots.filter { it.posts != null }
                .groupBy { it.countryCode }
                .values
                .mapNotNull { list ->
                    val s = list.sortedBy { it.capturedAt }
                    if (s.size < 2) return@mapNotNull null
                    val a = s[s.size - 2]
                    val b = s.last()
                    val days = (b.capturedAt - a.capturedAt) / 86_400_000.0
                    if (days < 0.04 || a.posts!! <= 0) null else ((b.posts!! - a.posts).toDouble() / a.posts) / days
                }
            return best.maxOrNull()
        }
    }
}

object TrendScorer {

    private const val MIN_COVERAGE = 0.30
    private const val MIN_FACTORS = 3
    private const val PROMISING_THRESHOLD = 65
    private const val WATCH_THRESHOLD = 45

    fun scoreVideo(video: Video, metrics: VideoMetrics, ctx: ScoringContext): TrendScore {
        val factors = mutableListOf<ScoreFactor>()
        val warnings = mutableListOf<ScoreWarning>()

        if (metrics.snapshotCount == 0) warnings += ScoreWarning.NO_STATS
        else if (!metrics.hasMeasuredGrowth) warnings += ScoreWarning.SINGLE_SNAPSHOT
        if (video.publishedAt == null) warnings += ScoreWarning.NO_PUBLISH_DATE
        if (video.authorFollowers == null) warnings += ScoreWarning.NO_FOLLOWERS

        metrics.currentViewsPerHour?.let { factors += ScoreFactor(FactorKind.VIEW_VELOCITY, it, normVelocity(it)) }
        if (metrics.currentViewsPerHour == null) {
            metrics.avgViewsPerHourSincePublish?.let { factors += ScoreFactor(FactorKind.AVG_VELOCITY, it, normVelocity(it)) }
        }
        metrics.acceleration?.let { factors += ScoreFactor(FactorKind.ACCELERATION, it, clamp((it - 0.5) / 1.5)) }
        metrics.engagementRate?.let { factors += ScoreFactor(FactorKind.ENGAGEMENT_RATE, it, clamp(it / 0.12)) }
        metrics.engagementRateChange?.let { factors += ScoreFactor(FactorKind.ENGAGEMENT_GROWTH, it, clamp(0.5 + it / 0.04)) }
        metrics.ageHours?.let { factors += ScoreFactor(FactorKind.FRESHNESS, it, normFreshness(it)) }
        metrics.breakoutRatio?.let { factors += ScoreFactor(FactorKind.BREAKOUT, it, normBreakout(it)) }

        val ids = video.trendIds()
        val maxAuthors = ids.maxOfOrNull { ctx.distinctAuthors(it) } ?: 0
        if (maxAuthors >= 2) factors += ScoreFactor(FactorKind.CROSS_AUTHOR, maxAuthors.toDouble(), normAuthors(maxAuthors))
        else warnings += ScoreWarning.SMALL_SAMPLE

        val countries = HashMap<String, RegionBasis>()
        video.popularIn.forEach { countries.merge(it.countryCode, it.basis) { a, b -> if (a.reliability >= b.reliability) a else b } }
        ids.forEach { id -> ctx.countries(id).forEach { (c, b) -> countries.merge(c, b) { x, y -> if (x.reliability >= y.reliability) x else y } } }
        addGeo(countries, factors, warnings)

        ids.mapNotNull { ctx.tagGrowthPerDay(it) }.maxOrNull()?.let {
            factors += ScoreFactor(FactorKind.TAG_DYNAMICS, it, clamp(it / 0.5))
        }

        return finish(factors, warnings, measuredGrowthSnapshots = if (metrics.hasMeasuredGrowth) metrics.snapshotCount else 0)
    }

    /**
     * Score for a hashtag / sound / format: combines its own usage history with the videos that use it.
     */
    fun scoreTrend(trend: Trend, linkedVideos: List<Video>, ctx: ScoringContext, now: Long): TrendScore {
        val factors = mutableListOf<ScoreFactor>()
        val warnings = mutableListOf<ScoreWarning>()
        val videos = linkedVideos.filter { it.linksTo(trend) }
        val metrics = videos.map { MetricsCalculator.compute(it, now) }

        val velocities = metrics.mapNotNull { it.currentViewsPerHour }
        if (velocities.isNotEmpty()) {
            val v = velocities.max()
            factors += ScoreFactor(FactorKind.VIEW_VELOCITY, v, normVelocity(v))
        } else {
            warnings += if (metrics.any { it.snapshotCount > 0 }) ScoreWarning.SINGLE_SNAPSHOT else ScoreWarning.NO_STATS
            metrics.mapNotNull { it.avgViewsPerHourSincePublish }.maxOrNull()?.let {
                factors += ScoreFactor(FactorKind.AVG_VELOCITY, it, normVelocity(it))
            }
        }
        metrics.mapNotNull { it.acceleration }.maxOrNull()?.let { factors += ScoreFactor(FactorKind.ACCELERATION, it, clamp((it - 0.5) / 1.5)) }
        metrics.mapNotNull { it.engagementRate }.takeIf { it.isNotEmpty() }?.let { list ->
            val m = median(list)
            factors += ScoreFactor(FactorKind.ENGAGEMENT_RATE, m, clamp(m / 0.12))
        }
        metrics.mapNotNull { it.breakoutRatio }.takeIf { it.isNotEmpty() }?.let { list ->
            val m = median(list)
            factors += ScoreFactor(FactorKind.BREAKOUT, m, normBreakout(m))
        }
        val newest = videos.mapNotNull { it.publishedAt }.maxOrNull()
        if (newest != null) {
            val age = (now - newest) / 3_600_000.0
            factors += ScoreFactor(FactorKind.FRESHNESS, age, normFreshness(age))
        }

        val authors = ctx.distinctAuthors(trend.id)
        if (authors >= 2) factors += ScoreFactor(FactorKind.CROSS_AUTHOR, authors.toDouble(), normAuthors(authors))
        else warnings += ScoreWarning.SMALL_SAMPLE

        val countries = HashMap(ctx.countries(trend.id))
        trend.popularIn.forEach { countries.merge(it.countryCode, it.basis) { a, b -> if (a.reliability >= b.reliability) a else b } }
        addGeo(countries, factors, warnings)

        ScoringContext.postsGrowthPerDay(trend.snapshots)?.let {
            factors += ScoreFactor(FactorKind.TAG_DYNAMICS, it, clamp(it / 0.5))
        }

        val measured = metrics.filter { it.hasMeasuredGrowth }.maxOfOrNull { it.snapshotCount } ?: 0
        val tagHistory = trend.snapshots.size
        return finish(factors, warnings, measuredGrowthSnapshots = maxOf(measured, tagHistory.takeIf { it >= 2 } ?: 0))
    }

    private fun addGeo(countries: Map<String, RegionBasis>, factors: MutableList<ScoreFactor>, warnings: MutableList<ScoreWarning>) {
        if (countries.isEmpty()) {
            warnings += ScoreWarning.NO_GEO_DATA
            return
        }
        if (countries.values.all { it == RegionBasis.USER_OBSERVATION }) warnings += ScoreWarning.GEO_USER_ONLY
        val n = countries.size
        factors += ScoreFactor(FactorKind.MULTI_COUNTRY, n.toDouble(), clamp((n - 1) / 4.0))
    }

    private fun finish(factors: List<ScoreFactor>, warnings: List<ScoreWarning>, measuredGrowthSnapshots: Int): TrendScore {
        val totalWeight = FactorKind.entries.filter { it != FactorKind.AVG_VELOCITY }.sumOf { it.weight }
        val usedWeight = factors.sumOf { it.kind.weight }
        val coverage = (usedWeight / totalWeight).coerceIn(0.0, 1.0)
        val present = factors.map { it.kind }.toSet()
        // AVG_VELOCITY is only a fallback for VIEW_VELOCITY, so it is never reported as missing.
        val missing = FactorKind.entries.filter { it != FactorKind.AVG_VELOCITY && it !in present }

        if (factors.size < MIN_FACTORS || coverage < MIN_COVERAGE) {
            return TrendScore(null, ScoreLabel.INSUFFICIENT_DATA, Reliability.NONE, factors, missing, warnings, coverage)
        }

        val score = (factors.sumOf { it.kind.weight * it.normalized } / usedWeight * 100).toInt().coerceIn(0, 100)

        val geoWeak = ScoreWarning.NO_GEO_DATA in warnings || ScoreWarning.GEO_USER_ONLY in warnings
        val reliability = when {
            measuredGrowthSnapshots >= 3 && coverage >= 0.7 && !geoWeak -> Reliability.HIGH
            measuredGrowthSnapshots >= 2 && coverage >= 0.45 -> Reliability.MEDIUM
            else -> Reliability.LOW
        }
        val label = when {
            score >= PROMISING_THRESHOLD && reliability >= Reliability.MEDIUM -> ScoreLabel.PROMISING
            score >= WATCH_THRESHOLD -> ScoreLabel.WATCH
            else -> ScoreLabel.LOW
        }
        return TrendScore(score, label, reliability, factors, missing, warnings, coverage)
    }

    // --- Normalisation (heuristic, see docs/SCORING.md) ---

    /** 100 000 views/hour -> 1.0 on a log scale. */
    fun normVelocity(viewsPerHour: Double): Double = if (viewsPerHour <= 0) 0.0 else clamp(log10(viewsPerHour + 1) / 5.0)

    /** <=24h -> 1.0, linear down to 0 at 30 days. */
    fun normFreshness(ageHours: Double): Double = when {
        ageHours <= 24 -> 1.0
        ageHours >= 720 -> 0.0
        else -> 1.0 - (ageHours - 24) / (720 - 24)
    }

    /** views/followers: 1x -> 0, 10x -> 0.5, 100x+ -> 1. */
    fun normBreakout(ratio: Double): Double = if (ratio <= 1.0) 0.0 else clamp(log10(ratio) / 2.0)

    /** 2 authors -> 0.11 ... 10+ authors -> 1. */
    fun normAuthors(n: Int): Double = clamp((n - 1) / 9.0)

    private fun clamp(x: Double): Double = x.coerceIn(0.0, 1.0)

    private fun median(list: List<Double>): Double {
        val s = list.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
