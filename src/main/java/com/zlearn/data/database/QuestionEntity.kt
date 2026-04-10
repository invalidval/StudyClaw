package com.zlearn.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cloudId: Int? = null,
    val imagePath: String,
    val ocrText: String,
    val aiAnalysis: String,
    val summary: String, // AI生成的简短摘要
    val subject: String,
    val difficulty: Int,
    val createTime: Long,
    val isArchived: Boolean,
    val archiveType: String? = null, // 归档类型，可为空
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
