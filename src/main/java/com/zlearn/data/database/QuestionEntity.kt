package com.zlearn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String,
    val ocrText: String,
    val aiAnalysis: String,
    val summary: String, // AI生成的简短摘要
    val subject: String,
    val difficulty: Int,
    val createTime: Long,
    val isArchived: Boolean
)
