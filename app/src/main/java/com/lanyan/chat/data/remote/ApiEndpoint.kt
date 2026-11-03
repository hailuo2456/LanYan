package com.lanyan.chat.data.remote

object ApiEndpoint {
    fun chatCompletions(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://") || normalized.startsWith("http://")) {
            "API 地址必须以 http:// 或 https:// 开头"
        }
        return if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/chat/completions"
        }
    }
}
