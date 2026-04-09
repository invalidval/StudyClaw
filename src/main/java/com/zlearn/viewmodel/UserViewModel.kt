package com.zlearn.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor() : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // 本地假注册逻辑，允许任意注册
            if (username.isNotBlank() && password.isNotBlank()) {
                _authState.value = AuthState.Success(token = "mock-token-${'$'}username")
            } else {
                _authState.value = AuthState.Error("用户名和密码不能为空")
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            // 本地假登录逻辑，允许任意非空用户名密码
            if (username.isNotBlank() && password.isNotBlank()) {
                _authState.value = AuthState.Success(token = "mock-token-${'$'}username")
            } else {
                _authState.value = AuthState.Error("用户名和密码不能为空")
            }
        }
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
