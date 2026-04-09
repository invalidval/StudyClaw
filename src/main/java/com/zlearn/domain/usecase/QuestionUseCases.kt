package com.zlearn.domain.usecase

import com.zlearn.data.repository.QuestionRepository
import com.zlearn.data.database.QuestionEntity
import javax.inject.Inject

class QuestionUseCases @Inject constructor(
    private val repository: QuestionRepository
) {
    suspend fun addQuestion(question: QuestionEntity) = repository.insert(question)
    suspend fun getQuestions() = repository.getAll()
}

