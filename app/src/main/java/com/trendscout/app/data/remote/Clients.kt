package com.trendscout.app.data.remote

import com.trendscout.core.ideas.ChatMessage
import com.trendscout.core.io.ImportResult
import com.trendscout.core.io.JsonFeedParser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Public TikTok oEmbed metadata. No statistics are available from this endpoint. */
@Serializable
data class OEmbed(
    val title: String? = null,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    @SerialName("author_unique_id") val authorUniqueId: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("embed_product_id") val embedProductId: String? = null,
)

/**
 * TikTok's public oEmbed endpoint (documented at developers.tiktok.com/doc/embed-videos).
 * Used only for one video at a time, on the user's explicit request.
 */
class OEmbedClient(private val net: Net) {
    suspend fun fetch(videoUrl: String): NetResult<Pair<OEmbed, String>> {
        val url = "https://www.tiktok.com/oembed".toHttpUrl().newBuilder().addQueryParameter("url", videoUrl).build()
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        return net.execute(req) { resp, body ->
            // The final URL after redirects lets us resolve vm.tiktok.com short links.
            lenientJson.decodeFromString(OEmbed.serializer(), body) to resp.request.url.toString()
        }
    }

    /** Follows a short link (vm.tiktok.com/...) and returns the final URL. */
    suspend fun resolve(shortUrl: String): NetResult<String> {
        val req = Request.Builder().url(shortUrl).head().build()
        return net.execute(req) { resp, _ -> resp.request.url.toString() }
    }
}

/** Fetches a JSON feed (own backend or licensed provider) in the documented format. */
class FeedClient(private val net: Net) {
    suspend fun fetch(url: String, token: String?): NetResult<ImportResult> {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
        return net.execute(b.build()) { _, body -> JsonFeedParser.parse(body) }
    }

    /** Reads plain text (e.g. a CSV) from a URL. */
    suspend fun fetchText(url: String): NetResult<String> =
        net.execute(Request.Builder().url(url).build()) { _, body -> body }
}

data class LlmConfig(val baseUrl: String, val model: String, val apiKey: String)

/**
 * OpenAI-compatible chat client. Default target: Zhipu GLM-4.5-Flash (free tier),
 * https://open.bigmodel.cn/api/paas/v4 or https://api.z.ai/api/paas/v4.
 * The key belongs to the user, is typed in Settings and stored encrypted on the device; nothing is
 * bundled in the APK.
 */
class LlmClient(private val net: Net) {
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(cfg: LlmConfig, messages: List<ChatMessage>, temperature: Double = 0.8): NetResult<String> {
        val first = send(cfg, messages, temperature, extras = true)
        // Some compatible endpoints reject the optional fields: retry once with a minimal body.
        if (first is NetResult.Err && first.error is NetError.Http && (first.error as NetError.Http).code == 400) {
            return send(cfg, messages, temperature, extras = false)
        }
        return first
    }

    private suspend fun send(cfg: LlmConfig, messages: List<ChatMessage>, temperature: Double, extras: Boolean): NetResult<String> {
        val body = buildJsonObject {
            put("model", cfg.model)
            put("temperature", temperature)
            put("messages", buildJsonArray {
                messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
            })
            if (extras) {
                put("thinking", buildJsonObject { put("type", "disabled") })
                put("response_format", buildJsonObject { put("type", "json_object") })
            }
        }.toString()
        val req = Request.Builder()
            .url(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .post(body.toRequestBody(mediaType))
            .build()
        return net.execute(req) { _, text ->
            val root = lenientJson.parseToJsonElement(text).jsonObject
            val choices = root["choices"] as? JsonArray ?: error("no choices")
            val msg = (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject ?: error("no message")
            (msg["content"] as? JsonPrimitive)?.contentOrNull ?: error("empty content")
        }
    }
}
