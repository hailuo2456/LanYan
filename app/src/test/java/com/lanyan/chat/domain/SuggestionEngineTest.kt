package com.lanyan.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun `parse strips numbering bold and wrapping quotes`() {
        val parsed = SuggestionEngine.parse(
            """
            1. **帮我写一封请假邮件**
            2、用简单的话解释量子纠缠
            3. 「规划一次三天的旅行」
            """.trimIndent(),
        )
        assertEquals(listOf("帮我写一封请假邮件", "用简单的话解释量子纠缠", "规划一次三天的旅行"), parsed)
    }

    @Test
    fun `parse filters short lines duplicates and caps at three`() {
        val parsed = SuggestionEngine.parse(
            """
            嗯
            帮我写一份周报
            帮我写一份周报
            帮我写一份周报
            """.trimIndent(),
        )
        assertEquals(listOf("帮我写一份周报"), parsed)
    }

    @Test
    fun `parse rejects overly long lines`() {
        val longLine = "请帮我非常详细地分析一下".repeat(10)
        assertEquals(emptyList<String>(), SuggestionEngine.parse(longLine))
    }

    @Test
    fun `cleanTitle strips prefixes punctuation and brackets`() {
        assertEquals("周报助手方案", SuggestionEngine.cleanTitle("标题：周报助手方案。"))
        assertEquals("旅行规划", SuggestionEngine.cleanTitle("「旅行规划」"))
        assertEquals("长期记忆是什么", SuggestionEngine.cleanTitle("1. **长期记忆是什么？**"))
    }

    @Test
    fun `cleanTitle rejects blank or too short results`() {
        assertNull(SuggestionEngine.cleanTitle("好"))
        assertNull(SuggestionEngine.cleanTitle(""))
        assertNull(SuggestionEngine.cleanTitle("标题：\n下一行"))
    }
}
