package com.lanyan.chat.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lanyan.chat.data.model.DeliveryStatus
import com.lanyan.chat.data.model.MemoryCategory
import com.lanyan.chat.data.model.MessageRole
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val summary: String = "",
    val summarizedMessageCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val autoTitled: Boolean = false,
    val aiAvatarPath: String? = null,
    val userAvatarPath: String? = null,
    val systemPrompt: String? = null,
    @ColumnInfo(defaultValue = "0") val isGroup: Boolean = false,
    @ColumnInfo(defaultValue = "''") val memberIds: String = "",
    val modelProfileId: String? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: DeliveryStatus = DeliveryStatus.COMPLETE,
    val errorText: String? = null,
    val usedMemoryIds: String = "",
    val usedSummary: Boolean = false,
    @ColumnInfo(defaultValue = "''") val imagePaths: String = "",
    val senderConversationId: String? = null,
    val senderName: String? = null,
)

@Entity(
    tableName = "memories",
    indices = [Index("updatedAt"), Index("sourceConversationId")],
)
data class MemoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val category: MemoryCategory,
    val content: String,
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null,
    val useCount: Int = 0,
    val sourceConversationId: String? = null,
    val enabled: Boolean = true,
)
