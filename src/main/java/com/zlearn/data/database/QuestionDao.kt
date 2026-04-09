package com.zlearn.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: QuestionEntity)

    @Query("SELECT * FROM questions")
    suspend fun getAll(): List<QuestionEntity>
}

