// FlowingBorderTextField.kt
package com.zlearn.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 流光边框输入框组件
 * 模仿 ChatGPT / Perplexity 的输入框动效
 *
 * @param value 输入框当前值
 * @param onValueChange 值变化回调
 * @param label 输入框提示文字
 * @param modifier 外部修饰符
 * @param isActive 是否激活流光效果（通常在有输入内容时激活）
 * @param placeholder 占位文字（可选）
 * @param enabled 是否启用
 * @param trailingIcon 尾部图标（可选）
 */
@Composable
fun FlowingBorderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = value.isNotEmpty(),  // 默认：有内容时激活
    placeholder: String? = null,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    // 流光动画
    val infiniteTransition = rememberInfiniteTransition(label = "borderFlow")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    // 定义渐变色（可自定义）
    val flowColors = listOf(
        Color(0xFF6366F1),  // 靛蓝
        Color(0xFF8B5CF6),  // 紫色
        Color(0xFFEC4899),  // 粉色
        Color(0xFF06B6D4),  // 青色
        Color(0xFF6366F1)   // 回到靛蓝
    )

    Box(modifier = modifier) {
        // 底层：流光边框（仅当激活时显示）
        if (isActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(28.dp))
                    .drawBehind {
                        val strokeWidth = 4.5.dp.toPx() // 原为1.8.dp，改为更宽
                        val gradient = Brush.linearGradient(
                            colors = flowColors,
                            start = Offset(
                                x = size.width * gradientOffset,
                                y = 0f
                            ),
                            end = Offset(
                                x = size.width * (gradientOffset - 0.3f),
                                y = size.height
                            )
                        )
                        drawRoundRect(
                            brush = gradient,
                            style = Stroke(width = strokeWidth),
                            cornerRadius = CornerRadius(28.dp.toPx())
                        )
                    }
            )
        }

        // 上层：实际输入框
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            enabled = enabled,
            trailingIcon = trailingIcon,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                // 激活流光时，隐藏默认边框
                focusedBorderColor = if (isActive) Color.Transparent else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isActive) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}