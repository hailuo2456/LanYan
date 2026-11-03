package com.lanyan.chat.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 对话的专属设置：AI 头像、用户头像与系统提示词。
 * 既用于已有会话（侧栏「对话设置」），也用于新对话开始前的草稿设置。
 * 头像只在该对话生效；提示词留空表示跟随全局设置。
 */
@Composable
fun ConversationSettingsDialog(
    aiAvatarPath: String?,
    userAvatarPath: String?,
    prompt: String,
    onDismiss: () -> Unit,
    onSetAvatar: (Bitmap, Boolean) -> Unit,
    onClearAvatar: (Boolean) -> Unit,
    onSavePrompt: (String) -> Unit,
) {
    var text by remember { mutableStateOf(prompt) }
    var cropUri by remember { mutableStateOf<Uri?>(null) }
    var pendingTarget by remember { mutableStateOf(AvatarTarget.AI) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cropUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提示词与头像") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarSettingRow(
                    label = "AI 头像",
                    note = "只在这个对话里生效",
                    path = aiAvatarPath,
                    isUser = false,
                    showClear = aiAvatarPath != null,
                    onPick = {
                        pendingTarget = AvatarTarget.AI
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClear = { onClearAvatar(false) },
                )
                AvatarSettingRow(
                    label = "用户头像",
                    note = "你在这段对话里的头像",
                    path = userAvatarPath,
                    isUser = true,
                    showClear = userAvatarPath != null,
                    onPick = {
                        pendingTarget = AvatarTarget.USER
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClear = { onClearAvatar(true) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(4000) },
                    label = { Text("本对话系统提示词") },
                    placeholder = { Text("例如：你是一位严格的写作教练，回答要简短。") },
                    supportingText = { Text("留空则使用全局提示词，只影响本对话") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSavePrompt(text)
                    onDismiss()
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    cropUri?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onDismiss = { cropUri = null },
            onConfirm = { bitmap ->
                cropUri = null
                onSetAvatar(bitmap, pendingTarget == AvatarTarget.USER)
            },
        )
    }
}

private enum class AvatarTarget { AI, USER }

@Composable
private fun ColumnScope.AvatarSettingRow(
    label: String,
    note: String,
    path: String?,
    isUser: Boolean,
    showClear: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChatAvatar(path, isUser = isUser, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPick) { Text("更换") }
    }
    if (showClear) {
        TextButton(onClick = onClear, modifier = Modifier.align(Alignment.End)) { Text("恢复全局头像") }
    }
}
