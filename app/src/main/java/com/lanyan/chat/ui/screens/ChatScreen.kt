package com.lanyan.chat.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lanyan.chat.data.local.ConversationEntity
import com.lanyan.chat.data.local.MessageEntity
import com.lanyan.chat.data.model.DeliveryStatus
import com.lanyan.chat.data.model.MessageRole
import com.lanyan.chat.domain.SuggestionEngine
import com.lanyan.chat.ui.MainUiState
import com.lanyan.chat.ui.components.ChatAvatar
import com.lanyan.chat.ui.components.ConversationDrawer
import com.lanyan.chat.ui.components.ConversationSettingsDialog
import com.lanyan.chat.ui.components.MessageContent
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_ATTACHMENTS = 3

@Composable
fun ChatScreen(
    state: MainUiState,
    onSend: (String, List<Uri>) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (ConversationEntity) -> Unit,
    onSetConversationAvatar: (String, Bitmap, Boolean) -> Unit,
    onClearConversationAvatar: (String, Boolean) -> Unit,
    onSaveConversationPrompt: (String, String) -> Unit,
    onSetDraftAvatar: (Bitmap, Boolean) -> Unit,
    onClearDraftAvatar: (Boolean) -> Unit,
    onSaveDraftPrompt: (String) -> Unit,
    onSelectDraftModel: (String?) -> Unit,
    onCreateGroup: (List<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onToggleMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemories: () -> Unit,
    onEnsureSuggestions: () -> Unit,
    onRefreshSuggestions: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                ConversationDrawer(
                    conversations = state.conversations,
                    selectedId = state.currentConversationId,
                    onSelect = onSelectConversation,
                    onNewChat = onNewChat,
                    onRename = onRenameConversation,
                    onDelete = onDeleteConversation,
                    onSetAvatar = onSetConversationAvatar,
                    onClearAvatar = onClearConversationAvatar,
                    onSavePrompt = onSaveConversationPrompt,
                    onOpenSettings = onOpenSettings,
                    onOpenMemories = onOpenMemories,
                    modifier = Modifier.width(300.dp),
                )
                ChatContent(
                    state = state,
                    compact = false,
                    onSend = onSend,
                    onStop = onStop,
                    onNewChat = onNewChat,
                    onEnsureSuggestions = onEnsureSuggestions,
                    onRefreshSuggestions = onRefreshSuggestions,
                    onDismissNotice = onDismissNotice,
                    onSetDraftAvatar = onSetDraftAvatar,
                    onClearDraftAvatar = onClearDraftAvatar,
                    onSaveDraftPrompt = onSaveDraftPrompt,
                    onSelectDraftModel = onSelectDraftModel,
                    onCreateGroup = onCreateGroup,
                    onEditMessage = onEditMessage,
                    onToggleMemory = onToggleMemory,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val focusManager = LocalFocusManager.current
            // 打开侧栏时收起键盘，避免输入框与输入法一直停留在前台
            LaunchedEffect(drawerState.isOpen) {
                if (drawerState.isOpen) focusManager.clearFocus()
            }
            fun closeDrawer() { scope.launch { drawerState.close() } }
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(modifier = Modifier.width(310.dp)) {
                        ConversationDrawer(
                            conversations = state.conversations,
                            selectedId = state.currentConversationId,
                            onSelect = { closeDrawer(); onSelectConversation(it) },
                            onNewChat = { closeDrawer(); onNewChat() },
                            onRename = onRenameConversation,
                            onDelete = { closeDrawer(); onDeleteConversation(it) },
                            onSetAvatar = onSetConversationAvatar,
                            onClearAvatar = onClearConversationAvatar,
                            onSavePrompt = onSaveConversationPrompt,
                            onOpenSettings = { closeDrawer(); onOpenSettings() },
                            onOpenMemories = { closeDrawer(); onOpenMemories() },
                        )
                    }
                },
            ) {
                ChatContent(
                    state = state,
                    compact = true,
                    onMenu = {
                        focusManager.clearFocus()
                        scope.launch { drawerState.open() }
                    },
                    onSend = onSend,
                    onStop = onStop,
                    onNewChat = onNewChat,
                    onEnsureSuggestions = onEnsureSuggestions,
                    onRefreshSuggestions = onRefreshSuggestions,
                    onDismissNotice = onDismissNotice,
                    onSetDraftAvatar = onSetDraftAvatar,
                    onClearDraftAvatar = onClearDraftAvatar,
                    onSaveDraftPrompt = onSaveDraftPrompt,
                    onSelectDraftModel = onSelectDraftModel,
                    onCreateGroup = onCreateGroup,
                    onEditMessage = onEditMessage,
                    onToggleMemory = onToggleMemory,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(
    state: MainUiState,
    compact: Boolean,
    onSend: (String, List<Uri>) -> Unit,
    onStop: () -> Unit,
    onNewChat: () -> Unit,
    onEnsureSuggestions: () -> Unit,
    onRefreshSuggestions: () -> Unit,
    onDismissNotice: () -> Unit,
    onSetDraftAvatar: (Bitmap, Boolean) -> Unit,
    onClearDraftAvatar: (Boolean) -> Unit,
    onSaveDraftPrompt: (String) -> Unit,
    onSelectDraftModel: (String?) -> Unit,
    onCreateGroup: (List<String>) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onToggleMemory: () -> Unit,
    modifier: Modifier = Modifier,
    onMenu: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var pendingImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showDraftSettings by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showModelLocked by remember { mutableStateOf(false) }
    var showGroupCreate by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pendingImages = (pendingImages + uris).distinct().take(MAX_ATTACHMENTS)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var autoFollow by remember { mutableStateOf(true) }
    // ↓ 回到底部时正在播放滚动动画，避免流式输出的瞬时滚动打断动画
    var smoothScrolling by remember { mutableStateOf(false) }
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()
            if (lastVisible == null) {
                true
            } else {
                lastVisible.index >= info.totalItemsCount - 1 &&
                    lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 8
            }
        }
    }
    LaunchedEffect(isAtBottom) { autoFollow = isAtBottom }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0) {
            imeWasVisible = true
            // 键盘弹出时，如果用户正停留在底部，让最后一条内容贴在输入框上方
            if (autoFollow && state.messages.isNotEmpty()) {
                listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
            }
        } else if (imeWasVisible) {
            // 键盘收起时释放输入框焦点，避免蓝色聚焦描边一直亮着
            focusManager.clearFocus()
            imeWasVisible = false
        }
    }
    val currentConversation = state.conversations.firstOrNull { it.id == state.currentConversationId }
    val isGroup = currentConversation?.isGroup == true
    val groupSize = currentConversation?.memberIds?.split(',')?.count { it.isNotBlank() } ?: 0
    val title = currentConversation?.title ?: "新对话"
    val aiAvatar = currentConversation?.aiAvatarPath ?: state.avatars.aiAvatar
    val conversationUserAvatar = currentConversation?.userAvatarPath ?: state.avatars.userAvatar
    val activeProfile = state.settings.resolveProfile(
        currentConversation?.modelProfileId ?: state.draftModelProfileId,
    )
    val modelLabel = when {
        isGroup -> "群组 · $groupSize 位成员"
        activeProfile != null -> "${activeProfile.label} · ${activeProfile.modelName}"
        else -> state.settings.model.ifBlank { "未配置模型" }
    }
    val canSwitchModel = state.settings.profiles.size >= 2 && !isGroup
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            autoFollow = true
            listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }
    LaunchedEffect(state.messages.lastOrNull()?.content?.length) {
        if (autoFollow && !smoothScrolling && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex, Int.MAX_VALUE)
        }
    }

    val backgroundPath = state.avatars.chatBackground
    Box(modifier = modifier.fillMaxSize()) {
        if (!backgroundPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(backgroundPath)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
            )
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (!backgroundPath.isNullOrBlank()) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    if (compact) IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "历史会话") }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .then(
                                    if (canSwitchModel) {
                                        Modifier.clickable {
                                            if (state.currentConversationId != null) {
                                                showModelLocked = true
                                            } else {
                                                showModelPicker = true
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                modelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (canSwitchModel) {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = "切换模型",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = { IconButton(onClick = onNewChat) { Icon(Icons.Rounded.Add, "新对话") } },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = if (!backgroundPath.isNullOrBlank()) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ChatInput(
                value = input,
                onValueChange = { input = it },
                sending = state.sending,
                model = state.settings.model,
                memoryEnabled = state.memoryOverride ?: state.settings.memoryEnabled,
                pendingImages = pendingImages,
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = { uri -> pendingImages = pendingImages - uri },
                editing = editingMessage != null,
                onCancelEdit = {
                    editingMessage = null
                    input = ""
                },
                onToggleMemory = onToggleMemory,
                transparentBackground = !backgroundPath.isNullOrBlank(),
                onSend = {
                    val editing = editingMessage
                    if (editing != null) {
                        if (input.isNotBlank() && !state.sending) {
                            onEditMessage(editing.id, input)
                            editingMessage = null
                            input = ""
                        }
                    } else if ((input.isNotBlank() || pendingImages.isNotEmpty()) && !state.sending) {
                        onSend(input, pendingImages)
                        input = ""
                        pendingImages = emptyList()
                    }
                },
                onStop = onStop,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.messages.isEmpty()) {
                EmptyChat(
                    modifier = Modifier.fillMaxSize(),
                    suggestions = state.suggestions,
                    suggestionsLoading = state.suggestionsLoading,
                    onSuggestion = { onSend(it, emptyList()) },
                    onEnsureSuggestions = onEnsureSuggestions,
                    onRefreshSuggestions = onRefreshSuggestions,
                    onOpenSettings = if (state.currentConversationId == null) {
                        { showDraftSettings = true }
                    } else {
                        null
                    },
                    groupModeEnabled = state.settings.groupModeEnabled && state.currentConversationId == null,
                    onCreateGroup = { showGroupCreate = true },
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        val sender = message.senderConversationId
                            ?.let { id -> state.conversations.firstOrNull { it.id == id } }
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MessageItem(
                                message = message,
                                userAvatar = conversationUserAvatar,
                                aiAvatar = sender?.aiAvatarPath ?: aiAvatar,
                                senderName = message.senderName ?: sender?.title,
                                showSender = isGroup,
                                onEdit = if (!isGroup && message.role == MessageRole.USER) {
                                    {
                                        editingMessage = message
                                        input = message.content
                                        pendingImages = emptyList()
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.messages.isNotEmpty() && !isAtBottom,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            coroutineScope.launch {
                                autoFollow = true
                                smoothScrolling = true
                                try {
                                    val lastIndex = state.messages.lastIndex
                                    // 先平滑滚到最后一条，再把剩余距离补完，让整屏都是滚动动画
                                    listState.animateScrollToItem(lastIndex)
                                    val info = listState.layoutInfo
                                    val last = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
                                    if (last != null) {
                                        val remaining = last.offset + last.size -
                                            (info.viewportEndOffset - info.afterContentPadding)
                                        if (remaining > 0) {
                                            listState.animateScrollBy(remaining.toFloat())
                                        }
                                    }
                                } finally {
                                    smoothScrolling = false
                                }
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = "回到底部",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    }

    if (showDraftSettings) {
        ConversationSettingsDialog(
            aiAvatarPath = state.draftAiAvatar,
            userAvatarPath = state.draftUserAvatar,
            prompt = state.draftSystemPrompt.orEmpty(),
            onDismiss = { showDraftSettings = false },
            onSetAvatar = { bitmap, isUser -> onSetDraftAvatar(bitmap, isUser) },
            onClearAvatar = { isUser -> onClearDraftAvatar(isUser) },
            onSavePrompt = { prompt ->
                onSaveDraftPrompt(prompt)
                showDraftSettings = false
            },
        )
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("选择模型") },
            text = {
                Column {
                    state.settings.profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectDraftModel(profile.id)
                                    showModelPicker = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = activeProfile?.id == profile.id, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(profile.label, fontWeight = FontWeight.Medium)
                                Text(
                                    profile.modelName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelPicker = false }) { Text("取消") } },
        )
    }

    if (showModelLocked) {
        AlertDialog(
            onDismissRequest = { showModelLocked = false },
            title = { Text("更换模型") },
            text = { Text("更换模型请到下一个对话") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showModelLocked = false
                        onNewChat()
                    },
                ) { Text("开启新对话") }
            },
            dismissButton = { TextButton(onClick = { showModelLocked = false }) { Text("取消") } },
        )
    }

    if (showGroupCreate) {
        GroupCreateDialog(
            conversations = state.conversations,
            onDismiss = { showGroupCreate = false },
            onCreate = { ids ->
                showGroupCreate = false
                onCreateGroup(ids)
            },
        )
    }
}

@Composable
private fun EmptyChat(
    suggestions: List<String>,
    suggestionsLoading: Boolean,
    onSuggestion: (String) -> Unit,
    onEnsureSuggestions: () -> Unit,
    onRefreshSuggestions: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    groupModeEnabled: Boolean,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onEnsureSuggestions() }
    var showInfo by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("今天想聊点什么？", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "我可以回答问题、协助创作，也会记住你允许保留的偏好。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        if (onOpenSettings != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("提示词与头像")
                }
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = "说明",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("说明") },
                text = { Text("如无需修改则使用系统提示词和头像") },
                confirmButton = { TextButton(onClick = { showInfo = false }) { Text("知道了") } },
            )
        }
        if (groupModeEnabled) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreateGroup, shape = RoundedCornerShape(20.dp)) {
                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("群组模式（Beta）")
            }
        }
        Spacer(Modifier.height(24.dp))
        (suggestions.ifEmpty { SuggestionEngine.FALLBACK }).forEach { suggestion ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().clickable { onSuggestion(suggestion) },
            ) {
                Text(suggestion, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        TextButton(
            onClick = onRefreshSuggestions,
            enabled = !suggestionsLoading,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            if (suggestionsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("换一批", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MessageItem(
    message: MessageEntity,
    userAvatar: String?,
    aiAvatar: String?,
    senderName: String? = null,
    showSender: Boolean = false,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val isUser = message.role == MessageRole.USER
    var actionsVisible by remember(message.id) { mutableStateOf(false) }
    val imageFiles = remember(message.imagePaths) {
        message.imagePaths.split('\n').filter { it.isNotBlank() }.map { File(it) }
    }
    var viewingImage by remember { mutableStateOf<File?>(null) }
    if (isUser) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    modifier = Modifier.clickable { actionsVisible = !actionsVisible },
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (imageFiles.isEmpty()) 16.dp else 12.dp,
                            vertical = if (imageFiles.isEmpty()) 12.dp else 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (imageFiles.isNotEmpty()) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                imageFiles.forEach { file ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(file)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "图片",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(150.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewingImage = file },
                                    )
                                }
                            }
                        }
                        if (message.content.isNotBlank()) {
                            MessageContent(message.content)
                        }
                    }
                }
                if (actionsVisible && message.content.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MessageAction("复制", Icons.Rounded.ContentCopy) {
                            clipboard.setText(AnnotatedString(message.content))
                        }
                        if (onEdit != null) {
                            MessageAction("修改", Icons.Rounded.Edit, onClick = onEdit)
                        }
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            ChatAvatar(userAvatar, isUser = true, size = 34.dp)
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.Top) {
            ChatAvatar(aiAvatar, isUser = false, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f).clickable { actionsVisible = !actionsVisible },
            ) {
                if (showSender && !senderName.isNullOrBlank()) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                if (message.content.isBlank() && message.status == DeliveryStatus.STREAMING) {
                    Text(
                        "正在思考…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else if (message.content.isNotBlank()) {
                    MessageContent(message.content)
                }
                if (message.status == DeliveryStatus.STREAMING && message.content.isNotBlank()) {
                    Text("▍", color = MaterialTheme.colorScheme.primary)
                }
                message.errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val memoryCount = message.usedMemoryIds.split(',').count { it.isNotBlank() }
                    if (memoryCount > 0 || message.usedSummary) {
                        Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            buildString {
                                if (memoryCount > 0) append("已关联 $memoryCount 条记忆")
                                if (message.usedSummary) append(if (memoryCount > 0) " · 已调用摘要" else "已调用会话摘要")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (message.content.isNotBlank() && message.status != DeliveryStatus.STREAMING) {
                        // 复制按钮改为点击消息后在下方出现，平时保持界面干净
                    }
                }
                if (actionsVisible && message.content.isNotBlank() && message.status != DeliveryStatus.STREAMING) {
                    Row(horizontalArrangement = Arrangement.Start) {
                        MessageAction("复制", Icons.Rounded.ContentCopy) {
                            clipboard.setText(AnnotatedString(message.content))
                        }
                    }
                }
            }
        }
    }

    viewingImage?.let { file ->
        ImageViewerDialog(file = file, onDismiss = { viewingImage = null })
    }
}

@Composable
private fun MessageAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ImageViewerDialog(file: File, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GroupCreateDialog(
    conversations: List<ConversationEntity>,
    onDismiss: () -> Unit,
    onCreate: (List<String>) -> Unit,
) {
    val candidates = conversations.filter { !it.isGroup }
    var selected by remember { mutableStateOf<List<String>>(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建群组（Beta）") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "选择 2-4 个对话作为成员。每个成员会用自己的头像、提示词和模型依次回复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                candidates.forEach { conversation ->
                    val checked = conversation.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = when {
                                    checked -> selected - conversation.id
                                    selected.size >= 4 -> selected
                                    else -> selected + conversation.id
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                conversation.systemPrompt?.takeIf { it.isNotBlank() }?.take(36)
                                    ?: "使用全局提示词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    Text("还没有可用的对话，先单独聊几句再来建群。")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.size in 2..4,
                onClick = { onCreate(selected) },
            ) { Text("创建群组") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    sending: Boolean,
    model: String,
    memoryEnabled: Boolean,
    pendingImages: List<Uri>,
    onPickImage: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    onToggleMemory: () -> Unit,
    transparentBackground: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            color = if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            AnimatedVisibility(
                visible = editing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
                        modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "修改输入",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onCancelEdit, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "取消修改", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            if (pendingImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pendingImages.forEach { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = "待发送图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                            )
                            IconButton(
                                onClick = { onRemoveImage(uri) },
                                modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
                            ) {
                                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        "移除图片",
                                        tint = Color.White,
                                        modifier = Modifier.padding(3.dp).size(12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val canSend = sending || value.isNotBlank() || pendingImages.isNotEmpty()
            // 输入框卡片：文字在上，长期记忆与发送等操作放在卡片内部下方
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 820.dp).fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = !sending,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 6,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 36.dp)
                                    .padding(horizontal = 4.dp),
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        when {
                                            editing -> "修改这条消息…"
                                            pendingImages.isEmpty() -> "给澜言发送消息…"
                                            else -> "补充图片说明（可选）…"
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onPickImage,
                            enabled = !sending && !editing && pendingImages.size < MAX_ATTACHMENTS,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = "添加图片",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        AnimatedVisibility(
                            visible = !editing,
                            enter = fadeIn() + scaleIn(initialScale = 0.9f),
                            exit = fadeOut() + scaleOut(targetScale = 0.9f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (memoryEnabled) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (memoryEnabled) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                ),
                                modifier = Modifier.padding(start = 4.dp).clickable { onToggleMemory() },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Memory,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (memoryEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "长期记忆",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (memoryEnabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = if (sending) onStop else onSend,
                            enabled = canSend,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(modifier = Modifier.padding(8.dp)) {
                                    Icon(
                                        if (sending) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                                        if (sending) "停止生成" else "发送",
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "内容由 AI 生成，请核对重要信息${if (memoryEnabled) " · 长期记忆已开启" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            }
        }
    }
}
