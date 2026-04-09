package com.zlearn.network

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Response

// 阿里云大模型对话请求体
// 你需要根据实际API文档调整字段
// 这里只做通用示例

data class AliyunChatRequest(
    val model: String, // 如 qwen-turbo
    val input: Input
)
data class Input(
    val messages: List<Message>
)
data class Message(
    val role: String, // "user" or "assistant"
    val content: String
)
data class AliyunChatResponse(
    val code: String?,
    val output: Output?
)
data class Output(
    val text: String?
)

interface AiApiService {
    @Headers("Content-Type: application/json")
    @POST("/api/v1/services/aigc/text-generation/generation") // 阿里云Qwen对话API路径
    suspend fun chat(@Body request: AliyunChatRequest): Response<AliyunChatResponse>
}
