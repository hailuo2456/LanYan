package com.lanyan.chat.data.repository

import com.lanyan.chat.data.local.MemoryDao
import com.lanyan.chat.data.local.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val memories: Flow<List<MemoryEntity>> = memoryDao.observeAll()

    suspend fun enabledMemories(): List<MemoryEntity> = memoryDao.getEnabled()

    suspend fun allMemories(): List<MemoryEntity> = memoryDao.getAll()

    suspend fun save(memory: MemoryEntity) = memoryDao.upsert(memory)

    suspend fun delete(memory: MemoryEntity) = memoryDao.delete(memory)

    suspend fun clear() = memoryDao.clear()

    suspend fun markUsed(ids: List<String>, usedAt: Long = System.currentTimeMillis()) {
        ids.forEach { id ->
            memoryDao.get(id)?.let { memory ->
                memoryDao.update(memory.copy(lastUsedAt = usedAt, useCount = memory.useCount + 1))
            }
        }
    }
}
