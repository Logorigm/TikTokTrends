@file:OptIn(ExperimentalLayoutApi::class)

package com.trendscout.app.ui.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.trendscout.app.data.db.TARGET_TREND
import com.trendscout.app.data.repo.AiOutcome
import com.trendscout.app.data.repo.Collection
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.ConfirmDialog
import com.trendscout.app.ui.components.CountryPickerDialog
import com.trendscout.app.ui.components.EmptyState
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.LineChart
import com.trendscout.app.ui.components.LoadingBox
import com.trendscout.app.ui.components.NumberField
import com.trendscout.app.ui.components.ScoreBadge
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.TextInputDialog
import com.trendscout.app.ui.components.VideoCard
import com.trendscout.app.ui.components.openUrl
import com.trendscout.app.ui.search.BackTopBar
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.feed.FeedEntry
import com.trendscout.core.feed.TrendCatalog
import com.trendscout.core.feed.TrendEntry
import com.trendscout.core.ideas.TrendBrief
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.io.TikTokUrl
import com.trendscout.core.model.Period
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.linksTo
import com.trendscout.core.scoring.Insight
import com.trendscout.core.scoring.Insights
import com.trendscout.core.scoring.MetricsCalculator
import com.trendscout.core.scoring.ScoringContext
import com.trendscout.core.scoring.TagCount
import com.trendscout.core.scoring.TrendScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrendDetailUi(
    val loaded: Boolean = false,
    val entry: TrendEntry? = null,
    val examples: List<FeedEntry> = emptyList(),
    val insights: List<Insight> = emptyList(),
    val related: List<TagCount> = emptyList(),
    val recurring: List<TagCount> = emptyList(),
    val hookNotes: List<String> = emptyList(),
    val availableCountries: List<String> = emptyList(),
    val collections: List<Collection> = emptyList(),
)

class TrendDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val hyp = MutableStateFlow<HypState>(HypState.Idle)
    /** Analysis filters: country (null = all) and period (null = all time). */
    val country = MutableStateFlow<String?>(null)
    val period = MutableStateFlow<Period?>(null)

    init {
        viewModelScope.launch { c.library.ensureTrendById(id) }
    }

    val ui: StateFlow<TrendDetailUi> = combine(c.library.library, c.library.collectionsFlow, country, period) { lib, cols, cc, p ->
        val now = System.currentTimeMillis()
        val entry = TrendCatalog.build(lib.videos, lib.trends, now).firstOrNull { it.trend.id == id }
            ?: return@combine TrendDetailUi(loaded = lib.loaded, collections = cols)
        val t = entry.trend
        val ctx = ScoringContext(lib.videos, lib.trends, now)
        val horizon = p?.let { now - it.millis } ?: Long.MIN_VALUE
        val linked = lib.videos.filter { it.linksTo(t) }
            .filter { (it.publishedAt ?: it.addedAt) >= horizon }
            .filter { v -> cc == null || v.popularIn.any { it.countryCode == cc } }
        val examples = linked
            .map { v -> val m = MetricsCalculator.compute(v, now); FeedEntry(v, m, TrendScorer.scoreVideo(v, m, ctx), 0.0, emptySet()) }
            .sortedWith(compareByDescending<FeedEntry> { it.metrics.currentViewsPerHour ?: -1.0 }.thenByDescending { it.metrics.latest?.views ?: -1L })
            // One example per author first, so the list shows how different creators use the format.
            .let { list -> list.distinctBy { it.video.authorHandle ?: it.video.id } + list.filter { e -> list.distinctBy { it.video.authorHandle ?: it.video.id }.none { it === e } } }
        TrendDetailUi(
            loaded = true,
            entry = entry,
            examples = examples,
            insights = Insights.forTrend(t, lib.videos, ctx),
            related = Insights.relatedTags(t, linked),
            recurring = Insights.recurringFormats(linked),
            hookNotes = linked.mapNotNull { it.hookNotes?.takeIf { s -> s.isNotBlank() } },
            availableCountries = (entry.countries.keys + t.snapshots.mapNotNull { it.countryCode }).distinct().sorted(),
            collections = cols,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendDetailUi())

    private fun trend() = ui.value.entry?.trend

    fun toggleSaved() = viewModelScope.launch { trend()?.let { c.library.updateTrend(it.copy(saved = !it.saved)) } }
    fun toggleUsed() = viewModelScope.launch { trend()?.let { c.library.updateTrend(it.copy(used = !it.used)) } }
    fun setNotes(n: String) = viewModelScope.launch { trend()?.let { c.library.updateTrend(it.copy(notes = n.ifBlank { null })) } }
    fun setHook(n: String) = viewModelScope.launch { trend()?.let { c.library.updateTrend(it.copy(hookNotes = n.ifBlank { null })) } }
    fun addRegion(o: RegionObservation) = viewModelScope.launch { c.library.addTrendRegion(id, o) }
    fun deleteRegion(rid: Long) = viewModelScope.launch { c.library.deleteRegion(rid) }
    fun addSnapshot(s: TrendSnapshot) = viewModelScope.launch { c.library.addTrendSnapshot(id, s) }
    fun deleteSnapshot(sid: Long) = viewModelScope.launch { c.library.deleteTrendSnapshot(sid) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.library.deleteTrend(id); onDone() }
    fun setFavorite(videoId: String, f: Boolean) = viewModelScope.launch { c.library.setFavorite(videoId, f) }
    fun toggleCollection(cid: Long, add: Boolean) = viewModelScope.launch {
        if (add) c.library.addToCollection(cid, TARGET_TREND, id) else c.library.removeFromCollection(cid, TARGET_TREND, id)
    }
    fun createCollection(name: String) = viewModelScope.launch { c.library.addToCollection(c.library.createCollection(name), TARGET_TREND, id) }
    fun openTrend(tid: String, display: String, go: (String) -> Unit) = viewModelScope.launch { go(c.library.ensureTrendById(tid, display)) }

    fun brief(): TrendBrief? {
        val u = ui.value
        val e = u.entry ?: return null
        val t = e.trend
        val r = c.app.resources
        return TrendBrief(
            name = t.name,
            typeLabel = r.getString(L.type(t.type)),
            hashtags = (if (t.type == TrendType.HASHTAG) listOf(t.name) else emptyList()) + u.related.filter { it.type == TrendType.HASHTAG }.map { it.display.removePrefix("#") },
            sounds = (if (t.type == TrendType.SOUND) listOf(t.name) else emptyList()) + u.related.filter { it.type == TrendType.SOUND }.map { it.display },
            formatTags = u.recurring.map { it.display },
            exampleTitles = u.examples.map { it.video.title }.filter { it.isNotBlank() },
            userNotes = t.notes,
            hookNotes = (listOfNotNull(t.hookNotes) + u.hookNotes).joinToString("; ").ifBlank { null },
            countries = e.countries.keys.toList(),
            dataFacts = u.insights.map { L.insight(r, it) },
        )
    }

    fun requestHypotheses() = viewModelScope.launch {
        val b = brief() ?: return@launch
        hyp.value = HypState.Loading
        hyp.value = when (val o = c.ai.hypotheses(b)) {
            is AiOutcome.Ok -> HypState.Done(o.value)
            else -> HypState.Error(o)
        }
    }
}

@Composable
fun TrendDetailScreen(nav: Nav, id: String) {
    val vm = appViewModel(key = "trend_$id") { TrendDetailViewModel(it, id) }
    val ui by vm.ui.collectAsState()
    val hyp by vm.hyp.collectAsState()
    val country by vm.country.collectAsState()
    val period by vm.period.collectAsState()
    val ctx = LocalContext.current
    val r = ctx.resources
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        BackTopBar(stringResource(R.string.trend_analysis), nav) {
            ui.entry?.trend?.let { t ->
                IconButton(onClick = { vm.toggleSaved() }) {
                    Icon(if (t.saved) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.action_save_trend), tint = if (t.saved) Palette.Amber else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }) { padding ->
        val e = ui.entry
        when {
            !ui.loaded -> LoadingBox(Modifier.padding(padding))
            e == null -> EmptyState(stringResource(R.string.not_found), stringResource(R.string.not_found_text), Modifier.padding(padding))
            else -> {
                val t = e.trend
                LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                    item {
                        val prefix = when (t.type) { TrendType.HASHTAG -> "#"; TrendType.SOUND -> "♪ "; else -> "" }
                        Text(prefix + t.name, style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(L.type(t.type)) + " · " + stringResource(R.string.trend_counts, e.videoCount, e.authors), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            ScoreBadge(e.score)
                        }
                        if (t.used) Text(stringResource(R.string.trend_used_label), color = Palette.Amber, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = { nav.ideas(t.id, null) }, label = { Text(stringResource(R.string.make_ideas)) })
                            AssistChip(onClick = { vm.toggleSaved() }, label = { Text(stringResource(if (t.saved) R.string.unsave else R.string.action_save_trend)) })
                            AssistChip(onClick = { vm.toggleUsed() }, label = { Text(stringResource(if (t.used) R.string.mark_unused else R.string.mark_used)) })
                            AssistChip(onClick = { dialog = "collection" }, label = { Text(stringResource(R.string.to_collection)) })
                            val link = when (t.type) {
                                TrendType.HASHTAG -> TikTokUrl.tagUrl(t.name)
                                else -> TikTokUrl.searchUrl(t.name)
                            }
                            AssistChip(onClick = { openUrl(ctx, link) }, label = { Text(stringResource(R.string.open_in_tiktok)) })
                        }
                    }

                    item { SectionTitle(stringResource(R.string.analysis_scope)) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = country == null, onClick = { vm.country.value = null }, label = { Text(stringResource(R.string.all_countries)) }) }
                            items(ui.availableCountries) { cc -> FilterChip(selected = country == cc, onClick = { vm.country.value = cc }, label = { Text(Fmt.countryShort(cc)) }) }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = period == null, onClick = { vm.period.value = null }, label = { Text(stringResource(R.string.period_all)) }) }
                            items(Period.entries) { p -> FilterChip(selected = period == p, onClick = { vm.period.value = p }, label = { Text(stringResource(L.period(p))) }) }
                        }
                    }

                    item { SectionTitle(stringResource(R.string.dynamics), trailing = { TextButton(onClick = { dialog = "snapshot" }) { Text(stringResource(R.string.add_stats)) } }) }
                    item {
                        AppCard {
                            Column {
                                val snaps = t.snapshots.filter { s -> country == null || s.countryCode == country }
                                    .filter { s -> period == null || s.capturedAt >= System.currentTimeMillis() - period!!.millis }
                                val byCountry = snaps.groupBy { it.countryCode }
                                if (snaps.isEmpty()) InfoNote(stringResource(R.string.trend_no_history))
                                byCountry.forEach { (cc, list) ->
                                    Text(cc?.let { Fmt.country(it) } ?: stringResource(R.string.no_country), style = MaterialTheme.typography.labelLarge)
                                    LineChart(list.mapNotNull { s -> s.posts?.let { s.capturedAt to it.toDouble() } }, height = 100.dp)
                                    list.sortedByDescending { it.capturedAt }.forEach { s ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                Fmt.date(s.capturedAt) + " · " + stringResource(R.string.posts) + " " + Fmt.count(r, s.posts) +
                                                    (s.views?.let { " · " + r.getString(R.string.views) + " " + Fmt.count(r, it) } ?: "") +
                                                    (s.rank?.let { " · #$it" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                            )
                                            IconButton(onClick = { vm.deleteSnapshot(s.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { SectionTitle(stringResource(R.string.countries_evidence)) }
                    item { RegionsBlock(null, false, t.popularIn, null, onAdd = { dialog = "region" }, onDelete = { vm.deleteRegion(it) }) }
                    if (e.countries.keys.any { k -> t.popularIn.none { it.countryCode == k } }) {
                        item {
                            InfoNote(stringResource(R.string.countries_from_videos, e.countries.keys.filter { k -> t.popularIn.none { it.countryCode == k } }.joinToString(" ") { Fmt.countryShort(it) }))
                        }
                    }

                    item { ScoreBreakdown(e.score) }

                    item { SectionTitle(stringResource(R.string.related_tags)) }
                    item {
                        if (ui.related.isEmpty()) InfoNote(stringResource(R.string.no_related))
                        else TagChips(ui.related.map { it.type.name + ":" + it.key to (it.display + " ×" + it.count) }) { tid ->
                            val tc = ui.related.first { it.type.name + ":" + it.key == tid }
                            vm.openTrend(tid, tc.display.removePrefix("#")) { nav.trend(it) }
                        }
                    }

                    item { SectionTitle(stringResource(R.string.recurring_elements)) }
                    item {
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val rec = ui.recurring.filter { it.count >= 2 }
                                if (rec.isEmpty()) Text(stringResource(R.string.no_recurring), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                rec.forEach { Text("• ${it.display} — " + stringResource(R.string.in_n_videos, it.count), style = MaterialTheme.typography.bodySmall) }
                                if (ui.hookNotes.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(stringResource(R.string.first_seconds_observed), style = MaterialTheme.typography.labelLarge)
                                    ui.hookNotes.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { EditableNote(stringResource(R.string.first_seconds_and_mechanism), t.hookNotes, stringResource(R.string.hook_placeholder)) { dialog = "hook" } }

                    item { SectionTitle(stringResource(R.string.why_it_may_work)) }
                    item { InsightsBlock(ui.insights) }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { HypothesesBlock(hyp, onRequest = { vm.requestHypotheses() }, onOpenSettings = { nav.settings() }) }

                    item { SectionTitle(stringResource(R.string.examples_by_authors, ui.examples.size)) }
                    if (ui.examples.isEmpty()) item { InfoNote(stringResource(R.string.no_examples)) }
                    items(ui.examples.take(20), key = { "ex" + it.video.id }) { ex ->
                        Column(Modifier.padding(bottom = 10.dp)) {
                            VideoCard(ex, onOpen = { nav.video(ex.video.id) }, onToggleFavorite = { vm.setFavorite(ex.video.id, !ex.video.favorite) })
                        }
                    }
                    item { OutlinedButton(onClick = { nav.addVideo() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_example)) } }

                    item { SectionTitle(stringResource(R.string.your_notes)) }
                    item { EditableNote(stringResource(R.string.field_notes), t.notes, stringResource(R.string.notes_placeholder)) { dialog = "notes" } }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    val t = ui.entry?.trend ?: return
    when (dialog) {
        "delete" -> ConfirmDialog(stringResource(R.string.delete_trend_q), stringResource(R.string.delete_trend_text), { dialog = null }) {
            dialog = null
            vm.delete { nav.back() }
        }
        "region" -> AddRegionDialog({ dialog = null }) { vm.addRegion(it); dialog = null }
        "snapshot" -> AddTrendSnapshotDialog(country, { dialog = null }) { vm.addSnapshot(it); dialog = null }
        "hook" -> TextInputDialog(stringResource(R.string.first_seconds_and_mechanism), t.hookNotes.orEmpty(), { dialog = null }, { vm.setHook(it); dialog = null })
        "notes" -> TextInputDialog(stringResource(R.string.field_notes), t.notes.orEmpty(), { dialog = null }, { vm.setNotes(it); dialog = null })
        "collection" -> CollectionPickerDialog(ui.collections, TARGET_TREND to t.id, { dialog = null }, { cid, add -> vm.toggleCollection(cid, add) }, { vm.createCollection(it) })
    }
}

@Composable
private fun AddTrendSnapshotDialog(initialCountry: String?, onDismiss: () -> Unit, onSave: (TrendSnapshot) -> Unit) {
    var country by remember { mutableStateOf(initialCountry) }
    var posts by remember { mutableStateOf("") }
    var views by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_stats)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(country?.let { Fmt.country(it) } ?: stringResource(R.string.no_country))
                }
                NumberField(stringResource(R.string.posts), posts, { posts = it }, Modifier.fillMaxWidth())
                NumberField(stringResource(R.string.views), views, { views = it }, Modifier.fillMaxWidth())
                NumberField(stringResource(R.string.rank_optional), rank, { rank = it.filter(Char::isDigit) }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.trend_stats_explain), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(TrendSnapshot(System.currentTimeMillis(), country, CsvImporter.parseCount(posts), CsvImporter.parseCount(views), rank.toIntOrNull(), RegionBasis.CREATIVE_CENTER.name.lowercase()))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) CountryPickerDialog(setOfNotNull(country), multi = false, onDismiss = { picking = false }) { country = it.firstOrNull(); picking = false }
}
