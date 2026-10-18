package com.trendscout.core

import com.trendscout.core.compare.CountryComparator
import com.trendscout.core.feed.FeedEngine
import com.trendscout.core.feed.FeedQuery
import com.trendscout.core.feed.GeoMode
import com.trendscout.core.feed.SortMode
import com.trendscout.core.ideas.Complexity
import com.trendscout.core.ideas.Emotion
import com.trendscout.core.ideas.Genre
import com.trendscout.core.ideas.IdeaRequest
import com.trendscout.core.ideas.LlmJson
import com.trendscout.core.ideas.TemplateIdeaGenerator
import com.trendscout.core.ideas.TrendBrief
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.io.JsonFeedParser
import com.trendscout.core.io.TikTokUrl
import com.trendscout.core.model.Countries
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.Trend
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.scoring.MetricsCalculator
import com.trendscout.core.scoring.ScoreLabel
import com.trendscout.core.scoring.ScoreWarning
import com.trendscout.core.scoring.ScoringContext
import com.trendscout.core.scoring.TrendScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val H = 3_600_000L

class CoreTest {
    private val now = 1_760_000_000_000L

    private fun video(
        id: String, author: String = "a$id", followers: Long? = 1000, snaps: List<StatSnapshot> = emptyList(),
        tags: List<String> = listOf("cat"), popular: List<String> = emptyList(), authorCountry: String? = null,
        publishedAt: Long? = now - 30 * H,
    ) = Video(
        id = id, url = "https://www.tiktok.com/@$author/video/$id", title = "t$id", authorHandle = author,
        authorFollowers = followers, authorCountry = authorCountry, publishedAt = publishedAt, addedAt = now - 40 * H,
        hashtags = tags, snapshots = snaps,
        popularIn = popular.map { RegionObservation(it, RegionBasis.CREATIVE_CENTER, now - H) },
    )

    @Test fun singleSnapshotIsNotGrowth() {
        val v = video("1", snaps = listOf(StatSnapshot(now - H, views = 50_000, likes = 5000)))
        val m = MetricsCalculator.compute(v, now)
        assertNull(m.currentViewsPerHour)
        assertNotNull(m.avgViewsPerHourSincePublish)
        val s = TrendScorer.scoreVideo(v, m, ScoringContext(listOf(v), emptyList(), now))
        assertTrue(ScoreWarning.SINGLE_SNAPSHOT in s.warnings)
        assertTrue(s.label != ScoreLabel.PROMISING, "single snapshot must never be labelled promising")
    }

    @Test fun measuredGrowthAndAcceleration() {
        val v = video("2", snaps = listOf(
            StatSnapshot(now - 25 * H, views = 1_000),
            StatSnapshot(now - 13 * H, views = 13_000),
            StatSnapshot(now - H, views = 61_000, likes = 9000, comments = 400, shares = 600),
        ))
        val m = MetricsCalculator.compute(v, now)
        assertEquals(4000.0, m.currentViewsPerHour!!, 0.01)
        assertEquals(4.0, m.acceleration!!, 0.01)
        assertEquals(61.0, m.breakoutRatio!!, 0.01)
        assertNotNull(m.periodGrowth[com.trendscout.core.model.Period.H24])
    }

    @Test fun promisingNeedsData() {
        val vids = (1..5).map { i ->
            video("$i", author = "u$i", popular = listOf("US", "FR"), snaps = listOf(
                StatSnapshot(now - 25 * H, views = 1_000, likes = 100),
                StatSnapshot(now - 13 * H, views = 20_000, likes = 2_000),
                StatSnapshot(now - H, views = 200_000, likes = 30_000, comments = 1_000, shares = 2_000),
            ))
        }
        val trend = Trend("HASHTAG:cat", TrendType.HASHTAG, "cat", createdAt = now, snapshots = listOf(
            TrendSnapshot(now - 48 * H, "US", posts = 1000), TrendSnapshot(now - H, "US", posts = 3000),
        ))
        val entries = FeedEngine.build(vids, listOf(trend), FeedQuery(), now)
        val s = entries.first().score
        assertEquals(ScoreLabel.PROMISING, s.label)
        assertTrue(s.score!! >= 65)
    }

    @Test fun geoModesAreSeparate() {
        val a = video("1", authorCountry = "FR")
        val b = video("2", popular = listOf("FR"))
        val pop = FeedEngine.build(listOf(a, b), emptyList(), FeedQuery(countries = setOf("FR"), geoMode = GeoMode.POPULAR_IN), now)
        assertEquals(listOf("2"), pop.map { it.video.id })
        val auth = FeedEngine.build(listOf(a, b), emptyList(), FeedQuery(countries = setOf("FR"), geoMode = GeoMode.AUTHOR_COUNTRY), now)
        assertEquals(listOf("1"), auth.map { it.video.id })
    }

    @Test fun sortFastestPutsMeasuredFirst() {
        val a = video("1", snaps = listOf(StatSnapshot(now - H, views = 9_000_000)))
        val b = video("2", snaps = listOf(StatSnapshot(now - 3 * H, views = 100), StatSnapshot(now - H, views = 300)))
        val list = FeedEngine.build(listOf(a, b), emptyList(), FeedQuery(sort = SortMode.FASTEST_GROWING), now)
        assertEquals("2", list.first().video.id)
    }

    @Test fun urlParsing() {
        val p = TikTokUrl.parse("look https://www.tiktok.com/@scout2015/video/6718335390845095173?lang=en wow")!!
        assertEquals("6718335390845095173", p.videoId)
        assertEquals("scout2015", p.handle)
        assertFalse(p.isShortLink)
        assertTrue(TikTokUrl.parse("https://vm.tiktok.com/ZMabc123/")!!.isShortLink)
        assertNull(TikTokUrl.parse("https://youtube.com/watch?v=1"))
        val ts = TikTokUrl.publishedAtFromId("6718335390845095173", now)!!
        assertEquals(1564200000000L / 100000000, ts / 100000000) // July 2019
        assertEquals(listOf("fyp", "кот"), TikTokUrl.hashtagsFromText("hello #fyp #кот #FYP"))
    }

    @Test fun csvImport() {
        val csv = "url;views;likes;captured_at;popular_in;hashtags\n" +
            "https://www.tiktok.com/@a/video/7300000000000000000;\"12,5K\";1 200;2025-10-01T10:00:00Z;US|FR;#a #b\n" +
            "bad;1;2;;;\n"
        val r = CsvImporter.parse(csv)
        assertEquals(1, r.items.size)
        assertEquals(1, r.errors.size)
        assertEquals(12_500L, r.items[0].views)
        assertEquals(1_200L, r.items[0].likes)
        assertEquals(listOf("US", "FR"), r.items[0].popularIn.map { it.first })
        assertEquals(listOf("a", "b"), r.items[0].hashtags)
        assertEquals(RegionBasis.USER_OBSERVATION, r.items[0].popularBasis)
    }

    @Test fun jsonFeed() {
        val json = """{"source":{"name":"Demo"},"items":[{"url":"https://www.tiktok.com/@x/video/7300000000000000000","popular_in":[{"country":"jp","rank":3}],"stats":{"views":10},"extra":1}]}"""
        val r = JsonFeedParser.parse(json)
        assertEquals("Demo", r.sourceName)
        assertEquals("JP", r.items.single().popularIn.single().first)
        assertTrue(JsonFeedParser.parse("{oops").errors.isNotEmpty())
    }

    @Test fun comparisonIgnoresAuthorCountry() {
        val v1 = video("1", tags = listOf("dance"), popular = listOf("US", "FR"))
        val v2 = video("2", tags = listOf("baguette"), popular = listOf("FR"))
        val v3 = video("3", tags = listOf("onlyauthor"), authorCountry = "US")
        val r = CountryComparator.compare(listOf(v1, v2, v3), emptyList(), listOf("US", "FR"), null, now)
        assertEquals(listOf("#dance"), r.shared.map { it.displayName })
        assertEquals(listOf("#baguette"), r.uniqueByCountry["FR"]!!.map { it.displayName })
        assertTrue(r.uniqueByCountry["US"]!!.isEmpty())
        assertTrue(r.samples["US"]!!.isSmall)
    }

    @Test fun llmParsing() {
        val text = "Вот идеи:\n```json\n{\"ideas\":[{\"title\":\"A\",\"concept\":\"C\",\"script\":[{\"from\":\"0\",\"to\":2,\"action\":\"x\"}],\"editingTips\":\"one\"}]}\n```"
        val ideas = LlmJson.parseIdeas(text)
        assertEquals(1, ideas.size)
        assertEquals(0, ideas[0].script[0].from)
        assertEquals(listOf("one"), ideas[0].editingTips)
        assertTrue(LlmJson.parseIdeas("no json").isEmpty())
        val enc = LlmJson.encodeIdea(ideas[0])
        assertEquals(ideas[0], LlmJson.decodeIdea(enc))
    }

    @Test fun templateIdeas() {
        val req = IdeaRequest(TrendBrief("cat", "хештег", hashtags = listOf("cat")), "JP", "Япония", "", Genre.NO_WORDS, 15, false, Complexity.LOW, Emotion.LAUGH, 2)
        val ideas = TemplateIdeaGenerator.generate(req)
        assertEquals(2, ideas.size)
        assertEquals(15, ideas[0].script.last().to)
        assertTrue(ideas[0].soundAndTagNotes.contains("#cat"))
    }

    @Test fun countries() {
        assertEquals("🇺🇸", Countries.flag("US"))
        assertTrue(Countries.isValid("kr"))
        assertEquals(12, Countries.PRIORITY.size)
    }
}

class CatalogTest {
    @Test fun derivesTrendsFromVideos() {
        val now = 1_760_000_000_000L
        val v = Video(id = "1", url = "u", addedAt = now, hashtags = listOf("Cat"), soundTitle = "Song A", authorHandle = "x")
        val list = com.trendscout.core.feed.TrendCatalog.build(listOf(v), emptyList(), now)
        assertEquals(setOf("HASHTAG:cat", "SOUND:song_a"), list.map { it.trend.id }.toSet())
        assertTrue(list.all { !it.persisted && it.videoCount == 1 })
    }
}
