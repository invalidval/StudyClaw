package com.zlearn.domain.model

data class Question(
    val id: Int = 0,
    val imagePath: String,
    val ocrText: String,
    val aiAnalysis: String,
    val summary: String, // AI生成的简短摘要
    val subject: String,
    val difficulty: Int,
    val createTime: Long,
    val isArchived: Boolean,
    val archiveType: String? = null
)
