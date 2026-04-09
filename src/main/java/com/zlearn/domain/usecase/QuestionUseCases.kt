package com.zlearn.domain.usecase

import com.zlearn.data.repository.QuestionRepository
import com.zlearn.data.database.QuestionEntity
import com.zlearn.network.AliyunChatRequest
import com.zlearn.network.AliyunChatResponse
import retrofit2.Response
import javax.inject.Inject

class QuestionUseCases @Inject constructor(
    private val repository: QuestionRepository
) {
    suspend fun addQuestion(question: QuestionEntity) = repository.insert(question)
    suspend fun getQuestions() = repository.getAll()
    suspend fun getQuestionById(id: Int) = repository.getById(id)
    suspend fun updateQuestion(question: QuestionEntity) = repository.update(question)
    suspend fun deleteQuestion(question: QuestionEntity) = repository.delete(question)
    suspend fun archiveQuestion(id: Int) = repository.archive(id)
    suspend fun getActiveQuestions() = repository.getActive()
    suspend fun getArchivedQuestions() = repository.getArchived()

    suspend fun chatWithAi(request: AliyunChatRequest): Response<AliyunChatResponse> =
        repository.chatWithAi(request)
}
