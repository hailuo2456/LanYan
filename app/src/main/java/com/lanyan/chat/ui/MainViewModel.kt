package com.lanyan.chat.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lanyan.chat.AppContainer
import com.lanyan.chat.data.local.ConversationEntity
import com.lanyan.chat.data.local.MemoryEntity
import com.lanyan.chat.data.local.MessageEntity
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.AvatarSettings
import com.lanyan.chat.data.model.DeliveryStatus
import com.lanyan.chat.data.model.MemoryCategory
import com.lanyan.chat.data.model.MessageRole
import com.lanyan.chat.data.model.PromptMessage
import com.lanyan.chat.data.model.ThemeMode
import com.lanyan.chat.data.repository.ChatRepository
import com.lanyan.chat.domain.SuggestionEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val currentConversationId: String? = null,
    val sending: Boolean = false,
    val testingConnection: Boolean = false,
    val settings: ApiSettings = ApiSettings(),
    val avatars: AvatarSettings = AvatarSettings(),
    val memories: List<MemoryEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val showIntro: Boolean = true,
    val draftAiAvatar: String? = null,
    val draftUserAvatar: String? = null,
    val draftSystemPrompt: String? = null,
    val draftModelProfileId: String? = null,
    val memoryOverride: Boolean? = null,
    val notice: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val currentConversationId = MutableStateFlow<String?>(null)
    private var generationJob: Job? = null
    private var maintenanceJob: Job? = null
    private var suggestionsJob: Job? = null
    private var launchSuggestionRefreshDone = false

    init {
        viewModelScope.launch {
            container.chatRepository.conversations.collect { conversations ->
                _uiState.update { state ->
                    val selected = state.currentConversationId
                    state.copy(
                        conversations = conversations,
                        currentConversationId = selected?.takeIf { id -> conversations.any { it.id == id } },
                    )
                }
            }
        }
        viewModelScope.launch {
            currentConversationId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else container.chatRepository.messages(id)
            }.collect { messages ->
                _uiState.update { it.copy(messages = messages, currentConversationId = currentConversationId.value) }
            }
        }
        viewModelScope.launch {
            container.settingsStore.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                // 每次启动应用刷新一次首页开场问题
                if (!launchSuggestionRefreshDone) {
                    launchSuggestionRefreshDone = true
                    val configured = settings.resolveProfile()
                        ?.let { it.baseUrl.isNotBlank() && it.modelName.isNotBlank() }
                        ?: (settings.baseUrl.isNotBlank() && settings.model.isNotBlank())
                    if (configured) generateSuggestions()
                }
            }
        }
        viewModelScope.launch {
            container.settingsStore.avatars.collect { avatars -> _uiState.update { it.copy(avatars = avatars) } }
        }
        viewModelScope.launch {
            container.memoryRepository.memories.collect { memories -> _uiState.update { it.copy(memories = memories) } }
        }
        viewModelScope.launch {
            container.settingsStore.suggestionCache.collect { cache ->
                _uiState.update { it.copy(suggestions = cache?.items.orEmpty()) }
            }
        }
    }

    fun sendMessage(rawText: String, imageUris: List<Uri> = emptyList()) {
        val text = rawText.trim()
        if ((text.isBlank() && imageUris.isEmpty()) || _uiState.value.sending) return
        val settings = _uiState.value.settings
        val configured = settings.resolveProfile()
            ?.let { it.baseUrl.isNotBlank() && it.modelName.isNotBlank() }
            ?: (settings.baseUrl.isNotBlank() && settings.model.isNotBlank())
        if (!configured) {
            showNotice("请先在设置中填写 API 地址和模型名称")
            return
        }
        maintenanceJob?.cancel()
        _uiState.update { it.copy(sending = true, notice = null) }
        generationJob = viewModelScope.launch {
            var pending: MessageEntity? = null
            val partial = StringBuilder()
            try {
                val importedImages = imageUris
                    .take(MAX_IMAGES_PER_MESSAGE)
                    .mapNotNull { uri -> runCatching { container.chatImageStorage.importImage(uri) }.getOrNull() }
                if (text.isBlank() && importedImages.isEmpty()) {
                    showNotice("图片读取失败，请重新选择")
                    return@launch
                }
                val conversation = currentConversationId.value
                    ?.let { container.chatRepository.getConversation(it) }
                    ?: container.chatRepository.createConversation(text.ifBlank { "图片对话" }).also { created ->
                        val draft = _uiState.value
                        if (draft.draftAiAvatar != null) {
                            container.chatRepository.setConversationAiAvatar(created.id, draft.draftAiAvatar)
                        }
                        if (draft.draftUserAvatar != null) {
                            container.chatRepository.setConversationUserAvatar(created.id, draft.draftUserAvatar)
                        }
                        if (draft.draftSystemPrompt != null) {
                            container.chatRepository.setConversationPrompt(created.id, draft.draftSystemPrompt)
                        }
                        val chosenModel = draft.draftModelProfileId ?: settings.activeProfileId
                        if (chosenModel != null) {
                            container.chatRepository.setConversationModel(created.id, chosenModel)
                        }
                        currentConversationId.value = created.id
                        _uiState.update {
                            it.copy(
                                draftAiAvatar = null,
                                draftUserAvatar = null,
                                draftSystemPrompt = null,
                                draftModelProfileId = null,
                            )
                        }
                    }
                container.chatRepository.saveMessage(
                    ChatRepository.userMessage(conversation.id, text, importedImages),
                )
                container.chatRepository.touchConversation(conversation.id)
                val refreshedConversation = container.chatRepository.getConversation(conversation.id) ?: conversation
                if (refreshedConversation.isGroup) {
                    runGroupReplies(refreshedConversation, settings)
                    return@launch
                }
                val effectiveSettings = settings.withProfile(
                    settings.resolveProfile(refreshedConversation.modelProfileId),
                ).copy(memoryEnabled = _uiState.value.memoryOverride ?: settings.memoryEnabled)
                val prompt = container.memoryEngine.prepare(
                    conversation = refreshedConversation,
                    messages = container.chatRepository.currentMessages(conversation.id),
                    settings = effectiveSettings,
                )
                val assistant = ChatRepository.pendingAssistant(conversation.id).copy(
                    usedMemoryIds = prompt.memoryIds.joinToString(","),
                    usedSummary = prompt.usedSummary,
                )
                pending = assistant
                container.chatRepository.saveMessage(assistant)
                val answer = container.modelGateway.streamChat(effectiveSettings, prompt.messages) { delta ->
                    partial.append(delta)
                    val streaming = assistant.copy(content = partial.toString())
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { message ->
                                if (message.id == assistant.id) streaming else message
                            },
                        )
                    }
                }
                container.chatRepository.updateMessage(
                    assistant.copy(
                        content = answer.ifBlank { "模型返回了空内容，请稍后重试。" },
                        status = DeliveryStatus.COMPLETE,
                    ),
                )
                container.chatRepository.touchConversation(conversation.id)
                maintenanceJob = viewModelScope.launch(Dispatchers.IO) {
                    runCatching { generateTitleIfNeeded(conversation.id, effectiveSettings) }
                    if (effectiveSettings.memoryEnabled && settings.autoOrganizeMemory) {
                        runCatching { container.memoryConsolidator.learnFromTurn(text, conversation.id) }
                        runCatching {
                            container.memoryConsolidator.summarizeIfNeeded(conversation.id, effectiveSettings)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    pending?.let { message ->
                        if (partial.isBlank()) {
                            container.chatRepository.deleteMessage(message)
                        } else {
                            container.chatRepository.updateMessage(
                                message.copy(content = partial.toString(), status = DeliveryStatus.COMPLETE),
                            )
                        }
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                pending?.let { message ->
                    container.chatRepository.updateMessage(
                        message.copy(
                            content = partial.toString(),
                            status = DeliveryStatus.ERROR,
                            errorText = error.message ?: "网络请求失败，请稍后重试",
                        ),
                    )
                }
                showNotice(error.message ?: "网络请求失败，请稍后重试")
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(sending = false) }
    }

    /** 输入框下方的「长期记忆」开关：只影响后续发送，不修改全局设置。 */
    fun toggleMemoryOverride() {
        _uiState.update { state ->
            val current = state.memoryOverride ?: state.settings.memoryEnabled
            state.copy(memoryOverride = !current)
        }
    }

    /**
     * 修改一条已发送的用户消息：更新内容、删除其后的消息并重新生成回复。
     */
    fun editUserMessage(messageId: String, newText: String) {
        val text = newText.trim()
        if (text.isBlank() || _uiState.value.sending) return
        val conversationId = currentConversationId.value ?: return
        val baseSettings = _uiState.value.settings
        _uiState.update { it.copy(sending = true, notice = null) }
        generationJob = viewModelScope.launch {
            var pending: MessageEntity? = null
            val partial = StringBuilder()
            try {
                val conversation = container.chatRepository.getConversation(conversationId) ?: return@launch
                if (conversation.isGroup) {
                    showNotice("群组对话暂不支持修改单条消息")
                    return@launch
                }
                val all = container.chatRepository.currentMessages(conversationId)
                val index = all.indexOfFirst { it.id == messageId }
                if (index < 0) return@launch
                container.chatRepository.updateMessage(all[index].copy(content = text, errorText = null))
                all.drop(index + 1).forEach { container.chatRepository.deleteMessage(it) }
                container.chatRepository.clearSummary(conversationId)

                val memoryEnabled = _uiState.value.memoryOverride ?: baseSettings.memoryEnabled
                val effective = baseSettings.withProfile(
                    baseSettings.resolveProfile(conversation.modelProfileId),
                ).copy(memoryEnabled = memoryEnabled)
                val prompt = container.memoryEngine.prepare(
                    conversation = conversation.copy(summary = "", summarizedMessageCount = 0),
                    messages = container.chatRepository.currentMessages(conversationId),
                    settings = effective,
                )
                val assistant = ChatRepository.pendingAssistant(conversationId).copy(
                    usedMemoryIds = prompt.memoryIds.joinToString(","),
                    usedSummary = prompt.usedSummary,
                )
                pending = assistant
                container.chatRepository.saveMessage(assistant)
                val answer = container.modelGateway.streamChat(effective, prompt.messages) { delta ->
                    partial.append(delta)
                    val streaming = assistant.copy(content = partial.toString())
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { message ->
                                if (message.id == assistant.id) streaming else message
                            },
                        )
                    }
                }
                container.chatRepository.updateMessage(
                    assistant.copy(
                        content = answer.ifBlank { "模型返回了空内容，请稍后重试。" },
                        status = DeliveryStatus.COMPLETE,
                    ),
                )
                container.chatRepository.touchConversation(conversationId)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    pending?.let { message ->
                        if (partial.isBlank()) {
                            container.chatRepository.deleteMessage(message)
                        } else {
                            container.chatRepository.updateMessage(
                                message.copy(content = partial.toString(), status = DeliveryStatus.COMPLETE),
                            )
                        }
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                pending?.let { message ->
                    container.chatRepository.updateMessage(
                        message.copy(
                            content = partial.toString(),
                            status = DeliveryStatus.ERROR,
                            errorText = error.message ?: "网络请求失败，请稍后重试",
                        ),
                    )
                }
                showNotice(error.message ?: "网络请求失败，请稍后重试")
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    fun newConversation() {
        stopGeneration()
        val drafts = listOfNotNull(_uiState.value.draftAiAvatar, _uiState.value.draftUserAvatar)
        currentConversationId.value = null
        _uiState.update {
            it.copy(
                currentConversationId = null,
                messages = emptyList(),
                draftAiAvatar = null,
                draftUserAvatar = null,
                draftSystemPrompt = null,
            )
        }
        drafts.forEach { path ->
            viewModelScope.launch(Dispatchers.IO) { container.avatarStorage.removeStoredAvatar(path) }
        }
    }

    fun ensureSuggestions() {
        if (suggestionsJob?.isActive == true) return
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val configured = settings.resolveProfile()
                ?.let { it.baseUrl.isNotBlank() && it.modelName.isNotBlank() }
                ?: (settings.baseUrl.isNotBlank() && settings.model.isNotBlank())
            if (!configured) return@launch
            val cache = container.settingsStore.suggestionCache.first()
            val stale = if (cache == null) {
                true
            } else {
                System.currentTimeMillis() - cache.generatedAt >= SUGGESTION_TTL_MS &&
                    container.chatRepository.countMessages() > cache.messageCount
            }
            if (!stale) return@launch
            if (cache == null && container.chatRepository.recentChatDigest().isBlank()) return@launch
            generateSuggestions()
        }
    }

    fun refreshSuggestions() {
        val settings = _uiState.value.settings
        val configured = settings.resolveProfile()
            ?.let { it.baseUrl.isNotBlank() && it.modelName.isNotBlank() }
            ?: (settings.baseUrl.isNotBlank() && settings.model.isNotBlank())
        if (!configured) {
            showNotice("请先在设置中填写 API 地址和模型名称")
            return
        }
        if (suggestionsJob?.isActive == true) return
        generateSuggestions(notifyOnFailure = true)
    }

    private fun generateSuggestions(notifyOnFailure: Boolean = false) {
        suggestionsJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(suggestionsLoading = true) }
            try {
                val digest = container.chatRepository.recentChatDigest()
                val baseSettings = _uiState.value.settings
                val suggestions = container.suggestionEngine.generate(
                    baseSettings.withProfile(baseSettings.resolveProfile()),
                    digest,
                )
                if (suggestions.isNotEmpty()) {
                    container.settingsStore.saveSuggestions(suggestions, container.chatRepository.countMessages())
                } else if (notifyOnFailure) {
                    showNotice("模型没有返回可用的开场问题")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 自动生成失败时静默保留当前建议；手动刷新则给出提示
                if (notifyOnFailure) showNotice(error.message ?: "开场问题生成失败")
            } finally {
                _uiState.update { it.copy(suggestionsLoading = false) }
            }
        }
    }

    private suspend fun generateTitleIfNeeded(conversationId: String, settings: ApiSettings) {
        val conversation = container.chatRepository.getConversation(conversationId)?.takeIf { !it.autoTitled } ?: return
        val early = container.chatRepository.firstMessages(conversationId)
        val firstUser = early.firstOrNull { it.role == MessageRole.USER } ?: return
        val firstAssistant = early
            .firstOrNull { it.role == MessageRole.ASSISTANT && it.createdAt >= firstUser.createdAt }
            ?: return
        val raw = container.modelGateway.complete(
            settings = settings,
            messages = listOf(
                PromptMessage(
                    MessageRole.SYSTEM,
                    "你为 AI 聊天会话起简短标题。根据用户的第一条提问概括主题，输出不超过 12 个字的中文标题。" +
                        "只输出标题本身，不要引号、结尾标点、“标题：”前缀、编号或任何解释。",
                ),
                PromptMessage(
                    MessageRole.USER,
                    "用户的第一条提问：${firstUser.content.ifBlank { "（用户发送了图片）" }.take(500)}\n\n" +
                        "助手的首次回复（截取）：${firstAssistant.content.take(300)}\n\n请输出标题。",
                ),
            ),
            maxTokens = 32,
        )
        val title = SuggestionEngine.cleanTitle(raw) ?: return
        val latest = container.chatRepository.getConversation(conversationId) ?: return
        if (latest.autoTitled) return
        container.chatRepository.renameConversation(conversationId, title)
    }

    fun selectConversation(id: String) {
        if (id == currentConversationId.value) return
        stopGeneration()
        currentConversationId.value = id
        _uiState.update { it.copy(currentConversationId = id, messages = emptyList()) }
    }

    fun renameConversation(id: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { container.chatRepository.renameConversation(id, title) }
    }

    fun deleteConversation(conversation: ConversationEntity) {
        stopGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            val images = container.chatRepository.currentMessages(conversation.id)
                .flatMap { it.imagePaths.split('\n') }
                .filter { it.isNotBlank() }
            container.chatRepository.deleteConversation(conversation)
            container.avatarStorage.removeStoredAvatar(conversation.aiAvatarPath)
            container.avatarStorage.removeStoredAvatar(conversation.userAvatarPath)
            images.forEach { container.chatImageStorage.remove(it) }
            if (currentConversationId.value == conversation.id) newConversation()
        }
    }

    fun saveConversationAvatar(conversationId: String, bitmap: Bitmap, isUser: Boolean) {
        viewModelScope.launch {
            try {
                val old = container.chatRepository.getConversation(conversationId)
                    ?.let { if (isUser) it.userAvatarPath else it.aiAvatarPath }
                val prefix = (if (isUser) "convu_" else "conv_") + conversationId.take(8)
                val path = container.avatarStorage.saveAvatarBitmap(bitmap, prefix)
                container.avatarStorage.removeStoredAvatar(old)
                if (isUser) {
                    container.chatRepository.setConversationUserAvatar(conversationId, path)
                } else {
                    container.chatRepository.setConversationAiAvatar(conversationId, path)
                }
                showNotice(if (isUser) "用户头像已更新（本对话）" else "AI 头像已更新（本对话）")
            } catch (error: Exception) {
                showNotice(error.message ?: "头像保存失败")
            }
        }
    }

    fun clearConversationAvatar(conversationId: String, isUser: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = container.chatRepository.getConversation(conversationId)
                ?.let { if (isUser) it.userAvatarPath else it.aiAvatarPath }
            if (isUser) {
                container.chatRepository.setConversationUserAvatar(conversationId, null)
            } else {
                container.chatRepository.setConversationAiAvatar(conversationId, null)
            }
            container.avatarStorage.removeStoredAvatar(old)
            showNotice("已恢复全局头像")
        }
    }

    fun updateConversationPrompt(conversationId: String, prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.chatRepository.setConversationPrompt(conversationId, prompt)
            showNotice(if (prompt.isBlank()) "已恢复全局提示词" else "提示词已保存")
        }
    }

    /** 新对话尚未创建时，先把头像与提示词存成草稿，发出第一条消息时再写入会话。 */
    fun setDraftAvatar(bitmap: Bitmap, isUser: Boolean) {
        viewModelScope.launch {
            try {
                val old = if (isUser) _uiState.value.draftUserAvatar else _uiState.value.draftAiAvatar
                val path = container.avatarStorage.saveAvatarBitmap(bitmap, if (isUser) "draft_user" else "draft_ai")
                container.avatarStorage.removeStoredAvatar(old)
                _uiState.update { state ->
                    if (isUser) state.copy(draftUserAvatar = path) else state.copy(draftAiAvatar = path)
                }
            } catch (error: Exception) {
                showNotice(error.message ?: "头像保存失败")
            }
        }
    }

    fun clearDraftAvatar(isUser: Boolean) {
        val old = if (isUser) _uiState.value.draftUserAvatar else _uiState.value.draftAiAvatar
        _uiState.update { state ->
            if (isUser) state.copy(draftUserAvatar = null) else state.copy(draftAiAvatar = null)
        }
        if (old != null) {
            viewModelScope.launch(Dispatchers.IO) { container.avatarStorage.removeStoredAvatar(old) }
        }
    }

    fun updateDraftPrompt(prompt: String) {
        _uiState.update { it.copy(draftSystemPrompt = prompt.trim().takeIf { text -> text.isNotBlank() }) }
    }

    fun selectDraftModel(profileId: String?) {
        _uiState.update { it.copy(draftModelProfileId = profileId) }
        viewModelScope.launch(Dispatchers.IO) { container.settingsStore.setActiveProfile(profileId) }
    }

    fun setGroupMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setGroupMode(enabled) }
    }

    /** 用已有对话的智能体创建一个群组会话。 */
    fun createGroup(memberIds: List<String>) {
        if (memberIds.size < 2) {
            showNotice("至少选择两个对话作为成员")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val members = memberIds.distinct()
                .mapNotNull { container.chatRepository.getConversation(it) }
                .filter { !it.isGroup }
            if (members.size < 2) {
                showNotice("成员不足，无法创建群组")
                return@launch
            }
            val state = _uiState.value
            val group = container.chatRepository.createGroupConversation(
                members = members,
                modelProfileId = state.draftModelProfileId ?: state.settings.activeProfileId,
            )
            currentConversationId.value = group.id
            _uiState.update {
                it.copy(
                    currentConversationId = group.id,
                    messages = emptyList(),
                    draftModelProfileId = null,
                    draftAiAvatar = null,
                    draftUserAvatar = null,
                    draftSystemPrompt = null,
                )
            }
        }
    }

    private suspend fun runGroupReplies(group: ConversationEntity, settings: ApiSettings) {
        val members = group.memberIds.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { container.chatRepository.getConversation(it) }
        if (members.isEmpty()) {
            showNotice("群组成员已被删除")
            return
        }
        for (member in members) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val profile = settings.resolveProfile(member.modelProfileId ?: group.modelProfileId)
            val effective = settings.withProfile(profile)
            val history = container.chatRepository.currentMessages(group.id)
            val prompt = buildGroupPrompt(group, member, history, effective)
            val assistant = MessageEntity(
                conversationId = group.id,
                role = MessageRole.ASSISTANT,
                content = "",
                status = DeliveryStatus.STREAMING,
                senderConversationId = member.id,
                senderName = member.title,
            )
            container.chatRepository.saveMessage(assistant)
            val partial = StringBuilder()
            try {
                val answer = container.modelGateway.streamChat(effective, prompt) { delta ->
                    partial.append(delta)
                    val streaming = assistant.copy(content = partial.toString())
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { message ->
                                if (message.id == assistant.id) streaming else message
                            },
                        )
                    }
                }
                container.chatRepository.updateMessage(
                    assistant.copy(
                        content = answer.ifBlank { "（${member.title} 没有返回内容）" },
                        status = DeliveryStatus.COMPLETE,
                    ),
                )
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    if (partial.isBlank()) {
                        container.chatRepository.deleteMessage(assistant)
                    } else {
                        container.chatRepository.updateMessage(
                            assistant.copy(content = partial.toString(), status = DeliveryStatus.COMPLETE),
                        )
                    }
                }
                throw cancelled
            } catch (error: Exception) {
                container.chatRepository.updateMessage(
                    assistant.copy(
                        content = partial.toString(),
                        status = DeliveryStatus.ERROR,
                        errorText = error.message ?: "请求失败，请稍后重试",
                    ),
                )
            }
        }
        container.chatRepository.touchConversation(group.id)
    }

    private suspend fun buildGroupPrompt(
        group: ConversationEntity,
        member: ConversationEntity,
        history: List<MessageEntity>,
        settings: ApiSettings,
    ): List<PromptMessage> {
        val names = group.memberIds.split(',')
            .filter { it.isNotBlank() }
            .mapNotNull { container.chatRepository.getConversation(it)?.title }
        val basePrompt = member.systemPrompt?.takeIf { it.isNotBlank() } ?: settings.systemPrompt
        val system = buildString {
            append(basePrompt.ifBlank { "你是一位乐于助人的 AI 助手。" })
            append("\n\n你正在参与多人对话「").append(group.title).append("」，成员包括：")
            append(names.joinToString("、").ifBlank { member.title })
            append("。请始终以「").append(member.title).append("」的身份发言，保持自己的风格，")
            append("不要替其他成员说话，也不要复述群聊规则。回答尽量简洁。")
        }
        val turns = history
            .filter { it.status == DeliveryStatus.COMPLETE && (it.content.isNotBlank() || it.imagePaths.isNotBlank()) }
            .takeLast(settings.contextMessages)
            .map { message ->
                if (message.role == MessageRole.USER) {
                    PromptMessage(MessageRole.USER, message.content.ifBlank { "（用户发送了图片）" })
                } else {
                    val speaker = message.senderName?.takeIf { it.isNotBlank() } ?: "成员"
                    PromptMessage(MessageRole.ASSISTANT, "【$speaker】${message.content}")
                }
            }
        return listOf(PromptMessage(MessageRole.SYSTEM, system)) + turns
    }

    fun saveSettings(settings: ApiSettings) {
        viewModelScope.launch {
            runCatching { container.settingsStore.saveSettings(settings) }
                .onSuccess { showNotice("设置已安全保存") }
                .onFailure { showNotice(it.message ?: "设置保存失败") }
        }
    }

    fun testConnection(settings: ApiSettings) {
        if (_uiState.value.testingConnection) return
        viewModelScope.launch {
            _uiState.update { it.copy(testingConnection = true) }
            try {
                container.modelGateway.complete(
                    settings.withProfile(settings.resolveProfile()),
                    listOf(PromptMessage(MessageRole.USER, "请仅回复：连接成功")),
                    maxTokens = 16,
                )
                showNotice("连接成功，模型响应正常")
            } catch (error: Exception) {
                showNotice(error.message ?: "连接失败")
            } finally {
                _uiState.update { it.copy(testingConnection = false) }
            }
        }
    }

    fun saveCroppedAvatar(bitmap: Bitmap, isUser: Boolean) {
        viewModelScope.launch {
            try {
                val oldPath = if (isUser) _uiState.value.avatars.userAvatar else _uiState.value.avatars.aiAvatar
                val path = container.avatarStorage.saveAvatarBitmap(bitmap, isUser)
                container.settingsStore.saveAvatar(isUser, path)
                container.avatarStorage.removeStoredAvatar(oldPath)
                showNotice(if (isUser) "我的头像已更新" else "AI 头像已更新")
            } catch (error: Exception) {
                showNotice(error.message ?: "头像保存失败")
            }
        }
    }

    fun removeAvatar(isUser: Boolean) {
        viewModelScope.launch {
            val oldPath = if (isUser) _uiState.value.avatars.userAvatar else _uiState.value.avatars.aiAvatar
            container.settingsStore.saveAvatar(isUser, null)
            container.avatarStorage.removeStoredAvatar(oldPath)
        }
    }

    fun addMemory(content: String, category: MemoryCategory) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            container.memoryRepository.save(
                MemoryEntity(
                    category = category,
                    content = content.trim(),
                    sourceConversationId = currentConversationId.value,
                ),
            )
        }
    }

    fun updateMemory(memory: MemoryEntity, content: String, category: MemoryCategory) {
        if (content.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            container.memoryRepository.save(
                memory.copy(content = content.trim(), category = category, updatedAt = System.currentTimeMillis()),
            )
        }
    }

    fun toggleMemory(memory: MemoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            container.memoryRepository.save(memory.copy(enabled = !memory.enabled))
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch(Dispatchers.IO) { container.memoryRepository.delete(memory) }
    }

    fun clearMemories() {
        viewModelScope.launch(Dispatchers.IO) {
            container.memoryRepository.clear()
            showNotice("长期记忆已全部清除")
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setMemoryEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setThemeMode(mode) }
    }

    fun clearSummary(conversationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.chatRepository.clearSummary(conversationId)
            showNotice("会话摘要已清除")
        }
    }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    fun onIntroFinished() = _uiState.update { it.copy(showIntro = false) }

    private fun showNotice(message: String) = _uiState.update { it.copy(notice = message) }

    private companion object {
        const val SUGGESTION_TTL_MS = 24 * 60 * 60 * 1000L
        const val MAX_IMAGES_PER_MESSAGE = 3
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
