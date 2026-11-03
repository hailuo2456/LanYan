package com.lanyan.chat.domain

import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.MessageRole
import com.lanyan.chat.data.model.PromptMessage
import com.lanyan.chat.data.remote.ModelGateway

class SuggestionEngine(private val modelGateway: ModelGateway) {

    suspend fun generate(settings: ApiSettings, recentDigest: String): List<String> {
        val angle = ANGLES.random()
        val answer = modelGateway.complete(
            settings = settings,
            messages = listOf(
                PromptMessage(MessageRole.SYSTEM, SYSTEM_PROMPT),
                PromptMessage(
                    MessageRole.USER,
                    buildString {
                        append("用户最近的聊天片段：\n")
                        append(recentDigest.ifBlank { "（还没有历史聊天）" })
                        append("\n\n请生成 3 个开场问题，整体偏向「$angle」这个角度，但三个问题之间也要各不相同。")
                    },
                ),
            ),
            maxTokens = 160,
        )
        return parse(answer)
    }

    companion object {
        val FALLBACK = listOf(
            "帮我制定一份高效的一周学习计划",
            "用通俗的语言解释什么是长期记忆",
            "为我的新项目 brainstorm 三个创意",
        )

        private const val SYSTEM_PROMPT =
            "你根据用户最近的聊天内容，为 AI 助手生成适合开启新对话的开场问题。" +
                "要求：只输出 3 行，每行一个简短的问题，每个问题不超过 18 个字；" +
                "不要编号、不要引号、不要解释或任何其他文字。"

        private val ANGLES = listOf(
            "学习方法与自我提升",
            "工作效率与工具",
            "创作与表达",
            "科技与数码",
            "生活与健康",
            "理财与消费",
            "职业发展",
            "趣味知识与脑洞",
        )

        fun parse(raw: String): List<String> = raw.lineSequence()
            .map { cleanLine(it) }
            .filter { it.length in 4..24 }
            .distinct()
            .take(3)
            .toList()

        fun cleanTitle(raw: String): String? {
            val cleaned = raw.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                .replace(Regex("^(?:标题|题目)\\s*[:：]\\s*"), "")
                .replace(NUMBERING_PREFIX, "")
                .trim('*', ' ', '\t')
                .trim('“', '”', '"', '「', '」', '『', '』', '《', '》', '。', '.', '！', '!', '？', '?', '，', ',', '；', ';', '、')
                .trim()
                .take(16)
            return cleaned.takeIf { it.length >= 2 }
        }

        private fun cleanLine(line: String): String = line.trim()
            .replace(NUMBERING_PREFIX, "")
            .trim('*', ' ', '\t')
            .trim('“', '”', '"', '「', '」', '『', '』', '《', '》')
            .trim()

        private val NUMBERING_PREFIX = Regex("^\\s*(?:[0-9０-９]{1,2}[.、．)）]|[-•·*])\\s*")
    }
}
