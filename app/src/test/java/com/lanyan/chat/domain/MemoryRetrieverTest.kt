package com.lanyan.chat.domain

import com.lanyan.chat.data.local.MemoryEntity
import com.lanyan.chat.data.model.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRetrieverTest {
    @Test
    fun `relevant preference ranks before unrelated memory`() {
        val preference = memory("1", "用户喜欢简洁直接的回答", MemoryCategory.PREFERENCE, 3)
        val location = memory("2", "用户居住在上海", MemoryCategory.PROFILE, 5)
        val result = MemoryRetriever.retrieve(
            query = "以后回答能否更简洁？",
            memories = listOf(location, preference),
            now = 1_000_000L,
        )
        assertEquals("1", result.first().id)
    }

    @Test
    fun `disabled memories are never retrieved`() {
        val disabled = memory("1", "用户喜欢红茶").copy(enabled = false)
        val enabled = memory("2", "用户从事产品设计")
        val result = MemoryRetriever.retrieve("红茶", listOf(disabled, enabled))
        assertTrue(result.none { it.id == "1" })
    }

    @Test
    fun `similar memories can be detected for updates`() {
        val similarity = MemoryRetriever.similarity("用户喜欢简洁的回答", "用户喜欢简洁直接的回答")
        assertTrue(similarity > 0.5)
    }

    @Test
    fun `retrieval respects limit`() {
        val memories = (1..10).map { memory(it.toString(), "用户喜欢第 $it 种设计") }
        assertEquals(5, MemoryRetriever.retrieve("设计", memories).size)
    }

    private fun memory(
        id: String,
        content: String,
        category: MemoryCategory = MemoryCategory.FACT,
        importance: Int = 3,
    ) = MemoryEntity(
        id = id,
        category = category,
        content = content,
        importance = importance,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
