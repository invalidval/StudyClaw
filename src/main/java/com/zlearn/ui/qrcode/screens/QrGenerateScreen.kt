package com.zlearn.ui.qrcode.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel
import com.zlearn.utils.QrCodeUtil
import com.zlearn.utils.NfcShareCodec
import com.zlearn.domain.model.SharePayload
import com.zlearn.domain.model.ShareItem
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import java.security.MessageDigest
import java.util.UUID
import com.zlearn.data.database.QuestionEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrGenerateScreen(id: Int, viewModel: QuestionViewModel = hiltViewModel()) {
    val questions by viewModel.questions.collectAsState()
    val question = questions.find { it.id == id }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(question) {
        if (question != null) {
            val payload = sharePayloadFromQuestion(question)
            val encoded = NfcShareCodec.encode(payload)
            qrBitmap = QrCodeUtil.encodeToBitmap(encoded)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("题目二维码", style = MaterialTheme.typography.titleMedium) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 二维码卡片展示
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 2.dp,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "二维码",
                                modifier = Modifier.size(240.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = question?.summary ?: "题目内容",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "请另一台设备扫码录入此题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.padding(48.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 辅助信息
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "你可以将生成的二维码展示给同学进行快速分享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun sharePayloadFromQuestion(question: QuestionEntity): SharePayload {
    val hashInput = "${question.ocrText}|${question.summary}|${question.subject}"
    val hash = MessageDigest.getInstance("SHA-256")
        .digest(hashInput.toByteArray())
        .joinToString("") { "%02x".format(it) }
    return SharePayload(
        sessionId = UUID.randomUUID().toString(),
        senderDevice = android.os.Build.MODEL ?: "android",
        createdAt = System.currentTimeMillis(),
        items = listOf(
            ShareItem(
                contentHash = hash,
                ocrText = question.ocrText,
                summary = question.summary,
                subject = question.subject,
                difficulty = question.difficulty,
                aiAnalysis = question.aiAnalysis,
                isArchived = question.isArchived,
                archiveType = question.archiveType ?: ""
            )
        )
    )
}
