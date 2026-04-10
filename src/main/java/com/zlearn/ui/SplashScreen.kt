package com.zlearn.ui

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.MainActivity
import com.zlearn.ui.question.viewmodel.QuestionViewModel

@Composable
fun SplashScreen() {
    val questionViewModel: QuestionViewModel = hiltViewModel()
    val context = LocalContext.current
    val hostActivity = context as? Activity
    val isSplashHost = hostActivity is SplashActivity

    if (!isSplashHost) {
        // MainActivity directly renders main content; splash host handles the cold-start gate.
        MainScreen()
        return
    }

    var readyToEnterMain by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        questionViewModel.loadQuestions()
        // Keep splash visible during initial load; fallback covers empty database first launch.
        delay(700)
        readyToEnterMain = true
    }

    LaunchedEffect(readyToEnterMain) {
        if (readyToEnterMain) {
            context.startActivity(Intent(context, MainActivity::class.java))
            hostActivity.finish()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "StudyClaw",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AI 助力，轻松学习",
                fontSize = 18.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = "Created by ZCY & ChatGPT",
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}
