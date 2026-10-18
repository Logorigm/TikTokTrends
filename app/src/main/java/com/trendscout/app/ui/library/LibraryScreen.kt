@file:OptIn(ExperimentalMaterial3Api::class)

package com.trendscout.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.data.db.TARGET_IDEA
import com.trendscout.app.data.db.TARGET_TREND
import com.trendscout.app.data.db.TARGET_VIDEO
import com.trendscout.app.data.repo.Collection
import com.trendscout.app.data.repo.SavedIdea
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.ConfirmDialog
import com.trendscout.app.ui.components.EmptyState
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.Pill
import com.trendscout.app.ui.components.TextInputDialog
import com.trendscout.app.ui.components.TrendRow
import com.trendscout.app.ui.components.VideoCard
import com.trendscout.app.ui.search.BackTopBar
import com.trendscout.core.feed.FeedEngine
import com.trendscout.core.feed.FeedEntry
import com.trendscout.core.feed.FeedQuery
import com.trendscout.core.feed.SortMode
import com.trendscout.core.feed.TrendCatalog
import com.trendscout.core.feed.TrendEntry
import com.trendscout.core.ideas.Genre
import com.trendscout.core.ideas.IdeaStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibTab(val label: Int) {
    VIDEOS(R.string.lib_videos), TRENDS(R.string.lib_trends), IDEAS(R.string.lib_ideas),
    PRODUCTION(R.string.lib_production), PUBLISHED(R.string.lib_published), ARCHIVE(R.string.lib_archive), COLLECTIONS(R.string.lib_collections),
}

data class LibFilter(val text: String = "", val country: String? = null, val genre: Genre? = null)

data class LibraryUi(
    val loaded: Boolean = false,
    val videos: List<FeedEntry> = emptyList(),
    val trends: List<TrendEntry> = emptyList(),
    val archivedTrends: List<TrendEntry> = emptyList(),
    val ideas: List<SavedIdea> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val countries: List<String> = emptyList(),
)

class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(LibFilter())

    val ui: StateFlow<LibraryUi> = combine(c.library.library, c.library.savedIdeas, c.library.collectionsFlow, filter) { lib, ideas, cols, f ->
        val now = System.currentTimeMillis()
        val countries = f.country?.let { setOf(it) } ?: emptySet()
        val videos = FeedEngine.build(lib.videos, lib.trends, FeedQuery(favoritesOnly = true, countries = countries, text = f.text, sort = SortMode.NEWEST), now)
        val allTrends = TrendCatalog.build(lib.videos, lib.trends.filter { it.saved || it.used }, now, countries = countries, text = f.text)
            .filter { it.persisted }
        val q = f.text.trim().lowercase()
        LibraryUi(
            loaded = lib.loaded,
            videos = videos,
            trends = allTrends.filter { it.trend.saved && !it.trend.used },
            archivedTrends = allTrends.filter { it.trend.used },
            ideas = ideas.filter { i ->
                (f.country == null || i.targetCountry == f.country) &&
                    (f.genre == null || i.genre == f.genre.name) &&
                    (q.isEmpty() || i.idea.title.lowercase().contains(q) || i.idea.concept.lowercase().contains(q) || i.notes?.lowercase()?.contains(q) == true)
            },
            collections = cols,
            countries = (lib.videos.flatMap { v -> v.popularIn.map { it.countryCode } + listOfNotNull(v.authorCountry) } + ideas.mapNotNull { it.targetCountry }).distinct().sorted(),
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    fun setFavorite(id: String, f: Boolean) = viewModelScope.launch { c.library.setFavorite(id, f) }
    fun createCollection(name: String) = viewModelScope.launch { c.library.createCollection(name) }
    fun deleteCollection(id: Long) = viewModelScope.launch { c.library.deleteCollection(id) }
    fun renameCollection(id: Long, name: String) = viewModelScope.launch { c.library.renameCollection(id, name) }
}

@Composable
fun LibraryScreen(nav: Nav) {
    val vm = appViewModel { LibraryViewModel(it) }
    val ui by vm.ui.collectAsState()
    val f by vm.filter.collectAsState()
    var tab by rememberSaveable { mutableStateOf(LibTab.VIDEOS) }
    var newCollection by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_library)) },
            actions = { if (tab == LibTab.COLLECTIONS) IconButton(onClick = { newCollection = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_collection)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp, containerColor = MaterialTheme.colorScheme.background) {
                LibTab.entries.forEach { t -> Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) }) }
            }
            if (tab != LibTab.COLLECTIONS) {
                OutlinedTextField(
                    f.text, { v -> vm.filter.value = f.copy(text = v) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.search_library)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = f.country == null, onClick = { vm.filter.value = f.copy(country = null) }, label = { Text(stringResource(R.string.all_countries)) }) }
                    items(ui.countries) { cc -> FilterChip(selected = f.country == cc, onClick = { vm.filter.value = f.copy(country = cc) }, label = { Text(Fmt.countryShort(cc)) }) }
                }
                if (tab in setOf(LibTab.IDEAS, LibTab.PRODUCTION, LibTab.PUBLISHED, LibTab.ARCHIVE)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = f.genre == null, onClick = { vm.filter.value = f.copy(genre = null) }, label = { Text(stringResource(R.string.all_genres)) }) }
                        items(Genre.entries) { g -> FilterChip(selected = f.genre == g, onClick = { vm.filter.value = f.copy(genre = g) }, label = { Text(stringResource(L.genre(g))) }) }
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    LibTab.VIDEOS -> {
                        if (ui.videos.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty_videos), stringResource(R.string.lib_empty_videos_text)) }
                        items(ui.videos, key = { it.video.id }) { e -> VideoCard(e, { nav.video(e.video.id) }, { vm.setFavorite(e.video.id, !e.video.favorite) }) }
                    }
                    LibTab.TRENDS -> {
                        if (ui.trends.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty_trends), stringResource(R.string.lib_empty_trends_text)) }
                        items(ui.trends, key = { it.trend.id }) { e -> TrendRow(e) { nav.trend(e.trend.id) } }
                    }
                    LibTab.IDEAS, LibTab.PRODUCTION, LibTab.PUBLISHED -> {
                        val status = when (tab) { LibTab.IDEAS -> IdeaStatus.IDEA; LibTab.PRODUCTION -> IdeaStatus.IN_PRODUCTION; else -> IdeaStatus.PUBLISHED }
                        val list = ui.ideas.filter { it.status == status }
                        if (list.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty_ideas), stringResource(R.string.lib_empty_ideas_text)) }
                        items(list, key = { it.id }) { i -> IdeaRow(i) { nav.idea(i.id) } }
                    }
                    LibTab.ARCHIVE -> {
                        val ideas = ui.ideas.filter { it.status == IdeaStatus.ARCHIVED }
                        if (ui.archivedTrends.isEmpty() && ideas.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty_archive), stringResource(R.string.lib_empty_archive_text)) }
                        items(ui.archivedTrends, key = { "a" + it.trend.id }) { e -> TrendRow(e) { nav.trend(e.trend.id) } }
                        items(ideas, key = { "ai" + it.id }) { i -> IdeaRow(i) { nav.idea(i.id) } }
                    }
                    LibTab.COLLECTIONS -> {
                        if (ui.collections.isEmpty()) item {
                            EmptyState(stringResource(R.string.no_collections), stringResource(R.string.collections_hint), actionLabel = stringResource(R.string.new_collection), onAction = { newCollection = true })
                        }
                        items(ui.collections, key = { it.id }) { col ->
                            AppCard(onClick = { nav.collection(col.id) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(col.name, style = MaterialTheme.typography.titleSmall)
                                        Text(stringResource(R.string.items_fmt, col.items.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (newCollection) {
        TextInputDialog(stringResource(R.string.new_collection), "", { newCollection = false }, { if (it.isNotBlank()) vm.createCollection(it); newCollection = false }, singleLine = true)
    }
}

@Composable
fun IdeaRow(i: SavedIdea, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column {
            Text(i.idea.title, style = MaterialTheme.typography.titleSmall)
            Text(i.idea.concept, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                Pill(stringResource(L.status(i.status)))
                i.targetCountry?.let { Pill(Fmt.countryShort(it)) }
                i.genre?.let { g -> Genre.entries.firstOrNull { it.name == g }?.let { Pill(stringResource(L.genre(it))) } }
                Pill(stringResource(L.origin(i.idea.origin)))
            }
        }
    }
}

// ---------------- Collection detail ----------------

class CollectionViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    data class Ui(val collection: Collection? = null, val videos: List<FeedEntry> = emptyList(), val trends: List<TrendEntry> = emptyList(), val ideas: List<SavedIdea> = emptyList(), val loaded: Boolean = false)

    val ui: StateFlow<Ui> = combine(c.library.library, c.library.savedIdeas, c.library.collectionsFlow) { lib, ideas, cols ->
        val col = cols.firstOrNull { it.id == id } ?: return@combine Ui(loaded = lib.loaded)
        val now = System.currentTimeMillis()
        val ids = col.items.groupBy({ it.first }, { it.second })
        val vIds = ids[TARGET_VIDEO].orEmpty().toSet()
        val tIds = ids[TARGET_TREND].orEmpty().toSet()
        val iIds = ids[TARGET_IDEA].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        Ui(
            col,
            FeedEngine.build(lib.videos.filter { it.id in vIds }, lib.trends, FeedQuery(sort = SortMode.NEWEST), now),
            TrendCatalog.build(lib.videos, lib.trends, now).filter { it.trend.id in tIds },
            ideas.filter { it.id in iIds },
            true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ui())

    fun rename(name: String) = viewModelScope.launch { c.library.renameCollection(id, name) }
    fun delete(done: () -> Unit) = viewModelScope.launch { c.library.deleteCollection(id); done() }
    fun remove(type: String, itemId: String) = viewModelScope.launch { c.library.removeFromCollection(id, type, itemId) }
    fun setFavorite(vid: String, f: Boolean) = viewModelScope.launch { c.library.setFavorite(vid, f) }
}

@Composable
fun CollectionScreen(nav: Nav, id: Long) {
    val vm = appViewModel(key = "col_$id") { CollectionViewModel(it, id) }
    val ui by vm.ui.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        BackTopBar(ui.collection?.name ?: stringResource(R.string.lib_collections), nav) {
            IconButton(onClick = { dialog = "rename" }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ui.loaded && ui.videos.isEmpty() && ui.trends.isEmpty() && ui.ideas.isEmpty()) {
                item { EmptyState(stringResource(R.string.collection_empty), stringResource(R.string.collection_empty_text)) }
            }
            items(ui.trends, key = { "t" + it.trend.id }) { e -> TrendRow(e) { nav.trend(e.trend.id) } }
            items(ui.videos, key = { "v" + it.video.id }) { e -> VideoCard(e, { nav.video(e.video.id) }, { vm.setFavorite(e.video.id, !e.video.favorite) }) }
            items(ui.ideas, key = { "i" + it.id }) { i -> IdeaRow(i) { nav.idea(i.id) } }
        }
    }
    when (dialog) {
        "rename" -> TextInputDialog(stringResource(R.string.rename), ui.collection?.name.orEmpty(), { dialog = null }, { vm.rename(it); dialog = null }, singleLine = true)
        "delete" -> ConfirmDialog(stringResource(R.string.delete_collection_q), stringResource(R.string.delete_collection_text), { dialog = null }) { dialog = null; vm.delete { nav.back() } }
    }
}
