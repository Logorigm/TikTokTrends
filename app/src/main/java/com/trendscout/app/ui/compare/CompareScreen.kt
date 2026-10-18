@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trendscout.app.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.EmptyState
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.compare.ComparisonResult
import com.trendscout.core.compare.CountryComparator
import com.trendscout.core.compare.TagPresence
import com.trendscout.core.model.Period
import com.trendscout.core.model.Reliability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CompareUi(val myCountries: List<String> = emptyList(), val result: ComparisonResult? = null)

class CompareViewModel(private val c: AppContainer) : ViewModel() {
    val selected = MutableStateFlow<List<String>>(emptyList())
    val period = MutableStateFlow<Period?>(Period.D30)

    val ui: StateFlow<CompareUi> = combine(c.library.library, c.settings.settings, selected, period) { lib, s, sel, p ->
        CompareUi(
            myCountries = s.myCountries,
            result = if (sel.size >= 2) CountryComparator.compare(lib.videos, lib.trends, sel, p, System.currentTimeMillis()) else null,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUi())

    init {
        viewModelScope.launch {
            if (selected.value.isEmpty()) selected.value = c.settings.current().myCountries.take(2)
        }
    }

    fun toggle(code: String) {
        selected.value = if (code in selected.value) selected.value - code else selected.value + code
    }

    fun openTrend(id: String, display: String, go: (String) -> Unit) = viewModelScope.launch { go(c.library.ensureTrendById(id, display)) }
}

@Composable
fun CompareScreen(nav: Nav) {
    val vm = appViewModel { CompareViewModel(it) }
    val ui by vm.ui.collectAsState()
    val selected by vm.selected.collectAsState()
    val period by vm.period.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tab_compare)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text(stringResource(R.string.compare_pick), style = MaterialTheme.typography.labelLarge) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.myCountries) { code ->
                        FilterChip(selected = code in selected, onClick = { vm.toggle(code) }, label = { Text(Fmt.countryShort(code)) })
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = period == null, onClick = { vm.period.value = null }, label = { Text(stringResource(R.string.period_all)) }) }
                    items(Period.entries) { p -> FilterChip(selected = period == p, onClick = { vm.period.value = p }, label = { Text(stringResource(L.period(p))) }) }
                }
            }
            item { InfoNote(stringResource(R.string.compare_method)) }

            val res = ui.result
            if (res == null) {
                item { EmptyState(stringResource(R.string.compare_need_two), stringResource(R.string.compare_need_two_text)) }
                return@LazyColumn
            }
            item { SectionTitle(stringResource(R.string.sample_size)) }
            item {
                AppCard {
                    Column {
                        res.countries.forEach { cc ->
                            val s = res.samples[cc]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(Fmt.country(cc), Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.sample_fmt, s?.observations ?: 0, s?.distinctTags ?: 0),
                                    color = if (s == null || s.isSmall) Palette.Amber else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        if (res.samples.values.any { it.isSmall }) {
                            Text(stringResource(R.string.small_sample_warning, CountryComparator.MIN_SAMPLE), color = Palette.Amber, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            section(R.string.cmp_shared, R.string.cmp_shared_desc, res.shared, res.countries, vm, nav)
            res.countries.forEach { cc ->
                val list = res.uniqueByCountry[cc].orEmpty()
                item { SectionTitle(stringResource(R.string.cmp_unique_fmt, Fmt.country(cc))) }
                if (list.isEmpty()) item { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(list.take(15), key = { "u$cc" + it.key.id }) { p -> TagRow(p, res.countries) { vm.openTrend(p.key.id, p.displayName) { nav.trend(it) } } }
            }
            section(R.string.cmp_multi, R.string.cmp_multi_desc, res.multiCountry, res.countries, vm, nav)
            section(R.string.cmp_new_single, R.string.cmp_new_single_desc, res.newSingleMarket, res.countries, vm, nav)
            section(R.string.cmp_adaptable, R.string.cmp_adaptable_desc, res.adaptable, res.countries, vm, nav)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: Int, desc: Int, list: List<TagPresence>, countries: List<String>, vm: CompareViewModel, nav: Nav,
) {
    item { SectionTitle(stringResource(title)) }
    item { Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (list.isEmpty()) item { Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    items(list.take(15), key = { "$title" + it.key.id }) { p -> TagRow(p, countries) { vm.openTrend(p.key.id, p.displayName) { nav.trend(it) } } }
}

@Composable
private fun TagRow(p: TagPresence, selected: List<String>, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(stringResource(L.type(p.key.type)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                p.observations.entries.sortedByDescending { it.value }.joinToString("  ") { (cc, n) ->
                    (if (cc in selected) "" else "·") + Fmt.countryShort(cc) + " ×" + n
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(R.string.authors_fmt, p.distinctAuthors) + " · " +
                    stringResource(R.string.reliability_fmt, stringResource(L.reliability(p.bestReliability))),
                style = MaterialTheme.typography.labelSmall,
                color = if (p.bestReliability <= Reliability.LOW) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
