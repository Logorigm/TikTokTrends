@file:OptIn(ExperimentalLayoutApi::class)

package com.trendscout.app.ui.ideas

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.data.db.TARGET_IDEA
import com.trendscout.app.data.repo.AiOutcome
import com.trendscout.app.data.repo.Collection
import com.trendscout.app.data.repo.Library
import com.trendscout.app.data.repo.SavedIdea
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.ChipGroup
import com.trendscout.app.ui.components.ConfirmDialog
import com.trendscout.app.ui.components.Dropdown
import com.trendscout.app.ui.components.ErrorBanner
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.LoadingBox
import com.trendscout.app.ui.components.Pill
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.TextInputDialog
import com.trendscout.app.ui.components.openUrl
import com.trendscout.app.ui.components.shareText
import com.trendscout.app.ui.detail.CollectionPickerDialog
import com.trendscout.app.ui.search.BackTopBar
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.feed.TrendCatalog
import com.trendscout.core.ideas.Complexity
import com.trendscout.core.ideas.Emotion
import com.trendscout.core.ideas.Genre
import com.trendscout.core.ideas.Idea
import com.trendscout.core.ideas.IdeaOrigin
import com.trendscout.core.ideas.IdeaRequest
import com.trendscout.core.ideas.IdeaStatus
import com.trendscout.core.ideas.TrendBrief
import com.trendscout.core.model.Countries
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.linksTo
import com.trendscout.core.scoring.Insights
import com.trendscout.core.scoring.MetricsCalculator
import com.trendscout.core.scoring.ScoringContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/** Builds generator input strictly from stored data. */
object BriefFactory {
    fun forTrend(lib: Library, id: String, r: Resources, now: Long = System.currentTimeMillis()): TrendBrief? {
        val e = TrendCatalog.build(lib.videos, lib.trends, now).firstOrNull { it.trend.id == id } ?: return null
        val t = e.trend
        val linked = lib.videos.filter { it.linksTo(t) }
        val ctx = ScoringContext(lib.videos, lib.trends, now)
        val related = Insights.relatedTags(t, lib.videos)
        return TrendBrief(
            name = t.name,
            typeLabel = r.getString(L.type(t.type)),
            hashtags = (if (t.type == TrendType.HASHTAG) listOf(t.name) else emptyList()) + related.filter { it.type == TrendType.HASHTAG }.map { it.display.removePrefix("#") },
            sounds = (if (t.type == TrendType.SOUND) listOf(t.name) else emptyList()) + related.filter { it.type == TrendType.SOUND }.map { it.display },
            formatTags = Insights.recurringFormats(linked).map { it.display },
            exampleTitles = linked.map { it.title }.filter { it.isNotBlank() },
            userNotes = t.notes,
            hookNotes = (listOfNotNull(t.hookNotes) + linked.mapNotNull { it.hookNotes }).joinToString("; ").ifBlank { null },
            countries = e.countries.keys.toList(),
            dataFacts = Insights.forTrend(t, lib.videos, ctx).map { L.insight(r, it) },
        )
    }

    fun forVideo(lib: Library, id: String, r: Resources, now: Long = System.currentTimeMillis()): TrendBrief? {
        val v = lib.videos.firstOrNull { it.id == id } ?: return null
        val ctx = ScoringContext(lib.videos, lib.trends, now)
        val m = MetricsCalculator.compute(v, now)
        return TrendBrief(
            name = v.title.ifBlank { v.hashtags.firstOrNull()?.let { "#$it" } ?: v.url },
            typeLabel = r.getString(R.string.brief_video),
            hashtags = v.hashtags,
            sounds = listOfNotNull(v.soundTitle),
            formatTags = v.formatTags,
            exampleTitles = listOf(v.title).filter { it.isNotBlank() },
            userNotes = v.notes,
            hookNotes = v.hookNotes,
            countries = v.popularIn.map { it.countryCode }.distinct(),
            dataFacts = Insights.forVideo(v, m, ctx).map { L.insight(r, it) },
        )
    }
}

data class GenParams(
    val targetCountry: String = "US",
    val audience: String = "",
    val genre: Genre = Genre.VISUAL_HUMOR,
    val duration: Int = 15,
    val speech: Boolean = false,
    val complexity: Complexity = Complexity.LOW,
    val emotion: Emotion = Emotion.LAUGH,
    val count: Int = 3,
)

sealed class GenState {
    data object Idle : GenState()
    data object Loading : GenState()
    data class Done(val ideas: List<Idea>, val aiError: String? = null) : GenState()
    data class Error(val message: String, val canUseTemplate: Boolean) : GenState()
}

class IdeaGeneratorViewModel(private val c: AppContainer, private val trendId: String?, private val videoId: String?) : ViewModel() {
    val brief = MutableStateFlow<TrendBrief?>(null)
    val freeName = MutableStateFlow("")
    val params = MutableStateFlow(GenParams())
    val state = MutableStateFlow<GenState>(GenState.Idle)
    val saved = MutableStateFlow<Set<Int>>(emptySet())
    val countries = MutableStateFlow(Countries.PRIORITY)
    val hasKey = MutableStateFlow(c.ai.hasKey())

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            countries.value = s.myCountries.ifEmpty { Countries.PRIORITY }
            params.value = params.value.copy(targetCountry = countries.value.first())
            val lib = c.library.library.first { it.loaded }
            val r = c.app.resources
            brief.value = when {
                trendId != null -> BriefFactory.forTrend(lib, trendId, r)
                videoId != null -> BriefFactory.forVideo(lib, videoId, r)
                else -> null
            }
        }
    }

    private fun request(): IdeaRequest? {
        val b = brief.value ?: freeName.value.trim().takeIf { it.isNotEmpty() }?.let { TrendBrief(it, c.app.getString(R.string.brief_free)) } ?: return null
        val p = params.value
        return IdeaRequest(b, p.targetCountry, Countries.name(p.targetCountry, Locale.getDefault()), p.audience, p.genre, p.duration, p.speech, p.complexity, p.emotion, p.count)
    }

    fun generate(useAi: Boolean) = viewModelScope.launch {
        val req = request() ?: return@launch
        saved.value = emptySet()
        if (!useAi) {
            state.value = GenState.Done(c.ai.templateIdeas(req))
            return@launch
        }
        state.value = GenState.Loading
        state.value = when (val o = c.ai.ideas(req)) {
            is AiOutcome.Ok -> GenState.Done(o.value)
            else -> GenState.Error(L.ai(c.app.resources, o).orEmpty(), canUseTemplate = true)
        }
    }

    fun save(index: Int, idea: Idea) = viewModelScope.launch {
        c.library.saveIdea(idea, trendId, videoId, params.value.targetCountry, params.value.genre.name)
        saved.value = saved.value + index
    }
}

@Composable
fun IdeaGeneratorScreen(nav: Nav, trendId: String?, videoId: String?) {
    val vm = appViewModel(key = "gen_${trendId}_$videoId") { IdeaGeneratorViewModel(it, trendId, videoId) }
    val brief by vm.brief.collectAsState()
    val p by vm.params.collectAsState()
    val state by vm.state.collectAsState()
    val saved by vm.saved.collectAsState()
    val countries by vm.countries.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val freeName by vm.freeName.collectAsState()
    fun edit(f: (GenParams) -> GenParams) { vm.params.value = f(vm.params.value) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.idea_generator), nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val b = brief
                if (b == null) {
                    if (trendId != null || videoId != null) InfoNote(stringResource(R.string.loading))
                    OutlinedTextField(freeName, { vm.freeName.value = it }, label = { Text(stringResource(R.string.free_trend_name)) }, modifier = Modifier.fillMaxWidth())
                } else {
                    BriefCard(b)
                }
            }
            item { SectionTitle(stringResource(R.string.gen_params)) }
            item { Dropdown(stringResource(R.string.target_country), countries, p.targetCountry, { Fmt.country(it) }, { v -> edit { it.copy(targetCountry = v) } }) }
            item { OutlinedTextField(p.audience, { v -> edit { it.copy(audience = v) } }, label = { Text(stringResource(R.string.audience)) }, placeholder = { Text(stringResource(R.string.audience_hint)) }, modifier = Modifier.fillMaxWidth()) }
            item { Text(stringResource(R.string.genre), style = MaterialTheme.typography.labelLarge) }
            item { ChipGroup(Genre.entries, setOf(p.genre), { stringResource(L.genre(it)) }, { g -> edit { it.copy(genre = g) } }) }
            item { Text(stringResource(R.string.duration), style = MaterialTheme.typography.labelLarge) }
            item { ChipGroup(listOf(7, 15, 30, 60, 90), setOf(p.duration), { stringResource(R.string.seconds_fmt, it) }, { d -> edit { it.copy(duration = d) } }) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.speech_in_video), Modifier.weight(1f))
                    Switch(checked = p.speech, onCheckedChange = { v -> edit { it.copy(speech = v) } })
                }
            }
            item { Text(stringResource(R.string.complexity), style = MaterialTheme.typography.labelLarge) }
            item { ChipGroup(Complexity.entries, setOf(p.complexity), { stringResource(L.complexity(it)) }, { x -> edit { it.copy(complexity = x) } }) }
            item { Text(stringResource(R.string.emotion), style = MaterialTheme.typography.labelLarge) }
            item { ChipGroup(Emotion.entries, setOf(p.emotion), { stringResource(L.emotion(it)) }, { x -> edit { it.copy(emotion = x) } }) }
            item { Text(stringResource(R.string.idea_count), style = MaterialTheme.typography.labelLarge) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { n -> FilterChip(selected = p.count == n, onClick = { edit { it.copy(count = n) } }, label = { Text("$n") }) }
                }
            }
            item {
                val ready = brief != null || freeName.isNotBlank()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.generate(true) }, enabled = ready && hasKey && state != GenState.Loading, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.generate_ai))
                    }
                    OutlinedButton(onClick = { vm.generate(false) }, enabled = ready && state != GenState.Loading, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.generate_template))
                    }
                    if (!hasKey) {
                        InfoNote(stringResource(R.string.ai_key_missing_hint))
                        TextButton(onClick = { nav.settings() }) { Text(stringResource(R.string.open_ai_settings)) }
                    }
                }
            }
            when (val s = state) {
                GenState.Idle -> {}
                GenState.Loading -> item {
                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ai_thinking)) }
                }
                is GenState.Error -> item {
                    ErrorBanner(s.message)
                    if (s.canUseTemplate) TextButton(onClick = { vm.generate(false) }) { Text(stringResource(R.string.use_template_instead)) }
                }
                is GenState.Done -> {
                    item { SectionTitle(stringResource(R.string.results)) }
                    item { InfoNote(stringResource(R.string.ideas_disclaimer)) }
                    items(s.ideas.size) { i ->
                        IdeaCard(s.ideas[i], brief) {
                            if (i in saved) Text(stringResource(R.string.saved_to_library), color = Palette.Green)
                            else TextButton(onClick = { vm.save(i, s.ideas[i]) }) { Text(stringResource(R.string.save_idea)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BriefCard(b: TrendBrief) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.source_trend), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(b.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.data_used_by_generator), style = MaterialTheme.typography.labelMedium, color = Palette.Green)
            DataLine(stringResource(R.string.field_hashtags), b.hashtags.joinToString(" ") { "#$it" })
            DataLine(stringResource(R.string.field_sound), b.sounds.joinToString(", "))
            DataLine(stringResource(R.string.recurring_elements), b.formatTags.joinToString(", "))
            DataLine(stringResource(R.string.popular_in), b.countries.joinToString(" ") { Fmt.countryShort(it) })
            b.dataFacts.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun DataLine(label: String, value: String) {
    Text(
        "$label: " + value.ifBlank { stringResource(R.string.no_data) },
        style = MaterialTheme.typography.bodySmall,
        color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

fun ideaAsText(r: Resources, idea: Idea): String = buildString {
    appendLine(idea.title)
    appendLine(idea.concept)
    appendLine()
    appendLine(r.getString(R.string.script_by_seconds))
    idea.script.forEach { appendLine("${it.from}–${it.to}s: ${it.action}") }
    appendLine()
    appendLine(r.getString(R.string.hook_2s) + ": " + idea.hook)
    appendLine(r.getString(R.string.climax) + ": " + idea.climax)
    appendLine(r.getString(R.string.loop_ending) + ": " + idea.loopEnding)
    appendLine(r.getString(R.string.editing_tips) + ": " + idea.editingTips.joinToString("; "))
    appendLine(r.getString(R.string.adaptation) + ": " + idea.adaptation)
}

@Composable
fun IdeaCard(idea: Idea, brief: TrendBrief?, actions: @Composable () -> Unit) {
    val ctx = LocalContext.current
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(stringResource(L.origin(idea.origin)), color = if (idea.origin == IdeaOrigin.AI) Palette.Pink else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { shareText(ctx, ideaAsText(ctx.resources, idea)) }) { Icon(Icons.Outlined.Share, stringResource(R.string.share)) }
            }
            Text(idea.title, style = MaterialTheme.typography.titleMedium)
            Labeled(stringResource(R.string.concept), idea.concept)
            Text(stringResource(R.string.script_by_seconds), style = MaterialTheme.typography.labelLarge)
            idea.script.forEach { b ->
                Row {
                    Text("${b.from}–${b.to}s", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan, modifier = Modifier.width(64.dp))
                    Text(b.action, style = MaterialTheme.typography.bodySmall)
                }
            }
            Labeled(stringResource(R.string.hook_2s), idea.hook)
            Labeled(stringResource(R.string.climax), idea.climax)
            Labeled(stringResource(R.string.loop_ending), idea.loopEnding)
            if (idea.editingTips.isNotEmpty()) Labeled(stringResource(R.string.editing_tips), idea.editingTips.joinToString("\n") { "• $it" })
            Text(stringResource(R.string.sounds_and_tags), style = MaterialTheme.typography.labelLarge)
            val dataTags = brief?.let { it.hashtags.map { h -> "#$h" } + it.sounds.map { s -> "♪ $s" } }.orEmpty()
            if (dataTags.isEmpty()) {
                Text(stringResource(R.string.from_data) + ": " + stringResource(R.string.no_data), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.from_data) + ":", style = MaterialTheme.typography.labelSmall, color = Palette.Green)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { dataTags.take(10).forEach { Pill(it) } }
            }
            if (idea.soundAndTagNotes.isNotBlank()) {
                Text(stringResource(R.string.generator_suggestion) + ": " + idea.soundAndTagNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Labeled(stringResource(R.string.adaptation), idea.adaptation)
            actions()
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    if (text.isBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Saved idea ----------------

class IdeaDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val idea: StateFlow<SavedIdea?> = c.library.savedIdeas.map { list -> list.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val loaded = MutableStateFlow(false)
    val collections: StateFlow<List<Collection>> = c.library.collectionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { c.library.savedIdeas.first(); loaded.value = true }
    }

    fun setStatus(s: IdeaStatus) = viewModelScope.launch { c.library.updateIdea(id, status = s) }
    fun setNotes(n: String) = viewModelScope.launch { c.library.updateIdea(id, notes = n) }
    fun setPublishedUrl(u: String) = viewModelScope.launch { c.library.updateIdea(id, publishedUrl = u.trim()) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.library.deleteIdea(id); onDone() }
    fun toggleCollection(cid: Long, add: Boolean) = viewModelScope.launch {
        if (add) c.library.addToCollection(cid, TARGET_IDEA, id.toString()) else c.library.removeFromCollection(cid, TARGET_IDEA, id.toString())
    }
    fun createCollection(name: String) = viewModelScope.launch { c.library.addToCollection(c.library.createCollection(name), TARGET_IDEA, id.toString()) }
}

@Composable
fun IdeaDetailScreen(nav: Nav, id: Long) {
    val vm = appViewModel(key = "idea_$id") { IdeaDetailViewModel(it, id) }
    val saved by vm.idea.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val collections by vm.collections.collectAsState()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        BackTopBar(stringResource(R.string.idea), nav) {
            IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
        }
    }) { padding ->
        val s = saved
        when {
            s == null && !loaded -> LoadingBox(Modifier.padding(padding))
            s == null -> com.trendscout.app.ui.components.EmptyState(stringResource(R.string.not_found), stringResource(R.string.not_found_text), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(stringResource(R.string.status), style = MaterialTheme.typography.labelLarge) }
                item { ChipGroup(IdeaStatus.entries, setOf(s.status), { stringResource(L.status(it)) }, { vm.setStatus(it) }) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.trendId?.let { tid -> OutlinedButton(onClick = { nav.trend(tid) }) { Text(stringResource(R.string.open_source_trend)) } }
                        s.videoId?.let { vid -> OutlinedButton(onClick = { nav.video(vid) }) { Text(stringResource(R.string.open_source_video)) } }
                        OutlinedButton(onClick = { dialog = "collection" }) { Text(stringResource(R.string.to_collection)) }
                    }
                }
                item {
                    val parts = listOfNotNull(
                        s.targetCountry?.let { Fmt.country(it) },
                        s.genre?.let { g -> Genre.entries.firstOrNull { it.name == g }?.let { stringResource(L.genre(it)) } },
                        Fmt.date(s.createdAt),
                    )
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { IdeaCard(s.idea, null) {} }
                item { com.trendscout.app.ui.detail.EditableNote(stringResource(R.string.field_notes), s.notes, stringResource(R.string.notes_placeholder)) { dialog = "notes" } }
                item {
                    com.trendscout.app.ui.detail.EditableNote(stringResource(R.string.published_url), s.publishedUrl, stringResource(R.string.published_url_hint)) { dialog = "url" }
                }
                s.publishedUrl?.takeIf { it.isNotBlank() }?.let { u -> item { TextButton(onClick = { openUrl(ctx, u) }) { Text(stringResource(R.string.action_open_original)) } } }
            }
        }
    }
    val s = saved ?: return
    when (dialog) {
        "delete" -> ConfirmDialog(stringResource(R.string.delete_idea_q), stringResource(R.string.delete_idea_text), { dialog = null }) { dialog = null; vm.delete { nav.back() } }
        "notes" -> TextInputDialog(stringResource(R.string.field_notes), s.notes.orEmpty(), { dialog = null }, { vm.setNotes(it); dialog = null })
        "url" -> TextInputDialog(stringResource(R.string.published_url), s.publishedUrl.orEmpty(), { dialog = null }, { vm.setPublishedUrl(it); dialog = null }, singleLine = true)
        "collection" -> CollectionPickerDialog(collections, TARGET_IDEA to s.id.toString(), { dialog = null }, { cid, add -> vm.toggleCollection(cid, add) }, { vm.createCollection(it) })
    }
}
