package com.zlearn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zlearn.data.local.AuthSessionStore
import com.zlearn.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authSessionStore: AuthSessionStore
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    val token: StateFlow<String?> = authSessionStore.token

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            if (username.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("用户名和密码不能为空")
                return@launch
            }
            val response = userRepository.register(username, hashPassword(password))
            if (response?.success == true && !response.token.isNullOrBlank()) {
                authSessionStore.saveToken(response.token)
                _authState.value = AuthState.Success(token = response.token)
            } else {
                _authState.value = AuthState.Error(response?.message ?: "注册失败")
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            if (username.isBlank() || password.isBlank()) {
                _authState.value = AuthState.Error("用户名和密码不能为空")
                return@launch
            }
            val response = userRepository.login(username, hashPassword(password))
            if (response?.success == true && !response.token.isNullOrBlank()) {
                authSessionStore.saveToken(response.token)
                _authState.value = AuthState.Success(token = response.token)
            } else {
                _authState.value = AuthState.Error(response?.message ?: "登录失败")
            }
        }
    }

    fun logout() {
        authSessionStore.clearToken()
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val token: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}
