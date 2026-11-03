package com.lanyan.chat.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.lanyan.chat.data.local.ConversationEntity

@Composable
fun ConversationDrawer(
    conversations: List<ConversationEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onNewChat: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (ConversationEntity) -> Unit,
    onSetAvatar: (String, Bitmap, Boolean) -> Unit,
    onClearAvatar: (String, Boolean) -> Unit,
    onSavePrompt: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleting by remember { mutableStateOf<ConversationEntity?>(null) }
    var configuringId by remember { mutableStateOf<String?>(null) }
    val filtered = conversations.filter { it.title.contains(query, ignoreCase = true) }

    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("澜言", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(22.dp))
            Button(onClick = onNewChat, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("开启新对话")
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索历史会话") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "最近对话",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(filtered, key = { it.id }) { conversation ->
                    var menuOpen by remember { mutableStateOf(false) }
                    NavigationDrawerItem(
                        label = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = selectedId == conversation.id,
                        onClick = { onSelect(conversation.id) },
                        icon = null,
                        badge = {
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "会话操作")
                                }
                                ConversationItemMenu(
                                    expanded = menuOpen,
                                    showConfigure = !conversation.isGroup,
                                    onDismiss = { menuOpen = false },
                                    onConfigure = { menuOpen = false; configuringId = conversation.id },
                                    onRename = { menuOpen = false; renaming = conversation },
                                    onDelete = { menuOpen = false; deleting = conversation },
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "还没有历史会话" else "没有匹配的会话",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMemories).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Memory, contentDescription = null)
                Spacer(Modifier.width(14.dp))
                Text("记忆中心", modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null)
                Spacer(Modifier.width(14.dp))
                Text("模型与偏好设置", modifier = Modifier.weight(1f))
            }
        }
    }

    renaming?.let { conversation ->
        var title by remember(conversation.id) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名会话") },
            text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { onRename(conversation.id, title); renaming = null }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
        )
    }
    configuringId?.let { id ->
        conversations.firstOrNull { it.id == id }?.let { conversation ->
            ConversationSettingsDialog(
                aiAvatarPath = conversation.aiAvatarPath,
                userAvatarPath = conversation.userAvatarPath,
                prompt = conversation.systemPrompt.orEmpty(),
                onDismiss = { configuringId = null },
                onSetAvatar = { bitmap, isUser -> onSetAvatar(id, bitmap, isUser) },
                onClearAvatar = { isUser -> onClearAvatar(id, isUser) },
                onSavePrompt = { prompt -> onSavePrompt(id, prompt) },
            )
        }
    }

    deleting?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这段对话？") },
            text = { Text("「${conversation.title}」及其全部消息将被永久删除，相关长期记忆会保留。") },
            confirmButton = {
                TextButton(onClick = { onDelete(conversation); deleting = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ConversationItemMenu(
    expanded: Boolean,
    showConfigure: Boolean,
    onDismiss: () -> Unit,
    onConfigure: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    if (!expanded) return
    val belowButton = with(LocalDensity.current) { IntOffset(0, 48.dp.roundToPx()) }
    Popup(
        alignment = Alignment.TopEnd,
        offset = belowButton,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.width(164.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                if (showConfigure) {
                    DropdownMenuItem(
                        text = { Text("对话设置") },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                        onClick = onConfigure,
                    )
                }
                DropdownMenuItem(
                    text = { Text("重命名") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = onRename,
                )
                DropdownMenuItem(
                    text = { Text("删除会话") },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                    onClick = onDelete,
                )
            }
        }
    }
}
