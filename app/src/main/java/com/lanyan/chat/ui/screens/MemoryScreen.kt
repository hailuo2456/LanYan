package com.lanyan.chat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanyan.chat.data.local.ConversationEntity
import com.lanyan.chat.data.local.MemoryEntity
import com.lanyan.chat.data.model.MemoryCategory
import com.lanyan.chat.ui.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onAdd: (String, MemoryCategory) -> Unit,
    onUpdate: (MemoryEntity, String, MemoryCategory) -> Unit,
    onDelete: (MemoryEntity) -> Unit,
    onClear: () -> Unit,
    onClearSummary: (String) -> Unit,
    onDismissNotice: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var clearingSummary by remember { mutableStateOf<ConversationEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆中心") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (selectedTab == 0 && state.memories.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("清空") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.widthIn(max = 860.dp).fillMaxSize().padding(horizontal = 18.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Agent 长期记忆", fontWeight = FontWeight.SemiBold)
                            Text(
                                "分层检索相关记忆；近期消息与会话摘要仅属于各自会话。",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Switch(checked = state.settings.memoryEnabled, onCheckedChange = onToggle)
                    }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("长期记忆 ${state.memories.size}") })
                    val summaries = state.conversations.count { it.summary.isNotBlank() }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("会话摘要 $summaries") })
                }
                if (selectedTab == 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "明确的姓名、偏好和项目信息会自动写入，也可以手动维护。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { adding = true }) {
                            Icon(Icons.Rounded.Add, null)
                            Text("新增")
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        items(state.memories, key = { it.id }) { memory ->
                            MemoryCard(memory = memory, onEdit = { editor = memory }, onDelete = { onDelete(memory) })
                        }
                        if (state.memories.isEmpty()) {
                            item { EmptyMemory("还没有长期记忆", "试着在对话中说“请记住我喜欢简洁的回答”，或点击新增手动写入。") }
                        }
                    }
                } else {
                    val summaries = state.conversations.filter { it.summary.isNotBlank() }
                    Text(
                        "清除摘要将无法回复，请谨慎处理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    ) {
                        items(summaries, key = { it.id }) { conversation ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(conversation.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { clearingSummary = conversation }) { Text("清除摘要") }
                                    }
                                    Text(conversation.summary, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "已合并 ${conversation.summarizedMessageCount} 条较早消息 · 近期原文仍独立保留",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                        }
                        if (summaries.isEmpty()) {
                            item { EmptyMemory("还没有会话摘要", "开启自动整理后，每积累 12 条未摘要消息，会合并较早内容并保留最近原文。") }
                        }
                    }
                }
            }
        }
    }

    if (adding || editor != null) {
        MemoryEditorDialog(
            memory = editor,
            onDismiss = { adding = false; editor = null },
            onSave = { content, category ->
                editor?.let { onUpdate(it, content, category) } ?: onAdd(content, category)
                adding = false
                editor = null
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部长期记忆？") },
            text = { Text("这会永久删除 ${state.memories.size} 条用户画像、偏好、项目和事实记忆。会话原文与摘要不受影响，且无法撤销。") },
            confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("全部清除") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
        )
    }
    clearingSummary?.let { conversation ->
        AlertDialog(
            onDismissRequest = { clearingSummary = null },
            title = { Text("清除会话摘要？") },
            text = { Text("只会删除「${conversation.title}」的压缩摘要，原始消息仍然保留。后续自动整理会重新生成摘要。") },
            confirmButton = { TextButton(onClick = { onClearSummary(conversation.id); clearingSummary = null }) { Text("清除") } },
            dismissButton = { TextButton(onClick = { clearingSummary = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun MemoryCard(memory: MemoryEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val date = remember(memory.updatedAt) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(memory.updatedAt))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = onEdit, label = { Text(memory.category.label) })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "编辑记忆") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "删除记忆") }
            }
            Text(memory.content, style = MaterialTheme.typography.bodyLarge)
            Text(
                "重要性 ${memory.importance}/5 · 被调用 ${memory.useCount} 次 · 更新于 $date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun EmptyMemory(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryEditorDialog(
    memory: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, MemoryCategory) -> Unit,
) {
    var content by remember(memory?.id) { mutableStateOf(memory?.content.orEmpty()) }
    var category by remember(memory?.id) { mutableStateOf(memory?.category ?: MemoryCategory.FACT) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (memory == null) "新增长期记忆" else "编辑长期记忆") },
        text = {
            Column {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("记忆类型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        MemoryCategory.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.label) }, onClick = { category = item; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(1000) },
                    label = { Text("记忆内容") },
                    supportingText = { Text("只保存稳定、在未来对话中有用的信息") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(content, category) }, enabled = content.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
