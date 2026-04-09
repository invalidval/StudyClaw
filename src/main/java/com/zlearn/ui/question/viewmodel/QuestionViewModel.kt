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

@HiltViewModel
class QuestionViewModel @Inject constructor(
    private val useCases: QuestionUseCases
) : ViewModel() {
    private val _questions = MutableStateFlow<List<QuestionEntity>>(emptyList())
    val questions: StateFlow<List<QuestionEntity>> = _questions.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    init {
        loadQuestions()
    }

    fun loadQuestions() {
        viewModelScope.launch {
            _questions.value = useCases.getActiveQuestions()
        }
    }

    fun addQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            useCases.addQuestion(question)
            loadQuestions()
        }
    }

    fun deleteQuestion(question: QuestionEntity) {
        viewModelScope.launch {
            useCases.deleteQuestion(question)
            loadQuestions()
        }
    }

    fun archiveQuestion(id: Int) {
        viewModelScope.launch {
            useCases.archiveQuestion(id)
            loadQuestions()
        }
    }

    fun chatWithAi(message: String) {
        viewModelScope.launch {
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
            }
        }
    }
}
