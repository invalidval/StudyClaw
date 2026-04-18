package com.zlearn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zlearn.utils.NfcUtil
import kotlin.math.roundToInt

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem("题库", "question_list", Icons.AutoMirrored.Filled.List),
        NavItem("二维码", "qrcode", Icons.Filled.QrCode),
        NavItem("互传", "nfc/receiver", Icons.AutoMirrored.Filled.Send),
        NavItem("AI助理", "focus", Icons.Filled.AutoAwesome)
    )
    Scaffold(
        bottomBar = {
            ModernBottomNavigationBar(navController, items)
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
fun ModernBottomNavigationBar(navController: NavHostController, items: List<NavItem>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "question_list"

    val selectedIndex = items.indexOfFirst { item ->
        val route = item.route
        currentRoute == route ||
        (route.contains("/") && currentRoute.startsWith(route.substringBefore("/")))
    }.let { if (it == -1) 0 else it }

    // 用于动画层的位置和大小
    var itemWidth by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .height(64.dp)
            .shadow(12.dp, RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(32.dp))
            .onGloballyPositioned {
                itemWidth = it.size.width.toFloat() / items.size
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 滑动选中背景
        val offsetX by animateFloatAsState(
            targetValue = selectedIndex * itemWidth,
            animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.75f),
            label = "nav_offset"
        )

        val indicatorWidth = with(density) { itemWidth.toDp() }
        if (itemWidth > 0) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .padding(6.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
            )
        }

        // 按钮行
        Row(modifier = Modifier.fillMaxSize()) {
            items.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "content_color"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1.0f,
                    label = "icon_scale"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = contentColor,
                            modifier = Modifier.size(if (isSelected) 24.dp else 22.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale)
                        )
                        if (isSelected) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = contentColor,
                                maxLines = 1
                            )
                        }
                    }
                }
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
        composable("qrcode") { com.zlearn.ui.review.screens.ReviewScreen(navController = navController) }
        composable("review") { com.zlearn.ui.review.screens.ReviewScreen(navController = navController) } // compatibility route
        composable("nfc") { com.zlearn.ui.nfc.screens.NfcScreen(mode = "receiver") }
        composable("nfc/{mode}") { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "receiver"
            com.zlearn.ui.nfc.screens.NfcScreen(mode = mode)
        }
        composable("focus") { com.zlearn.ui.focus.screens.FocusScreen() }
        composable("qrcode/generate/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toIntOrNull() ?: -1
            com.zlearn.ui.qrcode.screens.QrGenerateScreen(id = id)
        }
        composable("qrcode/scan") {
            com.zlearn.ui.qrcode.screens.QrScanScreen()
        }
    }
}
