package com.zlearn.data.repository

import com.zlearn.data.database.QuestionDao
import com.zlearn.data.database.QuestionEntity
import com.zlearn.data.remote.ApiService
import com.zlearn.data.remote.SyncQuestionDto
import com.zlearn.data.remote.SyncQuestionsRequest
import com.zlearn.network.AiApiService
import com.zlearn.network.AliyunChatRequest
import com.zlearn.network.AliyunChatResponse
import retrofit2.Response
import javax.inject.Inject

class QuestionRepository @Inject constructor(
    private val questionDao: QuestionDao,
    private val apiService: ApiService,
    private val aiApiService: AiApiService
) {
    suspend fun insert(question: QuestionEntity) = questionDao.insert(question)
    suspend fun getAll(): List<QuestionEntity> = questionDao.getAll()
    suspend fun getById(id: Int): QuestionEntity? = questionDao.getById(id)
    suspend fun update(question: QuestionEntity) = questionDao.update(question)
    suspend fun delete(question: QuestionEntity) = questionDao.delete(question)
    suspend fun archive(id: Int) = questionDao.archive(id, System.currentTimeMillis())
    suspend fun archive(id: Int, archiveType: String) =
        questionDao.archive(id, archiveType, System.currentTimeMillis())

    suspend fun unarchive(id: Int) = questionDao.unarchive(id, System.currentTimeMillis())
    suspend fun getActive(): List<QuestionEntity> = questionDao.getActive()
    suspend fun getArchived(): List<QuestionEntity> = questionDao.getArchived()
    suspend fun getAllArchiveTypes(): List<String> = questionDao.getAllArchiveTypes()
    suspend fun getByArchiveType(type: String): List<QuestionEntity> = questionDao.getByArchiveType(type)

    suspend fun chatWithAi(request: AliyunChatRequest): Response<AliyunChatResponse> =
        aiApiService.chat(request)

    suspend fun chatWithAiStream(request: AliyunChatRequest): okhttp3.ResponseBody =
        aiApiService.chatStream(request)

    suspend fun syncQuestions(token: String): String {
        val localQuestions = questionDao.getAll()
        val payload = localQuestions.map { it.toSyncDto() }
        val response = apiService.syncQuestions(
            authorization = "Bearer $token",
            request = SyncQuestionsRequest(questions = payload)
        )

        if (!response.isSuccessful) {
            throw IllegalStateException("同步失败: ${response.code()}")
        }

        val body = response.body() ?: throw IllegalStateException("同步失败: 空响应")
        if (!body.success) {
            throw IllegalStateException(body.message ?: "同步失败")
        }

        body.questions.forEach { remote ->
            val existing = remote.id?.let { questionDao.getByCloudId(it) }
                ?: questionDao.getUnsyncedByFingerprint(
                    createTime = remote.createTime,
                    ocrText = remote.ocrText
                )
            val merged = if (existing == null) {
                remote.toEntity(localId = 0)
            } else {
                remote.toEntity(localId = existing.id)
            }
            questionDao.insertOrReplace(merged)
        }
        return body.message ?: "同步成功（${body.questions.size} 条）"
    }

    private fun QuestionEntity.toSyncDto(): SyncQuestionDto = SyncQuestionDto(
        id = cloudId,
        imagePath = imagePath,
        ocrText = ocrText,
        aiAnalysis = aiAnalysis,
        summary = summary.ifBlank { ocrText.take(20) },
        subject = subject.ifBlank { "未分类" },
        difficulty = difficulty,
        createTime = createTime,
        isArchived = isArchived,
        archiveType = archiveType,
        updatedAt = updatedAt
    )

    private fun SyncQuestionDto.toEntity(localId: Int): QuestionEntity = QuestionEntity(
        id = localId,
        cloudId = id,
        imagePath = imagePath,
        ocrText = ocrText,
        aiAnalysis = aiAnalysis,
        summary = summary.ifBlank { ocrText.take(20) },
        subject = subject.ifBlank { "未分类" },
        difficulty = difficulty,
        createTime = createTime,
        isArchived = isArchived,
        archiveType = archiveType,
        updatedAt = updatedAt
    )
}
