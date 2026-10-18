package com.trendscout.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trendscout.app.R
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.search.BackTopBar
import com.trendscout.app.ui.theme.Palette

private data class SourceInfo(@StringRes val title: Int, @StringRes val status: Int, @StringRes val body: Int, val usable: Boolean)

private val sources = listOf(
    SourceInfo(R.string.src_cc_title, R.string.src_status_link, R.string.src_cc_body, true),
    SourceInfo(R.string.src_oembed_title, R.string.src_status_used, R.string.src_oembed_body, true),
    SourceInfo(R.string.src_manual_title, R.string.src_status_used, R.string.src_manual_body, true),
    SourceInfo(R.string.src_feed_title, R.string.src_status_used, R.string.src_feed_body, true),
    SourceInfo(R.string.src_display_title, R.string.src_status_future, R.string.src_display_body, false),
    SourceInfo(R.string.src_research_title, R.string.src_status_unavailable, R.string.src_research_body, false),
    SourceInfo(R.string.src_commercial_title, R.string.src_status_unavailable, R.string.src_commercial_body, false),
    SourceInfo(R.string.src_thirdparty_title, R.string.src_status_future, R.string.src_thirdparty_body, false),
    SourceInfo(R.string.src_scrapers_title, R.string.src_status_excluded, R.string.src_scrapers_body, false),
    SourceInfo(R.string.src_glm_title, R.string.src_status_used, R.string.src_glm_body, true),
)

@Composable
fun AboutDataScreen(nav: Nav) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.where_data), nav) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { InfoNote(stringResource(R.string.about_principles)) }
            item { SectionTitle(stringResource(R.string.about_how_to_start)) }
            item { Text(stringResource(R.string.about_how_to_start_body), style = MaterialTheme.typography.bodyMedium) }
            item { SectionTitle(stringResource(R.string.about_sources)) }
            items(sources) { s ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(s.title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(s.status), style = MaterialTheme.typography.labelMedium, color = if (s.usable) Palette.Green else Palette.Amber)
                        Text(stringResource(s.body), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.about_limits)) }
            item { Text(stringResource(R.string.about_limits_body), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
