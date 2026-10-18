package com.trendscout.core.model

/** Time window used by filters and growth calculations. */
enum class Period(val hours: Long) {
    H24(24), D3(72), D7(168), D30(720);

    val millis: Long get() = hours * 3_600_000L
}

enum class TrendType { SOUND, HASHTAG, FORMAT, MEME, VISUAL }

/** How reliable a piece of evidence or a conclusion is. Ordered from weakest to strongest. */
enum class Reliability { NONE, LOW, MEDIUM, HIGH }

/**
 * Where a "popular in country X" claim comes from.
 * The author's registered country is stored separately on [Video.authorCountry] and is never
 * treated as evidence of popularity.
 */
enum class RegionBasis(val reliability: Reliability) {
    /** Region-specific top list in TikTok Creative Center, transcribed by the user. */
    CREATIVE_CENTER(Reliability.MEDIUM),
    /** Region field supplied by a connected data feed (licensed source / own backend). */
    DATA_FEED(Reliability.MEDIUM),
    /** The user saw it in their own For You feed while in / targeting that region. */
    USER_OBSERVATION(Reliability.LOW),
}

data class RegionObservation(
    val countryCode: String,
    val basis: RegionBasis,
    val observedAt: Long,
    val rank: Int? = null,
    val note: String? = null,
    /** Local storage id (0 when not persisted). */
    val id: Long = 0,
)

/** A single, dated reading of public counters. Growth is only ever computed between snapshots. */
data class StatSnapshot(
    val capturedAt: Long,
    val views: Long? = null,
    val likes: Long? = null,
    val comments: Long? = null,
    val shares: Long? = null,
    val source: String = SOURCE_MANUAL,
    val id: Long = 0,
) {
    val engagement: Long?
        get() = if (likes == null && comments == null && shares == null) null
        else (likes ?: 0) + (comments ?: 0) + (shares ?: 0)
}

enum class PublishedAtSource { VIDEO_ID, SOURCE, USER }

data class Video(
    val id: String,
    val url: String,
    val platformVideoId: String? = null,
    val title: String = "",
    val authorHandle: String? = null,
    val authorFollowers: Long? = null,
    /** Country the author is registered in / based in. NOT where the video is popular. */
    val authorCountry: String? = null,
    val thumbnailUrl: String? = null,
    val publishedAt: Long? = null,
    val publishedAtSource: PublishedAtSource? = null,
    val addedAt: Long,
    val category: String? = null,
    val hashtags: List<String> = emptyList(),
    val soundId: String? = null,
    val soundTitle: String? = null,
    val formatTags: List<String> = emptyList(),
    val trendTypes: Set<TrendType> = emptySet(),
    val hookNotes: String? = null,
    val notes: String? = null,
    val favorite: Boolean = false,
    val source: String = SOURCE_MANUAL,
    val snapshots: List<StatSnapshot> = emptyList(),
    val popularIn: List<RegionObservation> = emptyList(),
) {
    val latest: StatSnapshot? get() = snapshots.maxByOrNull { it.capturedAt }
    val soundKey: String? get() = soundId?.takeIf { it.isNotBlank() } ?: soundTitle?.let { normalizeTag(it) }?.takeIf { it.isNotBlank() }
}

data class TrendSnapshot(
    val capturedAt: Long,
    val countryCode: String? = null,
    /** Number of posts using the hashtag / sound, as shown by the source. */
    val posts: Long? = null,
    val views: Long? = null,
    val rank: Int? = null,
    val source: String = SOURCE_MANUAL,
    val id: Long = 0,
)

data class Trend(
    val id: String,
    val type: TrendType,
    val name: String,
    val category: String? = null,
    val notes: String? = null,
    val hookNotes: String? = null,
    val saved: Boolean = false,
    val used: Boolean = false,
    val createdAt: Long,
    val snapshots: List<TrendSnapshot> = emptyList(),
    val popularIn: List<RegionObservation> = emptyList(),
)

const val SOURCE_MANUAL = "manual"
const val SOURCE_OEMBED = "oembed"
const val SOURCE_CSV = "csv"
const val SOURCE_FEED_PREFIX = "feed:"

/** Lower-cases, strips '#' and whitespace so "#FYP " and "fyp" are the same key. */
fun normalizeTag(raw: String): String =
    raw.trim().removePrefix("#").trim().lowercase().replace(Regex("\\s+"), "_")

fun trendId(type: TrendType, name: String): String = "${type.name}:${normalizeTag(name)}"

/** Tag keys of a video for a given trend type (used to link videos with trends). */
fun Video.keysFor(type: TrendType): List<String> = when (type) {
    TrendType.HASHTAG -> hashtags.map(::normalizeTag)
    TrendType.SOUND -> listOfNotNull(soundKey)
    TrendType.FORMAT, TrendType.MEME, TrendType.VISUAL -> formatTags.map(::normalizeTag)
}

fun Video.linksTo(trend: Trend): Boolean {
    val key = trend.id.substringAfter(':')
    return when (trend.type) {
        TrendType.SOUND -> soundKey == key || soundTitle?.let(::normalizeTag) == key
        else -> key in keysFor(trend.type)
    }
}

/** All trend ids a video contributes to. */
fun Video.trendIds(): List<String> = buildList {
    hashtags.forEach { add(trendId(TrendType.HASHTAG, it)) }
    soundKey?.let { add("${TrendType.SOUND.name}:$it") }
    val formatType = when {
        TrendType.MEME in trendTypes -> TrendType.MEME
        TrendType.VISUAL in trendTypes -> TrendType.VISUAL
        else -> TrendType.FORMAT
    }
    formatTags.forEach { add(trendId(formatType, it)) }
}.distinct()
