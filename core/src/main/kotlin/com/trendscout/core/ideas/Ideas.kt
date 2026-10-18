package com.trendscout.core.ideas

import kotlinx.serialization.Serializable

enum class Genre { VISUAL_HUMOR, ABSURD, TWIST_ENDING, REACTION, ANIMATION, MINI_STORY, EXPERIMENT, PARODY, NO_WORDS }

enum class Complexity { LOW, MEDIUM, HIGH }

enum class Emotion { LAUGH, SURPRISE, CURIOSITY, AWE, WARMTH, SUSPENSE }

enum class IdeaOrigin { AI, TEMPLATE }

enum class IdeaStatus { IDEA, IN_PRODUCTION, PUBLISHED, ARCHIVED }

/** Facts about the source trend that are passed to the generator. Only real data goes here. */
data class TrendBrief(
    val name: String,
    val typeLabel: String,
    val hashtags: List<String> = emptyList(),
    val sounds: List<String> = emptyList(),
    val formatTags: List<String> = emptyList(),
    val exampleTitles: List<String> = emptyList(),
    val userNotes: String? = null,
    val hookNotes: String? = null,
    val countries: List<String> = emptyList(),
    /** Short human-readable data facts, e.g. "просмотры x35 к подписчикам". */
    val dataFacts: List<String> = emptyList(),
)

data class IdeaRequest(
    val brief: TrendBrief,
    val targetCountry: String,
    val targetCountryName: String,
    val audience: String,
    val genre: Genre,
    val durationSec: Int,
    val speech: Boolean,
    val complexity: Complexity,
    val emotion: Emotion,
    val count: Int = 3,
)

@Serializable
data class Beat(val from: Int, val to: Int, val action: String)

@Serializable
data class Idea(
    val title: String,
    val concept: String,
    val script: List<Beat> = emptyList(),
    val hook: String = "",
    val climax: String = "",
    val loopEnding: String = "",
    val editingTips: List<String> = emptyList(),
    /** Generator's own suggestion about sounds/hashtags (hypothesis). Data-backed tags are shown separately. */
    val soundAndTagNotes: String = "",
    val adaptation: String = "",
    val origin: IdeaOrigin = IdeaOrigin.TEMPLATE,
)

@Serializable
data class IdeaList(val ideas: List<Idea> = emptyList())

/** "Why it may work" hypotheses produced by the LLM. Always displayed as unverified. */
@Serializable
data class TrendHypotheses(
    val hypotheses: List<String> = emptyList(),
    val firstSeconds: List<String> = emptyList(),
    val attentionMechanism: String = "",
    val risks: List<String> = emptyList(),
)
