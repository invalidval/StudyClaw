package com.zlearn.ui.question.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown

enum class GroupItemPosition {
    Single,
    First,
    Middle,
    Last,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionCard(
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    position: GroupItemPosition = GroupItemPosition.Single,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val shape = when (position) {
        GroupItemPosition.Single -> RoundedCornerShape(16.dp)
        GroupItemPosition.First -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        GroupItemPosition.Middle -> RoundedCornerShape(0.dp)
        GroupItemPosition.Last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    }
    val showDivider = position == GroupItemPosition.First || position == GroupItemPosition.Middle

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongPress != null)
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = { onLongPress?.invoke() }
                    )
                else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(modifier = Modifier.padding(16.dp)) {
                Markdown(content = summary)
            }
            if (showDivider) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}
