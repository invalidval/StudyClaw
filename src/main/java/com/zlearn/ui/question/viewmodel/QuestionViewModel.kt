package com.zlearn.ui.question.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zlearn.domain.usecase.QuestionUseCases
import com.zlearn.data.database.QuestionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.reactivex.rxjava3.disposables.CompositeDisposable
import okhttp3.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import org.json.JSONException

@HiltViewModel
class QuestionViewModel @Inject constructor(
    private val useCases: QuestionUseCases
) : ViewModel() {

    private val disposables = CompositeDisposable()
    private var streamJob: Job? = null  // 用于取消正在进行的流式请求

    // 题目列表
    private val _questions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val questions: StateFlow<List<QuestionEntity>> = _questions.asStateFlow()

    // 普通 AI 响应（非流式）
    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    // 流式 AI 响应（实时更新）
    private val _aiStreamResponse = MutableStateFlow("")
    val aiStreamResponse: StateFlow<String> = _aiStreamResponse.asStateFlow()

    // 加载状态（用于 UI 显示）
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 归档类型
    private val _archiveTypes = MutableStateFlow<List<String>>(emptyList())
    val archiveTypes: StateFlow<List<String>> = _archiveTypes.asStateFlow()

    // 筛选后的题目列表
    private val _filteredQuestions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val filteredQuestions: StateFlow<List<QuestionEntity>> = _filteredQuestions.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        loadQuestions()
        loadArchiveTypes() // 初始化时加载归档类型
    }

    fun loadQuestions() {
        viewModelScope.launch {
            try {
                _questions.value = useCases.getQuestions() // 加载所有题目（未归档+已归档）
            } catch (e: Exception) {
                _error.value = "加载题目失败: ${e.message}"
            }
        }
    }

    fun addQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            try {
                useCases.addQuestion(question)
                loadQuestions()
            } catch (e: Exception) {
                _error.value = "添加题目失败: ${e.message}"
            }
        }
    }

    fun updateQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            try {
                useCases.updateQuestion(
                    question.copy(updatedAt = System.currentTimeMillis())
                )
                loadQuestions()
            } catch (e: Exception) {
                _error.value = "更新题目失败: ${e.message}"
            }
        }
    }

    fun deleteQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            try {
                useCases.deleteQuestion(question)
                loadQuestions()
            } catch (e: Exception) {
                _error.value = "删除题目失败: ${e.message}"
            }
        }
    }

    fun archiveQuestion(id: Int) {
        viewModelScope.launch {
            try {
                useCases.archiveQuestion(id)
                loadQuestions()
            } catch (e: Exception) {
                _error.value = "归档题目失败: ${e.message}"
            }
        }
    }

    fun archiveQuestion(id: Int, archiveType: String) {
        viewModelScope.launch {
            try {
                useCases.archiveQuestion(id, archiveType)
                loadQuestions()
                loadArchiveTypes() // 归档后立即刷新归档类型
            } catch (e: Exception) {
                _error.value = "归档题目失败: ${e.message}"
            }
        }
    }

    fun unarchiveQuestion(id: Int) {
        viewModelScope.launch {
            try {
                useCases.unarchiveQuestion(id)
                loadQuestions()
                loadArchiveTypes()
            } catch (e: Exception) {
                _error.value = "取消归档失败: ${e.message}"
            }
        }
    }

    fun chatWithAi(message: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val request = com.zlearn.network.AliyunChatRequest(
                    model = "qwen-turbo",
                    input = com.zlearn.network.Input(
                        messages = listOf(com.zlearn.network.Message(role = "user", content = message))
                    )
                )
                val response = useCases.chatWithAi(request)
                if (response.isSuccessful) {
                    _aiResponse.value = response.body()?.output?.text
                } else {
                    _aiResponse.value = "AI请求失败: ${response.code()}"
                    _error.value = "AI请求失败: ${response.code()}"
                }
            } catch (e: Exception) {
                _aiResponse.value = "AI请求出错: ${e.message}"
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    // 独立的 summary 生成方法
    suspend fun generateSummary(ocrText: String): String {
        return try {
            val request = com.zlearn.network.AliyunChatRequest(
                model = "qwen-turbo",
                input = com.zlearn.network.Input(
                    messages = listOf(
                        com.zlearn.network.Message(role = "user", content = "请用20字以内总结这段内容：$ocrText")
                    )
                )
            )
            val response = useCases.chatWithAi(request)
            if (response.isSuccessful) {
                response.body()?.output?.text ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun updateAiAnalysis(id: Int, newAnalysis: String) {
        viewModelScope.launch {
            try {
                val question = useCases.getQuestionById(id)
                if (question != null) {
                    useCases.updateQuestion(
                        question.copy(
                            aiAnalysis = newAnalysis,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    loadQuestions()
                }
            } catch (e: Exception) {
                _error.value = "更新AI分析失败: ${e.message}"
            }
        }
    }

    fun syncQuestions(token: String) {
        viewModelScope.launch {
            _syncing.value = true
            _syncMessage.value = null
            try {
                _syncMessage.value = useCases.syncQuestions(token)
                loadQuestions()
                loadArchiveTypes()
            } catch (e: Exception) {
                _syncMessage.value = e.message ?: "同步失败"
            } finally {
                _syncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun clearAiStreamResponse() {
        _aiStreamResponse.value = ""
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * 流式 AI 对话（SSE）
     * 使用 OkHttp 实现真正的流式输出
     */
    fun chatWithAiStream(message: String) {
        // 取消之前的流式请求
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _aiStreamResponse.value = ""

            try {
                val request = com.zlearn.network.AliyunChatRequest(
                    model = "qwen-plus",
                    input = com.zlearn.network.Input(
                        messages = listOf(com.zlearn.network.Message(role = "user", content = message))
                    )
                )

                // 在 IO 线程执行网络请求
                withContext(Dispatchers.IO) {
                    var responseBody: ResponseBody? = null
                    try {
                        responseBody = useCases.chatWithAiStream(request)
                        val source = responseBody.source()
                        val buffer = StringBuilder()

                        while (isActive) {  // 检查协程是否还在运行
                            val line = source.readUtf8Line() ?: break

                            // 解析 SSE 格式：data: {...}
                            if (line.startsWith("data:")) {
                                val jsonStr = line.removePrefix("data:").trim()

                                // 流结束标志
                                if (jsonStr == "[DONE]") {
                                    break
                                }

                                if (jsonStr.isNotBlank()) {
                                    try {
                                        val obj = JSONObject(jsonStr)
                                        // 根据你的 API 返回格式解析
                                        val content = obj.optJSONObject("output")
                                            ?.optString("text")
                                            ?: obj.optString("text", "")

                                        if (content.isNotBlank()) {
                                            // 只追加新内容，防止重复覆盖和重复内容
                                            val old = buffer.toString()
                                            val diff = if (content.startsWith(old)) {
                                                content.removePrefix(old)
                                            } else if (!old.contains(content)) {
                                                content
                                            } else {
                                                ""
                                            }
                                            if (diff.isNotEmpty()) {
                                                buffer.append(diff)
                                                withContext(Dispatchers.Main) {
                                                    _aiStreamResponse.value = buffer.toString()
                                                }
                                            }
                                        }
                                    } catch (e: JSONException) {
                                        // 忽略非 JSON 行
                                    }
                                }
                            }
                        }

                        // 流式结束，确保最终内容已更新
                        withContext(Dispatchers.Main) {
                            if (buffer.isNotEmpty()) {
                                _aiStreamResponse.value = buffer.toString()
                            }
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val errorMsg = when (e) {
                                is java.net.SocketTimeoutException -> "网络超时，请稍后重试"
                                is java.io.IOException -> "网络连接异常，请检查网络"
                                else -> "AI请求出错: ${e.message}"
                            }
                            _aiStreamResponse.value += "\n[错误: $errorMsg]"
                            _error.value = errorMsg
                        }
                    } finally {
                        responseBody?.close()
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
                _aiStreamResponse.value = "请求失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 取消当前流式请求
     */
    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
        _isLoading.value = false
    }

    /**
     * 加载所有归档类型
     */
    fun loadArchiveTypes() {
        viewModelScope.launch {
            try {
                _archiveTypes.value = useCases.getAllArchiveTypes()
            } catch (e: Exception) {
                _error.value = "加载归档类型失败: ${e.message}"
            }
        }
    }

    /**
     * 根据归档类型筛选题目
     */
    fun filterQuestionsByArchiveType(type: String) {
        viewModelScope.launch {
            try {
                _filteredQuestions.value = useCases.getByArchiveType(type)
            } catch (e: Exception) {
                _error.value = "筛选题目失败: ${e.message}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
        disposables.clear()
    }
}