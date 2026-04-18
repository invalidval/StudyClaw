// AiInputBar.kt
package com.zlearn.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * AI 输入栏组件（输入框 + 发送按钮）
 * 封装了流光边框输入框和发送按钮的布局
 *
 * @param input 输入框当前值
 * @param onInputChange 输入变化回调
 * @param onSend 发送回调
 * @param isLoading 是否正在加载（显示加载按钮）
 * @param modifier 外部修饰符
 * @param placeholder 占位文字
 * @param buttonText 按钮文字
 */
@Composable
fun AiInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    placeholder: String = "向大模型提问...",
    buttonText: String = "发送"
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        // 流光边框输入框
        FlowingBorderTextField(
            value = input,
            onValueChange = onInputChange,
            label = "", // 不用label，防止label动画导致输入框高度变化
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            isActive = !isLoading, // 只根据加载状态决定流光，输入内容不影响
            enabled = !isLoading, // 禁用输入
            placeholder = placeholder // 用placeholder代替label
        )

        // 发送按钮
        Button(
            onClick = { if (!isLoading) onSend() }, // 防止重复点击
            enabled = input.isNotBlank() && !isLoading,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.height(56.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(buttonText)
            }
        }
    }
}