package com.trendscout.app.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import com.trendscout.app.R
import com.trendscout.app.data.prefs.NotifyKind
import com.trendscout.app.data.remote.NetError
import com.trendscout.app.data.repo.AiOutcome
import com.trendscout.core.feed.GeoMode
import com.trendscout.core.feed.SortMode
import com.trendscout.core.ideas.Complexity
import com.trendscout.core.ideas.Emotion
import com.trendscout.core.ideas.Genre
import com.trendscout.core.ideas.IdeaOrigin
import com.trendscout.core.ideas.IdeaStatus
import com.trendscout.core.model.Period
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.Reliability
import com.trendscout.core.model.TrendType
import com.trendscout.core.scoring.FactorKind
import com.trendscout.core.scoring.Insight
import com.trendscout.core.scoring.InsightKind
import com.trendscout.core.scoring.ScoreLabel
import com.trendscout.core.scoring.ScoreWarning

/** Enum -> string resource mapping, so every visible text is translatable via res/values-xx. */
object L {
    @StringRes fun period(p: Period) = when (p) {
        Period.H24 -> R.string.period_24h
        Period.D3 -> R.string.period_3d
        Period.D7 -> R.string.period_7d
        Period.D30 -> R.string.period_30d
    }

    @StringRes fun type(t: TrendType) = when (t) {
        TrendType.SOUND -> R.string.type_sound
        TrendType.HASHTAG -> R.string.type_hashtag
        TrendType.FORMAT -> R.string.type_format
        TrendType.MEME -> R.string.type_meme
        TrendType.VISUAL -> R.string.type_visual
    }

    @StringRes fun sort(s: SortMode) = when (s) {
        SortMode.FASTEST_GROWING -> R.string.sort_fastest
        SortMode.MOST_POPULAR -> R.string.sort_popular
        SortMode.NEWEST -> R.string.sort_newest
        SortMode.ENGAGEMENT -> R.string.sort_engagement
        SortMode.TOPIC_MATCH -> R.string.sort_topic
    }

    @StringRes fun geo(g: GeoMode) = when (g) {
        GeoMode.POPULAR_IN -> R.string.geo_popular
        GeoMode.AUTHOR_COUNTRY -> R.string.geo_author
        GeoMode.EITHER -> R.string.geo_either
    }

    @StringRes fun genre(g: Genre) = when (g) {
        Genre.VISUAL_HUMOR -> R.string.genre_visual_humor
        Genre.ABSURD -> R.string.genre_absurd
        Genre.TWIST_ENDING -> R.string.genre_twist
        Genre.REACTION -> R.string.genre_reaction
        Genre.ANIMATION -> R.string.genre_animation
        Genre.MINI_STORY -> R.string.genre_story
        Genre.EXPERIMENT -> R.string.genre_experiment
        Genre.PARODY -> R.string.genre_parody
        Genre.NO_WORDS -> R.string.genre_no_words
    }

    @StringRes fun complexity(c: Complexity) = when (c) {
        Complexity.LOW -> R.string.complexity_low
        Complexity.MEDIUM -> R.string.complexity_medium
        Complexity.HIGH -> R.string.complexity_high
    }

    @StringRes fun emotion(e: Emotion) = when (e) {
        Emotion.LAUGH -> R.string.emotion_laugh
        Emotion.SURPRISE -> R.string.emotion_surprise
        Emotion.CURIOSITY -> R.string.emotion_curiosity
        Emotion.AWE -> R.string.emotion_awe
        Emotion.WARMTH -> R.string.emotion_warmth
        Emotion.SUSPENSE -> R.string.emotion_suspense
    }

    @StringRes fun status(s: IdeaStatus) = when (s) {
        IdeaStatus.IDEA -> R.string.status_idea
        IdeaStatus.IN_PRODUCTION -> R.string.status_production
        IdeaStatus.PUBLISHED -> R.string.status_published
        IdeaStatus.ARCHIVED -> R.string.status_archived
    }

    @StringRes fun origin(o: IdeaOrigin) = when (o) {
        IdeaOrigin.AI -> R.string.origin_ai
        IdeaOrigin.TEMPLATE -> R.string.origin_template
    }

    @StringRes fun basis(b: RegionBasis) = when (b) {
        RegionBasis.CREATIVE_CENTER -> R.string.basis_cc
        RegionBasis.DATA_FEED -> R.string.basis_feed
        RegionBasis.USER_OBSERVATION -> R.string.basis_user
    }

    @StringRes fun reliability(r: Reliability) = when (r) {
        Reliability.NONE -> R.string.rel_none
        Reliability.LOW -> R.string.rel_low
        Reliability.MEDIUM -> R.string.rel_medium
        Reliability.HIGH -> R.string.rel_high
    }

    @StringRes fun label(l: ScoreLabel) = when (l) {
        ScoreLabel.PROMISING -> R.string.label_promising
        ScoreLabel.WATCH -> R.string.label_watch
        ScoreLabel.LOW -> R.string.label_low
        ScoreLabel.INSUFFICIENT_DATA -> R.string.label_insufficient
    }

    @StringRes fun warning(w: ScoreWarning) = when (w) {
        ScoreWarning.SINGLE_SNAPSHOT -> R.string.warn_single_snapshot
        ScoreWarning.NO_STATS -> R.string.warn_no_stats
        ScoreWarning.NO_PUBLISH_DATE -> R.string.warn_no_date
        ScoreWarning.NO_FOLLOWERS -> R.string.warn_no_followers
        ScoreWarning.NO_GEO_DATA -> R.string.warn_no_geo
        ScoreWarning.GEO_USER_ONLY -> R.string.warn_geo_user
        ScoreWarning.SMALL_SAMPLE -> R.string.warn_small_sample
    }

    @StringRes fun factor(f: FactorKind) = when (f) {
        FactorKind.VIEW_VELOCITY -> R.string.factor_velocity
        FactorKind.AVG_VELOCITY -> R.string.factor_avg_velocity
        FactorKind.ACCELERATION -> R.string.factor_acceleration
        FactorKind.ENGAGEMENT_RATE -> R.string.factor_engagement
        FactorKind.ENGAGEMENT_GROWTH -> R.string.factor_engagement_growth
        FactorKind.FRESHNESS -> R.string.factor_freshness
        FactorKind.BREAKOUT -> R.string.factor_breakout
        FactorKind.CROSS_AUTHOR -> R.string.factor_cross_author
        FactorKind.MULTI_COUNTRY -> R.string.factor_multi_country
        FactorKind.TAG_DYNAMICS -> R.string.factor_tag_dynamics
    }

    @StringRes fun notify(k: NotifyKind) = when (k) {
        NotifyKind.NEW_FAST -> R.string.notify_new_fast
        NotifyKind.STAT_JUMP -> R.string.notify_jump
        NotifyKind.SPREAD -> R.string.notify_spread
        NotifyKind.SAVED_TAGS -> R.string.notify_saved
    }

    /** Human-readable raw value of a factor. */
    fun factorValue(r: Resources, f: FactorKind, raw: Double): String = when (f) {
        FactorKind.VIEW_VELOCITY, FactorKind.AVG_VELOCITY -> Fmt.rate(r, raw)
        FactorKind.ACCELERATION -> "×" + Fmt.ratio(raw)
        FactorKind.ENGAGEMENT_RATE -> Fmt.percent(raw)
        FactorKind.ENGAGEMENT_GROWTH -> (if (raw >= 0) "+" else "") + Fmt.percent(raw)
        FactorKind.FRESHNESS -> Fmt.hours(r, raw)
        FactorKind.BREAKOUT -> "×" + Fmt.ratio(raw)
        FactorKind.CROSS_AUTHOR -> raw.toInt().toString()
        FactorKind.MULTI_COUNTRY -> raw.toInt().toString()
        FactorKind.TAG_DYNAMICS -> (if (raw >= 0) "+" else "") + Fmt.percent(raw) + r.getString(R.string.per_day)
    }

    fun insight(r: Resources, i: Insight): String = when (i.kind) {
        InsightKind.BEYOND_AUDIENCE -> r.getString(R.string.ins_beyond, Fmt.ratio(i.value))
        InsightKind.ACCELERATING -> r.getString(R.string.ins_accelerating, Fmt.ratio(i.value))
        InsightKind.DECELERATING -> r.getString(R.string.ins_decelerating, Fmt.percent(i.value))
        InsightKind.HIGH_ENGAGEMENT -> r.getString(R.string.ins_engagement, Fmt.percent(i.value))
        InsightKind.MANY_AUTHORS -> r.getString(R.string.ins_authors, i.value?.toInt() ?: 0, i.items.firstOrNull().orEmpty())
        InsightKind.MULTI_COUNTRY -> r.getString(R.string.ins_multi_country, i.value?.toInt() ?: 0, i.items.joinToString(" ") { Fmt.countryShort(it) })
        InsightKind.SINGLE_COUNTRY -> r.getString(R.string.ins_single_country, i.items.joinToString { Fmt.country(it) })
        InsightKind.FRESH -> r.getString(R.string.ins_fresh, Fmt.hours(r, i.value))
        InsightKind.TAG_GROWTH -> r.getString(R.string.ins_tag_growth, Fmt.percent(i.value))
        InsightKind.RECURRING_ELEMENTS -> r.getString(R.string.ins_recurring, i.items.joinToString(", "))
    }

    fun netError(r: Resources, e: NetError): String = when (e) {
        NetError.NoInternet -> r.getString(R.string.err_no_internet)
        NetError.Timeout -> r.getString(R.string.err_timeout)
        NetError.Unauthorized -> r.getString(R.string.err_unauthorized)
        NetError.RateLimited -> r.getString(R.string.err_rate_limited)
        is NetError.Http -> r.getString(R.string.err_http, e.code)
        is NetError.Parse -> r.getString(R.string.err_parse)
        is NetError.Other -> r.getString(R.string.err_other, e.detail ?: "")
    }

    fun ai(r: Resources, o: AiOutcome<*>): String? = when (o) {
        is AiOutcome.Ok -> null
        AiOutcome.NoKey -> r.getString(R.string.ai_no_key)
        AiOutcome.Disabled -> r.getString(R.string.ai_disabled)
        AiOutcome.BadResponse -> r.getString(R.string.ai_bad_response)
        is AiOutcome.Failed -> netError(r, o.error)
    }
}
