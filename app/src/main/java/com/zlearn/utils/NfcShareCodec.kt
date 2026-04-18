package com.zlearn.utils

import com.zlearn.domain.model.ShareItem
import com.zlearn.domain.model.SharePayload
import org.json.JSONArray
import org.json.JSONObject

object NfcShareCodec {
    fun encode(payload: SharePayload): String {
        val items = JSONArray()
        payload.items.forEach { item ->
            items.put(
                JSONObject()
                    .put("contentHash", item.contentHash)
                    .put("ocrText", item.ocrText)
                    .put("summary", item.summary)
                    .put("subject", item.subject)
                    .put("difficulty", item.difficulty)
                    .put("aiAnalysis", item.aiAnalysis)
                    .put("isArchived", item.isArchived)
                    .put("archiveType", item.archiveType)
            )
        }

        return JSONObject()
            .put("schemaVersion", payload.schemaVersion)
            .put("sessionId", payload.sessionId)
            .put("senderDevice", payload.senderDevice)
            .put("createdAt", payload.createdAt)
            .put("items", items)
            .toString()
    }

    fun decode(raw: String): Result<SharePayload> = runCatching {
        val json = JSONObject(raw)
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<ShareItem>()
        for (i in 0 until itemsJson.length()) {
            val obj = itemsJson.optJSONObject(i) ?: continue
            items += ShareItem(
                contentHash = obj.optString("contentHash"),
                ocrText = obj.optString("ocrText"),
                summary = obj.optString("summary"),
                subject = obj.optString("subject"),
                difficulty = obj.optInt("difficulty", 3),
                aiAnalysis = obj.optString("aiAnalysis"),
                isArchived = obj.optBoolean("isArchived", false),
                archiveType = obj.optString("archiveType")
            )
        }

        SharePayload(
            schemaVersion = json.optInt("schemaVersion", 1),
            sessionId = json.optString("sessionId"),
            senderDevice = json.optString("senderDevice"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            items = items
        )
    }
}

