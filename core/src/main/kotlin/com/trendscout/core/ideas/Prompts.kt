package com.trendscout.core.ideas

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class ChatMessage(val role: String, val content: String)

object PromptBuilder {

    private fun genreRu(g: Genre) = when (g) {
        Genre.VISUAL_HUMOR -> "визуальный юмор"
        Genre.ABSURD -> "абсурд"
        Genre.TWIST_ENDING -> "неожиданный финал"
        Genre.REACTION -> "реакция"
        Genre.ANIMATION -> "анимация"
        Genre.MINI_STORY -> "мини-история"
        Genre.EXPERIMENT -> "эксперимент"
        Genre.PARODY -> "пародия"
        Genre.NO_WORDS -> "тренд без слов"
    }

    private fun emotionRu(e: Emotion) = when (e) {
        Emotion.LAUGH -> "смех"
        Emotion.SURPRISE -> "удивление"
        Emotion.CURIOSITY -> "любопытство"
        Emotion.AWE -> "восхищение"
        Emotion.WARMTH -> "тёплые чувства"
        Emotion.SUSPENSE -> "напряжение / ожидание"
    }

    private fun complexityRu(c: Complexity) = when (c) {
        Complexity.LOW -> "низкая (телефон, один человек, без реквизита)"
        Complexity.MEDIUM -> "средняя (простой реквизит, 1–2 локации)"
        Complexity.HIGH -> "высокая (несколько актёров, эффекты, сложный монтаж)"
    }

    private fun briefText(b: TrendBrief): String = buildString {
        appendLine("Тренд: ${b.name} (${b.typeLabel})")
        if (b.hashtags.isNotEmpty()) appendLine("Хештеги из данных: ${b.hashtags.joinToString(", ") { "#$it" }}")
        if (b.sounds.isNotEmpty()) appendLine("Звуки из данных: ${b.sounds.joinToString(", ")}")
        if (b.formatTags.isNotEmpty()) appendLine("Повторяющиеся элементы формата: ${b.formatTags.joinToString(", ")}")
        if (b.countries.isNotEmpty()) appendLine("Страны, где есть свидетельства популярности: ${b.countries.joinToString(", ")}")
        if (b.exampleTitles.isNotEmpty()) appendLine("Описания примеров: ${b.exampleTitles.take(5).joinToString(" | ")}")
        if (!b.hookNotes.isNullOrBlank()) appendLine("Наблюдения пользователя о первых секундах: ${b.hookNotes}")
        if (!b.userNotes.isNullOrBlank()) appendLine("Заметки пользователя: ${b.userNotes}")
        if (b.dataFacts.isNotEmpty()) appendLine("Факты из статистики: ${b.dataFacts.joinToString("; ")}")
    }

    private const val RULES = """Правила:
- Не копируй чужие ролики дословно: предлагай самостоятельные вариации механики тренда.
- Не выдумывай статистику, названия звуков, хештеги или факты о популярности. Если данных нет — так и пиши: "нет данных".
- Звуки и хештеги упоминай только из переданных данных; любые другие помечай как "предположение".
- Пиши по-русски, конкретно, без воды."""

    fun ideas(req: IdeaRequest): List<ChatMessage> {
        val system = """Ты — креативный продюсер коротких вертикальных видео для международной аудитории.
$RULES
Отвечай ТОЛЬКО валидным JSON без markdown по схеме:
{"ideas":[{"title":"","concept":"","script":[{"from":0,"to":2,"action":""}],"hook":"","climax":"","loopEnding":"","editingTips":[""],"soundAndTagNotes":"","adaptation":""}]}"""
        val user = buildString {
            append(briefText(req.brief))
            appendLine()
            appendLine("Сделай ${req.count} разные оригинальные идеи ролика на основе механики этого тренда.")
            appendLine("Целевая страна: ${req.targetCountryName} (${req.targetCountry})")
            appendLine("Аудитория: ${req.audience.ifBlank { "широкая" }}")
            appendLine("Жанр: ${genreRu(req.genre)}")
            appendLine("Длительность: ${req.durationSec} сек — сценарий по секундам должен покрывать 0..${req.durationSec}")
            appendLine("Речь в кадре: ${if (req.speech) "да" else "нет, только визуал и текст на экране"}")
            appendLine("Сложность производства: ${complexityRu(req.complexity)}")
            appendLine("Желаемая реакция зрителя: ${emotionRu(req.emotion)}")
            appendLine("Поле hook — что происходит в первые 2 секунды. climax — визуальная кульминация. loopEnding — финал, плавно переходящий в начало. adaptation — как адаптировать под другую страну (культурные отсылки, язык, юмор).")
        }
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }

    fun hypotheses(brief: TrendBrief): List<ChatMessage> {
        val system = """Ты — аналитик коротких видео. Ты НЕ видишь сами ролики — только описание и статистику ниже.
$RULES
Формулируй выводы как гипотезы ("может", "вероятно"), не как доказанные факты.
Отвечай ТОЛЬКО валидным JSON без markdown по схеме:
{"hypotheses":[""],"firstSeconds":[""],"attentionMechanism":"","risks":[""]}"""
        val user = briefText(brief) + "\nПочему этот тренд может работать? Какие приёмы первых секунд вероятны? Какой механизм удержания внимания? Какие риски у повторения?"
        return listOf(ChatMessage("system", system), ChatMessage("user", user))
    }
}

/** Tolerant parser for LLM JSON output (code fences, prose around JSON, string numbers). */
object LlmJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun extractObject(text: String): String? {
        val cleaned = text.replace("```json", "```").let {
            if (it.contains("```")) it.substringAfter("```").substringBefore("```") else it
        }
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    fun parseIdeas(text: String, origin: IdeaOrigin = IdeaOrigin.AI): List<Idea> {
        val obj = extractObject(text) ?: return emptyList()
        val root = runCatching { json.parseToJsonElement(obj) }.getOrNull() as? JsonObject ?: return emptyList()
        val arr = root["ideas"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val concept = o.str("concept")
            if (concept.isBlank()) return@mapNotNull null
            Idea(
                title = o.str("title").ifBlank { concept.take(60) },
                concept = concept,
                script = (o["script"] as? JsonArray)?.mapNotNull { b ->
                    val bo = b as? JsonObject ?: return@mapNotNull null
                    Beat(bo.int("from") ?: 0, bo.int("to") ?: 0, bo.str("action"))
                } ?: emptyList(),
                hook = o.str("hook"),
                climax = o.str("climax"),
                loopEnding = o.str("loopEnding"),
                editingTips = o.strList("editingTips"),
                soundAndTagNotes = o.str("soundAndTagNotes"),
                adaptation = o.str("adaptation"),
                origin = origin,
            )
        }
    }

    fun parseHypotheses(text: String): TrendHypotheses? {
        val obj = extractObject(text) ?: return null
        val root = runCatching { json.parseToJsonElement(obj) }.getOrNull() as? JsonObject ?: return null
        val h = TrendHypotheses(
            hypotheses = root.strList("hypotheses"),
            firstSeconds = root.strList("firstSeconds"),
            attentionMechanism = root.str("attentionMechanism"),
            risks = root.strList("risks"),
        )
        return if (h.hypotheses.isEmpty() && h.attentionMechanism.isBlank()) null else h
    }

    private fun JsonObject.str(k: String): String = when (val v = this[k]) {
        is JsonPrimitive -> v.contentOrNull ?: ""
        is JsonArray -> v.joinToString("; ") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
        else -> ""
    }

    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() }

    private fun JsonObject.strList(k: String): List<String> = when (val v: JsonElement? = this[k]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        is JsonPrimitive -> listOfNotNull(v.contentOrNull?.takeIf { it.isNotBlank() })
        else -> emptyList()
    }

    fun encodeIdea(idea: Idea): String = json.encodeToString(Idea.serializer(), idea)

    fun decodeIdea(text: String): Idea? = runCatching { json.decodeFromString(Idea.serializer(), text) }.getOrNull()
}
