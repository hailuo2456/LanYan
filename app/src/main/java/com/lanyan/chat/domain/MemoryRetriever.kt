package com.lanyan.chat.domain

import com.lanyan.chat.data.local.MemoryEntity
import kotlin.math.ln

object MemoryRetriever {
    fun retrieve(
        query: String,
        memories: List<MemoryEntity>,
        now: Long = System.currentTimeMillis(),
        limit: Int = 5,
    ): List<MemoryEntity> {
        val queryTokens = tokens(query)
        return memories
            .filter { it.enabled && it.content.isNotBlank() }
            .map { memory ->
                val overlap = tokens(memory.content).intersect(queryTokens).size
                val ageDays = ((now - (memory.lastUsedAt ?: memory.updatedAt)).coerceAtLeast(0L)) / 86_400_000.0
                val score = overlap * 4.0 + memory.importance * 0.35 +
                    ln((memory.useCount + 1).toDouble()) * 0.15 + 1.0 / (ageDays + 2.0)
                memory to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun similarity(first: String, second: String): Double {
        val left = tokens(first)
        val right = tokens(second)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size.toDouble()
    }

    fun tokens(text: String): Set<String> {
        val normalized = text.lowercase()
        val words = Regex("[a-z0-9_]{2,}").findAll(normalized).map { it.value }.toSet()
        val chinese = Regex("[\\u4e00-\\u9fff]+").findAll(normalized)
            .flatMap { match ->
                val value = match.value
                if (value.length == 1) listOf(value)
                else value.map { it.toString() } + value.windowed(2)
            }
            .filterNot { it in STOP_WORDS }
            .toSet()
        return words + chinese
    }

    private val STOP_WORDS = setOf(
        "我们", "你们", "他们", "这个", "那个", "一个", "什么", "怎么", "为什么", "可以",
        "就是", "还是", "现在", "知道", "觉得", "需要", "使用", "进行", "以及", "因为", "所以",
        "的", "了", "我", "你", "是", "在", "和", "有", "请", "吗", "呢", "吧", "很", "也", "都",
    )
}
