package com.lanyan.chat.domain

import com.lanyan.chat.data.remote.ApiEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiEndpointTest {
    @Test
    fun `base url gets completions path once`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            ApiEndpoint.chatCompletions(" https://api.deepseek.com/v1/ "),
        )
    }

    @Test
    fun `full endpoint stays unchanged`() {
        val url = "http://127.0.0.1:11434/v1/chat/completions"
        assertEquals(url, ApiEndpoint.chatCompletions(url))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid protocol is rejected`() {
        ApiEndpoint.chatCompletions("api.example.com/v1")
    }
}
