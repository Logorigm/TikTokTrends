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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.data.db.TARGET_VIDEO
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
import com.trendscout.app.ui.components.KeyValue
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.LineChart
import com.trendscout.app.ui.components.LoadingBox
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.TextInputDialog
import com.trendscout.app.ui.components.Thumbnail
import com.trendscout.app.ui.components.openUrl
import com.trendscout.app.ui.search.BackTopBar
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.ideas.TrendBrief
import com.trendscout.core.model.Period
import com.trendscout.core.model.PublishedAtSource
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import com.trendscout.core.model.trendIds
import com.trendscout.core.scoring.Insight
import com.trendscout.core.scoring.Insights
import com.trendscout.core.scoring.MetricsCalculator
import com.trendscout.core.scoring.ScoringContext
import com.trendscout.core.scoring.TrendScore
import com.trendscout.core.scoring.TrendScorer
import com.trendscout.core.scoring.VideoMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoDetailUi(
    val loaded: Boolean = false,
    val video: Video? = null,
    val metrics: VideoMetrics? = null,
    val score: TrendScore? = null,
    val insights: List<Insight> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val categories: List<String> = emptyList(),
)

class VideoDetailViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    val hyp = MutableStateFlow<HypState>(HypState.Idle)

    val ui: StateFlow<VideoDetailUi> = combine(c.library.library, c.library.collectionsFlow, c.settings.settings) { lib, cols, s ->
        val v = lib.videos.firstOrNull { it.id == id } ?: return@combine VideoDetailUi(loaded = lib.loaded, collections = cols)
        val now = System.currentTimeMillis()
        val ctx = ScoringContext(lib.videos, lib.trends, now)
        val m = MetricsCalculator.compute(v, now)
        VideoDetailUi(true, v, m, TrendScorer.scoreVideo(v, m, ctx), Insights.forVideo(v, m, ctx), cols, s.categories)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoDetailUi())

    private fun video() = ui.value.video

    fun setFavorite(f: Boolean) = viewModelScope.launch { c.library.setFavorite(id, f) }

    fun update(transform: (Video) -> Video) = viewModelScope.launch { video()?.let { c.library.updateVideo(transform(it)) } }

    fun addStats(s: StatsInput) = viewModelScope.launch {
        val at = System.currentTimeMillis() - (s.hoursAgo * 3_600_000).toLong()
        c.library.addSnapshot(id, StatSnapshot(at, s.views, s.likes, s.comments, s.shares))
    }

    fun deleteSnapshot(sid: Long) = viewModelScope.launch { c.library.deleteSnapshot(sid) }

    fun addRegion(o: com.trendscout.core.model.RegionObservation) = viewModelScope.launch { c.library.addVideoRegion(id, o) }

    fun deleteRegion(rid: Long) = viewModelScope.launch { c.library.deleteRegion(rid) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { c.library.deleteVideo(id); onDone() }

    fun toggleCollection(cid: Long, add: Boolean) = viewModelScope.launch {
        if (add) c.library.addToCollection(cid, TARGET_VIDEO, id) else c.library.removeFromCollection(cid, TARGET_VIDEO, id)
    }

    fun createCollection(name: String) = viewModelScope.launch {
        val cid = c.library.createCollection(name)
        c.library.addToCollection(cid, TARGET_VIDEO, id)
    }

    fun openTrend(trendId: String, display: String, go: (String) -> Unit) = viewModelScope.launch {
        go(c.library.ensureTrendById(trendId, display))
    }

    fun brief(): TrendBrief? {
        val u = ui.value
        val v = u.video ?: return null
        val r = c.app.resources
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
            dataFacts = u.insights.map { com.trendscout.app.ui.components.L.insight(r, it) },
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
fun VideoDetailScreen(nav: Nav, id: String) {
    val vm = appViewModel(key = "video_$id") { VideoDetailViewModel(it, id) }
    val ui by vm.ui.collectAsState()
    val hyp by vm.hyp.collectAsState()
    val ctx = LocalContext.current
    val r = ctx.resources
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        BackTopBar(stringResource(R.string.video_analysis), nav) {
            val v = ui.video
            if (v != null) {
                IconButton(onClick = { vm.setFavorite(!v.favorite) }) {
                    Icon(if (v.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.action_favorite), tint = if (v.favorite) Palette.Amber else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
            }
        }
    }) { padding ->
        val v = ui.video
        val m = ui.metrics
        when {
            !ui.loaded -> LoadingBox(Modifier.padding(padding))
            v == null || m == null -> EmptyState(stringResource(R.string.not_found), stringResource(R.string.not_found_text), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
                item {
                    Row {
                        Thumbnail(v.thumbnailUrl, Modifier.width(110.dp).height(170.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(v.title.ifBlank { stringResource(R.string.no_title) }, style = MaterialTheme.typography.titleMedium)
                            Text("@" + (v.authorHandle ?: "?"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                stringResource(R.string.followers_fmt, Fmt.count(r, v.authorFollowers)) +
                                    if (v.authorFollowers == null) " · " + stringResource(R.string.no_data) else "",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.published_fmt, Fmt.dateTime(v.publishedAt)) +
                                    if (v.publishedAtSource == PublishedAtSource.VIDEO_ID) " " + stringResource(R.string.from_video_id) else "",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            v.category?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Palette.Cyan) }
                            Button(onClick = { openUrl(ctx, v.url) }) { Text(stringResource(R.string.action_open_original)) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { nav.ideas(null, v.id) }, label = { Text(stringResource(R.string.make_ideas)) })
                        AssistChip(onClick = { dialog = "collection" }, label = { Text(stringResource(R.string.to_collection)) })
                        AssistChip(onClick = { dialog = "edit" }, label = { Text(stringResource(R.string.edit_tags)) })
                    }
                }

                item { SectionTitle(stringResource(R.string.dynamics), trailing = { TextButton(onClick = { dialog = "stats" }) { Text(stringResource(R.string.add_stats)) } }) }
                item {
                    AppCard {
                        Column {
                            LineChart(v.snapshots.mapNotNull { s -> s.views?.let { s.capturedAt to it.toDouble() } })
                            Spacer(Modifier.height(8.dp))
                            if (m.hasMeasuredGrowth) {
                                KeyValue(stringResource(R.string.growth_last_interval), Fmt.rate(r, m.currentViewsPerHour) + " (" + Fmt.hours(r, m.lastWindow?.hours) + ")")
                                m.acceleration?.let { KeyValue(stringResource(R.string.acceleration), "×" + Fmt.ratio(it)) }
                                Period.entries.forEach { p ->
                                    m.periodGrowth[p]?.let { w -> KeyValue(stringResource(R.string.growth_over, stringResource(L.period(p))), Fmt.rate(r, w.viewsPerHour)) }
                                }
                                m.lastWindow?.likesPerHour?.let { KeyValue(stringResource(R.string.likes_per_hour), Fmt.rate(r, it)) }
                                m.lastWindow?.commentsPerHour?.let { KeyValue(stringResource(R.string.comments_per_hour), Fmt.rate(r, it)) }
                            } else {
                                InfoNote(stringResource(R.string.growth_need_snapshots))
                                m.avgViewsPerHourSincePublish?.let { KeyValue(stringResource(R.string.avg_since_publish), Fmt.rate(r, it)) }
                            }
                            KeyValue(stringResource(R.string.engagement_rate), Fmt.percent(m.engagementRate))
                            KeyValue(stringResource(R.string.breakout_ratio), m.breakoutRatio?.let { "×" + Fmt.ratio(it) } ?: stringResource(R.string.no_data))
                        }
                    }
                }
                if (v.snapshots.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.snapshots)) }
                    items(v.snapshots.sortedByDescending { it.capturedAt }, key = { "s" + it.id }) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.dateTime(s.capturedAt), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "👁 ${Fmt.count(r, s.views)}  ♥ ${Fmt.count(r, s.likes)}  💬 ${Fmt.count(r, s.comments)}  ↗ ${Fmt.count(r, s.shares)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(s.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteSnapshot(s.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                        }
                    }
                }

                item { SectionTitle(stringResource(R.string.geography)) }
                item {
                    RegionsBlock(v.authorCountry, true, v.popularIn, onEditAuthor = { dialog = "author" }, onAdd = { dialog = "region" }, onDelete = { vm.deleteRegion(it) })
                }

                item { SectionTitle(stringResource(R.string.related_tags)) }
                item {
                    val tags = v.trendIds().map { tid ->
                        val type = TrendType.valueOf(tid.substringBefore(':'))
                        val key = tid.substringAfter(':')
                        val label = when (type) {
                            TrendType.HASHTAG -> "#" + (v.hashtags.firstOrNull { com.trendscout.core.model.normalizeTag(it) == key } ?: key)
                            TrendType.SOUND -> "♪ " + (v.soundTitle ?: key)
                            else -> v.formatTags.firstOrNull { com.trendscout.core.model.normalizeTag(it) == key } ?: key
                        }
                        tid to label
                    }
                    if (tags.isEmpty()) InfoNote(stringResource(R.string.no_tags))
                    else TagChips(tags) { tid ->
                        vm.openTrend(tid, tags.first { it.first == tid }.second.removePrefix("♪ ")) { nav.trend(it) }
                    }
                }

                item { ui.score?.let { ScoreBreakdown(it) } }

                item { SectionTitle(stringResource(R.string.why_it_may_work)) }
                item { InsightsBlock(ui.insights) }
                item { Spacer(Modifier.height(8.dp)) }
                item { HypothesesBlock(hyp, onRequest = { vm.requestHypotheses() }, onOpenSettings = { nav.settings() }) }

                item { SectionTitle(stringResource(R.string.your_notes)) }
                item { EditableNote(stringResource(R.string.field_hook), v.hookNotes, stringResource(R.string.hook_placeholder)) { dialog = "hook" } }
                item { Spacer(Modifier.height(8.dp)) }
                item { EditableNote(stringResource(R.string.field_notes), v.notes, stringResource(R.string.notes_placeholder)) { dialog = "notes" } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    val v = ui.video ?: return
    when (dialog) {
        "delete" -> ConfirmDialog(stringResource(R.string.delete_video_q), stringResource(R.string.delete_video_text), { dialog = null }) {
            dialog = null
            vm.delete { nav.back() }
        }
        "stats" -> AddStatsDialog({ dialog = null }) { vm.addStats(it); dialog = null }
        "region" -> AddRegionDialog({ dialog = null }) { vm.addRegion(it); dialog = null }
        "author" -> CountryPickerDialog(setOfNotNull(v.authorCountry), multi = false, onDismiss = { dialog = null }) { sel ->
            vm.update { it.copy(authorCountry = sel.firstOrNull()) }; dialog = null
        }
        "hook" -> TextInputDialog(stringResource(R.string.field_hook), v.hookNotes.orEmpty(), { dialog = null }, { t -> vm.update { it.copy(hookNotes = t.ifBlank { null }) }; dialog = null })
        "notes" -> TextInputDialog(stringResource(R.string.field_notes), v.notes.orEmpty(), { dialog = null }, { t -> vm.update { it.copy(notes = t.ifBlank { null }) }; dialog = null })
        "collection" -> CollectionPickerDialog(ui.collections, TARGET_VIDEO to v.id, { dialog = null }, { cid, add -> vm.toggleCollection(cid, add) }, { vm.createCollection(it) })
        "edit" -> EditTagsDialog(v, ui.categories, { dialog = null }) { updated -> vm.update { updated }; dialog = null }
    }
}

@Composable
private fun EditTagsDialog(v: Video, categories: List<String>, onDismiss: () -> Unit, onSave: (Video) -> Unit) {
    var hashtags by remember { mutableStateOf(v.hashtags.joinToString(" ") { "#$it" }) }
    var sound by remember { mutableStateOf(v.soundTitle.orEmpty()) }
    var formats by remember { mutableStateOf(v.formatTags.joinToString(", ")) }
    var followers by remember { mutableStateOf(v.authorFollowers?.toString().orEmpty()) }
    var category by remember { mutableStateOf(v.category) }
    var types by remember { mutableStateOf(v.trendTypes) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_tags)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedTextField(hashtags, { hashtags = it }, label = { Text(stringResource(R.string.field_hashtags)) })
                androidx.compose.material3.OutlinedTextField(sound, { sound = it }, label = { Text(stringResource(R.string.field_sound)) }, singleLine = true)
                androidx.compose.material3.OutlinedTextField(formats, { formats = it }, label = { Text(stringResource(R.string.field_formats)) })
                com.trendscout.app.ui.components.NumberField(stringResource(R.string.field_followers), followers, { followers = it })
                com.trendscout.app.ui.components.ChipGroup(TrendType.entries, types, { stringResource(L.type(it)) }, { t -> types = if (t in types) types - t else types + t })
                com.trendscout.app.ui.components.Dropdown(stringResource(R.string.category), categories, category, { it }, { category = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    v.copy(
                        hashtags = hashtags.split(',', ' ', '\n', ';').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                        soundTitle = sound.trim().ifBlank { null },
                        formatTags = formats.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() },
                        authorFollowers = com.trendscout.core.io.CsvImporter.parseCount(followers),
                        category = category,
                        trendTypes = types,
                    )
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
