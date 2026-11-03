package com.lanyan.chat.domain

import com.lanyan.chat.data.local.MemoryEntity
import com.lanyan.chat.data.local.MessageEntity
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.MemoryCategory
import com.lanyan.chat.data.model.MessageRole
import com.lanyan.chat.data.model.PromptMessage
import com.lanyan.chat.data.remote.ModelGateway
import com.lanyan.chat.data.repository.ChatRepository
import com.lanyan.chat.data.repository.MemoryRepository

class MemoryConsolidator(
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val modelGateway: ModelGateway,
) {
    suspend fun learnFromTurn(userText: String, conversationId: String): Int {
        val candidates = extract(userText, conversationId)
        var changes = 0
        candidates.forEach { candidate ->
            val existing = memoryRepository.allMemories()
            val duplicate = existing.firstOrNull { memory ->
                memory.category == candidate.category &&
                    (memory.content.equals(candidate.content, ignoreCase = true) ||
                        MemoryRetriever.similarity(memory.content, candidate.content) >= 0.72)
            }
            if (duplicate == null) {
                memoryRepository.save(candidate)
                changes++
            } else if (duplicate.content != candidate.content) {
                memoryRepository.save(
                    duplicate.copy(
                        content = candidate.content,
                        importance = maxOf(duplicate.importance, candidate.importance),
                        updatedAt = System.currentTimeMillis(),
                        sourceConversationId = conversationId,
                        enabled = true,
                    ),
                )
                changes++
            }
        }
        return changes
    }

    suspend fun summarizeIfNeeded(conversationId: String, settings: ApiSettings): Boolean {
        val conversation = chatRepository.getConversation(conversationId)
        val messages = chatRepository.currentMessages(conversationId)
            .filter { it.content.isNotBlank() || it.imagePaths.isNotBlank() }
        val existing = messages.take(conversation?.summarizedMessageCount ?: 0).size
        if (messages.size - existing < SUMMARY_INTERVAL || messages.size <= KEEP_RECENT) return false
        val end = messages.size - KEEP_RECENT
        val toSummarize = messages.take(end).drop(existing)
        if (toSummarize.isEmpty()) return false
        val oldSummary = conversation?.summary.orEmpty()
        val transcript = toSummarize.joinToString("\n") { message ->
            val speaker = if (message.role == MessageRole.USER) "用户" else "助手"
            val text = message.content.ifBlank { "（图片）" }
            "$speaker：$text"
        }.take(MAX_TRANSCRIPT_CHARS)
        val summary = modelGateway.complete(
            settings = settings,
            messages = listOf(
                PromptMessage(
                    MessageRole.SYSTEM,
                    "你负责维护会话摘要。保留用户目标、约束、决定、待办和关键事实，省略客套话。用中文输出不超过 350 字的第三人称摘要，不要使用 Markdown。",
                ),
                PromptMessage(
                    MessageRole.USER,
                    "已有摘要：\n${oldSummary.ifBlank { "无" }}\n\n新增对话：\n$transcript\n\n请生成合并后的最新摘要。",
                ),
            ),
            maxTokens = 500,
        )
        chatRepository.updateSummary(conversationId, summary, end)
        return true
    }

    private fun extract(text: String, conversationId: String): List<MemoryEntity> {
        val now = System.currentTimeMillis()
        val results = mutableListOf<MemoryEntity>()
        fun add(category: MemoryCategory, content: String, importance: Int = 3) {
            val cleaned = content.trim().trim('。', '；', ';', '\n').take(180)
            if (cleaned.length >= 2 && results.none { it.content == cleaned }) {
                results += MemoryEntity(
                    category = category,
                    content = cleaned,
                    importance = importance,
                    createdAt = now,
                    updatedAt = now,
                    sourceConversationId = conversationId,
                )
            }
        }

        Regex("(?:^|[。！？!?\n])\\s*请记住[：:，,]?\\s*([^。！？!?\n]{2,180})")
            .findAll(text).forEach { add(MemoryCategory.FACT, it.groupValues[1], 5) }
        Regex("(?:我叫|我的名字是)\\s*([\\p{L}\\p{N}_·-]{2,24})")
            .find(text)?.let { add(MemoryCategory.PROFILE, "用户的名字是${it.groupValues[1]}", 4) }
        Regex("(?:我住在|我来自)\\s*([^。！？!?\n，,]{2,40})")
            .find(text)?.let { match ->
                val prefix = if (match.value.startsWith("我住在")) "用户居住在" else "用户来自"
                add(MemoryCategory.PROFILE, prefix + match.groupValues[1], 3)
            }
        Regex("我的职业是\\s*([^。！？!?\n，,]{2,40})")
            .find(text)?.let { add(MemoryCategory.PROFILE, "用户的职业是${it.groupValues[1]}", 4) }
        Regex("我(?:喜欢|偏好)\\s*([^。！？!?\n]{2,100})")
            .findAll(text).forEach { add(MemoryCategory.PREFERENCE, "用户喜欢或偏好${it.groupValues[1]}") }
        Regex("我(?:不喜欢|讨厌)\\s*([^。！？!?\n]{2,100})")
            .findAll(text).forEach { add(MemoryCategory.PREFERENCE, "用户不喜欢${it.groupValues[1]}") }
        Regex("(?:以后请|请始终|请一直)\\s*([^。！？!?\n]{2,120})")
            .findAll(text).forEach { add(MemoryCategory.PREFERENCE, "用户希望助手始终${it.groupValues[1]}", 4) }
        Regex("我(?:正在开发|在做|正在做)\\s*([^。！？!?\n]{2,120})")
            .findAll(text).forEach { add(MemoryCategory.PROJECT, "用户正在进行${it.groupValues[1]}", 3) }
        return results
    }

    private companion object {
        const val SUMMARY_INTERVAL = 12
        const val KEEP_RECENT = 4
        const val MAX_TRANSCRIPT_CHARS = 12_000
    }
}
