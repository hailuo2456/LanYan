package com.lanyan.chat.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.AvatarSettings
import com.lanyan.chat.data.model.ModelProfile
import com.lanyan.chat.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Serializable
private data class StoredModelProfile(
    val id: String,
    val slot: Int,
    val baseUrl: String,
    val modelName: String,
)

private val settingsJson = Json { ignoreUnknownKeys = true }

data class SuggestionCache(
    val items: List<String>,
    val generatedAt: Long,
    val messageCount: Int,
)

class SettingsStore(private val context: Context) {
    private val securePreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_api_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val settings: Flow<ApiSettings> = context.settingsDataStore.data.map { values ->
        val storedProfiles = values[Keys.MODEL_PROFILES]?.let { raw ->
            runCatching {
                settingsJson.decodeFromString(ListSerializer(StoredModelProfile.serializer()), raw)
            }.getOrNull()
        }.orEmpty()
        val profiles = storedProfiles.map { stored ->
            ModelProfile(
                id = stored.id,
                slot = stored.slot,
                baseUrl = stored.baseUrl,
                apiKey = securePreferences.getString(profileKey(stored.id), "").orEmpty(),
                modelName = stored.modelName,
            )
        }
        ApiSettings(
            baseUrl = values[Keys.BASE_URL] ?: ApiSettings().baseUrl,
            apiKey = securePreferences.getString(Keys.API_KEY_SECURE, "").orEmpty(),
            model = values[Keys.MODEL] ?: ApiSettings().model,
            systemPrompt = values[Keys.SYSTEM_PROMPT] ?: ApiSettings().systemPrompt,
            temperature = values[Keys.TEMPERATURE] ?: ApiSettings().temperature,
            maxTokens = values[Keys.MAX_TOKENS] ?: ApiSettings().maxTokens,
            contextMessages = values[Keys.CONTEXT_MESSAGES] ?: ApiSettings().contextMessages,
            memoryEnabled = values[Keys.MEMORY_ENABLED] ?: true,
            autoOrganizeMemory = values[Keys.AUTO_MEMORY] ?: true,
            themeMode = values[Keys.THEME_MODE]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            profiles = profiles,
            activeProfileId = values[Keys.ACTIVE_PROFILE],
            groupModeEnabled = values[Keys.GROUP_MODE] ?: false,
        )
    }

    val avatars: Flow<AvatarSettings> = context.settingsDataStore.data.map { values ->
        AvatarSettings(
            userAvatar = values[Keys.USER_AVATAR]?.takeIf { it.isNotBlank() },
            aiAvatar = values[Keys.AI_AVATAR]?.takeIf { it.isNotBlank() },
            chatBackground = values[Keys.CHAT_BACKGROUND]?.takeIf { it.isNotBlank() },
        )
    }

    val suggestionCache: Flow<SuggestionCache?> = context.settingsDataStore.data.map { values ->
        val items = values[Keys.SUGGESTIONS]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        if (items.isEmpty()) {
            null
        } else {
            SuggestionCache(
                items = items,
                generatedAt = values[Keys.SUGGESTIONS_AT] ?: 0L,
                messageCount = values[Keys.SUGGESTIONS_MESSAGES] ?: 0,
            )
        }
    }

    suspend fun saveSettings(settings: ApiSettings) {
        securePreferences.edit().putString(Keys.API_KEY_SECURE, settings.apiKey.trim()).apply()
        settings.profiles.forEach { profile ->
            securePreferences.edit()
                .putString(profileKey(profile.id), profile.apiKey.trim())
                .apply()
        }
        context.settingsDataStore.edit { values ->
            values[Keys.BASE_URL] = settings.baseUrl.trim()
            values[Keys.MODEL] = settings.model.trim()
            values[Keys.SYSTEM_PROMPT] = settings.systemPrompt.trim()
            values[Keys.TEMPERATURE] = settings.temperature.coerceIn(0.0, 2.0)
            values[Keys.MAX_TOKENS] = settings.maxTokens.coerceIn(128, 32_768)
            values[Keys.CONTEXT_MESSAGES] = settings.contextMessages.coerceIn(2, 50)
            values[Keys.MEMORY_ENABLED] = settings.memoryEnabled
            values[Keys.AUTO_MEMORY] = settings.autoOrganizeMemory
            values[Keys.THEME_MODE] = settings.themeMode.name
            values[Keys.GROUP_MODE] = settings.groupModeEnabled
            values[Keys.MODEL_PROFILES] = settingsJson.encodeToString(
                ListSerializer(StoredModelProfile.serializer()),
                settings.profiles.map {
                    StoredModelProfile(it.id, it.slot, it.baseUrl.trim(), it.modelName.trim())
                },
            )
            val active = settings.activeProfileId ?: settings.profiles.firstOrNull()?.id
            if (active == null) {
                values.remove(Keys.ACTIVE_PROFILE)
            } else {
                values[Keys.ACTIVE_PROFILE] = active
            }
        }
    }

    suspend fun setActiveProfile(id: String?) {
        context.settingsDataStore.edit { values ->
            if (id == null) values.remove(Keys.ACTIVE_PROFILE) else values[Keys.ACTIVE_PROFILE] = id
        }
    }

    suspend fun setGroupMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GROUP_MODE] = enabled }
    }

    private fun profileKey(id: String) = "api_key_$id"

    suspend fun saveAvatar(isUser: Boolean, path: String?) {
        context.settingsDataStore.edit { values ->
            val key = if (isUser) Keys.USER_AVATAR else Keys.AI_AVATAR
            if (path == null) values.remove(key) else values[key] = path
        }
    }

    suspend fun saveChatBackground(path: String?) {
        context.settingsDataStore.edit { values ->
            if (path == null) values.remove(Keys.CHAT_BACKGROUND) else values[Keys.CHAT_BACKGROUND] = path
        }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveSuggestions(items: List<String>, messageCount: Int) {
        context.settingsDataStore.edit { values ->
            values[Keys.SUGGESTIONS] = items.joinToString("\n")
            values[Keys.SUGGESTIONS_AT] = System.currentTimeMillis()
            values[Keys.SUGGESTIONS_MESSAGES] = messageCount
        }
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOKENS = intPreferencesKey("max_tokens")
        val CONTEXT_MESSAGES = intPreferencesKey("context_messages")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val AUTO_MEMORY = booleanPreferencesKey("auto_memory")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USER_AVATAR = stringPreferencesKey("user_avatar")
        val AI_AVATAR = stringPreferencesKey("ai_avatar")
        val CHAT_BACKGROUND = stringPreferencesKey("chat_background")
        val SUGGESTIONS = stringPreferencesKey("home_suggestions")
        val SUGGESTIONS_AT = longPreferencesKey("home_suggestions_at")
        val SUGGESTIONS_MESSAGES = intPreferencesKey("home_suggestions_messages")
        val MODEL_PROFILES = stringPreferencesKey("model_profiles")
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
        val GROUP_MODE = booleanPreferencesKey("group_mode_enabled")
        const val API_KEY_SECURE = "api_key"
    }
}
