package com.lanyan.chat.data.model

data class ApiSettings(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val systemPrompt: String = "你是澜言，一位真诚、严谨、乐于助人的 AI 助手。请使用清晰简洁的语言回答。",
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val contextMessages: Int = 12,
    val memoryEnabled: Boolean = true,
    val autoOrganizeMemory: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val profiles: List<ModelProfile> = emptyList(),
    val activeProfileId: String? = null,
    val groupModeEnabled: Boolean = false,
) {
    /** 按 id 取模型配置；都未命中时回退到默认/第一个。 */
    fun resolveProfile(id: String? = null): ModelProfile? {
        val byId = id?.let { wanted -> profiles.firstOrNull { it.id == wanted } }
        return byId
            ?: profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.firstOrNull()
    }

    /** 生成真正用于请求的设置副本。 */
    fun withProfile(profile: ModelProfile?): ApiSettings {
        if (profile == null) return this
        return copy(
            baseUrl = profile.baseUrl.ifBlank { baseUrl },
            apiKey = profile.apiKey,
            model = profile.modelName.ifBlank { model },
        )
    }
}

data class ModelProfile(
    val id: String,
    val slot: Int,
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
) {
    val label: String
        get() = MODEL_SLOT_LABELS.getOrElse(slot - 1) { "模型$slot" }

    companion object {
        val MODEL_SLOT_LABELS = listOf("模型一", "模型二", "模型三", "模型四")
        const val MAX_PROFILES = 4
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

enum class MessageRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
}

enum class DeliveryStatus {
    COMPLETE,
    STREAMING,
    ERROR,
}

enum class MemoryCategory(val label: String) {
    PROFILE("用户画像"),
    PREFERENCE("偏好习惯"),
    PROJECT("项目进展"),
    FACT("长期事实"),
}

data class PromptMessage(
    val role: MessageRole,
    val content: String,
    val images: List<String> = emptyList(),
)

data class MemoryContext(
    val prompt: String,
    val memoryIds: List<String>,
    val usedSummary: Boolean,
)

data class PreparedPrompt(
    val messages: List<PromptMessage>,
    val memoryIds: List<String>,
    val usedSummary: Boolean,
)

data class AvatarSettings(
    val userAvatar: String? = null,
    val aiAvatar: String? = null,
    val chatBackground: String? = null,
)
