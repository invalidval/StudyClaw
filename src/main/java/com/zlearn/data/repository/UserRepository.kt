package com.zlearn.data.repository

import com.zlearn.data.model.User
import com.zlearn.data.remote.ApiService
import com.zlearn.data.remote.AuthRequest
import com.zlearn.data.remote.AuthResponse
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun register(username: String, password: String): AuthResponse? {
        return try {
            val response = apiService.register(AuthRequest(username, password))
            response.body()
        } catch (e: Exception) {
            AuthResponse(success = false, token = null, message = e.localizedMessage ?: "网络异常")
        }
    }

    suspend fun login(username: String, password: String): AuthResponse? {
        return try {
            val response = apiService.login(AuthRequest(username, password))
            response.body()
        } catch (e: Exception) {
            AuthResponse(success = false, token = null, message = e.localizedMessage ?: "网络异常")
        }
    }
}
