package com.lanyan.chat.domain

import com.lanyan.chat.data.local.ConversationEntity
import com.lanyan.chat.data.local.MessageEntity
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.DeliveryStatus
import com.lanyan.chat.data.model.MessageRole
import com.lanyan.chat.data.model.PreparedPrompt
import com.lanyan.chat.data.model.PromptMessage
import com.lanyan.chat.data.repository.MemoryRepository

class MemoryEngine(private val memoryRepository: MemoryRepository) {
    suspend fun prepare(
        conversation: ConversationEntity,
        messages: List<MessageEntity>,
        settings: ApiSettings,
    ): PreparedPrompt {
        val userQuery = messages.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()
        val relevantMemories = if (settings.memoryEnabled) {
            MemoryRetriever.retrieve(userQuery, memoryRepository.enabledMemories())
        } else {
            emptyList()
        }
        if (relevantMemories.isNotEmpty()) memoryRepository.markUsed(relevantMemories.map { it.id })

        val systemContent = buildString {
            val basePrompt = conversation.systemPrompt?.takeIf { it.isNotBlank() }
                ?: settings.systemPrompt
            append(basePrompt.ifBlank { "你是一位乐于助人的 AI 助手。" })
            if (relevantMemories.isNotEmpty()) {
                append("\n\n以下是可能相关的长期用户记忆。仅在适合当前对话时自然使用，不要复述记忆列表，不要把记忆当作绝对事实：")
                relevantMemories.forEach { memory ->
                    append("\n- [").append(memory.category.label).append("] ").append(memory.content)
                }
            }
            if (conversation.summary.isNotBlank()) {
                append("\n\n当前会话较早内容的摘要：\n").append(conversation.summary)
            }
        }

        val recent = messages
            .filter { it.status == DeliveryStatus.COMPLETE && (it.content.isNotBlank() || it.imagePaths.isNotBlank()) }
            .drop(conversation.summarizedMessageCount.coerceAtMost(messages.size))
            .takeLast(settings.contextMessages)
        val lastUserIndex = recent.indexOfLast { it.role == MessageRole.USER }

        return PreparedPrompt(
            messages = listOf(PromptMessage(MessageRole.SYSTEM, systemContent)) + recent.mapIndexed { index, message ->
                val hasImages = message.imagePaths.isNotBlank()
                PromptMessage(
                    role = message.role,
                    content = message.content.ifBlank { if (hasImages) "（用户发送了图片）" else "" },
                    // 只为最新一条用户消息附带图片，历史图片用占位文字表示，避免重复上传消耗 Token
                    images = if (index == lastUserIndex) message.imagePaths.split('\n').filter { it.isNotBlank() } else emptyList(),
                )
            },
            memoryIds = relevantMemories.map { it.id },
            usedSummary = conversation.summary.isNotBlank(),
        )
    }
}
