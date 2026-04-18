package com.zlearn.data.repository

import com.zlearn.data.database.QuestionDao
import com.zlearn.data.database.QuestionEntity
import com.zlearn.data.local.AuthSessionStore
import com.zlearn.data.remote.ApiService
import com.zlearn.data.remote.SyncCursor
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
    private val aiApiService: AiApiService,
    private val authSessionStore: AuthSessionStore
) {
    private fun currentOwnerUserId(): Int = authSessionStore.userId.value ?: GUEST_USER_ID

    suspend fun insert(question: QuestionEntity) =
        questionDao.insert(question.copy(ownerUserId = currentOwnerUserId()))

    suspend fun getAll(): List<QuestionEntity> = questionDao.getAll(currentOwnerUserId())

    suspend fun getById(id: Int): QuestionEntity? = questionDao.getById(id, currentOwnerUserId())

    suspend fun update(question: QuestionEntity) =
        questionDao.update(question.copy(ownerUserId = currentOwnerUserId()))

    suspend fun delete(question: QuestionEntity) {
        val now = System.currentTimeMillis()
        questionDao.softDeleteById(
            id = question.id,
            ownerUserId = currentOwnerUserId(),
            deletedAt = now,
            updatedAt = now
        )
    }

    suspend fun archive(id: Int) =
        questionDao.archive(id, currentOwnerUserId(), System.currentTimeMillis())

    suspend fun archive(id: Int, archiveType: String) =
        questionDao.archive(id, currentOwnerUserId(), archiveType, System.currentTimeMillis())

    suspend fun unarchive(id: Int) =
        questionDao.unarchive(id, currentOwnerUserId(), System.currentTimeMillis())

    suspend fun getActive(): List<QuestionEntity> = questionDao.getActive(currentOwnerUserId())

    suspend fun getArchived(): List<QuestionEntity> = questionDao.getArchived(currentOwnerUserId())

    suspend fun getAllArchiveTypes(): List<String> = questionDao.getAllArchiveTypes(currentOwnerUserId())

    suspend fun getByArchiveType(type: String): List<QuestionEntity> =
        questionDao.getByArchiveType(currentOwnerUserId(), type)

    suspend fun chatWithAi(request: AliyunChatRequest): Response<AliyunChatResponse> =
        aiApiService.chat(request)

    suspend fun chatWithAiStream(request: AliyunChatRequest): okhttp3.ResponseBody =
        aiApiService.chatStream(request)

    suspend fun getQuestionFromCloud(cloudId: Int): QuestionEntity? {
        val response = apiService.getQuestion(cloudId)
        if (response.isSuccessful) {
            val body = response.body()
            if (body?.success == true && body.question != null) {
                return body.question.toEntity(localId = 0, ownerUserId = currentOwnerUserId())
            }
        }
        return null
    }

    suspend fun syncQuestions(token: String): String {
        val ownerUserId = currentOwnerUserId()
        val lastUploadedAt = authSessionStore.getLastUploadUpdatedAt(ownerUserId)
        val localChanges = questionDao.getChangedSince(ownerUserId, lastUploadedAt)

        var cursorUpdatedAt = authSessionStore.getPullCursorUpdatedAt(ownerUserId)
        var cursorId = authSessionStore.getPullCursorId(ownerUserId)
        var sentLocalChanges = false
        var uploadUpdatedCount = 0
        var cloudPullCount = 0

        do {
            val response = apiService.syncQuestions(
                authorization = "Bearer $token",
                request = SyncQuestionsRequest(
                    questions = if (!sentLocalChanges) localChanges.map { it.toSyncDto() } else emptyList(),
                    cursorUpdatedAt = cursorUpdatedAt,
                    cursorId = cursorId,
                    limit = SYNC_PAGE_LIMIT
                )
            )

            if (!response.isSuccessful) {
                throw IllegalStateException("同步失败: ${response.code()}")
            }

            val body = response.body() ?: throw IllegalStateException("同步失败: 空响应")
            if (!body.success) {
                throw IllegalStateException(body.message ?: "同步失败")
            }

            if (!sentLocalChanges) {
                uploadUpdatedCount = body.stats?.updatedCount ?: 0
                sentLocalChanges = true
            }

            body.questions.forEach { remote ->
                val existing = remote.id?.let { questionDao.getByCloudId(ownerUserId, it) }
                    ?: questionDao.getUnsyncedByFingerprint(
                        ownerUserId = ownerUserId,
                        createTime = remote.createTime,
                        ocrText = remote.ocrText
                    )

                val shouldCountCloudPull = remote.deletedAt == null && (
                    existing == null || remote.updatedAt > existing.updatedAt
                )
                if (shouldCountCloudPull) {
                    cloudPullCount += 1
                }

                val merged = if (existing == null) {
                    remote.toEntity(localId = 0, ownerUserId = ownerUserId)
                } else {
                    remote.toEntity(localId = existing.id, ownerUserId = ownerUserId)
                }
                questionDao.insertOrReplace(merged)
            }

            val nextCursor = body.cursor ?: body.questions.toCursorFallback(cursorUpdatedAt, cursorId)
            cursorUpdatedAt = nextCursor.updatedAt
            cursorId = nextCursor.id
            authSessionStore.savePullCursor(ownerUserId, cursorUpdatedAt, cursorId)

            if (!nextCursor.hasMore) {
                break
            }
        } while (true)

        val newUploadedAt = localChanges.maxOfOrNull { it.updatedAt } ?: lastUploadedAt
        if (newUploadedAt > lastUploadedAt) {
            authSessionStore.saveLastUploadUpdatedAt(ownerUserId, newUploadedAt)
        }

        return when {
            uploadUpdatedCount > 0 && cloudPullCount > 0 ->
                "同步成功：更新 $uploadUpdatedCount 条，拉取 $cloudPullCount 条"
            cloudPullCount > 0 ->
                "同步成功：从云端拉取 $cloudPullCount 条"
            uploadUpdatedCount > 0 ->
                "同步成功：更新 $uploadUpdatedCount 条"
            else ->
                "同步成功：无新增更新"
        }
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
        deletedAt = deletedAt,
        updatedAt = updatedAt
    )

    private fun SyncQuestionDto.toEntity(localId: Int, ownerUserId: Int): QuestionEntity = QuestionEntity(
        id = localId,
        ownerUserId = ownerUserId,
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
        deletedAt = deletedAt,
        updatedAt = updatedAt
    )

    private fun List<SyncQuestionDto>.toCursorFallback(currentUpdatedAt: Long, currentId: Int): SyncCursor {
        if (isEmpty()) {
            return SyncCursor(updatedAt = currentUpdatedAt, id = currentId, hasMore = false)
        }
        val last = last()
        return SyncCursor(
            updatedAt = last.updatedAt,
            id = last.id ?: currentId,
            hasMore = size >= SYNC_PAGE_LIMIT
        )
    }

    companion object {
        private const val GUEST_USER_ID = 0
        private const val SYNC_PAGE_LIMIT = 200
    }
}
