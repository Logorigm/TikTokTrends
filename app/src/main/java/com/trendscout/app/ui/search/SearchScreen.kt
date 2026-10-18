@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trendscout.app.ui.search

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.trendscout.app.data.repo.ImportSummary
import com.trendscout.app.data.repo.SavedIdea
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.Dropdown
import com.trendscout.app.ui.components.ErrorBanner
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.TrendRow
import com.trendscout.app.ui.components.VideoCard
import com.trendscout.app.ui.components.openUrl
import com.trendscout.core.feed.FeedEngine
import com.trendscout.core.feed.FeedEntry
import com.trendscout.core.feed.FeedQuery
import com.trendscout.core.feed.TrendCatalog
import com.trendscout.core.feed.TrendEntry
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.io.TikTokUrl
import com.trendscout.core.model.SOURCE_CSV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUi(
    val videos: List<FeedEntry> = emptyList(),
    val trends: List<TrendEntry> = emptyList(),
    val ideas: List<SavedIdea> = emptyList(),
    val myCountries: List<String> = emptyList(),
)

sealed class ImportState {
    data object Idle : ImportState()
    data object Running : ImportState()
    data class Done(val summary: ImportSummary) : ImportState()
    data class Failed(val message: String) : ImportState()
}

class SearchViewModel(private val c: AppContainer) : ViewModel() {
    val text = MutableStateFlow("")
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)

    val ui: StateFlow<SearchUi> = combine(c.library.library, c.library.savedIdeas, text, c.settings.settings) { lib, ideas, t, s ->
        val q = t.trim()
        if (q.length < 2) return@combine SearchUi(myCountries = s.myCountries)
        val now = System.currentTimeMillis()
        SearchUi(
            videos = FeedEngine.build(lib.videos, lib.trends, FeedQuery(text = q, sort = com.trendscout.core.feed.SortMode.NEWEST), now).take(30),
            trends = TrendCatalog.build(lib.videos, lib.trends, now, text = q).take(30),
            ideas = ideas.filter { i ->
                val l = q.lowercase()
                i.idea.title.lowercase().contains(l) || i.idea.concept.lowercase().contains(l) || (i.notes?.lowercase()?.contains(l) == true)
            }.take(30),
            myCountries = s.myCountries,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    fun importCsv(uri: Uri) = viewModelScope.launch {
        importState.value = ImportState.Running
        try {
            val content = withContext(Dispatchers.IO) {
                c.app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } ?: error("empty")
            val parsed = withContext(Dispatchers.Default) { CsvImporter.parse(content) }
            if (parsed.items.isEmpty()) {
                importState.value = ImportState.Failed(parsed.errors.take(3).joinToString("; ") { "${it.line}: ${it.message}" })
            } else {
                importState.value = ImportState.Done(c.library.import(parsed, SOURCE_CSV))
            }
        } catch (e: Exception) {
            importState.value = ImportState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    fun toggleFavorite(id: String, fav: Boolean) = viewModelScope.launch { c.library.setFavorite(id, fav) }
}

private const val CC_BASE = "https://ads.tiktok.com/business/creativecenter/inspiration/popular"

private fun creativeCenterUrl(section: String, country: String, days: Int): String {
    val path = if (section.isEmpty()) "$CC_BASE/pc/en" else "$CC_BASE/$section/pc/en"
    return "$path?countryCode=$country&period=$days"
}

@Composable
fun SearchScreen(nav: Nav) {
    val vm = appViewModel { SearchViewModel(it) }
    val ui by vm.ui.collectAsState()
    val text by vm.text.collectAsState()
    val importState by vm.importState.collectAsState()
    val ctx = LocalContext.current
    val r = ctx.resources
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importCsv(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_search)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { vm.text.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { vm.text.value = "" }) { Icon(Icons.Outlined.Clear, null) } },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                )
            }
            val q = text.trim()
            if (q.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TikTokUrl.parse(q)?.let {
                            AssistChip(onClick = { nav.addVideo(q) }, label = { Text(stringResource(R.string.add_this_link)) }, leadingIcon = { Icon(Icons.Outlined.Link, null) })
                        }
                        AssistChip(onClick = { openUrl(ctx, TikTokUrl.searchUrl(q)) }, label = { Text(stringResource(R.string.search_in_tiktok)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) })
                        if (!q.contains(' ')) {
                            AssistChip(onClick = { openUrl(ctx, TikTokUrl.tagUrl(q)) }, label = { Text(stringResource(R.string.open_tag_tiktok)) }, leadingIcon = { Icon(Icons.Outlined.Tag, null) })
                        }
                    }
                }
            }
            if (q.length >= 2) {
                if (ui.trends.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.saved_trends_and_tags)) }
                    items(ui.trends, key = { "t" + it.trend.id }) { e -> TrendRow(e) { nav.trend(e.trend.id) } }
                }
                if (ui.videos.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.videos)) }
                    items(ui.videos, key = { "v" + it.video.id }) { e ->
                        VideoCard(e, onOpen = { nav.video(e.video.id) }, onToggleFavorite = { vm.toggleFavorite(e.video.id, !e.video.favorite) })
                    }
                }
                if (ui.ideas.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.ideas)) }
                    items(ui.ideas, key = { "i" + it.id }) { i ->
                        AppCard(onClick = { nav.idea(i.id) }) {
                            Column {
                                Text(i.idea.title, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(L.status(i.status)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (ui.trends.isEmpty() && ui.videos.isEmpty() && ui.ideas.isEmpty()) {
                    item { InfoNote(stringResource(R.string.search_local_empty)) }
                }
            } else {
                item { SectionTitle(stringResource(R.string.add_data)) }
                item {
                    ActionCard(Icons.Outlined.Link, stringResource(R.string.add_video), stringResource(R.string.add_video_desc)) { nav.addVideo() }
                }
                item {
                    ActionCard(Icons.Outlined.Tag, stringResource(R.string.add_trend), stringResource(R.string.add_trend_desc)) { nav.addTrend() }
                }
                item {
                    ActionCard(Icons.Outlined.UploadFile, stringResource(R.string.import_csv), stringResource(R.string.import_csv_desc)) {
                        picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*"))
                    }
                }
                when (val s = importState) {
                    ImportState.Running -> item { InfoNote(stringResource(R.string.importing)) }
                    is ImportState.Done -> item {
                        InfoNote(r.getString(R.string.import_done, s.summary.newVideos, s.summary.updatedVideos, s.summary.snapshots, s.summary.regions, s.summary.errors))
                    }
                    is ImportState.Failed -> item { ErrorBanner(r.getString(R.string.import_failed, s.message), onDismiss = { vm.importState.value = ImportState.Idle }) }
                    ImportState.Idle -> {}
                }
                item { SectionTitle(stringResource(R.string.creative_center)) }
                item { CreativeCenterBlock(ui.myCountries) }
            }
        }
    }
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CreativeCenterBlock(countries: List<String>) {
    val ctx = LocalContext.current
    var country by remember { mutableStateOf(countries.firstOrNull() ?: "US") }
    var days by remember { mutableStateOf(7) }
    AppCard {
        Column {
            Text(stringResource(R.string.creative_center_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Dropdown(stringResource(R.string.country), countries.ifEmpty { listOf("US") }, country, { Fmt.country(it) }, { country = it })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 120).forEach { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(R.string.days_fmt, d)) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { openUrl(ctx, creativeCenterUrl("hashtag", country, days)) }, label = { Text(stringResource(R.string.cc_hashtags)) })
                AssistChip(onClick = { openUrl(ctx, creativeCenterUrl("music", country, days)) }, label = { Text(stringResource(R.string.cc_songs)) })
                AssistChip(onClick = { openUrl(ctx, creativeCenterUrl("creator", country, days)) }, label = { Text(stringResource(R.string.cc_creators)) })
                AssistChip(onClick = { openUrl(ctx, creativeCenterUrl("", country, days)) }, label = { Text(stringResource(R.string.cc_videos)) })
            }
            InfoNote(stringResource(R.string.cc_manual_note))
        }
    }
}
