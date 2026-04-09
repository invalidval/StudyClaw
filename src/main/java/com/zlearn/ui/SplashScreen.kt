package com.zlearn.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.navigation.Navigator
import androidx.hilt.navigation.compose.hiltViewModel
import com.zlearn.ui.question.viewmodel.QuestionViewModel

@Composable
fun SplashScreen() {
    val questionViewModel: QuestionViewModel = hiltViewModel()
    // 预加载题库数据
    LaunchedEffect(Unit) {
        questionViewModel.loadQuestions()
    }
    var showMain by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        showMain = true
    }
    Crossfade(
        targetState = showMain,
        animationSpec = tween(durationMillis = 700),
        label = "splash-main-fade"
    ) { isMain ->
        if (isMain) {
            MainScreen()
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "喵喵喵~",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI 助力，轻松学习",
                        fontSize = 18.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
