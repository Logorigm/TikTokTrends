@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trendscout.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trendscout.app.AppContainer
import com.trendscout.app.BuildConfigShim
import com.trendscout.app.R
import com.trendscout.app.data.prefs.AppSettings
import com.trendscout.app.data.prefs.FeedSourceConfig
import com.trendscout.app.data.prefs.NotifyKind
import com.trendscout.app.data.prefs.SecretStore
import com.trendscout.app.data.repo.AiOutcome
import com.trendscout.app.notify.Notifier
import com.trendscout.app.notify.TrendCheckWorker
import com.trendscout.app.ui.Nav
import com.trendscout.app.ui.appViewModel
import com.trendscout.app.ui.components.AppCard
import com.trendscout.app.ui.components.ChipGroup
import com.trendscout.app.ui.components.CountryPickerDialog
import com.trendscout.app.ui.components.Fmt
import com.trendscout.app.ui.components.InfoNote
import com.trendscout.app.ui.components.L
import com.trendscout.app.ui.components.SectionTitle
import com.trendscout.app.ui.components.TextInputDialog
import com.trendscout.app.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hasKey = MutableStateFlow(c.ai.hasKey())
    val aiStatus = MutableStateFlow<String?>(null)
    val syncStatus = MutableStateFlow<String?>(null)

    fun setCountries(list: List<String>) = viewModelScope.launch { c.settings.setCountries(list) }
    fun setTopics(raw: String) = viewModelScope.launch { c.settings.setTopics(raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }) }
    fun setCategories(raw: String) = viewModelScope.launch { c.settings.setCategories(raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }) }

    fun saveAi(url: String, model: String, enabled: Boolean, key: String?) = viewModelScope.launch {
        c.settings.setLlm(url, model, enabled)
        if (key != null) {
            c.secrets.put(SecretStore.LLM_KEY, key)
            hasKey.value = c.ai.hasKey()
        }
        aiStatus.value = null
    }

    fun clearKey() {
        c.secrets.put(SecretStore.LLM_KEY, null)
        hasKey.value = false
    }

    fun testAi() = viewModelScope.launch {
        aiStatus.value = c.app.getString(R.string.ai_testing)
        val o = c.ai.test()
        aiStatus.value = if (o is AiOutcome.Ok) c.app.getString(R.string.ai_test_ok) else L.ai(c.app.resources, o)
    }

    fun addFeed(name: String, url: String, token: String) = viewModelScope.launch {
        val id = UUID.randomUUID().toString().take(8)
        c.secrets.put(SecretStore.feedToken(id), token.ifBlank { null })
        c.settings.setFeeds(c.settings.current().feeds + FeedSourceConfig(id, name.ifBlank { url }, url.trim()))
    }

    fun removeFeed(id: String) = viewModelScope.launch {
        c.secrets.put(SecretStore.feedToken(id), null)
        c.settings.setFeeds(c.settings.current().feeds.filter { it.id != id })
    }

    fun toggleFeed(id: String) = viewModelScope.launch { c.settings.updateFeed(id) { it.copy(enabled = !it.enabled) } }

    fun syncNow() = viewModelScope.launch {
        syncStatus.value = c.app.getString(R.string.syncing)
        val out = c.feedSync.syncAll()
        val r = c.app.resources
        syncStatus.value = if (out.isEmpty()) r.getString(R.string.no_feeds) else out.joinToString("\n") { o ->
            o.feed.name + ": " + (o.summary?.let { r.getString(R.string.sync_ok, it.newVideos, it.snapshots) } ?: o.error?.let { L.netError(r, it) } ?: "")
        }
    }

    fun setNotifications(enabled: Boolean, hours: Int, countries: Set<String>, categories: Set<String>, kinds: Set<NotifyKind>) = viewModelScope.launch {
        c.settings.setNotifications(enabled, hours, countries, categories, kinds)
        TrendCheckWorker.schedule(c.app, enabled, hours)
    }
}

@Composable
fun SettingsScreen(nav: Nav) {
    val vm = appViewModel { SettingsViewModel(it) }
    val s by vm.settings.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val aiStatus by vm.aiStatus.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val cur = s ?: return@rememberLauncherForActivityResult
        vm.setNotifications(granted, cur.notifyFrequencyHours, cur.notifyCountries, cur.notifyCategories, cur.notifyKinds)
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        val st = s
        if (st != null) {
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle(stringResource(R.string.my_countries), trailing = { TextButton(onClick = { dialog = "countries" }) { Text(stringResource(R.string.action_edit)) } })
            Text(st.myCountries.joinToString("  ") { Fmt.countryShort(it) }, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.my_countries_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionTitle(stringResource(R.string.my_topics), trailing = { TextButton(onClick = { dialog = "topics" }) { Text(stringResource(R.string.action_edit)) } })
            Text(st.topics.joinToString(", ").ifBlank { stringResource(R.string.topics_empty) }, style = MaterialTheme.typography.bodyMedium)

            SectionTitle(stringResource(R.string.categories), trailing = { TextButton(onClick = { dialog = "categories" }) { Text(stringResource(R.string.action_edit)) } })
            Text(st.categories.joinToString(", "), style = MaterialTheme.typography.bodyMedium)

            AiSection(st, hasKey, aiStatus, vm)

            SectionTitle(stringResource(R.string.data_sources), trailing = { TextButton(onClick = { dialog = "feed" }) { Text(stringResource(R.string.action_add)) } })
            InfoNote(stringResource(R.string.feeds_note))
            st.feeds.forEach { f ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.titleSmall)
                            Text(f.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(
                                (f.lastSyncAt?.let { Fmt.relative(it) } ?: stringResource(R.string.never_synced)) + (f.lastStatus?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (f.lastStatus?.startsWith("error") == true) Palette.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = f.enabled, onCheckedChange = { vm.toggleFeed(f.id) })
                        IconButton(onClick = { vm.removeFeed(f.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (st.feeds.isNotEmpty()) {
                OutlinedButton(onClick = { vm.syncNow() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Sync, null); Text(" " + stringResource(R.string.sync_now))
                }
            }
            syncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { nav.about() }) { Text(stringResource(R.string.where_data)) }

            SectionTitle(stringResource(R.string.notifications))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_enable), Modifier.weight(1f))
                Switch(checked = st.notifyEnabled, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(ctx)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        vm.setNotifications(on, st.notifyFrequencyHours, st.notifyCountries, st.notifyCategories, st.notifyKinds)
                    }
                })
            }
            if (st.notifyEnabled) {
                Text(stringResource(R.string.notify_frequency), style = MaterialTheme.typography.labelLarge)
                ChipGroup(listOf(6, 12, 24), setOf(st.notifyFrequencyHours), { stringResource(R.string.every_hours, it) }, { h ->
                    vm.setNotifications(true, h, st.notifyCountries, st.notifyCategories, st.notifyKinds)
                })
                Text(stringResource(R.string.notify_countries), style = MaterialTheme.typography.labelLarge)
                ChipGroup(st.myCountries, st.notifyCountries, { Fmt.countryShort(it) }, { cc ->
                    val n = if (cc in st.notifyCountries) st.notifyCountries - cc else st.notifyCountries + cc
                    vm.setNotifications(true, st.notifyFrequencyHours, n, st.notifyCategories, st.notifyKinds)
                })
                Text(stringResource(R.string.notify_categories), style = MaterialTheme.typography.labelLarge)
                ChipGroup(st.categories, st.notifyCategories, { it }, { cat ->
                    val n = if (cat in st.notifyCategories) st.notifyCategories - cat else st.notifyCategories + cat
                    vm.setNotifications(true, st.notifyFrequencyHours, st.notifyCountries, n, st.notifyKinds)
                })
                Text(stringResource(R.string.notify_kinds), style = MaterialTheme.typography.labelLarge)
                ChipGroup(NotifyKind.entries, st.notifyKinds, { stringResource(L.notify(it)) }, { k ->
                    val n = if (k in st.notifyKinds) st.notifyKinds - k else st.notifyKinds + k
                    vm.setNotifications(true, st.notifyFrequencyHours, st.notifyCountries, st.notifyCategories, n)
                })
                InfoNote(stringResource(R.string.notify_note))
            }

            SectionTitle(stringResource(R.string.about_app))
            Text(stringResource(R.string.version_fmt, BuildConfigShim.versionName(ctx)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
        }

        when (dialog) {
            "countries" -> CountryPickerDialog(st.myCountries.toSet(), multi = true, onDismiss = { dialog = null }) { sel ->
                // Keep the user's existing order, append new ones.
                vm.setCountries(st.myCountries.filter { it in sel } + sel.filter { it !in st.myCountries })
                dialog = null
            }
            "topics" -> TextInputDialog(stringResource(R.string.my_topics), st.topics.joinToString(", "), { dialog = null }, { vm.setTopics(it); dialog = null }, label = stringResource(R.string.comma_separated))
            "categories" -> TextInputDialog(stringResource(R.string.categories), st.categories.joinToString(", "), { dialog = null }, { vm.setCategories(it); dialog = null }, label = stringResource(R.string.comma_separated))
            "feed" -> AddFeedDialog({ dialog = null }) { n, u, t -> vm.addFeed(n, u, t); dialog = null }
        }
        }
    }
}

@Composable
private fun AiSection(st: AppSettings, hasKey: Boolean, status: String?, vm: SettingsViewModel) {
    var url by remember(st.llmBaseUrl) { mutableStateOf(st.llmBaseUrl) }
    var model by remember(st.llmModel) { mutableStateOf(st.llmModel) }
    var enabled by remember(st.llmEnabled) { mutableStateOf(st.llmEnabled) }
    var key by remember { mutableStateOf("") }
    LaunchedEffect(hasKey) { if (hasKey) key = "" }

    SectionTitle(stringResource(R.string.ai_section))
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ai_enable), Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Text(stringResource(R.string.ai_provider_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { url = AppSettings.DEFAULT_LLM_URL; model = AppSettings.DEFAULT_LLM_MODEL }) { Text("BigModel (CN)") }
                OutlinedButton(onClick = { url = AppSettings.ZAI_LLM_URL; model = AppSettings.DEFAULT_LLM_MODEL }) { Text("Z.ai (Intl)") }
            }
            OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.ai_base_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.ai_model)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                key, { key = it },
                label = { Text(stringResource(if (hasKey) R.string.ai_key_saved else R.string.ai_key)) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveAi(url, model, enabled, key.takeIf { it.isNotBlank() }) }) { Text(stringResource(R.string.action_save)) }
                OutlinedButton(onClick = { vm.testAi() }, enabled = hasKey) { Text(stringResource(R.string.ai_test)) }
                if (hasKey) TextButton(onClick = { vm.clearKey() }) { Text(stringResource(R.string.ai_clear_key)) }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            InfoNote(stringResource(R.string.ai_privacy_note))
        }
    }
}

@Composable
private fun AddFeedDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_feed)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.feed_name)) }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.feed_url)) }, singleLine = true)
                OutlinedTextField(token, { token = it }, label = { Text(stringResource(R.string.feed_token)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text(stringResource(R.string.feed_format_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = url.startsWith("https://") && url.length > 10, onClick = { onSave(name, url, token) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
