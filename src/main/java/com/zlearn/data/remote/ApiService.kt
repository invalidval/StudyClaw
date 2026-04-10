package com.zlearn.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.Response

// 注册/登录请求体
 data class AuthRequest(val username: String, val password: String)
// 注册/登录响应体
 data class AuthResponse(val success: Boolean, val token: String?, val message: String?, val user_id: Int? = null)

data class SyncQuestionDto(
    val id: Int? = null,
    val imagePath: String,
    val ocrText: String,
    val aiAnalysis: String,
    val summary: String,
    val subject: String,
    val difficulty: Int,
    val createTime: Long,
    val isArchived: Boolean,
    val archiveType: String? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long
)

data class SyncQuestionsRequest(
    val questions: List<SyncQuestionDto>
)

data class SyncQuestionsResponse(
    val success: Boolean,
    val questions: List<SyncQuestionDto> = emptyList(),
    val message: String? = null
)

interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/questions/sync")
    suspend fun syncQuestions(
        @Header("Authorization") authorization: String,
        @Body request: SyncQuestionsRequest
    ): Response<SyncQuestionsResponse>
}

