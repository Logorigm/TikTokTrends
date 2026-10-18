package com.trendscout.app.data.repo

import com.trendscout.app.data.prefs.FeedSourceConfig
import com.trendscout.app.data.prefs.SecretStore
import com.trendscout.app.data.prefs.SettingsRepository
import com.trendscout.app.data.remote.FeedClient
import com.trendscout.app.data.remote.LlmClient
import com.trendscout.app.data.remote.LlmConfig
import com.trendscout.app.data.remote.NetError
import com.trendscout.app.data.remote.NetResult
import com.trendscout.app.data.remote.OEmbedClient
import com.trendscout.core.ideas.ChatMessage
import com.trendscout.core.ideas.Idea
import com.trendscout.core.ideas.IdeaRequest
import com.trendscout.core.ideas.LlmJson
import com.trendscout.core.ideas.PromptBuilder
import com.trendscout.core.ideas.TemplateIdeaGenerator
import com.trendscout.core.ideas.TrendBrief
import com.trendscout.core.ideas.TrendHypotheses
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.io.TikTokUrl
import com.trendscout.core.model.PublishedAtSource
import com.trendscout.core.model.SOURCE_FEED_PREFIX
import com.trendscout.core.model.SOURCE_MANUAL
import com.trendscout.core.model.SOURCE_OEMBED
import com.trendscout.core.model.Video
import java.util.UUID

/** Result of looking up a pasted link. [warning] explains partially missing data. */
sealed class LookupResult {
    data class Ok(val draft: Video, val alreadySaved: Boolean, val metadataError: NetError?) : LookupResult()
    data object InvalidUrl : LookupResult()
    data class ResolveFailed(val error: NetError) : LookupResult()
}

class VideoLookupService(private val oembed: OEmbedClient, private val library: LibraryRepository) {

    suspend fun lookup(input: String, now: Long = System.currentTimeMillis()): LookupResult {
        var parsed = TikTokUrl.parse(input) ?: return LookupResult.InvalidUrl
        if (parsed.isShortLink) {
            when (val r = oembed.resolve(parsed.canonicalUrl)) {
                is NetResult.Ok -> parsed = TikTokUrl.parse(r.value) ?: return LookupResult.InvalidUrl
                is NetResult.Err -> return LookupResult.ResolveFailed(r.error)
            }
            if (parsed.isShortLink) return LookupResult.InvalidUrl
        }
        library.findByUrl(parsed.canonicalUrl)?.let { return LookupResult.Ok(it, alreadySaved = true, metadataError = null) }

        val publishedFromId = parsed.videoId?.let { TikTokUrl.publishedAtFromId(it, now) }
        var draft = Video(
            id = UUID.randomUUID().toString(),
            url = parsed.canonicalUrl,
            platformVideoId = parsed.videoId,
            authorHandle = parsed.handle,
            publishedAt = publishedFromId,
            publishedAtSource = publishedFromId?.let { PublishedAtSource.VIDEO_ID },
            addedAt = now,
            source = SOURCE_MANUAL,
        )
        return when (val r = oembed.fetch(parsed.canonicalUrl)) {
            is NetResult.Ok -> {
                val o = r.value.first
                val title = o.title.orEmpty()
                draft = draft.copy(
                    title = title,
                    authorHandle = o.authorUniqueId ?: o.authorUrl?.substringAfterLast("@")?.substringBefore('?') ?: draft.authorHandle,
                    thumbnailUrl = o.thumbnailUrl,
                    hashtags = TikTokUrl.hashtagsFromText(title),
                    source = SOURCE_OEMBED,
                )
                LookupResult.Ok(draft, alreadySaved = false, metadataError = null)
            }
            // Metadata is optional: the user can still save the link and fill fields manually.
            is NetResult.Err -> LookupResult.Ok(draft, alreadySaved = false, metadataError = r.error)
        }
    }
}

class FeedSyncService(
    private val feeds: FeedClient,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val library: LibraryRepository,
) {
    data class FeedOutcome(val feed: FeedSourceConfig, val summary: ImportSummary?, val error: NetError?)

    suspend fun syncAll(now: Long = System.currentTimeMillis()): List<FeedOutcome> =
        settings.current().feeds.filter { it.enabled }.map { sync(it, now) }

    suspend fun sync(feed: FeedSourceConfig, now: Long = System.currentTimeMillis()): FeedOutcome {
        val token = secrets.get(SecretStore.feedToken(feed.id))
        return when (val r = feeds.fetch(feed.url, token)) {
            is NetResult.Ok -> {
                val summary = library.import(r.value, SOURCE_FEED_PREFIX + feed.name, now)
                val status = if (r.value.items.isEmpty() && r.value.errors.isNotEmpty()) "error:${r.value.errors.first().message}"
                else "ok:${summary.newVideos}/${summary.snapshots}"
                settings.updateFeed(feed.id) { it.copy(lastSyncAt = now, lastStatus = status) }
                FeedOutcome(feed, summary, null)
            }
            is NetResult.Err -> {
                settings.updateFeed(feed.id) { it.copy(lastSyncAt = now, lastStatus = "error:${r.error::class.simpleName}") }
                FeedOutcome(feed, null, r.error)
            }
        }
    }

    suspend fun importCsvFromUrl(url: String): NetResult<ImportSummary> = when (val r = feeds.fetchText(url)) {
        is NetResult.Ok -> NetResult.Ok(library.import(CsvImporter.parse(r.value), "csv:$url"))
        is NetResult.Err -> r
    }
}

sealed class AiOutcome<out T> {
    data class Ok<T>(val value: T) : AiOutcome<T>()
    data object NoKey : AiOutcome<Nothing>()
    data object Disabled : AiOutcome<Nothing>()
    data object BadResponse : AiOutcome<Nothing>()
    data class Failed(val error: NetError) : AiOutcome<Nothing>()
}

class AiService(
    private val llm: LlmClient,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
) {
    fun hasKey(): Boolean = secrets.has(SecretStore.LLM_KEY)

    private suspend fun config(): LlmConfig? {
        val s = settings.current()
        if (!s.llmEnabled) return null
        val key = secrets.get(SecretStore.LLM_KEY) ?: return null
        return LlmConfig(s.llmBaseUrl, s.llmModel, key)
    }

    private suspend fun <T> run(messages: List<ChatMessage>, temperature: Double, parse: (String) -> T?): AiOutcome<T> {
        val s = settings.current()
        if (!s.llmEnabled) return AiOutcome.Disabled
        val cfg = config() ?: return AiOutcome.NoKey
        return when (val r = llm.chat(cfg, messages, temperature)) {
            is NetResult.Ok -> parse(r.value)?.let { AiOutcome.Ok(it) } ?: AiOutcome.BadResponse
            is NetResult.Err -> AiOutcome.Failed(r.error)
        }
    }

    suspend fun ideas(req: IdeaRequest): AiOutcome<List<Idea>> =
        run(PromptBuilder.ideas(req), 0.9) { text -> LlmJson.parseIdeas(text).takeIf { it.isNotEmpty() } }

    suspend fun hypotheses(brief: TrendBrief): AiOutcome<TrendHypotheses> =
        run(PromptBuilder.hypotheses(brief), 0.5) { LlmJson.parseHypotheses(it) }

    /** Minimal request to validate the key and endpoint from Settings. */
    suspend fun test(): AiOutcome<String> =
        run(listOf(ChatMessage("user", "Верни JSON: {\"ok\": true}")), 0.0) { it.trim().take(40).ifBlank { null } }

    fun templateIdeas(req: IdeaRequest): List<Idea> = TemplateIdeaGenerator.generate(req)
}
