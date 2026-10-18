package com.trendscout.app.data.prefs

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trendscout.core.model.Countries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class NotifyKind { NEW_FAST, STAT_JUMP, SPREAD, SAVED_TAGS }

@Serializable
data class FeedSourceConfig(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastSyncAt: Long? = null,
    val lastStatus: String? = null,
)

data class AppSettings(
    val myCountries: List<String> = Countries.PRIORITY,
    val topics: List<String> = emptyList(),
    val categories: List<String> = DEFAULT_CATEGORIES,
    val llmBaseUrl: String = DEFAULT_LLM_URL,
    val llmModel: String = DEFAULT_LLM_MODEL,
    val llmEnabled: Boolean = true,
    val feeds: List<FeedSourceConfig> = emptyList(),
    val notifyEnabled: Boolean = false,
    val notifyFrequencyHours: Int = 12,
    val notifyCountries: Set<String> = emptySet(),
    val notifyCategories: Set<String> = emptySet(),
    val notifyKinds: Set<NotifyKind> = NotifyKind.entries.toSet(),
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_LLM_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val ZAI_LLM_URL = "https://api.z.ai/api/paas/v4"
        const val DEFAULT_LLM_MODEL = "glm-4.5-flash"
        val DEFAULT_CATEGORIES = listOf(
            "Юмор", "Танцы", "Музыка", "Еда", "Красота", "Мода", "Животные", "Спорт",
            "Образование", "Технологии", "Игры", "Путешествия", "DIY", "Лайфстайл",
        )
    }
}

class SettingsRepository(private val context: Context) {
    private object K {
        val countries = stringPreferencesKey("my_countries")
        val topics = stringPreferencesKey("topics")
        val categories = stringPreferencesKey("categories")
        val llmUrl = stringPreferencesKey("llm_url")
        val llmModel = stringPreferencesKey("llm_model")
        val llmEnabled = booleanPreferencesKey("llm_enabled")
        val feeds = stringPreferencesKey("feeds")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val notifyFreq = intPreferencesKey("notify_freq")
        val notifyCountries = stringSetPreferencesKey("notify_countries")
        val notifyCategories = stringSetPreferencesKey("notify_categories")
        val notifyKinds = stringSetPreferencesKey("notify_kinds")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val feedsSerializer = ListSerializer(FeedSourceConfig.serializer())

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> read(p) }

    suspend fun current(): AppSettings = settings.first()

    private fun list(raw: String?): List<String>? = raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            myCountries = list(p[K.countries]) ?: d.myCountries,
            topics = list(p[K.topics]) ?: d.topics,
            categories = list(p[K.categories]) ?: d.categories,
            llmBaseUrl = p[K.llmUrl] ?: d.llmBaseUrl,
            llmModel = p[K.llmModel] ?: d.llmModel,
            llmEnabled = p[K.llmEnabled] ?: d.llmEnabled,
            feeds = p[K.feeds]?.let { runCatching { json.decodeFromString(feedsSerializer, it) }.getOrNull() } ?: d.feeds,
            notifyEnabled = p[K.notifyEnabled] ?: d.notifyEnabled,
            notifyFrequencyHours = p[K.notifyFreq] ?: d.notifyFrequencyHours,
            notifyCountries = p[K.notifyCountries] ?: d.notifyCountries,
            notifyCategories = p[K.notifyCategories] ?: d.notifyCategories,
            notifyKinds = p[K.notifyKinds]?.mapNotNull { n -> NotifyKind.entries.firstOrNull { it.name == n } }?.toSet() ?: d.notifyKinds,
            onboardingDone = p[K.onboarding] ?: false,
        )
    }

    suspend fun setCountries(codes: List<String>) = context.dataStore.edit { it[K.countries] = codes.joinToString("\n") }
    suspend fun setTopics(topics: List<String>) = context.dataStore.edit { it[K.topics] = topics.joinToString("\n") }
    suspend fun setCategories(c: List<String>) = context.dataStore.edit { it[K.categories] = c.joinToString("\n") }
    suspend fun setLlm(url: String, model: String, enabled: Boolean) = context.dataStore.edit {
        it[K.llmUrl] = url.trim()
        it[K.llmModel] = model.trim()
        it[K.llmEnabled] = enabled
    }
    suspend fun setFeeds(feeds: List<FeedSourceConfig>) = context.dataStore.edit { it[K.feeds] = json.encodeToString(feedsSerializer, feeds) }
    suspend fun updateFeed(id: String, transform: (FeedSourceConfig) -> FeedSourceConfig) {
        val feeds = current().feeds.map { if (it.id == id) transform(it) else it }
        setFeeds(feeds)
    }
    suspend fun setNotifications(enabled: Boolean, hours: Int, countries: Set<String>, categories: Set<String>, kinds: Set<NotifyKind>) =
        context.dataStore.edit {
            it[K.notifyEnabled] = enabled
            it[K.notifyFreq] = hours
            it[K.notifyCountries] = countries
            it[K.notifyCategories] = categories
            it[K.notifyKinds] = kinds.map { k -> k.name }.toSet()
        }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.onboarding] = true }
}

/**
 * Stores user-provided secrets (AI key, feed tokens) encrypted with an AES key that lives in the
 * Android Keystore and never leaves the device. No secret is compiled into the APK.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private val alias = "trendscout_secrets_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val enc = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        return try {
            val (iv, data) = packed.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key lost (e.g. restored on another device): the user simply re-enters the secret.
            prefs.edit().remove(name).apply()
            null
        }
    }

    fun has(name: String): Boolean = prefs.contains(name)

    companion object {
        const val LLM_KEY = "llm_api_key"
        fun feedToken(id: String) = "feed_token_$id"
    }
}
