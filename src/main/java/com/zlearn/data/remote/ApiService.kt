package com.zlearn.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response

// 注册/登录请求体
 data class AuthRequest(val username: String, val password: String)
// 注册/登录响应体
 data class AuthResponse(val success: Boolean, val token: String?, val message: String?)

interface ApiService {
    @POST("/api/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>
}

