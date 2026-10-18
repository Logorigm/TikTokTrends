@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trendscout.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.R
import com.trendscout.app.data.remote.NetError
import com.trendscout.app.data.repo.LookupResult
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.ChipGroup
import com.trendscout.app.ui.components.CountryPickerDialog
import com.trendscout.app.ui.components.Dropdown
import com.trendscout.app.ui.components.ErrorBanner
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.NumberField
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.Thumbnail
import com.trendscout.core.io.CsvImporter
import com.trendscout.core.model.RegionBasis
import com.trendscout.core.model.RegionObservation
import com.trendscout.core.model.StatSnapshot
import com.trendscout.core.model.TrendSnapshot
import com.trendscout.core.model.TrendType
import com.trendscout.core.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun BackTopBar(title: String, nav: Nav, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Editable form state for a video before saving. Strings keep exactly what the user typed. */
data class VideoForm(
    val title: String = "",
    val author: String = "",
    val followers: String = "",
    val authorCountry: String? = null,
    val category: String? = null,
    val hashtags: String = "",
    val sound: String = "",
    val formats: String = "",
    val types: Set<TrendType> = emptySet(),
    val popularCountries: Set<String> = emptySet(),
    val popularBasis: RegionBasis = RegionBasis.USER_OBSERVATION,
    val views: String = "",
    val likes: String = "",
    val comments: String = "",
    val shares: String = "",
    val hookNotes: String = "",
    val notes: String = "",
)

sealed class AddState {
    data object Idle : AddState()
    data object Loading : AddState()
    data class Ready(val draft: Video, val alreadySaved: Boolean, val metadataError: NetError?) : AddState()
    data object Invalid : AddState()
    data class Failed(val error: NetError) : AddState()
}

class AddVideoViewModel(private val c: AppContainer) : ViewModel() {
    val state = MutableStateFlow<AddState>(AddState.Idle)
    val form = MutableStateFlow(VideoForm())
    val categories = c.settings.settings

    fun lookup(input: String) = viewModelScope.launch {
        state.value = AddState.Loading
        state.value = when (val r = c.lookup.lookup(input)) {
            is LookupResult.Ok -> {
                val d = r.draft
                form.value = VideoForm(
                    title = d.title,
                    author = d.authorHandle.orEmpty(),
                    followers = d.authorFollowers?.toString().orEmpty(),
                    authorCountry = d.authorCountry,
                    category = d.category,
                    hashtags = d.hashtags.joinToString(" ") { "#$it" },
                    sound = d.soundTitle.orEmpty(),
                    formats = d.formatTags.joinToString(", "),
                    types = d.trendTypes,
                )
                AddState.Ready(d, r.alreadySaved, r.metadataError)
            }
            LookupResult.InvalidUrl -> AddState.Invalid
            is LookupResult.ResolveFailed -> AddState.Failed(r.error)
        }
    }

    fun save(onSaved: (String) -> Unit) = viewModelScope.launch {
        val s = state.value as? AddState.Ready ?: return@launch
        val f = form.value
        val now = System.currentTimeMillis()
        val video = s.draft.copy(
            title = f.title.trim(),
            authorHandle = f.author.trim().removePrefix("@").ifBlank { null },
            authorFollowers = CsvImporter.parseCount(f.followers),
            authorCountry = f.authorCountry,
            category = f.category,
            hashtags = c.library.splitTags(f.hashtags),
            soundTitle = f.sound.trim().ifBlank { null },
            formatTags = f.formats.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() },
            trendTypes = f.types,
            hookNotes = f.hookNotes.trim().ifBlank { null },
            notes = f.notes.trim().ifBlank { null },
        )
        val snap = StatSnapshot(
            capturedAt = now,
            views = CsvImporter.parseCount(f.views),
            likes = CsvImporter.parseCount(f.likes),
            comments = CsvImporter.parseCount(f.comments),
            shares = CsvImporter.parseCount(f.shares),
        )
        val regions = f.popularCountries.map { RegionObservation(it, f.popularBasis, now) }
        val id = c.library.saveVideo(video, snap, regions)
        onSaved(id)
    }
}

@Composable
fun AddVideoScreen(nav: Nav, initialUrl: String) {
    val vm = appViewModel(key = "add_video") { AddVideoViewModel(it) }
    val state by vm.state.collectAsState()
    val form by vm.form.collectAsState()
    val settings by vm.categories.collectAsState(initial = null)
    val clipboard = LocalClipboardManager.current
    val r = LocalContext.current.resources
    var url by remember { mutableStateOf(initialUrl) }
    var pickAuthorCountry by remember { mutableStateOf(false) }
    var pickPopular by remember { mutableStateOf(false) }

    LaunchedEffect(initialUrl) { if (initialUrl.isNotBlank() && state == AddState.Idle) vm.lookup(initialUrl) }

    fun edit(f: (VideoForm) -> VideoForm) { vm.form.value = f(vm.form.value) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.add_video), nav) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text(stringResource(R.string.tiktok_link)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let { url = it } }) { Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste)) }
                },
            )
            Button(onClick = { vm.lookup(url) }, enabled = url.isNotBlank() && state != AddState.Loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lookup))
            }
            InfoNote(stringResource(R.string.oembed_note))

            when (val s = state) {
                AddState.Idle -> {}
                AddState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.loading))
                }
                AddState.Invalid -> ErrorBanner(stringResource(R.string.err_invalid_link))
                is AddState.Failed -> ErrorBanner(L.netError(r, s.error), onRetry = { vm.lookup(url) })
                is AddState.Ready -> if (s.alreadySaved) {
                    InfoNote(stringResource(R.string.already_saved))
                    OutlinedButton(onClick = { nav.back(); nav.video(s.draft.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.open_saved)) }
                } else {
                    s.metadataError?.let { ErrorBanner(stringResource(R.string.metadata_failed, L.netError(r, it))) }
                    Row {
                        Thumbnail(s.draft.thumbnailUrl, Modifier.width(72.dp).height(110.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(Fmt.date(s.draft.publishedAt), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(if (s.draft.publishedAt != null) R.string.date_from_id_note else R.string.published_unknown),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedTextField(form.title, { v -> edit { it.copy(title = v) } }, label = { Text(stringResource(R.string.field_title)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(form.author, { v -> edit { it.copy(author = v) } }, label = { Text(stringResource(R.string.field_author)) }, singleLine = true, modifier = Modifier.weight(1f))
                        NumberField(stringResource(R.string.field_followers), form.followers, { v -> edit { it.copy(followers = v) } }, Modifier.weight(1f))
                    }

                    SectionTitle(stringResource(R.string.geography))
                    OutlinedButton(onClick = { pickAuthorCountry = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.author_country) + ": " + (form.authorCountry?.let { Fmt.country(it) } ?: stringResource(R.string.unknown)))
                    }
                    OutlinedButton(onClick = { pickPopular = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.popular_in) + ": " +
                                (form.popularCountries.takeIf { it.isNotEmpty() }?.joinToString(" ") { Fmt.countryShort(it) } ?: stringResource(R.string.no_data))
                        )
                    }
                    if (form.popularCountries.isNotEmpty()) {
                        Text(stringResource(R.string.evidence_basis), style = MaterialTheme.typography.labelMedium)
                        ChipGroup(RegionBasis.entries, setOf(form.popularBasis), { stringResource(L.basis(it)) }, { b -> edit { it.copy(popularBasis = b) } })
                    }
                    InfoNote(stringResource(R.string.geo_explain))

                    SectionTitle(stringResource(R.string.stats_now))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(stringResource(R.string.views), form.views, { v -> edit { it.copy(views = v) } }, Modifier.weight(1f))
                        NumberField(stringResource(R.string.likes), form.likes, { v -> edit { it.copy(likes = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(stringResource(R.string.comments), form.comments, { v -> edit { it.copy(comments = v) } }, Modifier.weight(1f))
                        NumberField(stringResource(R.string.shares), form.shares, { v -> edit { it.copy(shares = v) } }, Modifier.weight(1f))
                    }
                    InfoNote(stringResource(R.string.stats_explain))

                    SectionTitle(stringResource(R.string.trend_elements))
                    OutlinedTextField(form.hashtags, { v -> edit { it.copy(hashtags = v) } }, label = { Text(stringResource(R.string.field_hashtags)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.sound, { v -> edit { it.copy(sound = v) } }, label = { Text(stringResource(R.string.field_sound)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(form.formats, { v -> edit { it.copy(formats = v) } }, label = { Text(stringResource(R.string.field_formats)) }, modifier = Modifier.fillMaxWidth())
                    ChipGroup(TrendType.entries, form.types, { stringResource(L.type(it)) }, { t -> edit { it.copy(types = if (t in it.types) it.types - t else it.types + t) } })
                    Dropdown(stringResource(R.string.category), settings?.categories.orEmpty(), form.category, { it }, { v -> edit { it.copy(category = v) } })
                    OutlinedTextField(form.hookNotes, { v -> edit { it.copy(hookNotes = v) } }, label = { Text(stringResource(R.string.field_hook)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(form.notes, { v -> edit { it.copy(notes = v) } }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)

                    Button(onClick = { vm.save { id -> nav.back(); nav.video(id) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
                }
            }
        }
    }

    if (pickAuthorCountry) {
        CountryPickerDialog(setOfNotNull(form.authorCountry), multi = false, onDismiss = { pickAuthorCountry = false }) { sel ->
            edit { it.copy(authorCountry = sel.firstOrNull()) }
            pickAuthorCountry = false
        }
    }
    if (pickPopular) {
        CountryPickerDialog(form.popularCountries, multi = true, onDismiss = { pickPopular = false }) { sel ->
            edit { it.copy(popularCountries = sel) }
            pickPopular = false
        }
    }
}

// ---------------- Add trend (hashtag / sound / format) ----------------

class AddTrendViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings

    fun save(
        type: TrendType, name: String, category: String?, countries: Set<String>, basis: RegionBasis,
        posts: Long?, views: Long?, rank: Int?, notes: String, onSaved: (String) -> Unit,
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = c.library.ensureTrend(type, name, now)
        val lib = c.library.snapshotLibrary()
        lib.trends.firstOrNull { it.id == id }?.let { t ->
            c.library.updateTrend(t.copy(saved = true, category = category ?: t.category, notes = notes.ifBlank { t.notes }))
        }
        countries.forEach { code ->
            c.library.addTrendRegion(id, RegionObservation(code, basis, now, rank))
            if (posts != null || views != null || rank != null) {
                c.library.addTrendSnapshot(id, TrendSnapshot(now, code, posts, views, rank))
            }
        }
        if (countries.isEmpty() && (posts != null || views != null)) {
            c.library.addTrendSnapshot(id, TrendSnapshot(now, null, posts, views, rank))
        }
        onSaved(id)
    }
}

@Composable
fun AddTrendScreen(nav: Nav) {
    val vm = appViewModel { AddTrendViewModel(it) }
    val settings by vm.settings.collectAsState(initial = null)
    var type by remember { mutableStateOf(TrendType.HASHTAG) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var countries by remember { mutableStateOf(emptySet<String>()) }
    var basis by remember { mutableStateOf(RegionBasis.CREATIVE_CENTER) }
    var posts by remember { mutableStateOf("") }
    var views by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.add_trend), nav) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InfoNote(stringResource(R.string.add_trend_note))
            ChipGroup(TrendType.entries, setOf(type), { stringResource(L.type(it)) }, { type = it })
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_trend_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Dropdown(stringResource(R.string.category), settings?.categories.orEmpty(), category, { it }, { category = it })
            OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.popular_in) + ": " + (countries.takeIf { it.isNotEmpty() }?.joinToString(" ") { Fmt.countryShort(it) } ?: stringResource(R.string.no_data)))
            }
            Text(stringResource(R.string.evidence_basis), style = MaterialTheme.typography.labelMedium)
            ChipGroup(RegionBasis.entries, setOf(basis), { stringResource(L.basis(it)) }, { basis = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(stringResource(R.string.posts), posts, { posts = it }, Modifier.weight(1f))
                NumberField(stringResource(R.string.views), views, { views = it }, Modifier.weight(1f))
                NumberField(stringResource(R.string.rank), rank, { rank = it.filter(Char::isDigit) }, Modifier.weight(1f))
            }
            InfoNote(stringResource(R.string.trend_stats_explain))
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.field_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(
                onClick = {
                    vm.save(type, name, category, countries, basis, CsvImporter.parseCount(posts), CsvImporter.parseCount(views), rank.toIntOrNull(), notes) { id ->
                        nav.back(); nav.trend(id)
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
    if (pick) {
        CountryPickerDialog(countries, multi = true, onDismiss = { pick = false }) { countries = it; pick = false }
    }
}
