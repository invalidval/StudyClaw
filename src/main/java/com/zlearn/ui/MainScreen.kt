package com.zlearn.ui

import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import com.zlearn.utils.NfcUtil

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("题库", "question_list", Icons.AutoMirrored.Filled.List),
        NavItem("复习", "review", Icons.Filled.Refresh),
        NavItem("NFC", "nfc/receiver", Icons.Filled.Info), // 默认进入接收模式
        NavItem("StudyClaw", "focus", Icons.Filled.Star)
    )
    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController, items)
        }
    ) { innerPadding ->
        LaunchedEffect(navController) {
            NfcUtil.incomingPayload.collect {
                navController.navigate("nfc/receiver") {
                    launchSingleTop = true
                }
            }
        }
        NavGraphWithController(navController, Modifier.padding(innerPadding))
    }
}

data class NavItem(val label: String, val route: String, val icon: ImageVector)

@Composable
fun BottomNavigationBar(navController: NavHostController, items: List<NavItem>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    Box {
        // 原生毛玻璃模糊层（Android 14+）
        NavigationBar(
            modifier = Modifier
                .matchParentSize()
                .blur(24.dp), // 移除 renderBehind 参数，保持兼容
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {}
        // 前景内容层
        NavigationBar(
            modifier = Modifier,
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            items.forEach { item ->
                NavigationBarItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = currentRoute == item.route || (item.route == "question_list" && currentRoute == null),
                    onClick = {
                        if (currentRoute != item.route) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun NavGraphWithController(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = "question_list", modifier = modifier) {
        composable("question_list") { com.zlearn.ui.question.screens.QuestionListScreen(navController = navController) }
        composable("add_question") { com.zlearn.ui.question.screens.AddQuestionScreen(navController = navController) }
        composable("question_detail/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: -1
            com.zlearn.ui.question.screens.QuestionDetailScreen(id = id, navController = navController)
        }
        composable("review") { com.zlearn.ui.review.screens.ReviewScreen() }
        composable("nfc") { com.zlearn.ui.nfc.screens.NfcScreen(mode = "receiver") }
        composable("nfc/{mode}") { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "receiver"
            com.zlearn.ui.nfc.screens.NfcScreen(mode = mode)
        }
        composable("focus") { com.zlearn.ui.focus.screens.FocusScreen() }
    }
}
