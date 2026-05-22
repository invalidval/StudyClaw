#import "lib.typ": experiment-report, styled-parameter-table
#import "@preview/cuti:0.2.1": show-cn-fakebold
#show: show-cn-fakebold

#show: doc => experiment-report(
  row1: "移动互联网技术及应用",
  row2: "大作业报告",
  lab: "智能错题本系统的设计与实现",
  name: "张宸宇",
  student-id: "2023211173",
  class: "2023211310",
  date: "2026.5",
  doc
)

#show cite: it => super(it)

=	相关技术

本系统是一个基于 Android 平台的智能错题本应用，采用客户端—服务器架构，集成了 OCR 文字识别、大模型 AI 对话、NFC 近场通信、BLE 蓝牙低功耗传输、QR 二维码分享以及云端数据同步等多项移动互联网技术。

==	Android 与 Kotlin

客户端完全使用 Kotlin 语言编写，最低支持 Android 7.0（API 24），目标版本为 Android 16（API 36）。项目构建使用 Gradle Kotlin DSL，JDK 版本为 17。Kotlin 的协程（Coroutines）和 Flow 框架贯穿整个项目，用于管理异步操作和响应式数据流。

==	Jetpack Compose

UI 层采用 Jetpack Compose 声明式 UI 框架，搭配 Material 3 设计规范。Compose 编译插件由 Kotlin 编译器链集成。导航使用 Compose Navigation 组件，通过 NavHost 管理页面路由。

==	Room 本地数据库

本地持久化使用 Room 数据库框架，基于 SQLite 封装，通过 DAO 接口和 KAPT 编译时注解生成数据库访问代码。客户端使用 Room 6.6.1（通过 libs 目录管理版本），支持数据库迁移（Migration）和数据表索引。

==	Retrofit 与 OkHttp

网络通信使用 Retrofit 2 作为 HTTP 客户端框架，结合 OkHttp 作为底层 HTTP 引擎，使用 Gson 进行 JSON 序列化。AI 接口独立使用另一个 OkHttp 客户端实例，通过拦截器自动注入阿里云 DashScope API Key 认证头。

==	Google ML Kit OCR

客户端内嵌的 OCR 文字识别功能基于 Google ML Kit Text Recognition，专门配置了中文文本识别模型，均为设备本地模型，无需云端 OCR 服务即可离线识别图片中的中文题目文字。

==	阿里云 DashScope 大模型

AI 对话能力基于阿里云 DashScope 平台，通过其兼容 OpenAI 风格的 API 调用 Qwen（通义千问）系列大语言模型，支持 SSE（Server-Sent Events）流式输出和标准 JSON 响应两种模式。

==	Hilt 依赖注入

依赖注入使用 Dagger Hilt 框架，通过 `@HiltAndroidApp`、`@AndroidEntryPoint`、`@HiltViewModel` 等注解自动管理依赖关系。数据库和网络组件通过 `@Module` 和 `@Provides` 在 Singleton 作用域内提供单例实例。

==	NFC 近场通信

NFC 功能基于 Android 原生 `android.nfc` 包实现。NFC 数据的编码格式采用 NDEF（NFC Data Exchange Format）标准中的 RTD_TEXT 记录类型。在 `AndroidManifest.xml` 中申明了 NfcA、Ndef 和 NdefFormatable 三种 NFC 技术筛选器。系统在读取器模式下支持 NFC-A、NFC-B、NFC-F、NFC-V 和 NFC-BARCODE 五种标签类型。

NFC 交互分为两种模式：
- *读取器模式*：应用在前台时启用 NFC Reader Mode，当用户将设备靠近 NFC 标签时，系统自动读取 NDEF 消息并提取文本负载。
- *Intent 分发模式*：当应用在后台或被 NFC 标签唤醒时，通过 `NfcAdapter.ACTION_NDEF_DISCOVERED` Intent 接收数据，应用在 `AndroidManifest.xml` 中注册了自定义 MIME 类型 `application/vnd.com.zlearn.wrongbook-share` 用于过滤 NFC 分享数据。

#figure(
  caption: [NFC 两种交互模式],
  image("/assets/image-4.png")
)

NFC 数据编码采用 NDEF 的 RTD_TEXT 记录格式，其解析遵循 NFC Forum 规范：第 1 字节为状态字节，低 6 位表示语言编码长度，后续为语言编码（如 "en"），剩余部分为 UTF-8 文本内容。这一帧结构和解析过程如 @fig-ndef-frame。

#figure(
  caption: [NDEF RTD_TEXT 帧结构与解码],
  image("/assets/image-5.png")
) <fig-ndef-frame>

```kotlin
// NfcUtil.decodeRecordToText()
val status = payload[0].toInt()
val languageLength = status and 0x3F
val textStart = 1 + languageLength
payload.copyOfRange(textStart, payload.size)
.toString(Charsets.UTF_8)
```

==	BLE 蓝牙低功耗传输

设备间 P2P 数据传输通过 BLE GATT（Generic Attribute Profile）协议实现。系统同时实现了 GATT Server（发送端）和 GATT Client（接收端）两个角色。GATT 服务使用两个特征值（Characteristic）：
- *Read Characteristic*：携带分享数据（JSON 文本负载），由发送端的 GATT Server 暴露，接收端通过 `readCharacteristic()` 读取。
- *Write Characteristic*：用于传输确认回执（ACK），接收端通过 `writeCharacteristic()` 写入，发送端的 GATT Server 通过 `onCharacteristicWriteRequest` 回调接收。

广播使用 `ADVERTISE_MODE_LOW_LATENCY` 模式确保发现速度，扫描使用 `SCAN_MODE_LOW_LATENCY`。为避免 BLE MTU 限制导致的过长数据截断，接收端在连接后请求 MTU 为 517 字节。

==	Python Flask 后端

后端服务器基于 Python Flask 轻量级 Web 框架，使用 SQLite 3 作为数据库，通过 Flask-CORS 中间件处理跨域请求。采用 Werkzeug 提供的 PBKDF2-SHA256 密码哈希算法进行用户密码存储。整个后端是一个单文件应用（`app.py`），监听 0.0.0.0:8080 端口。

==	Zxing 二维码

二维码的生成与扫描使用 ZXing 库，生成端通过 `com.google.zxing` 核心库编码，扫描端通过 `zxing-android-embedded` 封装库提供相机扫码界面。所有 QR 数据均采用 UTF-8 字符集编码。


=	系统功能需求

本系统旨在为学习场景提供一站式的"错题采集-分析-管理-分享"工作流。核心功能如下：

==	错题采集与 OCR

用户可通过相机拍照或从相册选择图片导入错题。系统调用 Google ML Kit 中文 OCR 模型对图片文字进行离线识别，将识别结果自动填入题目内容区域。该功能由 `OcrUtil.kt` 中的 `recognizeTextFromUri()` 和 `recognizeTextFromBitmap()` 两个方法实现，均在 `Dispatchers.IO` 协程调度器上执行以避免阻塞主线程。

==	AI 智能解析

系统集成阿里云 DashScope 大模型，实现三个层面的 AI 能力：
1. *题目解析*：对 OCR 提取的文字进行知识点分析、学科归类、难度评估（1—5级）。
2. *摘要生成*：自动生成题目的一行简短摘要，便于在列表中快速浏览。
3. *多轮对话*：支持针对单道题目进行多轮 AI 问答，采用 SSE 流式输出逐字展示回复。
4. *全局 AI 助手*：在 FocusScreen 中提供基于工具调用（Tool-Use）的智能记事本管理，AI 可通过 `#TOOL# { ... }` 协议直接执行题库的增删改查归档操作。

==	错题管理

提供完整的错题 CRUD 操作：
- 错题列表按归档类别分组展示，支持颜色编码区分不同归档类型。
- 支持按归档类别筛选和整体搜索。
- 支持归档、取消归档和软删除（标记 `deletedAt` 时间戳而不物理删除数据）。
- 错题详情页以 Markdown 格式渲染 AI 分析内容，支持 LaTeX 数学公式。
- 新建错题时，AI 自动生成摘要，用户可覆盖编辑。

==	云同步

系统实现双向增量云同步：
- 上传本地的增删改至云端，基于 `updatedAt` 时间戳执行"最后写入胜出"合并策略。
- 使用游标 `(cursorUpdatedAt, cursorId)` 分页拉取云端更新，避免一次性加载全部数据。
- 支持游客模式（userId=0）与登录模式的数据隔离，切换账号后自动重新加载对应题库。
- 云端 30 天墓碑清理机制，超过 30 天的软删除记录将被物理删除。

==	NFC 碰一碰分享

用户可将错题通过 NFC + BLE 组合方式在设备间 P2P 分享：
- 发送端在题目详情页点击分享，系统创建 `SharePayload` 数据包，通过 BLE 广播等待接收端连接。
- 接收端通过 NFC 感应唤醒，自动启动 BLE 扫描，连接发送端读取数据，解析后逐条入库，并向发送端回传 ACK 确认。
- 状态机管理发送分享的完整生命周期：Idle → WaitingAck → Completed/Error。

==	二维码分享

支持将错题编码为二维码进行跨设备传输：
- 从题目详情页生成包含 `cloudId` 的二维码（格式为 `studyclaw://question/{cloudId}`），另一设备扫码后从云端拉取题目详情并确认加入本地题库。
- 二维码编码统一采用 UTF-8 字符集，与 NFC 分享协议共享数据模型，生成端使用 ZXing 编码，扫描端使用 zxing-android-embedded 提供相机扫码界面及相册图片解析回退方案。

==	用户系统

实现注册、登录、游客模式三种身份管理：
- 密码在客户端使用 SHA-256 哈希后再上传（前端一次哈希），服务端使用 Werkzeug PBKDF2-SHA256 二次哈希存储。
- 认证采用 Token 格式 `token_{user_id}`，每次请求通过 HTTP Authorization 头携带。
- 游客模式允许不登录使用全部功能，但数据不与云端关联。


=	系统设计与实现

==	总体架构

系统采用客户端—服务器架构。客户端使用 MVVM + Clean Architecture 分层模式，后端为 Python Flask RESTful API。总体架构如 @fig-architecture 所示。

#figure(
  caption: [系统总体架构],
  image("/assets/image-1.png",width: 110%)
) <fig-architecture>

==	客户端目录结构

客户端的源代码位于 `app/src/main/java/com/zlearn/` 下，按功能分层组织：

#styled-parameter-table(
  cols: (22%, 78%),
  [包路径], [职责说明],
  [`data/database/`], [Room 数据库：`AppDatabase`（数据库实例，版本6）、`QuestionEntity`（错题实体）、`QuestionDao`（CRUD 数据访问）、`UserDao`],
  [`data/local/`], [`AuthSessionStore`：管理 Token、userId 及同步游标的 SharedPreferences 存储],
  [`data/model/`], [User 实体（Room 对应 `users` 表）],
  [`data/remote/`], [`ApiService`：Retrofit 接口定义（register/login/sync/getQuestion）及对应 DTO],
  [`data/repository/`], [`QuestionRepository`：核心数据仓库，封装 Room + Retrofit + AI 调用，实现双向同步；`UserRepository`：用户认证仓库],
  [`di/`], [Hilt DI 模块：`AppModule` 提供数据库和 DAO，`NetworkModule` 提供 Retrofit/OkHttp 实例],
  [`domain/model/`], [领域模型：`Question`、`SharePayload`/`ShareItem`（分享数据协议）],
  [`domain/usecase/`], [`QuestionUseCases`：封装 Repository 方法的用例层],
  [`network/`], [`AiApiService`：阿里云 DashScope API 接口定义及请求/响应 DTO；`OcrApiService`（骨架）],
  [`ocr/`], [`OcrUtil`：Google ML Kit 中文文字识别工具],
  [`ui/theme/`], [Compose 主题：`Color`、`Theme`（3套配色方案）、`Type`],
  [`ui/`], [导航与主页：`NavGraph`（路由定义）、`MainScreen`（底部导航栏）、`SplashScreen`（启动屏含IP校验）],
  [`ui/question/`], [错题模块：`screens/`（列表/添加/详情页）、`components/`（卡片/输入栏/流式边框输入框）、`viewmodel/`（QuestionViewModel）],
  [`ui/nfc/`], [NFC 分享模块：`NfcScreen`（分享界面）、`NfcViewModel`（BLE 导入流程状态管理）],
  [`ui/focus/`], [AI 助手模块：`FocusScreen`（工具调用 AI 对话，约1015行）、`FocusViewModel`（骨架）],
  [`ui/qrcode/`], [二维码模块：`QrGenerateScreen`（生成）、`QrScanScreen`（扫描）],
  [`ui/review/`], [原“复习”模块：`ReviewScreen`（已被 qrcode 模块替代，仅作入口中转）、`ReviewViewModel`（骨架）],
  [`utils/`], [通用工具：`AppMode`（DEV/PRE/REL）、`BleShareTransport`（BLE GATT 传输）、`NfcUtil`（NFC 状态管理）、`NfcShareCodec`（分享负载编解码）、`PermissionUtil`（BLE 权限）、`QrCodeUtil`（ZXing QR 编解码）],
  [`viewmodel/`], [`UserViewModel`：注册/登录/登出的认证 ViewModel],
  [`MyApplication.kt`], [Hilt Application 类，初始化 BLE，管理 AppMode 和 PRE 模式心跳/IP校验],
  [`MainActivity.kt`], [主 Activity，管理 NFC Reader Mode，处理 NFC Intent 分发],
)

==	数据层设计

===	Room 数据库与实体

客户端本地数据库由 Room 管理，数据库版本为 6（包含从 v5 到 v6 的迁移：添加 `ownerUserId` 列以支持多用户隔离）。

错题实体 `QuestionEntity` 定义如下，存储在 `questions` 表中，同时建立了 `ownerUserId` 和 `(ownerUserId, cloudId)` 两个索引：

```kotlin
@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["ownerUserId"]),
        Index(value = ["ownerUserId", "cloudId"], unique = true)
    ]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerUserId: Int = 0,
    val cloudId: Int? = null,
    val imagePath: String,
    val ocrText: String,
    val aiAnalysis: String,
    val summary: String,
    val subject: String,
    val difficulty: Int,
    val createTime: Long,
    val isArchived: Boolean,
    val archiveType: String? = null,
    val deletedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
```

各字段含义如下：
- `id`：本地自增主键。
- `ownerUserId`：所属用户 ID，0 表示游客，用于多用户数据隔离。
- `cloudId`：云端 ID（`questions` 表的自增主键），为 null 表示尚未同步到云端。
- `imagePath`：错题图片的本地路径。
- `ocrText`：OCR 识别提取的题目文字。
- `aiAnalysis`：AI 对题目的分析内容（Markdown 格式）。
- `summary`：AI 生成的简短摘要。
- `subject`：学科分类（如"数学""英语"等）。
- `difficulty`：难度等级，整数值 1—5。
- `createTime`：创建时间戳（毫秒，UTC）。
- `isArchived`：是否已归档。
- `archiveType`：归档类别（如"易错""重点""已掌握"等）。
- `deletedAt`：软删除时间戳，为 null 表示未删除。
- `updatedAt`：最后更新时间戳，用于同步冲突检测。

QuestionDao 提供完整的 CRUD 操作，关键的同步相关查询包括：
- `getAllForSync()`：获取当前用户所有题目用于同步。
- `getChangedSince(ownerUserId, sinceUpdatedAt)`：查询自指定时间以来的变更记录。
- `getByCloudId(ownerUserId, cloudId)`：按云端 ID 查找本地对应记录。
- `insertOrReplace()`：使用 `OnConflictStrategy.REPLACE` 实现幂等插入。

===	分享数据协议

跨设备分享采用统一的数据协议 `SharePayload`，通过 `kotlinx-serialization` 标记为可序列化：

```kotlin
@Serializable
data class ShareItem(
    val contentHash: String,
    val ocrText: String,
    val summary: String,
    val subject: String,
    val difficulty: Int,
    val aiAnalysis: String = "",
    val isArchived: Boolean = false,
    val archiveType: String = ""
)

@Serializable
data class SharePayload(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val senderDevice: String,
    val createdAt: Long,
    val items: List<ShareItem>
)
```

每个 `ShareItem` 包含一道题目的完整信息（`contentHash` 用于去重检测），`SharePayload` 作为容器包装若干 `ShareItem` 并附加会话元信息（协议版本、会话 ID、发送设备标识、创建时间）。

`NfcShareCodec` 负责在 JSON 字符串与领域对象之间编解码，其中 encode 方法手动使用 `org.json` 构建 JSON 对象（而非 kotlinx-serialization 自动序列化，以确保字段级别的精确控制）：

```kotlin
fun encode(payload: SharePayload): String {
    val items = JSONArray()
    payload.items.forEach { item ->
        items.put(
            JSONObject()
                .put("contentHash", item.contentHash)
                .put("ocrText", item.ocrText)
                .put("summary", item.summary)
                .put("subject", item.subject)
                .put("difficulty", item.difficulty)
                .put("aiAnalysis", item.aiAnalysis)
                .put("isArchived", item.isArchived)
                .put("archiveType", item.archiveType)
        )
    }
    return JSONObject()
        .put("schemaVersion", payload.schemaVersion)
        .put("sessionId", payload.sessionId)
        .put("senderDevice", payload.senderDevice)
        .put("createdAt", payload.createdAt)
        .put("items", items)
        .toString()
}
```

===	认证会话存储

`AuthSessionStore` 以单例形式管理认证会话，基于 Android SharedPreferences 持久化存储：
- `token` 和 `userId`：通过 `StateFlow` 暴露为响应式状态，UI 层可观察登录状态变化。
- 同步游标：`lastUploadUpdatedAt`（最近上传时间戳）、`pullCursorUpdatedAt` 和 `pullCursorId`（下拉同步游标）按 `ownerUserId` 分区存储，确保多账号下各自的同步进度互不干扰。

===	网络层与 AI API

网络层使用 Retrofit + OkHttp 栈。后端主 API（`ApiService`）和 AI API（`AiApiService`）使用不同的 `OkHttpClient` 实例和不同的 base URL：

- 后端 API base URL 从 `BuildConfig.BASE_URL` 读取，DEBUG 模式为内网地址 `http://10.129.215.233:8080/`，Release 模式为正式域名。
- AI API 通过对 DashScope 域名 `https://dashscope.aliyuncs.com/` 发起请求，通过 OkHttp Interceptor 自动注入 `Authorization: Bearer {ALIYUN_API_KEY}` 请求头。

AI 对话接口定义如下：

```kotlin
interface AiApiService {
    @Headers("Content-Type: application/json")
    @POST("/api/v1/services/aigc/text-generation/generation")
    suspend fun chat(@Body request: AliyunChatRequest): Response<AliyunChatResponse>

    @Headers("Content-Type: application/json",
        "X-DashScope-SSE: enable"
    )
    @POST("/api/v1/services/aigc/text-generation/generation")
    @Streaming
    suspend fun chatStream(@Body request: AliyunChatRequest): okhttp3.ResponseBody
}
```

其中 `chat()` 返回标准 JSON 响应，`chatStream()` 通过 `@Streaming` 注解和 `X-DashScope-SSE` 头启用 SSE 流式输出，返回 `okhttp3.ResponseBody` 供上层逐字节解析。

==	云同步设计与实现

云同步是本系统的核心机制，基于增量同步策略实现客户端与服务端之间的双向数据合并。

#figure(
  caption: [客户端与服务端双向增量同步流程],
  image("/assets/image-7.png")
)

===	同步请求协议

客户端通过 `POST /api/questions/sync` 发起同步，请求体包含：

```json
{
  "questions": [...],         // 本地变更的题目列表（首次上传后为空）
  "cursorUpdatedAt": 0,       // 下拉游标：时间戳
  "cursorId": 0,              // 下拉游标：题目ID
  "limit": 200                // 每页拉取数
}
```

服务端响应包含：

```json
{
  "success": true,
  "questions": [...],         // 批量题目数据
  "stats": { "updatedCount": N },
  "cursor": {
    "updatedAt": ...,         // 下一页游标时间戳
    "id": ...,                // 下一页游标题目ID
    "hasMore": true/false     // 是否还有更多数据
  }
}
```

===	同步流程（客户端）

客户端同步在 `QuestionRepository.syncQuestions()` 中实现，流程如下：

1. 读取本地同步状态：获取 `lastUploadUpdatedAt`（上次上传截止时间）和 `(cursorUpdatedAt, cursorId)`（下拉游标）。
2. 查询本地变更：通过 `getChangedSince(ownerUserId, lastUploadUpdatedAt)` 获取自上次上传后的增删改记录，转换为 `SyncQuestionDto` 列表。
3. 发起同步请求：请求体携带本地变更列表、游标和每页数量限制（200 条）。
4. 处理服务端响应：
   - 首次请求时记录上传了 N 条数据（`stats.updatedCount`），后续分页请求中 `questions` 字段传空列表。
   - 对每条返回的远程题目，通过 `cloudId` 或 `(createTime, ocrText)` 组合查找本地对应记录。
   - 以 `updatedAt` 时间戳判断版本新旧：若远程更新时间晚于本地，则用远程数据覆盖本地（*最后写入胜出*）。
   - 通过 `insertOrReplace` 执行幂等写入，本地已有相同 `cloudId` 的记录将被替换。
5. 更新游标：将服务端返回的游标保存到 SharedPreferences，若 `hasMore` 为 true 则循环请求下一页。
6. 若本地有新上传的数据，更新 `lastUploadUpdatedAt` 为本次上传的最大 `updatedAt`。

核心同步循环的核心代码片段：

```kotlin
do {
    val response = apiService.syncQuestions(
        authorization = "Bearer $token",
        request = SyncQuestionsRequest(
            questions = if (!sentLocalChanges) localChanges.map { it.toSyncDto() } else emptyList(),
            cursorUpdatedAt = cursorUpdatedAt,
            cursorId = cursorId,
            limit = SYNC_PAGE_LIMIT
        )
    )
    // ... 错误处理 ...

    body.questions.forEach { remote ->
        val existing = remote.id?.let { questionDao.getByCloudId(ownerUserId, it) }
            ?: questionDao.getUnsyncedByFingerprint(
                ownerUserId = ownerUserId,
                createTime = remote.createTime,
                ocrText = remote.ocrText
            )
        val merged = if (existing == null) {
            remote.toEntity(localId = 0, ownerUserId = ownerUserId)
        } else {
            remote.toEntity(localId = existing.id, ownerUserId = ownerUserId)
        }
        questionDao.insertOrReplace(merged)
    }
    // ... 游标推进 ...
} while (nextCursor.hasMore)
```

===	服务端同步逻辑

服务端同步实现在 `app.py` 的 `sync_questions()` 路由中：

1. 解析 Authorization 头中的 token，提取 `user_id`。
2. 处理上传数据：
   - 遍历客户端提交的 `questions` 列表。
   - 若题目有 `cloudId`（已存在于云端），查询云端对应记录：比较 `updatedAt` 时间戳，仅当上传版本较新时更新（*最后写入胜出*），等版本时保留服务端已有删除状态以避免意外恢复已删除记录。
   - 若题目无 `cloudId`（首次上传），执行 INSERT 为服务端分配新的自增 ID。
   - 跳过 `ocrText` 为空的无效数据。
3. 墓碑清理：删除 `deleted_at` 早于 30 天前的软删除记录。
4. 游标分页查询：按 `(updated_at ASC, id ASC)` 排序查询，使用 `WHERE (updated_at > ? OR (updated_at = ? AND id > ?))` 实现稳定游标分页，避免同一时间戳下的数据丢失。
5. 返回：题目列表 + 游标 + `hasMore` 标志。

服务端游标分页的 SQL 查询：

```python
cursor.execute(
    '''
    SELECT id, image_path, ocr_text, ai_analysis, summary, subject, difficulty,
           create_time, is_archived, archive_type, deleted_at, updated_at
    FROM questions
    WHERE user_id = ?
      AND (
            updated_at > ?
            OR (updated_at = ? AND id > ?)
          )
    ORDER BY updated_at ASC, id ASC
    LIMIT ?
    ''',
    (user_id, cursor_updated_at, cursor_updated_at, cursor_id, limit)
)
```

===	数据映射

客户端在 Repository 中定义了两个映射函数用于本地实体和同步 DTO 之间的转换：

```kotlin
private fun QuestionEntity.toSyncDto(): SyncQuestionDto = SyncQuestionDto(
    id = cloudId,
    imagePath = imagePath,
    ocrText = ocrText,
    aiAnalysis = aiAnalysis,
    summary = summary.ifBlank { ocrText.take(20) },
    subject = subject.ifBlank { "未分类" },
    difficulty = difficulty,
    createTime = createTime,
    isArchived = isArchived,
    archiveType = archiveType,
    deletedAt = deletedAt,
    updatedAt = updatedAt
)
```

其中 `toSyncDto()` 用本地 `cloudId` 填充 DTO 的 `id`（表示同步到同一云端记录），`toEntity()` 则根据 `localId` 参数决定是创建新记录还是覆盖已有记录。

==	NFC + BLE P2P 分享实现

NFC 碰一碰分享是系统最具技术深度的功能之一，其完整交互涉及 NFC 标签读取、NDEF 文本解析、BLE GATT 双向通信、数据编解码和确认回执等多个环节。

===	整体流程

#figure(
  caption: [NFC + BLE 分享的完整交互流程],
  image("/assets/image-3.png")
)

===	NFC 标签读取与 NDEF 编码

NFC 使用 NDEF（NFC Data Exchange Format）协议进行数据传输。`NfcUtil.extractNfcTextPayload()` 方法从 Android Intent 中提取 NDEF 消息：

```kotlin
fun extractNfcTextPayload(intent: Intent?): String? {
    if (intent == null) return null
    // 只处理 NFC 相关 Intent 动作
    val action = intent.action ?: return null
    if (
        action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
        action != NfcAdapter.ACTION_TAG_DISCOVERED &&
        action != NfcAdapter.ACTION_TECH_DISCOVERED
    ) return null

    // 提取 NDEF 消息数组（兼容 API 33+ 的非废弃方法）
    val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Array<NdefMessage>::class.java)
    } else {
        intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
    }
    if (rawMessages != null) {
        val messages = rawMessages.mapNotNull { it as? NdefMessage }
        val firstRecord = messages.firstOrNull()?.records?.firstOrNull()
        val textPayload = firstRecord?.let { decodeRecordToText(it) }
        if (!textPayload.isNullOrBlank()) return textPayload
    }
    // 无 NDEF 文本时返回 Tag ID
    // ...
}
```

NDEF RTD_TEXT 记录的解析遵循 NFC Forum 规范，帧结构为 `[Status Byte | Language Code | Text Data]`：

```kotlin
private fun decodeRecordToText(record: NdefRecord): String? {
    val payload = record.payload ?: return null
    if (payload.isEmpty()) return null
    return if (
        record.tnf == NdefRecord.TNF_WELL_KNOWN &&
        record.type.contentEquals(NdefRecord.RTD_TEXT)
    ) {
        val status = payload[0].toInt()
        val languageLength = status and 0x3F      // 低6位为语言编码长度
        val textStart = 1 + languageLength         // 跳过状态字节和语言编码
        if (textStart >= payload.size) return null
        payload.copyOfRange(textStart, payload.size).toString(Charsets.UTF_8)
    } else {
        payload.toString(Charsets.UTF_8)
    }
}
```

其中状态字节的定义：
- Bit 7：编码指示（0=UTF-8，1=UTF-16）。
- Bit 6：保留位，始终为 0。
- Bit 5—0：语言编码长度（如 "en" 长度为 2）。

===	BLE GATT 传输

BLE 传输由 `BleShareTransport` 单例实现，同时承担 GATT Server（发送端）和 GATT Client（接收端）两个角色。定义了自定义的 GATT 服务 UUID 和特征值 UUID：

#styled-parameter-table(
  cols: (auto, auto, auto, auto),
  [组件], [UUID], [方向], [说明],
  [Service], [`12345678-1234-1234-
  1234-123456789abc`], [—], [自定义 GATT 主服务],
  [Read Char.], [`87654321-4321-4321-
  4321-cba987654321`], [Server→Client], [承载分享数据（Read 属性）],
  [Write Char.], [`11111111-2222-3333-
  4444-555555555555`], [Client→Server], [承载 ACK 确认（Write 属性）],
)

发送端通过 `startAdvertising()` 启动广播：
1. 创建 GATT 服务，包含 Read 特征值（`PROPERTY_READ`）和 Write 特征值（`PROPERTY_WRITE`）。
2. 将分享数据写入 Read 特征值的 value 字段（UTF-8 编码的字节数组）。
3. 使用 `BLUETOOTH_ADVERTISE` 和 `BLUETOOTH_CONNECT` 权限（Android 12+），`ADVERTISE_MODE_LOW_LATENCY` 广播模式。
4. 通过 `BluetoothGattServerCallback.onCharacteristicReadRequest` 回复数据（支持偏移读取以应对大数据量），通过 `onCharacteristicWriteRequest` 接收 ACK。

```kotlin
val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
val characteristic = BluetoothGattCharacteristic(
    CHARACTERISTIC_UUID,
    BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
)
val ackCharacteristic = BluetoothGattCharacteristic(
    ACK_CHARACTERISTIC_UUID,
    BluetoothGattCharacteristic.PROPERTY_WRITE or
        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
    BluetoothGattCharacteristic.PERMISSION_WRITE
)
characteristic.value = data.toByteArray(Charsets.UTF_8)
service.addCharacteristic(characteristic)
service.addCharacteristic(ackCharacteristic)
```

接收端通过 `startScanning()` 进行扫描连接：
1. 使用 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT` 权限，`SCAN_MODE_LOW_LATENCY` 扫描模式。
2. 在 `onScanResult` 回调中检查广播 Service UUID 匹配，优先匹配目标服务，4 秒后回退为广泛连接。
3. 连接后请求 MTU 为 517 字节以提高传输效率，请求 `CONNECTION_PRIORITY_HIGH`。
4. 通过 `discoverServices()` → `getCharacteristic()` → `readCharacteristic()` 读取数据。
5. 读取成功后调用 `sendAck()` 通过 Write 特征值回传确认消息。

ACk 消息的格式为 `ACK:{sessionId}`：

```kotlin
fun sendAck(sessionId: String, onComplete: ((Boolean) -> Unit)? = null): Boolean {
    val gatt = currentGatt ?: return false
    val characteristic = currentAckCharacteristic ?: return false
    val payload = "$ACK_PREFIX$sessionId".toByteArray(Charsets.UTF_8)
    // ...
    gatt.writeCharacteristic(characteristic, payload, ...)
}
```

===	导入流程状态管理

NfcViewModel 通过 NFC 状态机管理接收端的完整导入生命周期，包含 5 个状态：

```kotlin
sealed interface NfcUiState {
    data object Idle : NfcUiState
    data class Detected(val payload: String, val eventId: Long) : NfcUiState
    data class Importing(val payload: String, val eventId: Long, val preview: SharePreview?) : NfcUiState
    data class Result(val message: String, val preview: SharePreview?) : NfcUiState
    data class Error(val message: String) : NfcUiState
}
```

状态转换流程为：
1. *Idle → Detected*：收到 NFC `incomingPayload` 事件（经 800ms 去抖动）。
2. *Detected → Importing*：用户确认导入，启动 BLE 扫描，设置 45 秒超时。
3. *Importing → Importing（preview）*：BLE 连接成功、读取数据、解析预览信息。
4. *Importing → Result*：逐条入库、发送 ACK 成功。
5. *Importing → Error*：BLE 扫描超时、连接失败、Session ID 不匹配或 ACK 失败。

#figure(
  caption: [NfcViewModel 导入流程状态机],
  image("/assets/image-6.png")
)

===	AndroidManifest NFC 与 BLE 声明

系统在 `AndroidManifest.xml` 中进行了完整的 NFC 和 BLE 权限声明：

NFC 技术筛选器声明了三种支持的标签技术：

```xml
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <tech-list>
        <tech>android.nfc.tech.Ndef</tech>
    </tech-list>
    <tech-list>
        <tech>android.nfc.tech.NdefFormatable</tech>
    </tech-list>
    <tech-list>
        <tech>android.nfc.tech.NfcA</tech>
    </tech-list>
</resources>
```

Activity 注册了 NFC Intent 过滤器，监听自定义 MIME 类型：

```xml
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop">
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="application/vnd.com.zlearn.wrongbook-share" />
    </intent-filter>
</activity>
```

BLE 权限针对不同 API 级别做出了兼容性处理：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- Android 12+ 不再需要位置权限进行 BLE 扫描 -->
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
<!-- 旧版本兼容：位置权限用于 BLE 扫描 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

==	二维码分享实现

===	二维码生成

二维码生成在 `QrGenerateScreen` 中触发，核心逻辑通过 `QrCodeUtil.encodeToBitmap()` 实现，使用 ZXing 库将内容字符串编码为 Bitmap 图片。生成时使用题目的 `cloudId` 构造 URI 格式 `studyclaw://question/{cloudId}`，前提是题目已同步到云端。

```kotlin
object QrCodeUtil {
    fun encodeToBitmap(content: String, width: Int, height: Int): Bitmap? {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
        // ... 将 BitMatrix 渲染为 Bitmap ...
    }
}
```

===	二维码扫描

二维码扫描在 `QrScanScreen` 中实现，使用 `zxing-android-embedded` 库的 `CompoundBarcodeView` 组件提供实时相机扫码界面，同时支持从相册选取图片进行离线解码作为回退方案。扫描结果通过 `QrCodeUtil.decodeFromBitmap()` 解码：

```kotlin
fun decodeFromBitmap(bitmap: Bitmap): String? {
    val reader = MultiFormatReader()
    val hints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8")
    reader.setHints(hints)
    val binaryBitmap = BinaryBitmap(HybridBinarizer(...))
    return reader.decode(binaryBitmap).text
}
```

扫码后解析出 `cloudId`，调用 `fetchQuestionFromCloud(cloudId)` 从云端拉取题目详情，展示预览对话框，用户确认后加入本地题库。

==	OCR 文字识别实现

OCR 功能基于 Google ML Kit Text Recognition 中文模型，在 `OcrUtil.kt` 中封装。

```kotlin
object OcrUtil {
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognizeTextFromUri(uri: Uri, context: Context): String =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image).await().text
        }

    suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image).await().text
        }
}
```

两种识别入口均使用 `Dispatchers.IO` 在后台线程执行，避免卡顿主线程。`ChineseTextRecognizerOptions` 确保选择中文优化识别模型。

==	AI 对话与工具调用实现

===	标准对话与流式输出

单题 AI 对话在 `QuestionDetailScreen` 中通过 `AiInputBar` 组件触发，由 `QuestionViewModel.chatWithAiStream()` 处理。流式输出通过 OkHttp 直接读取 SSE 响应体，实现逐字符渲染：

```kotlin
suspend fun chatWithAiStream(request: AliyunChatRequest): okhttp3.ResponseBody =
    aiApiService.chatStream(request)
```

在 ViewModel 中，流式请求的响应通过 `okhttp3.ResponseBody.source()` 逐行读取 SSE 帧，按 `data:` 前缀提取文本，累计更新 `aiStreamResponse` StateFlow，UI 实时渲染。

===	FocusScreen 工具调用 AI

`FocusScreen`（约 1015 行）是本系统最具创新的功能模块，通过定义特殊的工具调用协议使 AI 能够直接操作题库数据库。系统`prompt`中定义了如下工具协议：

```text
当需要操作题库时，请在回复中输出以下格式的指令块：
#TOOL# {
  "action": "add" | "query" | "delete" | "update" | "archive" | "unarchive",
  "params": { ... }
}
```

支持的工具操作包括：
- `add`：新增错题（包含 ocrText、summary、subject、difficulty、aiAnalysis 等字段）。
- `query`：查询错题库（支持按 subject、difficulty、archiveType 等条件过滤）。
- `delete`：删除指定错题（按 id）。
- `update`：更新错题内容（按 id 指定）。
- `archive`：归档错题（基于摘要智能推断归档类型，如"物理""数学""算法"等）。
- `unarchive`：取消归档。

FocusScreen 在解析 AI 流式回复时，通过正则表达式匹配 `#TOOL#` 指令块，解析出 JSON 指令后执行对应的数据库操作，并将结果反馈给 AI 继续对话，形成了一个 AI 与本地数据交互的闭环。

#figure(
  caption: [FocusScreen AI 工具调用循环],
  image("/assets/image-8.png")
)

==	后端服务器实现

后端使用 Python Flask 框架，单文件 `app.py` 实现全部功能，代码约 343 行。使用 SQLite 3 作为轻量数据库（文件 `users.db`），通过 Flask-CORS 中间件允许跨域请求。

===	认证模块

注册和登录使用 Werkzeug 的 `generate_password_hash()` 和 `check_password_hash()` 进行 PBKDF2-SHA256 密码哈希。Token 采用简单格式 `token_{user_id}`，通过 HTTP Authorization 头传递，由 `parse_user_id_from_auth_header()` 函数解析。

```python
@app.route('/api/register', methods=['POST'])
def register():
    data = request.get_json()
    username = data.get('username')
    password = data.get('password')
    # ...
    hashed_password = generate_password_hash(password)
    cursor.execute(
        'INSERT INTO users (username, password) VALUES (?, ?)',
        (username, hashed_password)
    )
    db.commit()
    user_id = cursor.lastrowid
    token = f"token_{user_id}"
    return jsonify({'success': True, 'token': token, 'user_id': user_id})
```

===	数据库表结构

`users` 表：

```sql
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL
);
```

`questions` 表：

```sql
CREATE TABLE IF NOT EXISTS questions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    image_path TEXT,
    ocr_text TEXT,
    ai_analysis TEXT,
    summary TEXT,
    subject TEXT,
    difficulty INTEGER,
    create_time INTEGER,
    is_archived INTEGER DEFAULT 0,
    archive_type TEXT,
    deleted_at INTEGER,
    updated_at INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_questions_user_id ON questions (user_id);
CREATE INDEX IF NOT EXISTS idx_questions_user_cloud_sync ON questions (user_id, updated_at, id);
```

===	API 路由总览

#styled-parameter-table(
  cols: (32%, 38%, 30%),
  [路由], [功能], [认证],
  [POST `/api/register`], [用户注册，返回 Token], [无],
  [POST `/api/login`], [用户登录，返回 Token], [无],
  [POST `/api/questions/sync`], [双向增量同步（上传+下拉）], [Token],
  [GET `/api/questions/<id>`], [获取单题详情（已删除题目返回 404）], [无需认证],
  [GET `/api/heartbeat`], [PRE 模式心跳检测（IP 白名单）], [无],
  [GET `/api/check_ip`], [IP 白名单校验（PRE 模式）], [无],
)

===	心跳与 IP 白名单机制

PRE 模式下，客户端每 3 秒向 `/api/heartbeat` 发送心跳，服务端根据请求来源 IP 判断是否在白名单中。若心跳失败，客户端通过 `Process.killProcess()` 强制终止自身进程。这一机制用于内测阶段的设备管控。白名单 IP 在代码中硬编码定义：

```python
whitelist = ['127.0.0.1', '10.29.50.175', '10.129.247.156', '0.0.0.0']
```

==	UI 层实现

===	导航架构

应用导航基于 Jetpack Compose Navigation，路由定义在 `NavGraph.kt` 中：

```kotlin
NavHost(navController = navController, startDestination = "main") {
    composable("main") { MainScreen(context, mainNavController, innerNavController) }
    composable("question_list") { QuestionListScreen(...) }
    composable("add_question") { AddQuestionScreen(...) }
    composable("question_detail/{id}") { QuestionDetailScreen(...) }
    composable("qrcode") { ReviewScreen(...) }  // 中转页
    composable("qrcode/generate/{id}") { QrGenerateScreen(...) }
    composable("qrcode/scan") { QrScanScreen(...) }
    composable("review") { ReviewScreen(...) }
    composable("nfc") { NfcScreen(...) }
    composable("nfc/{mode}") { NfcScreen(...) }
    composable("focus") { FocusScreen(...) }
}
```

`MainScreen` 包含底部导航栏，四个标签页分配为：题库（QuestionList）、二维码（ReviewScreen 中转）、互传（NFC）、AI 助理（FocusScreen）。

===	主题系统

应用提供三套配色方案，定义在 `Theme.kt` 中：

```kotlin
enum class AppTheme { EyeCareNote, BlueNote, GreenNote }
```

- *EyeCareNote（护眼笔记本）*：温暖米白色调（`#F6F6EA` 背景色），模仿纸质笔记本的阅读体验，适于长时间学习使用。
- *BlueNote（蓝调笔记本）*：蓝色系，清爽专业。
- *GreenNote（绿调笔记本）*：绿色系，柔和自然。

每个主题均包含 `lightColorScheme` 和 `darkColorScheme`，使用 `isSystemInDarkTheme()` 自动跟随系统深色模式切换。

===	FlowingBorderTextField

自定义的"流式边框输入框"组件是 UI 层的亮点之一。通过 Compose 的 `drawBehind` Modifier 和 `infiniteRepeatable` 动画实现流动渐变边框效果。渐变颜色依次为 Indigo → Purple → Pink → Cyan，通过 `LinearGradientShader` 和动态变化的 `translateX` 偏移量实现边框流动旋转的视觉效果。该组件专为 AI 对话输入场景设计，增强了用户输入的交互体验。

==	依赖管理与版本控制

系统通过 Gradle Kotlin DSL 管理依赖，关键依赖版本在 `gradle/libs.versions.toml` 中统一定义。`app/build.gradle.kts` 中引用的关键依赖如下：

#styled-parameter-table(
  cols: (30%, 45%, 25%),
  [类别], [依赖], [用途],
  [UI], [Jetpack Compose BOM], [Material 3 + Navigation + Icons Extended],
  [UI], [androidx.core:core-splashscreen:1.0.1], [启动画面 API],
  [UI], [mikepenz/markdown-renderer-m3], [Compose 内 Markdown 渲染],
  [UI], [io.noties.markwon:core + ext-latex], [Markwon Markdown + LaTeX 渲染],
  [DI], [Dagger Hilt + Hilt Navigation Compose], [依赖注入 + Compose 集成],
  [DB], [Room Runtime + KTX + Compiler (kapt)], [本地 SQLite ORM],
  [网络], [Retrofit 2 + Gson Converter + OkHttp Logging], [HTTP 客户端],
  [OCR], [ML Kit Text Recognition + Chinese], [中文文字识别],
  [AI], [DashScope API (直接 HTTP)], [阿里云大模型对话],
  [NFC], [Android NFC API (系统库)], [近场通信],
  [BLE], [Android Bluetooth LE API (系统库)], [蓝牙低功耗传输],
  [QR], [com.google.zxing:core:3.5.2], [二维码编码核心库],
  [QR], [zxing-android-embedded:4.3.0], [Android 相机二维码扫描],
  [序列化], [kotlinx-serialization-json:1.7.3], [Kotlin JSON 序列化],
  [异步], [Kotlin Coroutines + Flow + RxJava 3], [异步编程 + 响应式数据流],
)

==	应用模式

系统通过 `AppMode` 枚举支持三种运行模式，在 `MyApplication.kt` 中配置：

```kotlin
enum class AppMode { DEV, PRE, REL }
```

- *DEV（开发模式）*：绑定内网后端（`http://10.129.215.233:8080/`），跳过所有 IP 和心跳校验，使用硬编码的 API Key。
- *PRE（预发布模式）*：启动时调用 `/api/check_ip` 校验设备 IP 是否在白名单中，若校验失败则 `killProcess()`。通过后每 3 秒向 `/api/heartbeat` 发送心跳维持连接，心跳失败同样终止进程。
- *REL（发布模式）*：使用正式域名后端（`https://api.yourdomain.com/`），API Key 从环境变量读取，ProGuard 混淆和资源压缩生效。

BuildConfig 通过 `build.gradle.kts` 配置针对 Debug 和 Release 两种构建类型设置不同的 `BASE_URL` 和 `ALIYUN_API_KEY`：

```kotlin
defaultConfig {
    buildConfigField("String", "BASE_URL", "\"http://10.129.215.233:8080/\"")
    buildConfigField("String", "ALIYUN_API_KEY", "\"sk-xxxxxxxx\"")
}
buildTypes {
    release {
        buildConfigField("String", "BASE_URL", "\"https://api.yourdomain.com/\"")
        buildConfigField("String", "ALIYUN_API_KEY", "\"${System.getenv("ALIYUN_API_KEY")}\"")
    }
}
```

=   系统运行截图

通过数据线将手机（Android 15.0）连接到电脑，在Android Studio中将APP运行在实体机上。
#align(center)[
#figure(
    image("/assets/image-9.png",width: 60%),
    
)
]

== 应用信息

本应用取名为“StudyClaw”，灵感来源于“OpenClaw”。具备签名，可在手机上正常安装。

#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    figure(
    image("/assets/image-11.png",width: 5cm),
    ),
    figure(
    image("/assets/81818525458acb156811c777d556f084.jpg",width: 5cm),
    )
)
]



== 功能页截图

=== 题库页面

此页面以列表布局展示用户所有错题，是用户启动APP所见的首页。浅色（左）/深色（右）模式跟随系统自动切换，以适应不同场景需求。

#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/dba7b8121b23efcaec5fb562c6ad146b.jpg",width: 5cm),
    image("/assets/b78aa6e21ddb2a1dde111344e60b047c.jpg",width: 5cm)
)
]

可在页面上方点击登录后进行云同步。用户没有账号时可先注册。


可在下拉列表中按所选归档展示，长按错题可进行删除/归档。


#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/645fe9ff8648527fe902bd764fc10e62.jpg",width: 5cm),
    image("/assets/a39872e3b41dfad03ffe31a5e30b4d25.jpg",width: 5cm),
    
)
]

可通过右下角的“+”添加错题，在子页面中可以选择图片进行OCR识别，也可以手动输入文本。*AI摘要会根据OCR文本自动生成。*

#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/8d68ca682bdfda964c85f2ffc826bc83.jpg",width: 5cm),
    image("/assets/42b704968d355798dc9927765839fed0.jpg",width: 5cm)
)
]

=== 二维码分享页面

从此处可以跳转到题库进行二维码生成，也可以打开摄像机扫码。
#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/ebd9dd894f0b3a956314dcef6fde198c.jpg",width: 5cm),
    image("/assets/image-10.png",width: 5cm)
)
]
=== NFC碰传页面

接收状态（左图）和发送状态（右图）。

#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/68c5bce28f7fee48736de7f90dace2ed.jpg",width: 5cm),
    image("/assets/e807a1b3c091f25f42e7a08fc4ab3bd7.jpg",width: 5cm),
    
)
]

=== StudyClaw智能助手页面

在此处与助手对话可自动操作题库，完成自动归档、添加AI解析、搜索错题等功能，方便智能。
对话记录自动保存到本地。
#align(center)[
#grid(
    column-gutter: 0.5cm,
    columns: 2,
    image("/assets/62501dd0d9d4723c846b5026e529d476.jpg",width: 5cm),
    image("/assets/44ff0a83789efa9dd03f982fa850de3a.jpg",width: 5cm)
    
)
]
=	系统可能的扩展

本系统在当前版本（v1.5）基础上，存在以下可扩展方向：

==	NFC 分享扩展

当前 NFC 分享只在发送端写入会话 ID 到 NFC 标签，完整数据通过 BLE 传输。未来可支持：
- 直接通过 NFC 标签携带完整的题目 JSON 数据（利用 NDEF 记录的大容量 NdefMessage），在无蓝牙的简化场景下完成纯 NFC 传输。
- 批量 NFC 分享，一次性传输多道题目。
- 分享加密，在 `SharePayload` 中增加签名字段，使用非对称加密确保传输数据不被篡改。

==	云端功能增强

当前后端为 Flask + SQLite 的单体极简实现，可增强：
- 图片云存储：将错题图片上传至云端对象存储（如阿里云 OSS），实现图片跨设备同步。
- 更安全的认证：当前 Token 格式为 `token_{user_id}` 的明文拼接，可升级为 JWT（JSON Web Token）标准，加入过期时间和签名校验。
- HTTPS 部署与 Nginx 反向代理。
- 使用更成熟的数据库（如 PostgreSQL）替代 SQLite 以支持更高并发。

==	AI 能力扩展

- 接入更多 AI 模型（如 OpenAI API、Google Gemini），允许用户切换模型。
- AI 学习报告：基于用户的错题数据生成学习分析报告，识别薄弱知识点。
- 智能推荐：根据错题模式和难度分布，推荐针对性练习题目。

==	社交功能

- 用户间错题共享社区。
- 电子留言板或评论功能，每道错题下的 AI 分析可开放讨论。
- 学习小组：多人共享题库，协同整理错题。

==	自动化测试

当前测试文件为项目模板生成的示例代码（`assertEquals(4, 2 + 2)`），实际业务逻辑尚未进行测试覆盖。可补充：
- `QuestionRepository` 的同步逻辑单元测试（Mock DAO 和 API）。
- `NfcViewModel` 状态机流程单元测试。
- Compose UI 测试覆盖核心页面的用户交互。

==	平台扩展

- iOS 客户端（使用 SwiftUI + CoreNFC + CoreBluetooth）。
- Windows / Web 端（使用 Kotlin Multiplatform 或 Flutter 进行跨平台复用）。


=	总结体会

通过这次移动互联网技术及应用课程的大作业，我独立设计并实现了一个完整的智能错题本系统。从技术选型、架构设计到代码实现，整个过程让我对移动互联网技术栈有了系统性的理解和实践。

在客户端开发方面，我深刻体会到了 Kotlin + Jetpack Compose 在 Android 开发中的生产力优势。Compose 声明式 UI 使得界面代码更加简洁和可组合。MVVM + Clean Architecture 的分层架构带来了良好的关注点分离：UI 层只关心界面展示，ViewModel 通过 StateFlow 管理状态，数据层通过 Repository 模式屏蔽了 Room、Retrofit 和 AI 调用三个异构数据源之间的差异。Room 数据库的版本迁移和索引优化也让我学习到了移动端本地存储的最佳实践。

服务端虽然使用了较简单的 Flask + SQLite 方案，但双向增量同步的设计充满工程挑战：版本冲突的"最后写入胜出"策略、游标分页的正确实现、墓碑记录的定期清理等，每一个细节都直接影响用户体验的正确性和流畅性。

NFC + BLE 的组合分享是系统技术含量最高的功能。NFC Forum 的 NDEF 规范定义了精确的字节级帧结构（状态字节 + 语言编码 + 文本负载），BLE GATT 协议定义了设备间服务的发现和特征值读写交互。将这两者结合起来，实现从用户"碰一下"的物理交互到数据完整入库的端到端流程，再辅以 ACK 回执确认，构成了一个完整的近场 P2P 传输方案。

AI 集成是本系统区别于传统笔记应用的核心竞争力。阿里云 DashScope 的大模型能力通过 SSE 流式输出提供了接近 ChatGPT 的实时对话体验，而 FocusScreen 中的工具调用协议更是让 AI 从单纯的聊天机器人进化为能够直接操作本地数据库的智能助手，这种 AI Agent 的设计思路在当前业界也属于前沿方向。

在整个开发过程中，我也遇到并解决了若干具有代表性的工程问题：如何正确处理 Android 12+ BLE 权限的 `neverForLocation` 标志；如何在 Compose Navigation 中搭建嵌套导航图以支持底部导航栏和页面路由的共存；如何使用协程 + Flow 实现从 `@Streaming` Retrofit 接口到 UI 实时渲染的完整数据流管道；如何设计游标分页使得同一时间戳的数据不会被遗漏。

这次大作业不仅让我掌握了具体的移动互联网开发技术，更重要的是建立了对移动应用系统设计的整体认识。从客户端分层架构到后端 RESTful API 设计，从本地数据库到云同步策略，从近场通信集成到 AI 大模型调用，这些经验将成为我未来进行更复杂的移动互联网系统开发的重要基础。
