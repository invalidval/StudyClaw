package com.zlearn.data.repository

import com.zlearn.data.database.QuestionDao
import com.zlearn.data.database.QuestionEntity
import javax.inject.Inject

class QuestionRepository @Inject constructor(
    private val questionDao: QuestionDao
) {
    suspend fun insert(question: QuestionEntity) = questionDao.insert(question)
    suspend fun getAll(): List<QuestionEntity> = questionDao.getAll()
}

