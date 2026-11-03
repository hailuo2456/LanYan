package com.lanyan.chat.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lanyan.chat.data.model.ApiSettings
import com.lanyan.chat.data.model.ModelProfile
import com.lanyan.chat.data.model.ThemeMode
import com.lanyan.chat.ui.MainUiState
import com.lanyan.chat.ui.components.AvatarCropDialog
import com.lanyan.chat.ui.components.ChatAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSave: (ApiSettings) -> Unit,
    onTest: (ApiSettings) -> Unit,
    onSaveAvatar: (android.graphics.Bitmap, Boolean) -> Unit,
    onRemoveAvatar: (Boolean) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onGroupModeChange: (Boolean) -> Unit,
    onDismissNotice: () -> Unit,
) {
    val loadedSettings = state.settings
    var form by remember {
        mutableStateOf(
            if (loadedSettings.profiles.isEmpty() && loadedSettings.model.isNotBlank()) {
                loadedSettings.copy(
                    profiles = listOf(
                        ModelProfile(
                            id = "model-1",
                            slot = 1,
                            baseUrl = loadedSettings.baseUrl,
                            apiKey = loadedSettings.apiKey,
                            modelName = loadedSettings.model,
                        ),
                    ),
                )
            } else {
                loadedSettings
            },
        )
    }
    var showKey by remember { mutableStateOf(false) }
    var syncedFromStore by remember { mutableStateOf(false) }
    LaunchedEffect(state.settings.profiles) {
        val stored = state.settings.profiles
        val untouchedSeed = form.profiles.size <= 1 && form.profiles.firstOrNull()?.id == "model-1"
        if (stored.isNotEmpty() && !syncedFromStore && untouchedSeed) {
            form = form.copy(profiles = stored, activeProfileId = state.settings.activeProfileId)
        }
        if (stored.isNotEmpty()) syncedFromStore = true
    }
    var pendingCrop by remember { mutableStateOf<Pair<android.net.Uri, Boolean>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            onDismissNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型与偏好设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "返回") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.testingConnection) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                SettingsSection(title = "个性化头像", description = "分别设置你与 AI 的头像，图片会复制到应用私有目录并长期保存。") {
                    AvatarSettingRow(
                        title = "我的头像",
                        path = state.avatars.userAvatar,
                        isUser = true,
                        onImport = { uri, isUser -> pendingCrop = uri to isUser },
                        onRemove = onRemoveAvatar,
                    )
                    Spacer(Modifier.height(18.dp))
                    AvatarSettingRow(
                        title = "AI 头像",
                        path = state.avatars.aiAvatar,
                        isUser = false,
                        onImport = { uri, isUser -> pendingCrop = uri to isUser },
                        onRemove = onRemoveAvatar,
                    )
                }

                SettingsSection(title = "外观", description = "手动切换深色与浅色模式，也可以跟随系统设置，选择后立即生效并长期保存。") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = form.themeMode == mode,
                                onClick = {
                                    form = form.copy(themeMode = mode)
                                    onThemeChange(mode)
                                },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }

                SettingsSection(
                    title = "模型服务",
                    description = "最多添加 4 个模型，名称不能重复。配置 2 个以上后，新对话可以切换模型。API 密钥由 Android Keystore 加密，仅保存在本机。",
                ) {
                    form.profiles.forEachIndexed { index, profile ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            profile.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profile.baseUrl,
                            onValueChange = { value ->
                                form = form.copy(
                                    profiles = form.profiles.replaceAt(index, profile.copy(baseUrl = value)),
                                )
                            },
                            label = { Text("API 地址") },
                            supportingText = { Text("例如 https://api.deepseek.com/v1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profile.apiKey,
                            onValueChange = { value ->
                                form = form.copy(
                                    profiles = form.profiles.replaceAt(index, profile.copy(apiKey = value)),
                                )
                            },
                            label = { Text("API 密钥") },
                            placeholder = { Text("本地无鉴权服务可留空") },
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "显示或隐藏密钥")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profile.modelName,
                            onValueChange = { value ->
                                form = form.copy(
                                    profiles = form.profiles.replaceAt(index, profile.copy(modelName = value)),
                                )
                            },
                            label = { Text("模型名称") },
                            placeholder = { Text("deepseek-chat") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (form.profiles.size > 1) {
                            TextButton(
                                onClick = {
                                    val removed = profile
                                    val remaining = form.profiles.filterIndexed { i, _ -> i != index }
                                    form = form.copy(
                                        profiles = remaining,
                                        activeProfileId = if (form.activeProfileId == removed.id) {
                                            remaining.firstOrNull()?.id
                                        } else {
                                            form.activeProfileId
                                        },
                                    )
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text("移除这个模型") }
                        }
                    }
                    if (form.profiles.size < ModelProfile.MAX_PROFILES) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val usedSlots = form.profiles.map { it.slot }.toSet()
                                val slot = (1..ModelProfile.MAX_PROFILES).first { it !in usedSlots }
                                form = form.copy(
                                    profiles = form.profiles + ModelProfile(
                                        id = "model-${System.currentTimeMillis()}",
                                        slot = slot,
                                        baseUrl = form.profiles.lastOrNull()?.baseUrl.orEmpty(),
                                    ),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("添加模型")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = form.systemPrompt,
                        onValueChange = { form = form.copy(systemPrompt = it) },
                        label = { Text("全局系统提示词") },
                        supportingText = { Text("单个对话可在「提示词与头像」里单独覆盖") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SettingsSection(
                    title = "实验性功能（Beta）",
                    description = "仍在测试的能力，行为和界面可能调整，建议先在小范围对话里试用。",
                ) {
                    SettingSwitch(
                        title = "群组模式（Beta）",
                        subtitle = "把多个对话的智能体拉进同一个群聊，每条消息由所有成员依次回复",
                        checked = form.groupModeEnabled,
                        onChange = {
                            form = form.copy(groupModeEnabled = it)
                            onGroupModeChange(it)
                        },
                    )
                }

                SettingsSection(title = "生成参数", description = "按模型服务能力调整，较大的上下文会消耗更多 Token。") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("随机性 Temperature", modifier = Modifier.weight(1f))
                        Text(String.format("%.1f", form.temperature), color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = form.temperature.toFloat(),
                        onValueChange = { form = form.copy(temperature = it.toDouble()) },
                        valueRange = 0f..2f,
                        steps = 19,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = form.maxTokens.toString(),
                            onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { form = form.copy(maxTokens = it) } },
                            label = { Text("最大输出 Token") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = form.contextMessages.toString(),
                            onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { form = form.copy(contextMessages = it) } },
                            label = { Text("近期消息数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SettingsSection(title = "Agent 长期记忆", description = "分层保存近期上下文、长期用户记忆和会话摘要，重启后仍然有效。") {
                    SettingSwitch(
                        title = "启用长期记忆",
                        subtitle = "新对话中按需检索相关记忆",
                        checked = form.memoryEnabled,
                        onChange = { form = form.copy(memoryEnabled = it) },
                    )
                    SettingSwitch(
                        title = "自动整理记忆与摘要",
                        subtitle = "提取明确的用户偏好；每 12 条新消息合并一次摘要",
                        checked = form.autoOrganizeMemory,
                        onChange = { form = form.copy(autoOrganizeMemory = it) },
                        enabled = form.memoryEnabled,
                    )
                }

                fun cleanedProfiles(): List<ModelProfile>? {
                    val cleaned = form.profiles
                        .map {
                            it.copy(
                                baseUrl = it.baseUrl.trim(),
                                apiKey = it.apiKey.trim(),
                                modelName = it.modelName.trim(),
                            )
                        }
                        .filter { it.modelName.isNotBlank() || it.baseUrl.isNotBlank() }
                    if (cleaned.any { it.baseUrl.isBlank() || it.modelName.isBlank() }) {
                        scope.launch { snackbarHostState.showSnackbar("请把每个模型的地址和名称填写完整") }
                        return null
                    }
                    val names = cleaned.map { it.modelName.lowercase() }
                    if (names.size != names.toSet().size) {
                        scope.launch { snackbarHostState.showSnackbar("模型名称不能重复") }
                        return null
                    }
                    return cleaned
                }

                val resolvedProfile = form.resolveProfile()
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = test@{
                            val cleaned = cleanedProfiles() ?: return@test
                            val base = form.copy(profiles = cleaned)
                            onTest(base.withProfile(base.resolveProfile()))
                        },
                        enabled = !state.testingConnection &&
                            resolvedProfile?.baseUrl?.isNotBlank() == true &&
                            resolvedProfile.modelName.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (state.testingConnection) "正在连接…" else "测试连接") }
                    Button(
                        onClick = save@{
                            val cleaned = cleanedProfiles() ?: return@save
                            val active = form.activeProfileId
                                ?.takeIf { id -> cleaned.any { it.id == id } }
                                ?: cleaned.firstOrNull()?.id
                            onSave(
                                form.copy(
                                    profiles = cleaned,
                                    activeProfileId = active,
                                    baseUrl = cleaned.firstOrNull()?.baseUrl ?: form.baseUrl,
                                    model = cleaned.firstOrNull()?.modelName ?: form.model,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("保存设置") }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    pendingCrop?.let { (uri, isUser) ->
        AvatarCropDialog(
            uri = uri,
            onDismiss = { pendingCrop = null },
            onConfirm = { bitmap ->
                pendingCrop = null
                onSaveAvatar(bitmap, isUser)
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            content()
        }
    }
}

@Composable
private fun AvatarSettingRow(
    title: String,
    path: String?,
    isUser: Boolean,
    onImport: (android.net.Uri, Boolean) -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onImport(it, isUser) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChatAvatar(path = path, isUser = isUser, size = 58.dp)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        TextButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("更换")
        }
        if (path != null) TextButton(onClick = { onRemove(isUser) }) { Text("恢复默认") }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

private fun List<ModelProfile>.replaceAt(index: Int, value: ModelProfile): List<ModelProfile> =
    toMutableList().also { it[index] = value }
