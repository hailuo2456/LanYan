package com.lanyan.chat.data.repository

import com.lanyan.chat.data.local.ConversationDao
import com.lanyan.chat.data.local.ConversationEntity
import com.lanyan.chat.data.local.MessageDao
import com.lanyan.chat.data.local.MessageEntity
import com.lanyan.chat.data.model.DeliveryStatus
import com.lanyan.chat.data.model.MessageRole
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
) {
    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeAll()

    fun messages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeForConversation(conversationId)

    suspend fun currentMessages(conversationId: String): List<MessageEntity> =
        messageDao.getForConversation(conversationId)

    suspend fun getConversation(id: String): ConversationEntity? = conversationDao.get(id)

    suspend fun createConversation(firstMessage: String): ConversationEntity {
        val conversation = ConversationEntity(title = firstMessage.lineSequence().first().take(32))
        conversationDao.upsert(conversation)
        return conversation
    }

    suspend fun createGroupConversation(
        members: List<ConversationEntity>,
        modelProfileId: String?,
    ): ConversationEntity {
        val conversation = ConversationEntity(
            title = "群组 · ${members.size} 位成员",
            isGroup = true,
            memberIds = members.joinToString(",") { it.id },
            modelProfileId = modelProfileId,
        )
        conversationDao.upsert(conversation)
        return conversation
    }

    suspend fun setConversationModel(id: String, profileId: String?) =
        conversationDao.setModelProfile(id, profileId)

    suspend fun saveMessage(message: MessageEntity) = messageDao.upsert(message)

    suspend fun updateMessage(message: MessageEntity) = messageDao.update(message)

    suspend fun deleteMessage(message: MessageEntity) = messageDao.delete(message)

    suspend fun touchConversation(id: String) {
        conversationDao.get(id)?.let { conversationDao.update(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    suspend fun renameConversation(id: String, title: String) = conversationDao.rename(id, title.trim())

    suspend fun setConversationAiAvatar(id: String, path: String?) = conversationDao.setAiAvatar(id, path)

    suspend fun setConversationUserAvatar(id: String, path: String?) = conversationDao.setUserAvatar(id, path)

    suspend fun setConversationPrompt(id: String, prompt: String?) =
        conversationDao.setPrompt(id, prompt?.takeIf { it.isNotBlank() })

    suspend fun countMessages(): Int = messageDao.countAll()

    suspend fun recentChatDigest(limit: Int = 8, maxChars: Int = 60): String {
        val seen = LinkedHashMap<String, String>()
        messageDao.getRecentUserMessages(limit).forEach { message ->
            if (message.content.isBlank()) return@forEach
            seen.putIfAbsent(message.conversationId, message.content.trim().lineSequence().first().take(maxChars))
        }
        return seen.values.reversed().joinToString("\n")
    }

    suspend fun firstMessages(conversationId: String, limit: Int = 8): List<MessageEntity> =
        messageDao.getFirstForConversation(conversationId, limit)

    suspend fun deleteConversation(conversation: ConversationEntity) = conversationDao.delete(conversation)

    suspend fun updateSummary(id: String, summary: String, messageCount: Int) {
        conversationDao.get(id)?.let {
            conversationDao.update(it.copy(summary = summary, summarizedMessageCount = messageCount))
        }
    }

    suspend fun clearSummary(id: String) = conversationDao.clearSummary(id)

    companion object {
        fun userMessage(conversationId: String, content: String, imagePaths: List<String> = emptyList()) =
            MessageEntity(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = content,
                imagePaths = imagePaths.joinToString("\n"),
            )

        fun pendingAssistant(conversationId: String) = MessageEntity(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = DeliveryStatus.STREAMING,
        )
    }
}
