package com.zlearn.ui.focus.screens

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.data.database.QuestionEntity
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.ui.components.AiInputBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    val viewModel: QuestionViewModel = hiltViewModel()
    val aiStreamResponse by viewModel.aiStreamResponse.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val systemPrompt = """
你是 StudyClaw 智能错题本的 AI 助手，只能用中文与用户交流，风格简洁专业。
你的能力：
- 你可以通过输出 #TOOL# { ... } 的 JSON 结构调用错题管理工具，支持 add、delete、update、query、archive、unarchive 操作。
- 当用户有错题的增删查改需求时，优先 query 搜索候选，再基于 id 执行后续操作。
- delete/update/archive 必须使用 id 或 ids。没有 id 时，先 query 并反问用户确认。
- archive 必须走归档预留接口（与错题列表一致）：action=archive + id/ids (+ 可选 archiveType)。
- 取消归档必须使用 action=unarchive + id/ids，不要把 archiveType 写成 none/null/未归档。
- 工具调用格式为 #TOOL# { "action":..., ... }，不要输出多余内容。
- 你可以在一次回复中输出多个 #TOOL# JSON（按顺序逐条执行）来完成批量任务。
- 工具调用后，请根据工具返回结果，用简洁自然语言总结反馈，不要直接输出 JSON。
- 用户询问“是否归档/归档类型/归档状态”时，必须先调用 query 获取字段（isArchived、archiveType）再回答，不要凭空判断。
- 若用户要求“自动归档到合适分类/智能分类批量处理”，你应先 query 获取目标集合，再按每条错题内容生成具体 archiveType；禁止把整批都归到“auto-classified”这种笼统类型。
- add 操作必须提供非空题干（ocrText）；若字段缺失或仅为占位词，必须返回失败，不得入库。
- 当用户明确要求“自动生成题目内容/举一反三”时，add 可使用自动生成模式（autoGenerate=true 或 mode=generate），由摘要/解析/学科生成题目草案后再入库。
- 数据库字段（QuestionEntity）为：id, cloudId, imagePath, ocrText, aiAnalysis, summary, subject, difficulty, createTime, isArchived, archiveType, deletedAt, updatedAt。
- query 可指定 field(单个) 或 fields/selectFields(数组) 来返回所需属性；若未指定则返回常用字段。
- query 返回 rows 时至少包含 id，便于后续 update/delete/archive/unarchive 精确操作。
【重要】
1. 工具调用 JSON 必须严格合法，不能有多余文本、注释、转义错误。
2. action 字段必须有，且只能为 add、delete、update、query、archive、unarchive 之一。
3. delete/update/archive 禁止按自然语言直接批量执行，必须 id 精确操作。
4. 不要用 update 修改 isArchived/archiveType；归档相关字段只能由 action=archive 处理。
5. query 如命中多条，展示带 id 的候选，并要求用户指定 id。
6. 回答归档相关问题前必须基于 query 返回字段，不可臆测。
""".trimIndent()

    var messages by remember { mutableStateOf(loadCachedMessages(context)) }
    var input by remember { mutableStateOf("") }
    var autoToolDepth by remember { mutableStateOf(0) }
    var smoothStreamResponse by remember { mutableStateOf("") }
            fun appendMessage(message: ChatMessage) {
                messages = (messages + message).takeLast(MAX_CACHED_MESSAGES)
            }

    val listState = rememberLazyListState()
    val maxAutoToolDepth = 5
    val maxHistoryRounds = 10

    fun extractIds(toolCall: JsonObject): List<Int> {
        val ids = toolCall["ids"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.contentOrNull?.toIntOrNull() }
            ?: emptyList()
        val id = toolCall["id"]?.jsonPrimitive?.intOrNull
            ?: toolCall["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        return (ids + listOfNotNull(id)).distinct()
    }

    fun cleanText(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lowered = text.lowercase()
        val placeholders = setOf("null", "none", "n/a", "na", "unknown", "未提供", "未填写", "暂无", "待补充", "空")
        return if (lowered in placeholders) null else text
    }

    fun pickText(obj: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val value = cleanText(obj[k]?.jsonPrimitive?.contentOrNull)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    fun parseBooleanFlag(obj: JsonObject, vararg keys: String): Boolean {
        for (k in keys) {
            val v = obj[k] ?: continue
            val p = runCatching { v.jsonPrimitive }.getOrNull() ?: continue
            p.booleanOrNull?.let { return it }
            val t = p.contentOrNull?.trim()?.lowercase() ?: continue
            if (t in setOf("true", "1", "yes", "y", "是", "开启", "开")) return true
            if (t in setOf("false", "0", "no", "n", "否", "关闭", "关")) return false
        }
        return false
    }

    fun buildGeneratedQuestion(subject: String, summary: String?, aiAnalysis: String?, seed: String?): String {
        val s = cleanText(summary)
        val a = cleanText(aiAnalysis)
        val k = cleanText(seed)
        return when {
            !k.isNullOrBlank() && !s.isNullOrBlank() -> "【举一反三-$subject】围绕“$k”，根据“$s”设计一道同类训练题并作答。"
            !s.isNullOrBlank() -> "【举一反三-$subject】根据“$s”设计一道同类训练题并作答。"
            !a.isNullOrBlank() -> "【举一反三-$subject】根据解析要点生成一道同类训练题：${a.take(40)}"
            !k.isNullOrBlank() -> "【举一反三-$subject】围绕“$k”生成一道同类训练题并作答。"
            else -> "【举一反三-$subject】生成一道同类训练题并作答。"
        }
    }

    fun renderCandidates(list: List<QuestionEntity>): String {
        if (list.isEmpty()) return "未找到相关错题。"
        return list.joinToString("\n") {
            val archiveLabel = if (it.isArchived) "已归档" else "未归档"
            val archiveType = it.archiveType?.takeIf { t -> t.isNotBlank() }?.let { "($it)" } ?: ""
            "- id:${it.id} [$archiveLabel$archiveType] [${it.subject}] ${it.summary.ifBlank { it.ocrText.take(24) }}"
        }
    }

    fun normalizeQueryField(name: String): String? {
        return when (name.trim()) {
            "id" -> "id"
            "cloudId", "云端id", "云端ID" -> "cloudId"
            "imagePath", "图片路径" -> "imagePath"
            "ocrText", "question", "questionText", "题目", "题干" -> "ocrText"
            "aiAnalysis", "analysis", "解析" -> "aiAnalysis"
            "summary", "摘要" -> "summary"
            "subject", "学科" -> "subject"
            "difficulty", "难度" -> "difficulty"
            "createTime", "创建时间" -> "createTime"
            "updatedAt", "更新时间" -> "updatedAt"
            "deleted", "是否删除", "已删除" -> "deleted"
            "deletedAt", "删除时间" -> "deletedAt"
            "isArchived", "是否归档" -> "isArchived"
            "archiveType", "归档类型" -> "archiveType"
            else -> null
        }
    }

    fun resolveQueryFields(toolCall: JsonObject): List<String> {
        val single = toolCall["field"]?.jsonPrimitive?.contentOrNull
        val listA = toolCall["fields"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val listB = toolCall["selectFields"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        val requested = buildList {
            if (!single.isNullOrBlank()) add(single)
            addAll(listA)
            addAll(listB)
        }
        val normalized = requested.mapNotNull { normalizeQueryField(it) }.distinct().toMutableList()
        if (normalized.isEmpty()) {
            normalized += listOf("summary", "subject", "isArchived", "archiveType")
        }
        if (!normalized.contains("id")) normalized.add(0, "id")
        return normalized
    }

    fun normalizeUpdateField(name: String): String? {
        return when (name.trim()) {
            "summary", "摘要" -> "summary"
            "aiAnalysis", "analysis", "解析" -> "aiAnalysis"
            "subject", "学科" -> "subject"
            "difficulty", "难度" -> "difficulty"
            "ocrText", "question", "questionText", "题目", "题干", "content", "title" -> "ocrText"
            "imagePath", "图片路径" -> "imagePath"
            else -> null
        }
    }

    fun isArchiveFieldName(name: String): Boolean {
        return when (name.trim()) {
            "isArchived", "archiveType", "是否归档", "归档类型" -> true
            else -> false
        }
    }

    fun containsArchiveUpdateRequest(toolCall: JsonObject): Boolean {
        val field = toolCall["field"]?.jsonPrimitive?.contentOrNull
        if (!field.isNullOrBlank() && isArchiveFieldName(field)) return true

        val fields = toolCall["fields"]?.jsonObject
        if (fields?.keys?.any { isArchiveFieldName(it) } == true) return true

        val reserved = setOf("action", "id", "ids", "field", "value", "fields")
        if (toolCall.keys.any { key -> key !in reserved && isArchiveFieldName(key) }) return true

        return false
    }

    fun resolveUpdatePayload(toolCall: JsonObject): Map<String, JsonElement> {
        val merged = linkedMapOf<String, JsonElement>()

        val fieldsObj = toolCall["fields"]?.jsonObject
        fieldsObj?.forEach { (k, v) ->
            normalizeUpdateField(k)?.let { normalized -> merged[normalized] = v }
        }

        val singleField = toolCall["field"]?.jsonPrimitive?.contentOrNull
        val singleValue = toolCall["value"]
        if (!singleField.isNullOrBlank() && singleValue != null) {
            normalizeUpdateField(singleField)?.let { normalized -> merged[normalized] = singleValue }
        }

        val reserved = setOf("action", "id", "ids", "field", "value", "fields")
        toolCall.forEach { (k, v) ->
            if (k in reserved) return@forEach
            val normalized = normalizeUpdateField(k) ?: return@forEach
            if (!merged.containsKey(normalized)) merged[normalized] = v
        }

        return merged
    }

    fun toQueryRow(q: QuestionEntity, fields: List<String>): JsonObject {
        val map = mutableMapOf<String, JsonElement>()
        fields.forEach { field ->
            when (field) {
                "id" -> map["id"] = JsonPrimitive(q.id)
                "cloudId" -> map["cloudId"] = q.cloudId?.let { JsonPrimitive(it) } ?: JsonNull
                "imagePath" -> map["imagePath"] = JsonPrimitive(q.imagePath)
                "ocrText" -> map["ocrText"] = JsonPrimitive(q.ocrText)
                "aiAnalysis" -> map["aiAnalysis"] = JsonPrimitive(q.aiAnalysis)
                "summary" -> map["summary"] = JsonPrimitive(q.summary)
                "subject" -> map["subject"] = JsonPrimitive(q.subject)
                "difficulty" -> map["difficulty"] = JsonPrimitive(q.difficulty)
                "createTime" -> map["createTime"] = JsonPrimitive(q.createTime)
                "updatedAt" -> map["updatedAt"] = JsonPrimitive(q.updatedAt)
                "deleted" -> map["deleted"] = JsonPrimitive(q.deletedAt != null)
                "deletedAt" -> map["deletedAt"] = q.deletedAt?.let { JsonPrimitive(it) } ?: JsonNull
                "isArchived" -> map["isArchived"] = JsonPrimitive(q.isArchived)
                "archiveType" -> map["archiveType"] = JsonPrimitive(q.archiveType ?: "")
            }
        }
        return JsonObject(map)
    }

    fun trimHistoryByRounds(allMessages: List<ChatMessage>, rounds: Int): List<ChatMessage> {
        if (rounds <= 0) return emptyList()
        val msgs = allMessages.filter { it.role != "system" }
        if (msgs.isEmpty()) return emptyList()

        val userIndexes = msgs.mapIndexedNotNull { index, msg -> if (msg.role == "user") index else null }
        if (userIndexes.isEmpty()) return msgs

        val startUserIndex = if (userIndexes.size > rounds) {
            userIndexes[userIndexes.size - rounds]
        } else {
            userIndexes.first()
        }
        return msgs.drop(startUserIndex)
    }

    fun buildPrompt(nextUserInput: String? = null): String {
        val historyMessages = trimHistoryByRounds(messages, maxHistoryRounds)
        val history = historyMessages.joinToString("\n") {
            when (it.role) {
                "user" -> "用户：" + it.content
                "assistant" -> "助手：" + it.content
                "tool" -> "#TOOL# " + it.content
                else -> it.content
            }
        }
        return if (nextUserInput != null) {
            if (history.isBlank()) {
                "$systemPrompt\n用户：$nextUserInput"
            } else {
                "$systemPrompt\n$history\n用户：$nextUserInput"
            }
        } else {
            "$systemPrompt\n$history\n请基于最新工具结果继续完成上一轮用户需求；如仍需工具，请继续输出 #TOOL# JSON。"
        }
    }

    fun inferArchiveType(q: QuestionEntity): String {
        val subject = q.subject.trim()
        if (subject.isNotBlank() && subject != "通用") return subject
        val text = (q.summary + " " + q.ocrText).lowercase()
        return when {
            text.contains("计算机") || text.contains("编程") || text.contains("算法") ||
                text.contains("数据结构") || text.contains("操作系统") || text.contains("计算机网络") ||
                text.contains("数据库") || text.contains("sql") || text.contains("java") ||
                text.contains("python") || text.contains("c++") || text.contains("cpp") ||
                text.contains("代码") || text.contains("程序") || text.contains("前端") ||
                text.contains("后端") || text.contains("linux") -> "计算机"
            text.contains("函数") || text.contains("方程") || text.contains("几何") || text.contains("math") -> "数学"
            text.contains("力学") || text.contains("电路") || text.contains("光学") || text.contains("physics") -> "物理"
            text.contains("化学") || text.contains("离子") || text.contains("反应") || text.contains("chem") -> "化学"
            text.contains("文言") || text.contains("古诗") || text.contains("语文") -> "语文"
            text.contains("english") || text.contains("完形") || text.contains("阅读") || text.contains("语法") -> "英语"
            else -> "待整理"
        }
    }

    fun extractToolJsonBlocksFromResponse(text: String): List<String> {
        val marker = "#TOOL#"
        val blocks = mutableListOf<String>()
        var searchStart = 0
        while (true) {
            val markerIndex = text.indexOf(marker, searchStart)
            if (markerIndex < 0) break
            val jsonStart = text.indexOf('{', markerIndex + marker.length)
            if (jsonStart < 0) {
                searchStart = markerIndex + marker.length
                continue
            }
            var depth = 0
            var jsonEnd = -1
            for (i in jsonStart until text.length) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            jsonEnd = i
                            break
                        }
                    }
                }
            }
            if (jsonEnd < 0) break
            blocks += text.substring(jsonStart, jsonEnd + 1).trim()
            searchStart = jsonEnd + 1
        }
        return blocks
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "StudyClaw 智能错题管理",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            state = listState
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
            if (isLoading && smoothStreamResponse.isNotBlank()) {
                item {
                    ChatBubble(ChatMessage(role = "assistant", content = smoothStreamResponse))
                }
            }
        }

        LaunchedEffect(aiStreamResponse, isLoading) {
            if (!isLoading) {
                smoothStreamResponse = ""
                return@LaunchedEffect
            }
            if (aiStreamResponse.length <= smoothStreamResponse.length) {
                smoothStreamResponse = aiStreamResponse
                return@LaunchedEffect
            }

            val append = aiStreamResponse.substring(smoothStreamResponse.length)
            var offset = 0
            while (offset < append.length && isActive) {
                val step = when {
                    append.length > 240 -> 12
                    append.length > 120 -> 8
                    append.length > 60 -> 5
                    else -> 3
                }
                val next = (offset + step).coerceAtMost(append.length)
                smoothStreamResponse += append.substring(offset, next)
                offset = next
                delay(14)
            }
        }

        LaunchedEffect(messages.size, smoothStreamResponse.length, isLoading) {
            val hasStreamingBubble = isLoading && smoothStreamResponse.isNotBlank()
            val lastIndex = messages.lastIndex + if (hasStreamingBubble) 1 else 0
            if (lastIndex < 0) return@LaunchedEffect
            if (hasStreamingBubble) {
                listState.scrollToItem(lastIndex)
            } else {
                listState.animateScrollToItem(lastIndex)
            }
        }

        LaunchedEffect(messages) {
            saveCachedMessages(context, messages)
        }

        LaunchedEffect(aiStreamResponse, isLoading) {
            if (aiStreamResponse.isBlank() || isLoading) return@LaunchedEffect

            val trimmed = aiStreamResponse.trim()
            val toolJsonBlocks = extractToolJsonBlocksFromResponse(trimmed)
            if (toolJsonBlocks.isEmpty()) {
                val hasToolMarker = trimmed.contains("#TOOL#")
                if (hasToolMarker) {
                    val toolFail = JsonObject(
                        mapOf(
                            "result" to JsonPrimitive("fail"),
                            "action" to JsonPrimitive("tool_parse"),
                            "reason" to JsonPrimitive("检测到工具标记但未解析到合法 JSON，请按 #TOOL# { ... } 输出")
                        )
                    ).toString()
                    appendMessage(ChatMessage(role = "tool", content = toolFail))
                    autoToolDepth = 0
                    return@LaunchedEffect
                }
                appendMessage(ChatMessage(role = "assistant", content = aiStreamResponse))
                autoToolDepth = 0
                return@LaunchedEffect
            }

            scope.launch {
                var executedCount = 0
                for (jsonPart in toolJsonBlocks) {
                    try {
                        val toolCall = Json.parseToJsonElement(jsonPart).jsonObject
                        val action = toolCall["action"]?.jsonPrimitive?.content?.trim().orEmpty()
                        if (action.isBlank()) {
                            appendMessage(ChatMessage(role = "tool", content = "工具调用 JSON 缺少 action 字段。\n原始内容：$jsonPart"))
                            continue
                        }

                        val all = viewModel.questions.value
                        var toolResult = ""

                        when (action) {
                        "add" -> {
                            val rawQuestion = pickText(toolCall, "question", "questionText", "ocrText", "title", "content", "题目", "题干")
                            val subject = pickText(toolCall, "subject", "学科") ?: "通用"
                            val difficulty = toolCall["difficulty"]?.jsonPrimitive?.intOrNull ?: 3
                            val summaryInput = pickText(toolCall, "summary", "摘要")
                            val aiAnalysis = pickText(toolCall, "aiAnalysis", "analysis", "解析") ?: ""
                            val imagePath = pickText(toolCall, "imagePath", "图片路径") ?: ""
                            val mode = pickText(toolCall, "mode", "strategy", "生成模式")?.lowercase()
                            val autoGenerate = parseBooleanFlag(toolCall, "autoGenerate", "generate", "autoCreate", "举一反三") ||
                                mode in setOf("generate", "auto", "auto_generate", "举一反三")
                            val seed = pickText(toolCall, "seed", "keyword", "topic", "关键词", "主题")
                            val question = rawQuestion ?: if (autoGenerate) {
                                buildGeneratedQuestion(subject, summaryInput, aiAnalysis, seed)
                            } else {
                                null
                            }

                            if (question.isNullOrBlank()) {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("add"),
                                    "reason" to JsonPrimitive("题干不能为空或为占位值；如需自动生成请设置 autoGenerate=true")
                                )).toString()
                            } else {
                                val duplicate = all.any {
                                    it.ocrText.contains(question) || question.contains(it.ocrText)
                                }
                                if (duplicate) {
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("add"),
                                        "reason" to JsonPrimitive("已存在类似错题，未重复添加")
                                    )).toString()
                                } else {
                                    val summary = summaryInput
                                        ?: cleanText(runCatching { viewModel.generateSummary(question) }.getOrNull())
                                        ?: question.take(20)
                                    val entity = QuestionEntity(
                                        ocrText = question,
                                        imagePath = imagePath,
                                        aiAnalysis = aiAnalysis,
                                        summary = summary,
                                        subject = subject,
                                        difficulty = difficulty,
                                        createTime = System.currentTimeMillis(),
                                        isArchived = false
                                    )
                                    viewModel.addQuestion(entity)
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("add"),
                                        "question" to JsonPrimitive(question)
                                    )).toString()
                                }
                            }
                        }

                        "query" -> {
                            val ids = extractIds(toolCall)
                            val selectedFields = resolveQueryFields(toolCall)
                            val keyword = toolCall["question"]?.jsonPrimitive?.content?.trim().orEmpty()
                            val filtered = when {
                                ids.isNotEmpty() -> all.filter { ids.contains(it.id) }
                                keyword.isNotBlank() -> all.filter {
                                    it.ocrText.contains(keyword) ||
                                        keyword.contains(it.ocrText) ||
                                        it.summary.contains(keyword)
                                }
                                else -> all.takeLast(20)
                            }

                            val markdown = renderCandidates(filtered)
                            val rows = JsonArray(filtered.map { toQueryRow(it, selectedFields) })
                            toolResult = JsonObject(mapOf(
                                "result" to JsonPrimitive(if (filtered.isEmpty()) "empty" else "success"),
                                "action" to JsonPrimitive("query"),
                                "count" to JsonPrimitive(filtered.size),
                                "selectedFields" to JsonArray(selectedFields.map { JsonPrimitive(it) }),
                                "markdown" to JsonPrimitive(markdown),
                                "rows" to rows
                            )).toString()

                        }

                        "delete" -> {
                            val ids = extractIds(toolCall)
                            if (ids.isEmpty()) {
                                val keyword = toolCall["question"]?.jsonPrimitive?.content?.trim().orEmpty()
                                val candidates = if (keyword.isBlank()) emptyList() else {
                                    all.filter { it.ocrText.contains(keyword) || it.summary.contains(keyword) }
                                }
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("delete"),
                                    "reason" to JsonPrimitive("删除必须指定 id。"),
                                    "markdown" to JsonPrimitive(renderCandidates(candidates))
                                )).toString()
                            } else {
                                val matches = all.filter { ids.contains(it.id) }
                                if (matches.isEmpty()) {
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("delete"),
                                        "reason" to JsonPrimitive("未找到对应 id 的错题")
                                    )).toString()
                                } else {
                                    matches.forEach { viewModel.deleteQuestion(it) }
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("delete"),
                                        "deletedCount" to JsonPrimitive(matches.size)
                                    )).toString()
                                }
                            }
                        }

                        "update" -> {
                            val ids = extractIds(toolCall)
                            if (ids.isEmpty()) {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("update"),
                                    "reason" to JsonPrimitive("更新必须指定 id。请先 query。")
                                )).toString()
                            } else {
                                val targets = all.filter { ids.contains(it.id) }

                                if (targets.isEmpty()) {
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("update"),
                                        "reason" to JsonPrimitive("未找到对应 id 的错题")
                                    )).toString()
                                } else if (containsArchiveUpdateRequest(toolCall)) {
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("update"),
                                        "reason" to JsonPrimitive("归档相关字段必须使用 action=archive 调用预留归档接口")
                                    )).toString()
                                } else {
                                    val payload = resolveUpdatePayload(toolCall)
                                    if (payload.isEmpty()) {
                                        val allowed = listOf("summary", "aiAnalysis", "subject", "difficulty", "ocrText", "imagePath")
                                        val supplied = toolCall.keys.filter { it !in setOf("action", "id", "ids", "field", "value", "fields") }
                                        toolResult = JsonObject(mapOf(
                                            "result" to JsonPrimitive("fail"),
                                            "action" to JsonPrimitive("update"),
                                            "reason" to JsonPrimitive("缺少有效更新字段。允许字段: ${allowed.joinToString(",")}"),
                                            "suppliedFields" to JsonArray(supplied.map { JsonPrimitive(it) })
                                        )).toString()
                                    } else {
                                        var updatedCount = 0
                                        targets.forEach { q ->
                                            var current = q
                                            payload.forEach { (k, v) ->
                                                current = when (k) {
                                                    "summary" -> current.copy(summary = v.jsonPrimitive.contentOrNull?.ifBlank { current.summary } ?: current.summary)
                                                    "aiAnalysis" -> current.copy(aiAnalysis = v.jsonPrimitive.contentOrNull?.ifBlank { current.aiAnalysis } ?: current.aiAnalysis)
                                                    "subject" -> current.copy(subject = v.jsonPrimitive.contentOrNull?.ifBlank { current.subject } ?: current.subject)
                                                    "difficulty" -> current.copy(difficulty = v.jsonPrimitive.intOrNull ?: current.difficulty)
                                                    "ocrText" -> current.copy(ocrText = v.jsonPrimitive.contentOrNull?.ifBlank { current.ocrText } ?: current.ocrText)
                                                    "imagePath" -> current.copy(imagePath = v.jsonPrimitive.contentOrNull?.ifBlank { current.imagePath } ?: current.imagePath)
                                                    else -> current
                                                }
                                            }
                                            if (current != q) {
                                                viewModel.updateQuestion(current)
                                                updatedCount += 1
                                            }
                                        }

                                        val result = if (updatedCount > 0) "success" else "empty"
                                        val reason = if (updatedCount == 0) "命中 id，但新旧值一致或无可生效改动" else ""
                                        toolResult = JsonObject(mapOf(
                                            "result" to JsonPrimitive(result),
                                            "action" to JsonPrimitive("update"),
                                            "updatedCount" to JsonPrimitive(updatedCount),
                                            "reason" to JsonPrimitive(reason)
                                        )).toString()
                                    }
                                }
                            }
                        }

                        "archive" -> {
                            val ids = extractIds(toolCall)
                            if (ids.isEmpty()) {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("archive"),
                                    "reason" to JsonPrimitive("归档必须指定 id。请先 query。")
                                )).toString()
                            } else {
                                val archiveType = toolCall["archiveType"]?.jsonPrimitive?.content
                                val mode = toolCall["mode"]?.jsonPrimitive?.content?.trim()?.lowercase()
                                val shouldUnarchive = archiveType != null &&
                                    setOf("none", "null", "未归档", "取消归档").contains(archiveType.trim().lowercase())
                                val autoTypes = setOf("auto", "auto-classified", "自动", "智能分类", "自动分类")
                                val shouldAutoClassify =
                                    (archiveType != null && autoTypes.contains(archiveType.trim().lowercase())) ||
                                        mode == "auto" || mode == "classify"
                                val targets = all.filter { ids.contains(it.id) }
                                if (shouldUnarchive) {
                                    targets.forEach { viewModel.unarchiveQuestion(it.id) }
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("unarchive"),
                                        "unarchivedCount" to JsonPrimitive(targets.size)
                                    )).toString()
                                } else if (shouldAutoClassify) {
                                    val grouped = mutableMapOf<String, Int>()
                                    targets.forEach {
                                        val inferred = inferArchiveType(it)
                                        viewModel.archiveQuestion(it.id, inferred)
                                        grouped[inferred] = (grouped[inferred] ?: 0) + 1
                                    }
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("archive"),
                                        "archivedCount" to JsonPrimitive(targets.size),
                                        "mode" to JsonPrimitive("auto"),
                                        "grouped" to JsonObject(grouped.mapValues { JsonPrimitive(it.value) })
                                    )).toString()
                                } else {
                                    targets.forEach {
                                        if (archiveType.isNullOrBlank()) {
                                            viewModel.archiveQuestion(it.id)
                                        } else {
                                            viewModel.archiveQuestion(it.id, archiveType)
                                        }
                                    }
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("archive"),
                                        "archivedCount" to JsonPrimitive(targets.size)
                                    )).toString()
                                }
                            }
                        }

                        "unarchive" -> {
                            val ids = extractIds(toolCall)
                            if (ids.isEmpty()) {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("unarchive"),
                                    "reason" to JsonPrimitive("取消归档必须指定 id。请先 query。")
                                )).toString()
                            } else {
                                val targets = all.filter { ids.contains(it.id) }
                                targets.forEach { viewModel.unarchiveQuestion(it.id) }
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("success"),
                                    "action" to JsonPrimitive("unarchive"),
                                    "unarchivedCount" to JsonPrimitive(targets.size)
                                )).toString()
                            }
                        }

                            else -> {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive(action),
                                    "reason" to JsonPrimitive("未知 action: $action")
                                )).toString()
                            }
                        }

                        appendMessage(ChatMessage(role = "tool", content = toolResult))
                        executedCount += 1
                    } catch (e: Exception) {
                        appendMessage(
                            ChatMessage(
                                role = "tool",
                                content = JsonObject(
                                    mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("tool_execute"),
                                        "reason" to JsonPrimitive("工具调用异常：${e.message}")
                                    )
                                ).toString()
                            )
                        )
                    }
                }

                if (executedCount == 0) {
                    autoToolDepth = 0
                    return@launch
                }

                if (autoToolDepth >= maxAutoToolDepth) {
                    appendMessage(ChatMessage(role = "assistant", content = "工具调用次数已达上限，请确认后继续。"))
                    autoToolDepth = 0
                    return@launch
                }

                // 同一轮回复里的多次工具调用只计一次
                autoToolDepth += 1
                viewModel.chatWithAiStream(buildPrompt())
            }
        }

        AiInputBar(
            input = input,
            onInputChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    appendMessage(ChatMessage(role = "user", content = input))
                    autoToolDepth = 0
                    viewModel.chatWithAiStream(buildPrompt(input))
                    input = ""
                    scope.launch {
                        listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                    }
                }
            },
            isLoading = isLoading
        )
    }
}

private const val CHAT_CACHE_PREF = "focus_chat_cache"
private const val CHAT_CACHE_KEY = "messages"
private const val MAX_CACHED_MESSAGES = 100

private fun loadCachedMessages(context: Context): List<ChatMessage> {
    val raw = context.getSharedPreferences(CHAT_CACHE_PREF, Context.MODE_PRIVATE)
        .getString(CHAT_CACHE_KEY, null)
        ?: return emptyList()
    return runCatching {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val role = obj["role"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
            ChatMessage(role = role, content = content)
        }
            .takeLast(MAX_CACHED_MESSAGES)
    }.getOrDefault(emptyList())
}

private fun saveCachedMessages(context: Context, messages: List<ChatMessage>) {
    val capped = messages.takeLast(MAX_CACHED_MESSAGES)
    val encoded = JsonArray(
        capped.map {
            JsonObject(
                mapOf(
                    "role" to JsonPrimitive(it.role),
                    "content" to JsonPrimitive(it.content)
                )
            )
        }
    ).toString()
    context.getSharedPreferences(CHAT_CACHE_PREF, Context.MODE_PRIVATE)
        .edit()
        .putString(CHAT_CACHE_KEY, encoded)
        .apply()
}

data class ChatMessage(
    val role: String,
    val content: String
)

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isTool = msg.role == "tool"
    val bubbleColor = when {
        isUser -> Brush.horizontalGradient(listOf(Color(0xFFB2DFDB), Color(0xFF80CBC4)))
        isTool -> Brush.horizontalGradient(listOf(Color(0xFFEEEEEE), Color(0xFFBDBDBD)))
        else -> Brush.horizontalGradient(listOf(Color(0xFFE1BEE7), Color(0xFFCE93D8)))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(bubbleColor, shape = MaterialTheme.shapes.medium)
                .animateContentSize()
                .padding(12.dp)
                .widthIn(max = 320.dp)
        ) {
            Text(
                text = if (isTool) formatToolStatus(msg.content) else msg.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
        }
    }
}

private fun formatToolStatus(raw: String): String {
    return try {
        val json = Json.parseToJsonElement(raw).jsonObject
        val action = json["action"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val result = json["result"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val status = when (result) {
            "success" -> "成功"
            "empty" -> "无结果"
            "fail" -> "失败"
            else -> "已执行"
        }
        val extra = json["reason"]?.jsonPrimitive?.contentOrNull
            ?: json["count"]?.jsonPrimitive?.contentOrNull?.let { "数量: $it" }
            ?: json["deletedCount"]?.jsonPrimitive?.contentOrNull?.let { "删除: $it" }
            ?: json["updatedCount"]?.jsonPrimitive?.contentOrNull?.let { "更新: $it" }
            ?: json["archivedCount"]?.jsonPrimitive?.contentOrNull?.let {
                val mode = json["mode"]?.jsonPrimitive?.contentOrNull
                if (mode == "auto") "自动归档: $it" else "归档: $it"
            }
            ?: json["unarchivedCount"]?.jsonPrimitive?.contentOrNull?.let { "取消归档: $it" }
        if (extra.isNullOrBlank()) "工具 $action | $status" else "工具 $action | $status | $extra"
    } catch (_: Exception) {
        raw
    }
}

