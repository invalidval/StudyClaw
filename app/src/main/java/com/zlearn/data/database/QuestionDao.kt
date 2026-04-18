package com.zlearn.data.database

import androidx.room.Dao
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

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL ORDER BY createTime DESC")
    suspend fun getAll(ownerUserId: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId ORDER BY createTime DESC")
    suspend fun getAllForSync(ownerUserId: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND updatedAt > :updatedAfter ORDER BY updatedAt ASC, id ASC")
    suspend fun getChangedSince(ownerUserId: Int, updatedAfter: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun getById(id: Int, ownerUserId: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND cloudId = :cloudId LIMIT 1")
    suspend fun getByCloudId(ownerUserId: Int, cloudId: Int): QuestionEntity?

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND cloudId IS NULL AND deletedAt IS NULL AND createTime = :createTime AND ocrText = :ocrText LIMIT 1")
    suspend fun getUnsyncedByFingerprint(ownerUserId: Int, createTime: Long, ocrText: String): QuestionEntity?

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("UPDATE questions SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun softDeleteById(id: Int, ownerUserId: Int, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE questions SET isArchived = 1, updatedAt = :updatedAt WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun archive(id: Int, ownerUserId: Int, updatedAt: Long)

    @Query("UPDATE questions SET isArchived = 1, archiveType = :archiveType, updatedAt = :updatedAt WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun archive(id: Int, ownerUserId: Int, archiveType: String, updatedAt: Long)

    @Query("UPDATE questions SET isArchived = 0, archiveType = NULL, updatedAt = :updatedAt WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun unarchive(id: Int, ownerUserId: Int, updatedAt: Long)

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND isArchived = 0 AND deletedAt IS NULL ORDER BY createTime DESC")
    suspend fun getActive(ownerUserId: Int): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND isArchived = 1 AND deletedAt IS NULL ORDER BY createTime DESC")
    suspend fun getArchived(ownerUserId: Int): List<QuestionEntity>

    @Query("SELECT DISTINCT archiveType FROM questions WHERE ownerUserId = :ownerUserId AND archiveType IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllArchiveTypes(ownerUserId: Int): List<String>

    @Query("SELECT * FROM questions WHERE ownerUserId = :ownerUserId AND archiveType = :type AND deletedAt IS NULL ORDER BY createTime DESC")
    suspend fun getByArchiveType(ownerUserId: Int, type: String): List<QuestionEntity>
}
