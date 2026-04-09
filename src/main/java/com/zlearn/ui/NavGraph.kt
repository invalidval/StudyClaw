package com.zlearn.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zlearn.ui.question.screens.QuestionListScreen
import com.zlearn.ui.question.screens.QuestionDetailScreen
import com.zlearn.ui.review.screens.ReviewScreen
import com.zlearn.ui.nfc.screens.NfcScreen
import com.zlearn.ui.focus.screens.FocusScreen
import androidx.navigation.NavBackStackEntry

@Composable
fun NavGraph(startDestination: String = "question_list") {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = startDestination) {
        composable("question_list") { QuestionListScreen() }
        composable("question_detail/{question}") { backStackEntry: NavBackStackEntry ->
            val question = backStackEntry.arguments?.getString("question") ?: ""
            QuestionDetailScreen(question)
        }
        composable("review") { ReviewScreen() }
        composable("nfc") { NfcScreen() }
        composable("focus") { FocusScreen() }
    }
}
