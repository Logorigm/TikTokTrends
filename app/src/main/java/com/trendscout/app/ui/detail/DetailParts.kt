@file:OptIn(ExperimentalLayoutApi::class)

package com.trendscout.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trendscout.app.R
import com.trendscout.app.data.repo.AiOutcome
import com.trendscout.app.data.repo.Collection
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.Bar
import com.trendscout.app.ui.components.ChipGroup
import com.trendscout.app.ui.components.CountryPickerDialog
import com.trendscout.app.ui.components.ErrorBanner
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.NumberField
import com.trendscout.app.ui.components.ReliabilityText
import com.trendscout.app.ui.components.ScoreBadge
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.theme.Palette
import com.trendscout.core.ideas.TrendHypotheses
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.scoring.Insight
import com.trendscout.core.scoring.TrendScore

/** Score with full transparency: factors used, their values, missing data and reliability. */
@Composable
fun ScoreBreakdown(score: TrendScore) {
    val r = LocalContext.current.resources
    SectionTitle(stringResource(R.string.score_title))
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreBadge(score)
                Spacer(Modifier.width(10.dp))
                ReliabilityText(score.reliability)
            }
            Text(
                stringResource(R.string.coverage_fmt, Fmt.percent(score.coverage)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (score.factors.isNotEmpty()) {
                Text(stringResource(R.string.factors_used), style = MaterialTheme.typography.titleSmall)
                score.factors.forEach { f ->
                    Column {
                        Row {
                            Text(stringResource(L.factor(f.kind)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(L.factorValue(r, f.kind, f.raw), style = MaterialTheme.typography.bodySmall)
                        }
                        Bar(f.normalized, Modifier.padding(top = 3.dp))
                    }
                }
            }
            if (score.missing.isNotEmpty()) {
                Text(
                    stringResource(R.string.factors_missing, score.missing.joinToString(", ") { r.getString(L.factor(it)) }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            score.warnings.forEach { w ->
                Text("• " + stringResource(L.warning(w)), style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
            }
            Text(stringResource(R.string.score_method_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Facts derived from data (no AI). */
@Composable
fun InsightsBlock(insights: List<Insight>) {
    val r = LocalContext.current.resources
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FactCheck, null, tint = Palette.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.facts_from_data), style = MaterialTheme.typography.titleSmall, color = Palette.Green)
            }
            if (insights.isEmpty()) {
                Text(stringResource(R.string.no_facts), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                insights.forEach { Text("• " + L.insight(r, it), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

sealed class HypState {
    data object Idle : HypState()
    data object Loading : HypState()
    data class Done(val value: TrendHypotheses) : HypState()
    data class Error(val outcome: AiOutcome<*>) : HypState()
}

/** AI hypotheses, always labelled as unverified and kept visually apart from data facts. */
@Composable
fun HypothesesBlock(state: HypState, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val r = LocalContext.current.resources
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Palette.Pink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ai_hypotheses), style = MaterialTheme.typography.titleSmall, color = Palette.Pink)
            }
            Text(stringResource(R.string.ai_hypotheses_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (state) {
                HypState.Idle -> OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_generate_hypotheses)) }
                HypState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ai_thinking))
                }
                is HypState.Error -> {
                    ErrorBanner(L.ai(r, state.outcome).orEmpty(), onRetry = onRequest)
                    if (state.outcome is AiOutcome.NoKey || state.outcome is AiOutcome.Disabled) {
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_ai_settings)) }
                    }
                }
                is HypState.Done -> {
                    val h = state.value
                    h.hypotheses.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    if (h.firstSeconds.isNotEmpty()) {
                        Text(stringResource(R.string.first_seconds_likely), style = MaterialTheme.typography.labelLarge)
                        h.firstSeconds.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    if (h.attentionMechanism.isNotBlank()) {
                        Text(stringResource(R.string.attention_mechanism), style = MaterialTheme.typography.labelLarge)
                        Text(h.attentionMechanism, style = MaterialTheme.typography.bodySmall)
                    }
                    if (h.risks.isNotEmpty()) {
                        Text(stringResource(R.string.risks), style = MaterialTheme.typography.labelLarge)
                        h.risks.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = onRequest) { Text(stringResource(R.string.regenerate)) }
                }
            }
        }
    }
}

@Composable
fun RegionsBlock(
    authorCountry: String?,
    showAuthor: Boolean,
    observations: List<RegionObservation>,
    onEditAuthor: (() -> Unit)?,
    onAdd: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showAuthor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.author_country) + ": " + (authorCountry?.let { Fmt.country(it) } ?: stringResource(R.string.unknown)),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    if (onEditAuthor != null) TextButton(onClick = onEditAuthor) { Text(stringResource(R.string.action_edit)) }
                }
                Text(stringResource(R.string.author_country_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            Text(stringResource(R.string.popular_in), style = MaterialTheme.typography.titleSmall)
            if (observations.isEmpty()) {
                Text(stringResource(R.string.geo_no_view_data_long), style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
            }
            observations.sortedByDescending { it.observedAt }.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.country(o.countryCode) + (o.rank?.let { "  #$it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(L.basis(o.basis)) + " · " + Fmt.date(o.observedAt) + " · " + stringResource(R.string.reliability_fmt, stringResource(L.reliability(o.basis.reliability))),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(o.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_region)) }
        }
    }
}

@Composable
fun AddRegionDialog(onDismiss: () -> Unit, onSave: (RegionObservation) -> Unit) {
    var country by remember { mutableStateOf<String?>(null) }
    var basis by remember { mutableStateOf(RegionBasis.CREATIVE_CENTER) }
    var rank by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_region)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(country?.let { Fmt.country(it) } ?: stringResource(R.string.pick_country))
                }
                Text(stringResource(R.string.evidence_basis), style = MaterialTheme.typography.labelMedium)
                ChipGroup(RegionBasis.entries, setOf(basis), { stringResource(L.basis(it)) }, { basis = it })
                NumberField(stringResource(R.string.rank_optional), rank, { rank = it.filter(Char::isDigit) }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = country != null, onClick = {
                country?.let { onSave(RegionObservation(it, basis, System.currentTimeMillis(), rank.toIntOrNull())) }
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (picking) CountryPickerDialog(setOfNotNull(country), multi = false, onDismiss = { picking = false }) { country = it.firstOrNull(); picking = false }
}

/** Stats reading entered by the user (from the TikTok app / own analytics). */
data class StatsInput(val hoursAgo: Double, val views: Long?, val likes: Long?, val comments: Long?, val shares: Long?)

@Composable
fun AddStatsDialog(onDismiss: () -> Unit, onSave: (StatsInput) -> Unit) {
    var views by remember { mutableStateOf("") }
    var likes by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf("") }
    var shares by remember { mutableStateOf("") }
    var ago by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_stats)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField(stringResource(R.string.views), views, { views = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField(stringResource(R.string.likes), likes, { likes = it }, Modifier.weight(1f))
                    NumberField(stringResource(R.string.comments), comments, { comments = it }, Modifier.weight(1f))
                }
                NumberField(stringResource(R.string.shares), shares, { shares = it }, Modifier.fillMaxWidth())
                OutlinedTextField(ago, { ago = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text(stringResource(R.string.hours_ago)) }, singleLine = true)
                Text(stringResource(R.string.add_stats_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(StatsInput(ago.toDoubleOrNull() ?: 0.0, CsvImporter.parseCount(views), CsvImporter.parseCount(likes), CsvImporter.parseCount(comments), CsvImporter.parseCount(shares)))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun CollectionPickerDialog(collections: List<Collection>, itemKey: Pair<String, String>, onDismiss: () -> Unit, onToggle: (Long, Boolean) -> Unit, onCreate: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.to_collection)) },
        text = {
            Column {
                if (collections.isEmpty()) Text(stringResource(R.string.no_collections), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(collections, key = { it.id }) { c ->
                        val inside = itemKey in c.items
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(c.id, !inside) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(checked = inside, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(c.name)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newName, { newName = it }, label = { Text(stringResource(R.string.new_collection)) }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = newName.isNotBlank(), onClick = { onCreate(newName); newName = "" }) { Text(stringResource(R.string.action_add)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
fun TagChips(items: List<Pair<String, String>>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (id, label) -> com.trendscout.app.ui.components.Pill(label, color = Palette.Cyan, onClick = { onClick(id) }) }
    }
}

@Composable
fun EditableNote(title: String, value: String?, placeholder: String, onEdit: () -> Unit) {
    AppCard(onClick = onEdit) {
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                value?.takeIf { it.isNotBlank() } ?: placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
