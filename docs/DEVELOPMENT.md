# 开发文档

本文面向维护者，说明澜言的架构、数据模型与构建方式。用户向的安装与 API 配置见根目录 [README](../README.md)。

## 构建与验证

需要 JDK 17、Android SDK 35，Gradle wrapper 已内置。

```bash
# 配置 SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 单元测试 + Lint + 打包
./gradlew testDebugUnitTest lintDebug assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

Windows（cmd）：

```bat
set "JAVA_HOME=C:\path\to\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## 模块划分

```text
app/src/main/java/com/lanyan/chat/
  LanYanApplication.kt   Application；AppContainer 手动装配数据库、仓库、网关等
  MainActivity.kt        单 Activity，Compose Navigation 三个页面：chat / settings / memories
  data/
    local/
      Entities.kt        ConversationEntity / MessageEntity / MemoryEntity
      Daos.kt            ConversationDao / MessageDao / MemoryDao
      LanYanDatabase.kt  数据库定义与 1→5 迁移
      SettingsStore.kt   DataStore 偏好 + EncryptedSharedPreferences 密钥
      AvatarStorage.kt   头像文件（用户 / AI / 会话 / 草稿）
      ChatImageStorage.kt 聊天图片（尽量保留原图字节）
      ImageDecoding.kt   采样解码 + EXIF 旋转
    model/Models.kt      ApiSettings、ModelProfile、PromptMessage 等
    remote/
      ApiEndpoint.kt     接口地址规范化（自动补 /chat/completions）
      OpenAiClient.kt    SSE 流式与非流式请求，多模态 content 组装
    repository/
      ChatRepository.kt  会话、消息、群组、模型绑定
      MemoryRepository.kt 长期记忆读写
  domain/
    MemoryEngine.kt      系统提示词 + 记忆 + 摘要 + 上下文
    MemoryConsolidator.kt 自动提取记忆、按 12 条合并摘要
    MemoryRetriever.kt   关键词 / bigram / 重要性 / 时间衰减打分
    SuggestionEngine.kt  首页开场问题生成、标题清洗
  ui/
    MainViewModel.kt     会话状态、发送、群组回复、草稿、模型选择
    components/          抽屉、消息渲染、头像裁剪、对话设置
    screens/             聊天 / 设置 / 记忆中心
    theme/               颜色与主题
```

## 数据模型

### conversations

| 字段 | 说明 |
|---|---|
| id / title / createdAt / updatedAt | 基础信息 |
| summary / summarizedMessageCount | 会话摘要及已摘要条数 |
| autoTitled | 是否由 AI 自动命名（手动重命名后置 true 并锁定） |
| aiAvatarPath / userAvatarPath | 本对话专属头像，空表示跟随全局 |
| systemPrompt | 本对话专属提示词，空表示跟随全局 |
| isGroup / memberIds | 群组标记与成员对话 id（逗号分隔） |
| modelProfileId | 本对话绑定的模型配置；空表示使用默认 |

### messages

| 字段 | 说明 |
|---|---|
| conversationId / role / content | 基础信息 |
| status / errorText | STREAMING / COMPLETE / ERROR |
| usedMemoryIds / usedSummary | 本次请求引用的记忆与摘要 |
| imagePaths | 聊天图片本地路径，换行分隔 |
| senderConversationId / senderName | 群组场景下的发言成员 |

### memories

分类（画像 / 偏好 / 项目 / 事实）、重要性、使用次数、来源会话、启用状态。

## 数据库迁移

| 迁移 | 内容 |
|---|---|
| 1 → 2 | `conversations.autoTitled` |
| 2 → 3 | `conversations.aiAvatarPath`、`systemPrompt`；`messages.imagePaths` |
| 3 → 4 | `conversations.userAvatarPath` |
| 4 → 5 | `conversations.isGroup`、`memberIds`、`modelProfileId`；`messages.senderConversationId`、`senderName` |

修改实体后必须：升 `@Database(version)` → 补对应 `Migration` → 在 `LanYanApplication` 注册 → 让 KSP 重新导出 `app/schemas/.../*.json`，并核对 schema 与迁移 SQL 一致。

## 多模型实现

- `ApiSettings.profiles: List<ModelProfile>` 存在 DataStore（JSON，不含密钥），每个模型的 Key 存在 `EncryptedSharedPreferences` 的 `api_key_<id>`
- `resolveProfile(id)`：按会话绑定 → 全局 active → 第一个
- `withProfile(profile)`：生成真正用于请求的设置副本
- 新会话创建时写入 `modelProfileId`；群组成员各自使用自己的绑定，未绑定则回落到群组/全局

## 群组模式（Beta）流程

1. 首页选择 2–4 个非群组对话 → `ChatRepository.createGroupConversation`
2. 群组会话 `isGroup=true`，`memberIds` 保存成员 id
3. 发送一条消息后，`runGroupReplies` 依次对每个成员：
   - 用成员的系统提示词 + 群聊规则构造 system
   - 历史消息中助手内容带 `【成员名】` 前缀
   - 使用成员的模型配置流式请求
   - 保存带 `senderConversationId / senderName` 的助手消息
4. 群组暂不使用长期记忆与会话摘要，也不自动生成标题

## 已知限制

- 群组为 Beta：所有成员对每条消息依次回复，暂不支持指定发言人
- 记忆检索基于关键词与 bigram，无向量检索
- Markdown 链接只渲染样式，不可点击
- 仅 debug 签名；发布前需配置 release keystore
- 图片输入依赖模型自身支持视觉能力
