package com.zlearn.data.repository

import com.zlearn.data.database.QuestionDao
import com.zlearn.data.database.QuestionEntity
import com.zlearn.network.AiApiService
import com.zlearn.network.AliyunChatRequest
import com.zlearn.network.AliyunChatResponse
import retrofit2.Response
import javax.inject.Inject

class QuestionRepository @Inject constructor(
    private val questionDao: QuestionDao,
    private val aiApiService: AiApiService
) {
    suspend fun insert(question: QuestionEntity) = questionDao.insert(question)
    suspend fun getAll(): List<QuestionEntity> = questionDao.getAll()
    suspend fun getById(id: Int): QuestionEntity? = questionDao.getById(id)
    suspend fun update(question: QuestionEntity) = questionDao.update(question)
    suspend fun delete(question: QuestionEntity) = questionDao.delete(question)
    suspend fun archive(id: Int) = questionDao.archive(id)
    suspend fun getActive(): List<QuestionEntity> = questionDao.getActive()
    suspend fun getArchived(): List<QuestionEntity> = questionDao.getArchived()

    suspend fun chatWithAi(request: AliyunChatRequest): Response<AliyunChatResponse> =
        aiApiService.chat(request)
}
