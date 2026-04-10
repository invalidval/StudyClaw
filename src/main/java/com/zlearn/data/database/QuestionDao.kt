package com.zlearn.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(question: QuestionEntity)

    @Query("SELECT * FROM questions ORDER BY createTime DESC")
    suspend fun getAll(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(cloudId: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE cloudId IS NULL AND createTime = :createTime AND ocrText = :ocrText LIMIT 1")
    suspend fun getUnsyncedByFingerprint(createTime: Long, ocrText: String): QuestionEntity?

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("UPDATE questions SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Int, updatedAt: Long)

    @Query("UPDATE questions SET isArchived = 1, archiveType = :archiveType, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Int, archiveType: String, updatedAt: Long)

    @Query("UPDATE questions SET isArchived = 0, archiveType = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun unarchive(id: Int, updatedAt: Long)

    @Query("SELECT * FROM questions WHERE isArchived = 0 ORDER BY createTime DESC")
    suspend fun getActive(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE isArchived = 1 ORDER BY createTime DESC")
    suspend fun getArchived(): List<QuestionEntity>

    @Query("SELECT DISTINCT archiveType FROM questions WHERE archiveType IS NOT NULL")
    suspend fun getAllArchiveTypes(): List<String>

    @Query("SELECT * FROM questions WHERE archiveType = :type ORDER BY createTime DESC")
    suspend fun getByArchiveType(type: String): List<QuestionEntity>
}
