package com.zlearn.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ShareItem(
    val contentHash: String,
    val ocrText: String,
    val summary: String,
    val subject: String,
    val difficulty: Int,
    val aiAnalysis: String = "",
    val isArchived: Boolean = false,
    val archiveType: String = ""
)

@Serializable
data class SharePayload(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val senderDevice: String,
    val createdAt: Long,
    val items: List<ShareItem>
)

