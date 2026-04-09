package com.zlearn.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: QuestionEntity)

    @Query("SELECT * FROM questions ORDER BY createTime DESC")
    suspend fun getAll(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Int): QuestionEntity?

    @Update
    suspend fun update(question: QuestionEntity)

    @Delete
    suspend fun delete(question: QuestionEntity)

    @Query("UPDATE questions SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: Int)

    @Query("SELECT * FROM questions WHERE isArchived = 0 ORDER BY createTime DESC")
    suspend fun getActive(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE isArchived = 1 ORDER BY createTime DESC")
    suspend fun getArchived(): List<QuestionEntity>
}
