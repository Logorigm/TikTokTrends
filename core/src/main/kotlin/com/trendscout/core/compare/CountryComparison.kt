package com.trendscout.core.compare

import com.trendscout.core.model.Period
import com.trendscout.core.model.Reliability
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.model.normalizeTag

data class TagKey(val type: TrendType, val name: String) {
    val id: String get() = "${type.name}:$name"
}

/** One hashtag / sound / format and the countries where it has popularity evidence. */
data class TagPresence(
    val key: TagKey,
    val displayName: String,
    /** Country -> number of independent observations (videos or trend records). */
    val observations: Map<String, Int>,
    val distinctAuthors: Int,
    /** Strongest evidence basis among the observations. */
    val bestReliability: Reliability,
    val firstSeenAt: Long,
) {
    val countryCount: Int get() = observations.size
}

data class CountrySample(val countryCode: String, val observations: Int, val distinctTags: Int) {
    /** Too few observations to say anything about the country as a whole. */
    val isSmall: Boolean get() = observations < CountryComparator.MIN_SAMPLE
}

data class ComparisonResult(
    val countries: List<String>,
    val samples: Map<String, CountrySample>,
    /** Present in at least two of the selected countries. */
    val shared: List<TagPresence>,
    /** Present in exactly one selected country. */
    val uniqueByCountry: Map<String, List<TagPresence>>,
    /** Has evidence in 2+ countries across all data (not only the selected ones). */
    val multiCountry: List<TagPresence>,
    /** Seen only in one country and first recorded within the last 7 days. */
    val newSingleMarket: List<TagPresence>,
    /** Sounds / formats / visual tricks that do not depend on language and are shared or multi-country. */
    val adaptable: List<TagPresence>,
)

/**
 * Compares countries using only "popular in" evidence. The author's country is deliberately ignored:
 * a French creator is not evidence that something is popular in France.
 */
object CountryComparator {
    const val MIN_SAMPLE = 10

    private class Acc(val key: TagKey, var display: String, var firstSeen: Long) {
        val obs = HashMap<String, Int>()
        val authors = HashSet<String>()
        var best = Reliability.NONE
    }

    fun compare(videos: List<Video>, trends: List<Trend>, countries: List<String>, period: Period?, now: Long): ComparisonResult {
        val horizon = period?.let { now - it.millis } ?: Long.MIN_VALUE
        val acc = HashMap<TagKey, Acc>()
        val perCountryObs = HashMap<String, Int>()

        fun record(key: TagKey, display: String, country: String, author: String?, rel: Reliability, at: Long) {
            val a = acc.getOrPut(key) { Acc(key, display, at) }
            a.obs.merge(country, 1, Int::plus)
            author?.let { a.authors += it.lowercase() }
            if (rel > a.best) a.best = rel
            if (at < a.firstSeen) a.firstSeen = at
        }

        for (v in videos) {
            val obs = v.popularIn.filter { it.observedAt >= horizon }
            if (obs.isEmpty()) continue
            val keys = buildList {
                v.hashtags.forEach { add(TagKey(TrendType.HASHTAG, normalizeTag(it)) to "#${it.removePrefix("#")}") }
                v.soundKey?.let { add(TagKey(TrendType.SOUND, it) to (v.soundTitle ?: it)) }
                val fType = if (TrendType.VISUAL in v.trendTypes) TrendType.VISUAL else if (TrendType.MEME in v.trendTypes) TrendType.MEME else TrendType.FORMAT
                v.formatTags.forEach { add(TagKey(fType, normalizeTag(it)) to it) }
            }
            for (o in obs.distinctBy { it.countryCode }) {
                perCountryObs.merge(o.countryCode, 1, Int::plus)
                for ((k, d) in keys) record(k, d, o.countryCode, v.authorHandle, o.basis.reliability, o.observedAt)
            }
        }
        for (t in trends) {
            val key = TagKey(t.type, t.id.substringAfter(':'))
            val display = if (t.type == TrendType.HASHTAG) "#${t.name.removePrefix("#")}" else t.name
            for (o in t.popularIn.filter { it.observedAt >= horizon }.distinctBy { it.countryCode }) {
                perCountryObs.merge(o.countryCode, 1, Int::plus)
                record(key, display, o.countryCode, null, o.basis.reliability, o.observedAt)
            }
        }

        val all = acc.values.map {
            TagPresence(it.key, it.display, it.obs.toMap(), it.authors.size, it.best, it.firstSeen)
        }
        val selected = countries.toSet()

        val samples = countries.associateWith { c ->
            CountrySample(c, perCountryObs[c] ?: 0, all.count { c in it.observations })
        }
        val inSelected = all.filter { p -> p.observations.keys.any { it in selected } }
        val shared = inSelected
            .filter { p -> p.observations.keys.count { it in selected } >= 2 }
            .sortedWith(compareByDescending<TagPresence> { p -> p.observations.keys.count { it in selected } }
                .thenByDescending { p -> p.observations.filterKeys { it in selected }.values.sum() })
        val unique = countries.associateWith { c ->
            inSelected.filter { p -> p.observations.keys.filter { it in selected } == listOf(c) }
                .sortedByDescending { it.observations[c] ?: 0 }
        }
        val multi = all.filter { it.countryCount >= 2 }.sortedByDescending { it.countryCount }
        val weekAgo = now - Period.D7.millis
        val newSingle = inSelected.filter { it.countryCount == 1 && it.firstSeenAt >= weekAgo }
            .sortedByDescending { it.firstSeenAt }
        val languageFree = setOf(TrendType.SOUND, TrendType.FORMAT, TrendType.VISUAL, TrendType.MEME)
        val adaptable = (shared + multi).distinctBy { it.key }
            .filter { it.key.type in languageFree }
            .sortedByDescending { it.countryCount }

        return ComparisonResult(countries, samples, shared, unique, multi, newSingle, adaptable)
    }
}
