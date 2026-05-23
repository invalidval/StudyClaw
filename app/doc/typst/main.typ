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

==	NFC 近场通信与 HCE 卡模拟

NFC 功能基于 Android 原生 `android.nfc` 包实现。在互传场景中，NFC 承担设备发现阶段的会话身份交换——发送端将随机生成的 `sessionId` 暴露为虚拟 NFC 标签，接收端以读卡器模式靠近后读取该标识符，从而在 BLE 连接建立前完成身份锚定。

发送端通过 HCE（Host-based Card Emulation，基于主机的卡模拟）技术注册了一个 `HostApduService` 子类 `ShareHceService`，使用自定义 AID `F05A4C4541524E`。当接收端的 NFC 读卡器靠近时，Android NFC 固件依据 AID 路由表将 APDU 命令派发至该服务。协议遵循 ISO 7816-4 标准——接收端首先发送 SELECT 命令（CLA=0x00, INS=0xA4, P1=0x04）选定 AID，收到状态字 9000 确认后，再发送 READ BINARY 命令（00 B0 00 00 00）读取 `sessionId` 的 UTF-8 字节。HCE 服务仅在发送端已启动分享且 `sessionData` 非空时响应有效数据，否则返回 6A82（文件未找到），系统即回退至钱包等其他 HCE 服务。AID 过滤器在 `aid_filter.xml` 中以 `category="other"` 注册，避免与支付类应用的 `category="payment"` 产生冲突。

接收端以 NF C Reader Mode 持续监听，启用 `FLAG_READER_NFC_A`、`FLAG_READER_NFC_B`、`FLAG_READER_NFC_F`、`FLAG_READER_NFC_V`、`FLAG_READER_NFC_BARCODE` 以及 `FLAG_READER_SKIP_NDEF_CHECK` 共六种标志位，确保各类 NFC 标签和 HCE 服务均能被检测到。在 Reader Mode 回调中，优先通过 `IsoDep.get(tag)` 尝试 HCE 通信路径，若失败则降级读取 NDEF 消息或 Tag ID。此外，发送端在处于分享状态时通过 `NfcAdapter.enableForegroundDispatch()` 拦截本机收到的所有 NFC Intent，将其路由至自身 Activity 而非第三方应用，从而避免交通卡或门禁卡 App 被意外唤起。

系统在 `AndroidManifest.xml` 中声明了 NFC 权限以及 NfcA、Ndef 和 NdefFormatable 三种技术筛选器，还注册了 `TECH_DISCOVERED` Intent Filter 用于处理其他 NFC 标签的被动唤醒。

```kotlin
// HCE APDU 处理 (ShareHceService.processCommandApdu)
return when {
    cla == 0x00 && ins == 0xA4 && p1 == 0x04 -> STATUS_SUCCESS // 9000
    else -> sessionData + STATUS_SUCCESS                        // data + 9000
}
```

NDEF RTD_TEXT 记录的解析在降级路径中仍被使用，其帧结构遵循 NFC Forum 规范：第 1 字节为状态字节，低 6 位表示语言编码长度，后续为语言编码（如 "en"），剩余部分为 UTF-8 文本内容。

```kotlin
val status = payload[0].toInt()
val languageLength = status and 0x3F
val textStart = 1 + languageLength
payload.copyOfRange(textStart, payload.size).toString(Charsets.UTF_8)
```

==	BLE 蓝牙低功耗传输

设备间 P2P 数据传输通过 BLE GATT 协议实现，`BleShareTransport` 单例同时承担 GATT Server（发送端）和 GATT Client（接收端）两个角色。GATT 服务使用两个特征值，辅以一个 CCCD 描述符：数据通道采用 NOTIFY 特征值（UUID `87654321-4321-4321-4321-cba987654321`），发送端在收到接收端的 CCCD 订阅请求后，通过 `notifyCharacteristicChanged()` 逐片推送 JSON 负载，每片上限 500 字节且使用 Indication 模式（`confirm=true`）确保 ATT 层确认送达；回执通道采用 WRITE 特征值（UUID `11111111-2222-3333-4444-555555555555`），接收端导入完成后写入 `"ACK:{sessionId}"` 通知发送端。CCCD 描述符使用标准 UUID `00002902-0000-1000-8000-00805f9b34fb`。

接收端连接首先请求 MTU 为 517 字节以提高传输效率。双方均使用 `ADVERTISE_MODE_LOW_LATENCY` 和 `SCAN_MODE_LOW_LATENCY` 确保发现速度。发送端在 CCCD 写入事件后等待 300 毫秒给连接参数协商窗口，随后按 40 毫秒间隔逐片推送数据。首片在数据前附加 4 字节大端总长度，接收端据此判断何时达到完整数据量后拼接并解码。

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

==	NFC + BLE P2P 分享

用户可将错题通过 NFC + BLE 组合方式在设备间 P2P 分享，系统提供两种发送模式：NFC 碰触模式和广播模式。在 NFC 模式中发送端启用 HCE 卡模拟将 `sessionId` 暴露为虚拟 NFC 标签，接收端靠近时 Reader Mode 通过 IsoDep APDU 读取标识符，从而建立双向的身份锚定。广播模式则完全不依赖 NFC——发送端在 BLE Scan Response 中以厂商自定义数据（manufacturer-specific data，厂商 ID `0xFFFD`）携带 `sessionId` 的 16 字节原始 UUID，接收端扫描到广播后从 scan record 解码出会话标识。

两种模式的后续流程完全一致。接收端获得 `sessionId` 后启动 BLE 连接，订阅 GATT 服务的 NOTIFY 特征值，发送端收到 CCCD 订阅后通过 `notifyCharacteristicChanged` 逐片推送 JSON payload，接收端累积拼接后被解码为 `SharePayload`。系统将 BLE 数据中的 `sessionId` 与发现阶段获取的标识符做精确比对，只有二者匹配才执行导入，不匹配则拒绝并展示错误信息。导入完成后接收端通过 WRITE 特征值回传 `"ACK:{sessionId}"` 确认，发送端收到即清理 HCE 和 BLE 广播，UI 更新为发送成功。发送端的分享状态由 `SenderShareState` 管理完整生命周期：Idle → WaitingAck → Completed 或 Error，接收端 UI 状态机覆盖 Idle、Detected、BroadcastDiscovering、Importing、Result 和 Error 六个状态。

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
  [`utils/`], [通用工具：`AppMode`（DEV/PRE/REL）、`BleShareTransport`（BLE GATT NOTIFY 传输）、`NfcUtil`（NFC 状态管理）、`NfcShareCodec`（分享负载编解码）、`ShareHceService`（HCE 主机卡模拟）、`PermissionUtil`（BLE 权限）、`QrCodeUtil`（ZXing QR 编解码）],
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

NFC + BLE P2P 分享是整个系统技术含量最高的功能之一。以下从设备发现、连接建立、数据传输、身份校验和确认回执五个阶段详细阐述其实现。

NFC 碰触模式的完整交互流程如 @fig-intercom-seq-nfc 所示，广播模式的完整交替流程如 @fig-intercom-seq-broadcast 所示，两者的 NOTIFY 推送、校验和 ACK 阶段完全一致，仅在发现阶段不同。

#figure(
  caption: [NFC 碰触模式完整交互流程],
  image("/assets/intercom-sequence-nfc.png", width: 70%)
) <fig-intercom-seq-nfc>

===	设备发现阶段

发送端在题目详情页点击「分享」时，弹出模式选择对话框，提供两种发现方式。在 NFC 碰触模式中，系统继承 Android 的 `HostApduService` 实现了一个 HCE 服务。该服务注册在 AID `F05A4C4541524E` 下，类别设为 `"other"` 以避开支付类应用的路由优先。当接收端以 Reader Mode 靠近时，主动发送 SELECT APDU 命令选定该 AID，HCE 服务返回状态字 9000 确认；随后接收端发送 READ BINARY APDU，HCE 服务将当前 `sessionId` 的 UTF-8 字节连同 9000 一并返回。读卡器模式启用全部五种 NFC 技术类型以及 `FLAG_READER_SKIP_NDEF_CHECK`，在回调中优先以 `IsoDep.get(tag)` 尝试 HCE 路径，失败后降级读取 NDEF 或 Tag ID。

在广播模式中，发送端不启用 HCE，而是在 BLE Scan Response 中嵌入 16 字节厂商自定义数据（类型 `0xFF`、厂商 ID `0xFFFD`、负载为 `sessionId` 原始 UUID）。接收端通过 `startBroadcastScan()` 扫描所有广播 `SERVICE_UUID` 的设备，从 `scanRecord.getManufacturerSpecificData(0xFFFD)` 中解码出会话标识，以列表形式展示设备名称和就绪状态供用户手动选择。广播模式的完整流程如 @fig-intercom-seq-broadcast 所示。

#figure(
  caption: [广播模式完整交互流程],
  image("/assets/intercom-sequence-broadcast.png", width: 60%)
) <fig-intercom-seq-broadcast>

GATT 服务的创建代码如下，包含 NOTIFY 特征值及其 CCCD 描述符，以及 WRITE 特征值：

```kotlin
val service = BluetoothGattService(SERVICE_UUID, SERVICE_TYPE_PRIMARY)
val notifyChar = BluetoothGattCharacteristic(
    CHAR_NOTIFY_UUID, PROPERTY_NOTIFY, 0)
val cccd = BluetoothGattDescriptor(
    CCCD_UUID, PERMISSION_READ or PERMISSION_WRITE)
notifyChar.addDescriptor(cccd)
service.addCharacteristic(notifyChar)
val ackChar = BluetoothGattCharacteristic(
    ACK_CHARACTERISTIC_UUID, PROPERTY_WRITE or PROPERTY_WRITE_NO_RESPONSE,
    PERMISSION_WRITE)
service.addCharacteristic(ackChar)
gattServer?.addService(service)
```

===	连接建立与通知订阅

接收端无论通过 NFC 还是广播模式获得 `sessionId` 后，均执行相同的 BLE 连接流程。连接使用 `connectGatt()` 并指定 `autoConnect=false`，连接后立即请求 `CONNECTION_PRIORITY_HIGH` 和 MTU 517。MTU 协商完成后调用 `discoverServices()` 发现 GATT 服务结构，找到 NOTIFY 特征值后调用 `setCharacteristicNotification(notifyChar, true)` 注册应用层回调，然后将 `ENABLE_NOTIFICATION_VALUE`（`[0x01, 0x00]`）写入 CCCD 描述符以启用链路层通知。发送端的 GATT Server 在 `onDescriptorWriteRequest` 回调中检测到 CCCD 写入，延迟 300 毫秒等待连接参数协商稳定，随后开始推送数据。

===	数据传输与分片协议

发送端通过 `notifyCharacteristicChanged(device, characteristic, confirm=true)` 以 Indication 模式推送数据。Indication 区别于普通 Notification 之处在于它要求 ATT 层确认，这对部分手机 BLE 芯片的固件可靠性至关重要——实测中发现部分 BLE 芯片对 Notification 只在队列中接受但未实际发射，切换为 Indication 后问题消除。分片协议的首片格式为 `[4 字节 BigEndian 总长度] + [数据 bytes 0..N]`，后续各片仅含数据本身。每片上限 500 字节，片间间隔 40 毫秒。接收端在 `onCharacteristicChanged` 中累积数据，当累积量达到首片声明的总长度后，将全部字节拼接为 UTF-8 字符串，交由上层解码。

NOTIFY 分片的首片前 4 字节以大端序声明数据总长度，接收端据此判断何时收集完所有片段。首片和后续片的帧布局如下表所示。每片上限 `min(MTU-3, 500)` = 500 字节，片间间隔 40ms。

#table(
  columns: 2,
  align: (left, left),
  table.header[偏移][内容],
  [首片 Bytes 0—3], [总长度，BigEndian Int32，例如 `0x00 00 04 25` → 1061 bytes],
  [首片 Bytes 4—N], [UTF-8 JSON 正文首段负载],
  [后续片 Bytes 0—N], [UTF-8 JSON 正文续段负载],
)

NFC 模式中 HCE 通信使用 ISO 7816-4 APDU 协议。SELECT 命令和 READ BINARY 命令的帧格式如下。

#align(center)[
#table(
  columns: 7,
  align: center,
  table.header[CLA][INS][P1][P2][Lc][AID][Le],
  [`00`],[`A4`],[`04`],[`00`],[`07`],[`F0 5A 4C`\ `45 41 52 4E`],[`00`],
//   caption: [SELECT APDU 帧（接收端 → HCE），响应 `90 00`],
)
]

#align(center)[
#table(
  columns: 5,
  align: center,
  table.header[CLA][INS][P1][P2][Le],
  [`00`],[`B0`],[`00`],[`00`],[`00`],
//   caption: [READ BINARY APDU 帧（接收端 → HCE），响应 `<sessionId UTF-8 bytes> 90 00`],
)
]

广播模式中 sessionId 通过 BLE Scan Response 的厂商自定义数据字段传递，帧格式如下。

#align(center)[
#table(
  columns: 4,
  align: center,
  table.header[AD Type][Length][Company ID][Data],
  [`0xFF`],[`0x12`],[`0xFFFD`],[sessionId raw UUID (16 bytes)],
//   caption: [Scan Response Manufacturer Specific Data 帧，接收端通过 `scanRecord.getManufacturerSpecificData(0xFFFD)` 解码],
)
]

===	校验与导入

接收端收到完整数据后调用 `NfcShareCodec.decode(data)` 解码 JSON 为 `SharePayload` 对象。该校验步骤是整个协议的安全锚点——系统将解码后的 `payload.sessionId` 与发现阶段获取的会话标识（NFC 模式下为 HCE 返回的字符串，广播模式下为 Scan Response 中解码的 UUID）做精确字符串比对。若 NFC 检测到的 payload 并非合法 UUID 格式（例如因安全元件交通卡干扰而读到了非预期的卡片数据），则接收端 UI 不显示确认导入按钮，改为提示用户更换轻触位置。校验通过后将各条 `ShareItem` 逐一构建为 `QuestionEntity` 并写入 Room 数据库。

确保校验逻辑严格的代码核心如下：

```kotlin
if (payload != null && isValidUuid(current.payload)
    && payload.sessionId == current.payload) {
    // 导入
}
```

===	确认回执与状态管理

导入完成后接收端调用 `BleShareTransport.sendAck()` 将字符串 `"ACK:{sessionId}"` 写入 WRITE 特征值。发送端 GATT Server 的 `onCharacteristicWriteRequest` 检测到 ACK 前缀后触发 `onAckReceived` 回调，发送端 UI 随之切换为"发送完成"状态，并清理 HCE 和 BLE 广播。发送端状态由 `NfcUtil.SenderShareState` 密封接口管理，接收端状态由 `NfcViewModel` 中 `NfcUiState` 状态机驱动，整个导入设置 45 秒超时保护以防止异常情况下的资源泄漏。接收端状态机的完整转换关系如 @fig-intercom-state 所示。

#figure(
  caption: [接收端 UI 状态机 — NfcUiState],
  image("/assets/image-12.png", width: 100%)
) <fig-intercom-state>


===	AndroidManifest 声明

`MainActivity` 注册了 `NDEF_DISCOVERED` 和 `TECH_DISCOVERED` 两个 NFC Intent Filter，并引用 `nfc_tech_filter.xml` 声明支持的标签技术。同时注册了 `ShareHceService` 作为 HCE 主机卡模拟服务。BLE 权限针对 Android 12 及以上版本使用 `BLUETOOTH_ADVERTISE`、`BLUETOOTH_CONNECT` 和 `BLUETOOTH_SCAN` 三个细粒度权限，其中 `BLUETOOTH_SCAN` 标注 `neverForLocation` 以声明不使用 BLE 获取位置信息。

```xml
<service android:name=".utils.ShareHceService" android:exported="true"
    android:permission="android.permission.BIND_NFC_SERVICE">
    <intent-filter>
        <action android:name="android.nfc.cardemulation.action.HOST_APDU_SERVICE" />
    </intent-filter>
    <meta-data android:name="android.nfc.cardemulation.host_apdu_service"
        android:resource="@xml/aid_filter" />
</service>
```

===	设计决策与已知限制

系统经历了多次传输层迭代才定型为当前的 NOTIFY 模式。最初使用单个 READ 特征值承载完整 JSON，但由于 Android native BLE 层对 `characteristic.value` 存在设备相关的属性值上限（约 600 字节），超出部分被静默丢弃，导致 JSON 截断。随后尝试将数据拆分为三个独立的 READ 特征值以绕过单特征值容量限制，但 BLE Long Read 在部分设备上仅支持两次读取（一次 Read Request 加一次 Read Blob Request），且 GATT 缓存机制在多特征值场景下存在 stale cache 问题。最终的 NOTIFY 方案彻底消除了上述限制——数据由发送端主动推送，容量理论上无上限，且避免了 GATT 缓存的复杂性。

对于安全元件（SE）交通卡与 HCE 的硬件路由优先级冲突，这是一个 NFC 控制器层面的硬件行为，Android 不提供 API 覆盖 SE 路由。系统的应对策略是在接收端检测非 UUID 格式的 payload 时拒绝导入并引导用户更换轻触位置，同时以广播模式作为纯 BLE 备选路径。

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

AI 对话通过 Retrofit 接口 `AiApiService` 对接阿里云 DashScope（通义千问）API，核心数据模型及接口定义如下：

```kotlin
data class AliyunChatRequest(val model: String, val input: Input)
data class Input(val messages: List<Message>)
data class Message(val role: String, val content: String)
data class AliyunChatResponse(val code: String?, val output: Output?)
data class Output(val text: String?)

interface AiApiService {
    @Headers("Content-Type: application/json")
    @POST("/api/v1/services/aigc/text-generation/generation")
    suspend fun chat(@Body request: AliyunChatRequest): Response<AliyunChatResponse>

    @Headers("Content-Type: application/json", "X-DashScope-SSE: enable")
    @POST("/api/v1/services/aigc/text-generation/generation")
    @Streaming
    suspend fun chatStream(@Body request: AliyunChatRequest): okhttp3.ResponseBody
}
```

接口提供两种模式：非流式 `chat()` 返回完整 JSON 响应，用于题目摘要生成等非实时场景；流式 `chatStream()` 标注 `@Streaming` 并设置 `X-DashScope-SSE: enable` 请求头，返回原始 `ResponseBody`，用于对话界面实时渲染。

`QuestionViewModel.chatWithAiStream()` 是流式对话的核心，其骨架实现如下：

```kotlin
fun chatWithAiStream(message: String) {
    streamJob?.cancel()                          // 取消上一次流式请求
    streamJob = viewModelScope.launch {
        _isLoading.value = true
        _aiStreamResponse.value = ""

        val request = AliyunChatRequest(
            model = "qwen-plus",
            input = Input(messages = listOf(Message(role = "user", content = message)))
        )
        withContext(Dispatchers.IO) {
            val responseBody = useCases.chatWithAiStream(request)
            val source = responseBody.source()
            val buffer = StringBuilder()

            while (isActive) {
                val line = source.readUtf8Line() ?: break         // 逐行读取 SSE
                if (!line.startsWith("data:")) continue
                val jsonStr = line.removePrefix("data:").trim()
                if (jsonStr == "[DONE]") break                    // 流结束标志

                val content = JSONObject(jsonStr)
                    .optJSONObject("output")?.optString("text") ?: ""
                if (content.isBlank()) continue

                // diff 比较：仅追加增量，避免重复渲染
                val diff = if (content.startsWith(buffer.toString()))
                    content.removePrefix(buffer.toString()) else content
                if (diff.isNotEmpty()) {
                    buffer.append(diff)
                    withContext(Dispatchers.Main) {
                        _aiStreamResponse.value = buffer.toString()   // 更新 UI
                    }
                }
            }
        }
        _isLoading.value = false
    }
}
```

该方法在 `viewModelScope` 中启动协程，构造请求后在 `Dispatchers.IO` 上通过 `source.readUtf8Line()` 逐行读取 SSE 帧。核心逻辑：过滤 `data:` 前缀行，遇 `[DONE]` 停止；解析 `output.text` 字段后用 diff 比较去重——若新内容以旧 buffer 为前缀则截去已输出部分；最终切到 `Dispatchers.Main` 更新 `_aiStreamResponse`（`MutableStateFlow<String>`），UI 通过 `collectAsState()` 实时渲染。错误处理覆盖 `SocketTimeoutException` 和 `IOException`，`streamJob` 引用支持外部取消流式请求。

在 `FocusScreen` 中，流式响应的展示增加了一层逐字平滑动画：

```kotlin
LaunchedEffect(aiStreamResponse, isLoading) {
    if (!isLoading) { smoothStreamResponse = ""; return@LaunchedEffect }
    if (aiStreamResponse.length <= smoothStreamResponse.length) {
        smoothStreamResponse = aiStreamResponse; return@LaunchedEffect
    }
    val append = aiStreamResponse.substring(smoothStreamResponse.length)
    var offset = 0
    while (offset < append.length && isActive) {
        val step = when {
            append.length > 240 -> 12
            append.length > 120 -> 8
            append.length > 60 -> 5
            else -> 3
        }
        val next = (offset + step).coerceAtMost(append.length)
        smoothStreamResponse += append.substring(offset, next)
        offset = next
        delay(14)
    }
}
```

该 `LaunchedEffect` 监听 `aiStreamResponse` 变化，将新增字符以可变步长（3\~12 字符/次）和 14ms 间隔逐步追加到 `smoothStreamResponse`，模拟实时生成效果。

===	FocusScreen 工具调用 AI

`FocusScreen` 是本系统的核心模块，通过自定义工具调用协议使 AI 能够直接操作本地题库数据库。

- *系统提示与对话组装：* 系统通过一份结构化的 `systemPrompt` 定义 AI 行为边界，其核心内容为：

```text
你是 StudyClaw 智能错题本的 AI 助手，只能用中文与用户交流，风格简洁专业。
你的能力：
- 通过输出 #TOOL# { ... } JSON 结构调用错题管理工具，支持 add、delete、
  update、query、archive、unarchive。
- 增删查改需求优先 query 搜索候选，再基于 id 执行后续操作。
- delete/update/archive 必须使用 id 或 ids。没有 id 时先 query 并反问确认。
- 工具调用格式为 #TOOL# { "action":..., ... }，不要输出多余内容。
- 一次回复可输出多个 #TOOL# JSON（按顺序逐条执行）完成批量任务。
- 工具调用后用简洁自然语言总结反馈，不直接输出 JSON。
- add 操作必须提供非空题干；字段缺失或仅为占位词则失败入库。
- update 禁止修改 isArchived/archiveType，归档相关字段只能由 action=archive 处理。
- 回答归档状态问题前必须先 query 获取实际字段（isArchived、archiveType），不可臆测。
- 自动归档/智能分类时先 query 获取目标集合，再逐条按内容生成具体 archiveType，
  禁止整批归到“auto-classified”笼统类型。
```

对话历史以 `ChatMessage(role, content)` 格式存储，通过 `SharedPreferences` 持久化缓存（上限 100 条）。`buildPrompt()` 函数每次发送前将 `systemPrompt` 与对话历史拼接：

```kotlin
fun buildPrompt(nextUserInput: String? = null): String {
    val historyMessages = trimHistoryByRounds(messages, maxHistoryRounds)
    val history = historyMessages.joinToString("\n") {
        when (it.role) {
            "user" -> "用户：" + it.content
            "assistant" -> "助手：" + it.content
            "tool" -> "#TOOL# " + it.content
            else -> it.content
        }
    }
    return if (nextUserInput != null) {
        "$systemPrompt\n$history\n用户：$nextUserInput"
    } else {
        "$systemPrompt\n$history\n请基于最新工具结果继续完成上一轮用户需求；如仍需工具，请继续输出 #TOOL# JSON。"
    }
}
```

历史通过 `trimHistoryByRounds()` 裁剪至最近 10 轮用户对话（`maxHistoryRounds=10`，以 `role="user"` 为轮次边界）。工具类型消息以 `#TOOL#` 前缀标记供 AI 理解。无新用户输入时（自动循环），使用特殊后缀提示 AI 基于工具结果继续。

- *工具协议解析：* AI 在回复中输出 `#TOOL# { "action": "...", ... }` 格式的 JSON 指令块。解析函数通过标记定位与大括号深度匹配提取完整 JSON：

```kotlin
fun extractToolJsonBlocksFromResponse(text: String): List<String> {
    val marker = "#TOOL#"
    val blocks = mutableListOf<String>()
    var searchStart = 0
    while (true) {
        val markerIndex = text.indexOf(marker, searchStart)
        if (markerIndex < 0) break
        val jsonStart = text.indexOf('{', markerIndex + marker.length)
        if (jsonStart < 0) { searchStart = markerIndex + marker.length; continue }
        var depth = 0
        var jsonEnd = -1
        for (i in jsonStart until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth-- ; if (depth == 0) { jsonEnd = i; break } }
            }
        }
        if (jsonEnd < 0) break
        blocks += text.substring(jsonStart, jsonEnd + 1).trim()
        searchStart = jsonEnd + 1
    }
    return blocks
}
```

- *工具执行流程：* 流式响应完成后，若检测到有效的 `#TOOL#` JSON 块，系统按顺序执行每条指令。核心分发逻辑的简化代码如下：

```kotlin
when (action) {
    "add" -> {
        val question = pickText(toolCall, "question", "ocrText", "title", "content")
        if (question.isNullOrBlank()) { /* 返回失败：题干不能为空 */ }
        // 子串匹配去重，无重复则调用 viewModel.addQuestion() 入库
        // 摘要未提供时自动调用 viewModel.generateSummary() 生成（不超过 20 字）
    }
    "query" -> {
        val filtered = when {
            ids.isNotEmpty() -> all.filter { ids.contains(it.id) }
            keyword.isNotBlank() -> all.filter {
                it.ocrText.contains(keyword) || it.summary.contains(keyword)
            }
            else -> all.takeLast(20)
        }
        toolResult = JsonObject(mapOf(
            "result" to JsonPrimitive("success"),
            "rows" to rows,           // 结构化字段数组，供后续工具操作
            "markdown" to JsonPrimitive(markdown)  // 人类可读列表，供 AI 总结
        )).toString()
    }
    "delete" -> /* 按 id 精确软删除，无 id 则返回候选列表要求用户确认 */
    "update" -> /* 解析 field/value 或 fields 对象，禁止修改 isArchived/archiveType */
    "archive" -> {
        when (archiveType) {
            "auto" -> targets.forEach { viewModel.archiveQuestion(it.id, inferArchiveType(it)) }
            "none", "null", "取消归档" -> targets.forEach { viewModel.unarchiveQuestion(it.id) }
            else -> targets.forEach { viewModel.archiveQuestion(it.id, archiveType) }
        }
    }
    "unarchive" -> /* 按 id 取消归档，调用 viewModel.unarchiveQuestion() */
}
```

`inferArchiveType()` 启发式函数基于 subject 字段和 ocrText/summary 内容进行关键词匹配，覆盖六大类（计算机、数学、物理、化学、语文、英语），当内容含"算法/数据结构/编程/代码"等归为计算机，含"函数/方程/几何"归为数学，以此类推；无法匹配则归为"待整理"。

- *自动工具循环：* 所有工具块执行完毕后，系统自动进入下一轮 AI 对话：

```kotlin
if (autoToolDepth >= maxAutoToolDepth) {
    appendMessage(ChatMessage(role = "assistant",
        content = "工具调用次数已达上限，请确认后继续。"))
    autoToolDepth = 0
    return@launch
}
autoToolDepth += 1
viewModel.chatWithAiStream(buildPrompt())
// buildPrompt() 无参数时将拼接："请基于最新工具结果继续完成上一轮用户需求；
// 如仍需工具，请继续输出 #TOOL# JSON。"
```

每次工具执行后 `autoToolDepth` 递增（同一轮回复中的多个工具块只计一次），上限 `maxAutoToolDepth=5`。AI 根据结构化的工具返回结果继续决策，若仍需更多操作则再次输出 `#TOOL#` 指令。这个循环形成了"用户需求 $arrow$ AI 决策 $arrow$ 工具执行 $arrow$ 结果反馈 $arrow$ AI 再决策"的完整闭环；达到 5 轮上限后以自然语言提示用户确认，防止无限循环。

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
=== 互传页面

接收端可同时使用 NFC 碰触或广播搜索两种方式（左图），发送端显示等待状态（右图）。

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

==	互传功能扩展

当前互传功能采用 HCE + BLE NOTIFY 架构完成 NFC 模式设备发现和全速数据传输，同时以 BLE Scan Response 携带 sessionId 的广播模式作为纯 BLE 备选方案。未来可支持分享加密，在 `SharePayload` 中增加签名字段，使用非对称加密确保传输数据不被篡改；也可考虑 Wi-Fi Direct 通道作为更大数据量场景下的备选传输路径。

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

NFC + BLE 的组合分享是系统技术含量最高的功能。在 NFC 模式下，HCE 卡模拟技术在 ISO 7816-4 APDU 层面实现会话身份交换，BLE GATT NOTIFY 推送 + Indication 确认机制提供了可靠的大数据通道，sessionId 的端到端校验串起了物理碰触与数据导入之间的信任链。经历了从 READ 特征值到多特征值分片再到 NOTIFY 推送的三次传输层迭代，并解决了部分手机 BLE 芯片通知静默丢失、SE 交通卡硬件路由抢占等一系列平台特有的工程问题，最终形成了一个稳定且支持双模式备选的设备间 P2P 传输方案。

AI 集成是本系统区别于传统笔记应用的核心竞争力。阿里云 DashScope 的大模型能力通过 SSE 流式输出提供了接近 ChatGPT 的实时对话体验，而 FocusScreen 中的工具调用协议更是让 AI 从单纯的聊天机器人进化为能够直接操作本地数据库的智能助手，这种 AI Agent 的设计思路在当前业界也属于前沿方向。

在整个开发过程中，我也遇到并解决了若干具有代表性的工程问题：如何正确处理 Android 12+ BLE 权限的 `neverForLocation` 标志；如何在 Compose Navigation 中搭建嵌套导航图以支持底部导航栏和页面路由的共存；如何使用协程 + Flow 实现从 `@Streaming` Retrofit 接口到 UI 实时渲染的完整数据流管道；如何设计游标分页使得同一时间戳的数据不会被遗漏。

这次大作业不仅让我掌握了具体的移动互联网开发技术，更重要的是建立了对移动应用系统设计的整体认识。从客户端分层架构到后端 RESTful API 设计，从本地数据库到云同步策略，从近场通信集成到 AI 大模型调用，这些经验将成为我未来进行更复杂的移动互联网系统开发的重要基础。
