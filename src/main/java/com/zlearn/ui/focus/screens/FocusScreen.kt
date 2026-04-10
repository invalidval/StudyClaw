package com.zlearn.ui.focus.screens

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.data.database.QuestionEntity
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.ui.components.AiInputBar
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    val viewModel: QuestionViewModel = hiltViewModel()
    val aiStreamResponse by viewModel.aiStreamResponse.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scope = rememberCoroutineScope()

    val systemPrompt = """
你是 StudyClaw 智能错题本的 AI 助手，只能用中文与用户交流，风格简洁专业。
你的能力：
- 你可以通过输出 #TOOL# { ... } 的 JSON 结构调用错题管理工具，支持 add、delete、update、query、archive 操作。
- 当用户有错题的增删查改需求时，优先 query 搜索候选，再基于 id 执行后续操作。
- delete/update/archive 必须使用 id 或 ids。没有 id 时，先 query 并反问用户确认。
- archive 必须走归档预留接口（与错题列表一致）：action=archive + id/ids (+ 可选 archiveType)。
- 工具调用格式为 #TOOL# { "action":..., ... }，不要输出多余内容。
- 工具调用后，请根据工具返回结果，用简洁自然语言总结反馈，不要直接输出 JSON。
【重要】
1. 工具调用 JSON 必须严格合法，不能有多余文本、注释、转义错误。
2. action 字段必须有，且只能为 add、delete、update、query、archive 之一。
3. delete/update/archive 禁止按自然语言直接批量执行，必须 id 精确操作。
4. 不要用 update 修改 isArchived/archiveType；归档相关字段只能由 action=archive 处理。
5. query 如命中多条，展示带 id 的候选，并要求用户指定 id。
""".trimIndent()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    fun extractIds(toolCall: JsonObject): List<Int> {
        val ids = toolCall["ids"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.intOrNull ?: it.jsonPrimitive.contentOrNull?.toIntOrNull() }
            ?: emptyList()
        val id = toolCall["id"]?.jsonPrimitive?.intOrNull
            ?: toolCall["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        return (ids + listOfNotNull(id)).distinct()
    }

    fun renderCandidates(list: List<QuestionEntity>): String {
        if (list.isEmpty()) return "未找到相关错题。"
        return list.joinToString("\n") {
            "- id:${it.id} [${it.subject}] ${it.summary.ifBlank { it.ocrText.take(24) }}"
        }
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
            if (isLoading && aiStreamResponse.isNotBlank()) {
                item {
                    ChatBubble(ChatMessage(role = "assistant", content = aiStreamResponse))
                }
            }
        }
        LaunchedEffect(aiStreamResponse, isLoading) {
            if (aiStreamResponse.isBlank() || isLoading) return@LaunchedEffect

            val trimmed = aiStreamResponse.trim()
            if (!trimmed.startsWith("#TOOL#")) {
                messages = messages + ChatMessage(role = "assistant", content = aiStreamResponse)
                return@LaunchedEffect
            }

            var jsonPart = trimmed.removePrefix("#TOOL#").trim()
            if (!jsonPart.startsWith("{") || !jsonPart.endsWith("}")) {
                val start = jsonPart.indexOf('{')
                val end = jsonPart.lastIndexOf('}')
                jsonPart = if (start >= 0 && end > start) jsonPart.substring(start, end + 1) else jsonPart
            }

            scope.launch {
                try {
                    val toolCall = Json.parseToJsonElement(jsonPart).jsonObject
                    val action = toolCall["action"]?.jsonPrimitive?.content?.trim().orEmpty()
                    if (action.isBlank()) {
                        messages = messages + ChatMessage(role = "tool", content = "工具调用 JSON 缺少 action 字段。\n原始内容：$jsonPart")
                        return@launch
                    }

                    val all = viewModel.questions.value
                    var toolResult = ""
                    var assistantMsg: String? = null

                    when (action) {
                        "add" -> {
                            val question = toolCall["question"]?.jsonPrimitive?.content?.trim().orEmpty()
                            val subject = toolCall["subject"]?.jsonPrimitive?.content?.trim().takeUnless { it.isNullOrBlank() } ?: "通用"
                            val difficulty = toolCall["difficulty"]?.jsonPrimitive?.intOrNull ?: 3
                            val summary = toolCall["summary"]?.jsonPrimitive?.content.orEmpty()
                            val aiAnalysis = toolCall["aiAnalysis"]?.jsonPrimitive?.content
                                ?: toolCall["analysis"]?.jsonPrimitive?.content
                                ?: toolCall["解析"]?.jsonPrimitive?.content
                                ?: ""
                            val imagePath = toolCall["imagePath"]?.jsonPrimitive?.content.orEmpty()

                            if (question.isBlank()) {
                                toolResult = JsonObject(mapOf(
                                    "result" to JsonPrimitive("fail"),
                                    "action" to JsonPrimitive("add"),
                                    "reason" to JsonPrimitive("题干不能为空")
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
                                    assistantMsg = "已添加错题：$question"
                                }
                            }
                        }

                        "query" -> {
                            val ids = extractIds(toolCall)
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
                            toolResult = JsonObject(mapOf(
                                "result" to JsonPrimitive(if (filtered.isEmpty()) "empty" else "success"),
                                "action" to JsonPrimitive("query"),
                                "count" to JsonPrimitive(filtered.size),
                                "markdown" to JsonPrimitive(markdown)
                            )).toString()

                            assistantMsg = if (filtered.size > 1) {
                                "$markdown\n请指定 id 再进行删除、更新或归档。"
                            } else {
                                markdown
                            }
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
                                assistantMsg = if (candidates.isEmpty()) {
                                    "删除前请先查询并提供 id。"
                                } else {
                                    "我找到了这些候选：\n${renderCandidates(candidates)}\n请回复要删除的 id。"
                                }
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
                                    assistantMsg = "已删除 ${matches.size} 条错题（id: ${matches.joinToString { it.id.toString() }}）。"
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
                                assistantMsg = "更新前请先查询并提供 id。"
                            } else {
                                val field = toolCall["field"]?.jsonPrimitive?.content
                                val value = toolCall["value"]
                                val fields = toolCall["fields"]?.jsonObject
                                val targets = all.filter { ids.contains(it.id) }

                                val hasArchiveField =
                                    (field == "isArchived" || field == "archiveType") ||
                                        (fields?.keys?.any { it == "isArchived" || it == "archiveType" } == true)
                                if (hasArchiveField) {
                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("fail"),
                                        "action" to JsonPrimitive("update"),
                                        "reason" to JsonPrimitive("归档相关字段必须使用 action=archive 调用预留归档接口")
                                    )).toString()
                                    assistantMsg = "归档请使用 archive 操作（带 id/ids，必要时带 archiveType）。"
                                } else {

                                    val updated = targets.mapNotNull { q ->
                                        if (fields != null && fields.isNotEmpty()) {
                                            var current = q
                                            fields.forEach { (k, v) ->
                                                if (k == "id") return@forEach
                                                current = when (k) {
                                                    "summary" -> current.copy(summary = v.jsonPrimitive.content.ifBlank { current.summary })
                                                    "aiAnalysis", "analysis", "解析" -> current.copy(aiAnalysis = v.jsonPrimitive.content.ifBlank { current.aiAnalysis })
                                                    "subject" -> current.copy(subject = v.jsonPrimitive.content.ifBlank { current.subject })
                                                    "difficulty" -> current.copy(difficulty = v.jsonPrimitive.intOrNull ?: current.difficulty)
                                                    "ocrText" -> current.copy(ocrText = v.jsonPrimitive.content.ifBlank { current.ocrText })
                                                    "imagePath" -> current.copy(imagePath = v.jsonPrimitive.content.ifBlank { current.imagePath })
                                                    else -> current
                                                }
                                            }
                                            viewModel.addQuestion(current)
                                            current
                                        } else if (field != null) {
                                            val changed = when (field) {
                                                "summary" -> q.copy(summary = value?.jsonPrimitive?.content?.ifBlank { q.summary } ?: q.summary)
                                                "aiAnalysis", "analysis", "解析" -> q.copy(aiAnalysis = value?.jsonPrimitive?.content?.ifBlank { q.aiAnalysis } ?: q.aiAnalysis)
                                                "subject" -> q.copy(subject = value?.jsonPrimitive?.content?.ifBlank { q.subject } ?: q.subject)
                                                "difficulty" -> q.copy(difficulty = value?.jsonPrimitive?.intOrNull ?: q.difficulty)
                                                "ocrText" -> q.copy(ocrText = value?.jsonPrimitive?.content?.ifBlank { q.ocrText } ?: q.ocrText)
                                                "imagePath" -> q.copy(imagePath = value?.jsonPrimitive?.content?.ifBlank { q.imagePath } ?: q.imagePath)
                                                else -> q
                                            }
                                            viewModel.addQuestion(changed)
                                            changed
                                        } else {
                                            null
                                        }
                                    }

                                    toolResult = JsonObject(mapOf(
                                        "result" to JsonPrimitive("success"),
                                        "action" to JsonPrimitive("update"),
                                        "updatedCount" to JsonPrimitive(updated.size)
                                    )).toString()
                                    assistantMsg = "已更新 ${updated.size} 条错题。"
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
                                assistantMsg = "归档前请先查询并提供 id。"
                            } else {
                                val archiveType = toolCall["archiveType"]?.jsonPrimitive?.content
                                val targets = all.filter { ids.contains(it.id) }
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
                                assistantMsg = "已归档 ${targets.size} 条错题。"
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

                    val success = toolResult.contains("\"result\":\"success\"") ||
                        toolResult.contains("\"result\":\"empty\"")
                    if (!success) {
                        messages = messages + ChatMessage(role = "tool", content = toolResult)
                    }
                    if (!assistantMsg.isNullOrBlank()) {
                        messages = messages + ChatMessage(role = "assistant", content = assistantMsg)
                    }
                } catch (e: Exception) {
                    messages = messages + ChatMessage(
                        role = "tool",
                        content = "工具调用异常：${e.message}\n原始内容：$jsonPart"
                    )
                }
            }
        }

        AiInputBar(
            input = input,
            onInputChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    val history = messages.filter { it.role != "system" }.joinToString("\n") {
                        when (it.role) {
                            "user" -> "用户：" + it.content
                            "assistant" -> "助手：" + it.content
                            "tool" -> "#TOOL# " + it.content
                            else -> it.content
                        }
                    }
                    val fullPrompt = if (history.isBlank())
                        systemPrompt + "\n用户：" + input
                    else
                        systemPrompt + "\n" + history + "\n用户：" + input
                    messages = messages + ChatMessage(role = "user", content = input)
                    viewModel.chatWithAiStream(fullPrompt)
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
                .padding(12.dp)
                .widthIn(max = 320.dp)
        ) {
            Text(
                text = msg.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black
            )
        }
    }
}

