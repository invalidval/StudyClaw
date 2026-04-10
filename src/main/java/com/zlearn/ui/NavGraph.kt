package com.zlearn.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zlearn.ui.LoginScreen
import com.zlearn.ui.RegisterScreen
import com.zlearn.ui.MainScreen
import com.zlearn.ui.question.screens.QuestionListScreen
import com.zlearn.ui.question.screens.QuestionDetailScreen
import com.zlearn.ui.question.screens.AddQuestionScreen
import com.zlearn.ui.review.screens.ReviewScreen
import com.zlearn.ui.nfc.screens.NfcScreen
import com.zlearn.ui.focus.screens.FocusScreen
import androidx.navigation.NavBackStackEntry

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { token ->
                    if (!token.isNullOrEmpty()) {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                }
            )
        }
        composable("register") {
            RegisterScreen(
                onRegisterSuccess = { token ->
                    if (!token.isNullOrEmpty()) {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack("login", inclusive = false)
                }
            )
        }
        composable("main") {
            MainScreen()
        }
        composable("question_list") { QuestionListScreen(navController = navController) }
        composable("add_question") { AddQuestionScreen(navController = navController) }
        composable("question_detail/{id}") { backStackEntry: NavBackStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: -1
            QuestionDetailScreen(id)
        }
        composable("review") { ReviewScreen() }
        composable("nfc") { NfcScreen() }
        composable("focus") { FocusScreen() }
    }
}
