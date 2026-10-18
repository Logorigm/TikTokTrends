@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trendscout.app.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.data.prefs.AppSettings
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.ChipGroup
import com.trendscout.app.ui.components.EmptyState
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.LoadingBox
import com.trendscout.app.ui.components.NumberField
import com.trendscout.app.ui.components.TrendRow
import com.trendscout.app.ui.components.VideoCard
import com.trendscout.core.feed.FeedEngine
import com.trendscout.core.feed.FeedEntry
import com.trendscout.core.feed.FeedQuery
import com.trendscout.core.feed.GeoMode
import com.trendscout.core.feed.SortMode
import com.trendscout.core.feed.TrendCatalog
import com.trendscout.core.feed.TrendEntry
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.model.Period
import com.trendscout.core.model.TrendType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedTab { VIDEOS, TRENDS }

data class FeedUi(
    val loaded: Boolean = false,
    val totalVideos: Int = 0,
    val entries: List<FeedEntry> = emptyList(),
    val trends: List<TrendEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class FeedViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow(FeedQuery())
    val tab = MutableStateFlow(FeedTab.VIDEOS)

    val ui: StateFlow<FeedUi> = combine(c.library.library, query, c.settings.settings, tab) { lib, q, s, t ->
        val now = System.currentTimeMillis()
        val withTopics = q.copy(topicKeywords = s.topics)
        FeedUi(
            loaded = lib.loaded,
            totalVideos = lib.videos.size,
            entries = if (t == FeedTab.VIDEOS) FeedEngine.build(lib.videos, lib.trends, withTopics, now) else emptyList(),
            trends = if (t == FeedTab.TRENDS) TrendCatalog.build(lib.videos, lib.trends, now, q.countries, q.trendTypes, q.text) else emptyList(),
            settings = s,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FeedUi())

    fun update(f: (FeedQuery) -> FeedQuery) = query.update(f)

    fun toggleFavorite(id: String, fav: Boolean) = viewModelScope.launch { c.library.setFavorite(id, fav) }
}

@Composable
fun FeedScreen(nav: Nav) {
    val vm = appViewModel { FeedViewModel(it) }
    val ui by vm.ui.collectAsState()
    val q by vm.query.collectAsState()
    val tab by vm.tab.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    val activeFilters = listOf(
        q.categories.isNotEmpty(), q.minViews != null, q.maxViews != null, q.minViewsPerHour != null,
        q.trendTypes.isNotEmpty(), q.publishedWithin != null, q.geoMode != GeoMode.EITHER,
    ).count { it }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_trends)) },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (activeFilters > 0) Badge { Text("$activeFilters") } }) {
                            Icon(Icons.Outlined.FilterList, stringResource(R.string.filters))
                        }
                    }
                    IconButton(onClick = { nav.addVideo() }) { Icon(Icons.Outlined.Add, stringResource(R.string.add_video)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                FeedTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = { vm.tab.value = t },
                        shape = SegmentedButtonDefaults.itemShape(i, FeedTab.entries.size),
                    ) { Text(stringResource(if (t == FeedTab.VIDEOS) R.string.feed_videos else R.string.feed_trends)) }
                }
            }
            if (tab == FeedTab.VIDEOS) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortMode.entries) { s ->
                        FilterChip(selected = q.sort == s, onClick = { vm.update { it.copy(sort = s) } }, label = { Text(stringResource(L.sort(s))) })
                    }
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(selected = q.countries.isEmpty(), onClick = { vm.update { it.copy(countries = emptySet()) } }, label = { Text(stringResource(R.string.all_countries)) })
                }
                items(ui.settings.myCountries) { code ->
                    FilterChip(
                        selected = code in q.countries,
                        onClick = { vm.update { it.copy(countries = if (code in it.countries) it.countries - code else it.countries + code) } },
                        label = { Text(Fmt.countryShort(code)) },
                    )
                }
            }
            if (tab == FeedTab.VIDEOS) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = q.period == null, onClick = { vm.update { it.copy(period = null) } }, label = { Text(stringResource(R.string.period_all)) }) }
                    items(Period.entries) { p ->
                        FilterChip(selected = q.period == p, onClick = { vm.update { it.copy(period = p) } }, label = { Text(stringResource(L.period(p))) })
                    }
                }
            }

            when {
                !ui.loaded -> LoadingBox()
                ui.totalVideos == 0 && ui.trends.isEmpty() -> EmptyState(
                    title = stringResource(R.string.feed_empty_title),
                    text = stringResource(R.string.feed_empty_text),
                    icon = Icons.Outlined.TravelExplore,
                    actionLabel = stringResource(R.string.add_video),
                    onAction = { nav.addVideo() },
                    secondaryLabel = stringResource(R.string.where_data),
                    onSecondary = { nav.about() },
                )
                tab == FeedTab.VIDEOS && ui.entries.isEmpty() -> EmptyState(
                    title = stringResource(R.string.nothing_found),
                    text = stringResource(R.string.nothing_found_text),
                    actionLabel = stringResource(R.string.reset_filters),
                    onAction = { vm.update { FeedQuery(sort = it.sort) } },
                )
                tab == FeedTab.TRENDS && ui.trends.isEmpty() -> EmptyState(
                    title = stringResource(R.string.nothing_found),
                    text = stringResource(R.string.trends_empty_text),
                    actionLabel = stringResource(R.string.add_trend),
                    onAction = { nav.addTrend() },
                )
                tab == FeedTab.VIDEOS -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (q.sort == SortMode.TOPIC_MATCH && ui.settings.topics.isEmpty()) {
                        item { InfoNote(stringResource(R.string.topic_sort_hint)) }
                    }
                    if (q.countries.isNotEmpty() && q.geoMode == GeoMode.EITHER) {
                        item { InfoNote(stringResource(R.string.geo_either_hint)) }
                    }
                    items(ui.entries, key = { it.video.id }) { e ->
                        VideoCard(e, onOpen = { nav.video(e.video.id) }, onToggleFavorite = { vm.toggleFavorite(e.video.id, !e.video.favorite) })
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { InfoNote(stringResource(R.string.trends_tab_hint)) }
                    items(ui.trends, key = { it.trend.id }) { e -> TrendRow(e) { nav.trend(e.trend.id) } }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            FilterSheet(q, ui.settings.categories, onChange = { f -> vm.update(f) }, onReset = { vm.update { FeedQuery(sort = it.sort) } })
        }
    }
}

@Composable
private fun FilterSheet(q: FeedQuery, categories: List<String>, onChange: ((FeedQuery) -> FeedQuery) -> Unit, onReset: () -> Unit) {
    var minViews by remember { mutableStateOf(q.minViews?.toString() ?: "") }
    var maxViews by remember { mutableStateOf(q.maxViews?.toString() ?: "") }
    var minRate by remember { mutableStateOf(q.minViewsPerHour?.toLong()?.toString() ?: "") }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Text(stringResource(R.string.filter_geo_mode), style = MaterialTheme.typography.titleSmall)
        ChipGroup(GeoMode.entries, setOf(q.geoMode), { stringResource(L.geo(it)) }, { g -> onChange { it.copy(geoMode = g) } })
        InfoNote(stringResource(R.string.geo_mode_explain))

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filter_published), style = MaterialTheme.typography.titleSmall)
        ChipGroup(listOf<Period?>(null) + Period.entries, setOf(q.publishedWithin), { p -> if (p == null) stringResource(R.string.period_all) else stringResource(L.period(p)) }, { p -> onChange { it.copy(publishedWithin = p) } })

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.titleSmall)
        ChipGroup(TrendType.entries, q.trendTypes, { stringResource(L.type(it)) }, { t ->
            onChange { it.copy(trendTypes = if (t in it.trendTypes) it.trendTypes - t else it.trendTypes + t) }
        })

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filter_category), style = MaterialTheme.typography.titleSmall)
        ChipGroup(categories, q.categories, { it }, { cat ->
            onChange { it.copy(categories = if (cat in it.categories) it.categories - cat else it.categories + cat) }
        })

        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filter_views), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(stringResource(R.string.from), minViews, { minViews = it }, Modifier.weight(1f))
            NumberField(stringResource(R.string.to), maxViews, { maxViews = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filter_growth), style = MaterialTheme.typography.titleSmall)
        NumberField(stringResource(R.string.min_views_per_hour), minRate, { minRate = it }, Modifier.fillMaxWidth())
        InfoNote(stringResource(R.string.growth_filter_explain))

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { minViews = ""; maxViews = ""; minRate = ""; onReset() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.reset_filters))
            }
            Button(onClick = {
                onChange {
                    it.copy(
                        minViews = CsvImporter.parseCount(minViews),
                        maxViews = CsvImporter.parseCount(maxViews),
                        minViewsPerHour = CsvImporter.parseCount(minRate)?.toDouble(),
                    )
                }
            }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.apply)) }
        }
    }
}
