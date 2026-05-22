# StudyClaw（智学伴侣）项目概述

## 1. 项目简介

**StudyClaw**（包名 `com.zlearn.v5`）是一款基于 Android + AI 的智能错题本应用。用户通过拍照或相册导入错题图片，App 自动进行 OCR 文字提取，并调用阿里云 DashScope 大模型（Qwen 系列）对题目进行 AI 解析、归类与答疑。支持云端同步、NFC 近场碰一碰分享、二维码分享等跨设备协作能力。

## 2. 核心功能

| 功能模块 | 说明 |
|---------|------|
| **错题录入** | 拍照/相册导入图片 → Google ML Kit 中文 OCR → 自动提取题目文字 |
| **AI 解析** | 调用阿里云 DashScope (Qwen) 进行题目分析、知识点归类、难度评估 |
| **错题管理** | 按学科/归档分类管理，支持增删改查、归档/取消归档、软删除 |
| **AI 对话** | 针对单题进行多轮 AI 对话，支持流式输出（SSE）；全局 AI 助手支持工具调用（增删改查归档） |
| **云同步** | 基于 Flask + SQLite 的后端，支持双向增量同步、游标分页、最后写入胜出冲突解决 |
| **NFC 分享** | NFC 碰一碰唤醒 + BLE 数据传输，实现设备间错题 P2P 分享，支持发送确认回执 |
| **二维码分享** | 通过二维码编码题目信息，另一设备扫码后预览确认加入题库 |
| **用户系统** | 注册/登录，基于 Token 认证（`token_{user_id}`），支持游客模式 |

## 3. 系统架构

### 3.1 总体架构

```
┌──────────────────────────────────────────────────┐
│                   Android 客户端                    │
│  ┌────────────────────────────────────────────┐  │
│  │         UI Layer (Jetpack Compose)          │  │
│  │  QuestionList / Detail / NFC / Focus / QR   │  │
│  ├────────────────────────────────────────────┤  │
│  │          ViewModel + StateFlow              │  │
│  ├────────────────────────────────────────────┤  │
│  │          Domain Layer (UseCases)            │  │
│  ├────────────────────────────────────────────┤  │
│  │  Data Layer (Repository)                     │  │
│  │  ┌──────────┐ ┌──────────┐ ┌─────────────┐ │  │
│  │  │   Room   │ │ Retrofit │ │  AiApiService│ │  │
│  │  │  (本地DB)│ │ (后端API)│ │ (阿里云AI)   │ │  │
│  │  └──────────┘ └──────────┘ └─────────────┘ │  │
│  ├────────────────────────────────────────────┤  │
│  │  Utils: MLKit OCR / NFC / BLE / QR / Codec │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────┘
                       │ HTTP (REST)
                       ▼
┌──────────────────────────────────────────────────┐
│              Backend Server (Flask)                │
│  ┌────────────────────────────────────────────┐  │
│  │     REST API (Flask + Flask-CORS)           │  │
│  │  register / login / sync / heartbeat        │  │
│  ├────────────────────────────────────────────┤  │
│  │         SQLite Database (users.db)          │  │
│  │          users + questions 表               │  │
│  └────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────┘
```

### 3.2 客户端分层架构

- **UI 层（`ui/`）**：Jetpack Compose + Material 3，按功能模块分包（question / nfc / focus / qrcode / review）
- **ViewModel 层**：使用 StateFlow 管理 UI 状态，通过 UseCase 访问数据层
- **Domain 层（`domain/`）**：领域模型（Question, SharePayload）+ 用例（QuestionUseCases）
- **Data 层（`data/`）**：Room 本地数据库 + Retrofit 远程 API + Repository 模式
- **DI 层（`di/`）**：Dagger Hilt 管理依赖注入（AppModule + NetworkModule）
- **Utils 层（`utils/`）**：OCR / NFC / BLE / QR Code / 权限 等通用工具

### 3.3 后端架构

后端位于 `backend/` 目录，是一个极简的 Python Flask 应用：

- **Web 框架**：Flask + Flask-CORS
- **数据库**：SQLite 3（文件型数据库，无需额外部署）
- **认证**：自定义 Token 认证（`token_{user_id}` 格式），密码使用 PBKDF2-SHA256 哈希
- **API 路由**：
  - `POST /api/register` — 用户注册
  - `POST /api/login` — 用户登录
  - `POST /api/questions/sync` — 双向增量同步（游标分页 + 软删除墓碑）
  - `GET /api/questions/<id>` — 获取单题详情
  - `GET /api/heartbeat` — 心跳检测（IP 白名单）
  - `GET /api/check_ip` — IP 白名单校验

## 4. 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **语言** | Kotlin 100% (客户端) / Python (后端) | |
| **UI 框架** | Jetpack Compose + Material 3 | 声明式 UI |
| **架构模式** | MVVM + Clean Architecture | data/domain/ui 三层 |
| **依赖注入** | Dagger Hilt (kapt) | |
| **本地数据库** | Room (kapt) | SQLite 封装 |
| **后端数据库** | SQLite 3 (Python stdlib) | 无需部署独立数据库 |
| **网络请求** | Retrofit 2 + OkHttp + Gson | |
| **后端框架** | Flask + Flask-CORS (Python) | |
| **AI 能力** | 阿里云 DashScope (Qwen 模型) | SSE 流式对话 |
| **OCR** | Google ML Kit Text Recognition (中文) | 设备本地识别 |
| **P2P 分享** | NFC (NDEF) + BLE (GATT Server/Client) | |
| **二维码** | ZXing (`com.google.zxing` + `zxing-android-embedded`) | 生成与扫描 |
| **Markdown 渲染** | mikepenz/markdown-renderer-m3 + noties/Markwon | 支持 LaTeX |
| **序列化** | kotlinx-serialization + org.json | |
| **异步** | Kotlin Coroutines + Flow | |
| **构建** | Gradle Kotlin DSL | JDK 17, Compose Compiler Plugin |
| **最低 Android 版本** | API 24 (Android 7.0) | |
| **目标版本** | API 36 (Android 16) | |

## 5. 目录结构

```
MyApplication/
├── app/                              # Android 客户端
│   ├── build.gradle.kts              # App 构建配置、依赖、BuildConfig
│   ├── proguard-rules.pro            # 混淆规则
│   ├── doc/                          # 项目文档
│   │   ├── PROJECT_OVERVIEW.md       # 本文件 - 项目概述
│   │   ├── PROJECT_STRUCTURE.md      # 模块与目录结构说明
│   │   ├── design.md                 # 设计简述
│   │   ├── NFC_P2P_SHARE_PLAN.md     # NFC 分享实施计划
│   │   ├── QR_SHARE_PLAN.md          # 二维码分享实施计划
│   │   └── typst/                    # Typst 论文/图表
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/zlearn/
│       │   │   ├── MyApplication.kt          # Hilt Application，应用模式管理
│       │   │   ├── MainActivity.kt           # 主 Activity，NFC 读写模式管理
│       │   │   ├── data/
│       │   │   │   ├── database/             # Room 数据库（AppDatabase, QuestionEntity, QuestionDao, UserDao）
│       │   │   │   ├── local/                # 本地存储（AuthSessionStore - SharedPreferences）
│       │   │   │   ├── model/                # 数据模型（User 实体）
│       │   │   │   ├── remote/               # 远程 API 接口与 DTO（ApiService）
│       │   │   │   └── repository/           # 数据仓库（QuestionRepository, UserRepository）
│       │   │   ├── di/
│       │   │   │   ├── AppModule.kt          # Hilt 数据库相关依赖
│       │   │   │   └── NetworkModule.kt      # Hilt 网络相关依赖（Retrofit, OkHttp, AI API）
│       │   │   ├── domain/
│       │   │   │   ├── model/                # 领域模型（Question, SharePayload）
│       │   │   │   └── usecase/              # 用例（QuestionUseCases）
│       │   │   ├── network/
│       │   │   │   ├── AiApiService.kt       # 阿里云 DashScope API（标准 + SSE 流式）
│       │   │   │   └── OcrApiService.kt      # OCR API 接口（骨架）
│       │   │   ├── ocr/
│       │   │   │   └── OcrUtil.kt            # Google ML Kit 中文 OCR 工具
│       │   │   ├── ui/
│       │   │   │   ├── MainActivity.kt       # 旧版 Activity
│       │   │   │   ├── SplashActivity.kt     # 启动页 Activity（LAUNCHER）
│       │   │   │   ├── SplashScreen.kt       # 启动屏 Composable（含 IP 校验、数据加载）
│       │   │   │   ├── MainScreen.kt         # 主页（底部导航：题库/二维码/互传/AI）
│       │   │   │   ├── NavGraph.kt           # 全局导航图
│       │   │   │   ├── theme/                # Compose 主题（Color, Theme, Typography）
│       │   │   │   ├── question/             # 错题管理模块
│       │   │   │   │   ├── screens/          # QuestionListScreem, AddQuestionScreen, QuestionDetailScreen
│       │   │   │   │   ├── components/       # QuestionCard, AiInputBar, FlowingBorderTextField
│       │   │   │   │   └── viewmodel/        # QuestionViewModel
│       │   │   │   ├── nfc/                  # NFC 分享模块
│       │   │   │   │   ├── screens/          # NfcScreen
│       │   │   │   │   └── viewmodel/        # NfcViewModel
│       │   │   │   ├── focus/                # AI 助手/专注模式
│       │   │   │   │   ├── screens/          # FocusScreen（工具调用 AI 对话）
│       │   │   │   │   └── viewmodel/        # FocusViewModel（骨架）
│       │   │   │   ├── qrcode/               # 二维码分享模块
│       │   │   │   │   └── screens/          # QrGenerateScreen, QrScanScreen
│       │   │   │   └── review/               # 待迁移的复习模块
│       │   │   │       ├── screens/          # ReviewScreen（二维码分享中转页）
│       │   │   │       └── viewmodel/        # ReviewViewModel（骨架）
│       │   │   ├── utils/
│       │   │   │   ├── AppMode.kt            # 应用模式枚举（DEV/PRE/REL）
│       │   │   │   ├── BleShareTransport.kt  # BLE GATT 传输（发送端/接收端）
│       │   │   │   ├── NfcUtil.kt            # NFC 状态管理与负载提取
│       │   │   │   ├── NfcShareCodec.kt      # 分享负载编解码
│       │   │   │   ├── PermissionUtil.kt     # BLE 权限判断
│       │   │   │   └── QrCodeUtil.kt         # 二维码生成与解码（ZXing）
│       │   │   └── viewmodel/
│       │   │       └── UserViewModel.kt      # 用户认证 ViewModel
│       │   └── res/                          # 资源文件（drawable, values, xml, mipmap）
│       ├── test/                             # 单元测试（模板代码）
│       └── androidTest/                      # 仪器测试（模板代码）
├── backend/                                 # 后端服务器
│   ├── app.py                               # Flask 主应用（路由、认证、业务逻辑）
│   ├── db.py                                # 数据库连接管理、建表、迁移
│   ├── requirements.txt                     # Python 依赖（Flask, flask-cors）
│   └── users.db                             # SQLite 数据库文件
├── build.gradle.kts                         # 根项目构建配置
├── settings.gradle.kts                      # 项目设置
├── gradle/                                  # Gradle wrapper
└── users.db                                 # SQLite 数据库副本（根目录）
```

## 6. 核心数据模型

### 6.1 客户端（Room 实体）

**QuestionEntity**（错题）：
| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long (PK) | 本地主键 |
| cloudId | Long? | 云端 ID（null 表示未同步） |
| ownerUserId | Int | 所属用户 ID（0=游客） |
| imagePath | String | 图片本地路径 |
| ocrText | String | OCR 提取的文字 |
| aiAnalysis | String | AI 分析结果 |
| summary | String | 题目摘要 |
| subject | String | 学科分类 |
| difficulty | Int | 难度等级 1-5 |
| createTime | Long | 创建时间戳（ms） |
| isArchived | Boolean | 是否已归档 |
| archiveType | String? | 归档类别 |
| deletedAt | Long? | 软删除时间（null=未删除） |
| updatedAt | Long | 更新时间戳（ms） |

### 6.2 后端（SQLite 表）

- **users**：id, username, password（PBKDF2-SHA256 哈希）
- **questions**：id, user_id, image_path, ocr_text, ai_analysis, summary, subject, difficulty, create_time, is_archived, archive_type, deleted_at, updated_at

### 6.3 云同步协议

同步采用双向增量同步 + 游标分页：
- **上传**：携带本地变更的题目列表，服务端基于 `updated_at` 时间戳进行"最后写入胜出"合并
- **下载**：通过游标 `(cursorUpdatedAt, cursorId)` 分页拉取服务端更新
- **软删除**：使用 `deleted_at` 字段标记，30 天后清除墓碑记录
- **去重**：客户端使用 `cloudId` 索引防止重复导入

## 7. 分享机制

### 7.1 NFC + BLE 分享

- **发送端**：题目详情页 → 组装 `SharePayload` → NFC 写标签/BLE 广播（GATT Server）
- **接收端**：NFC 感应唤醒 → BLE 扫描连接（GATT Client）→ 读取数据 → 解析入库 → 发送 ACK 回执
- **传输模型**：`NfcShareCodec` 负责 `SharePayload` ↔ JSON 编解码
- **传输载体**：`BleShareTransport` 实现完整的 BLE GATT Server/Client 双向通信

### 7.2 二维码分享

- **生成**：题目详情页 → `QrCodeUtil.encodeToBitmap()` → 生成二维码图片
- **扫描**：相机扫码（zxing-android-embedded）→ 解码 JSON → 预览 → 用户确认入库
- **编码格式**：复用 `SharePayload`/`ShareItem` 数据模型，与 NFC 分享协议统一

## 8. 应用模式（AppMode）

| 模式 | 常量 | 行为 |
|------|------|------|
| DEV | 开发模式 | 无限制，跳过所有校验 |
| PRE | 预发布模式 | 启动时校验 IP 白名单，每 3 秒发心跳，失败则杀进程 |
| REL | 发布模式 | 使用正式 API 域名，正常功能 |

## 9. 已实现 vs 待实现

### 已实现
- [x] 错题 OCR 录入与列表管理
- [x] AI 解析与多轮对话（SSE 流式输出）
- [x] 全局 AI 助手 FocusScreen（工具调用：增删改查归档）
- [x] 云同步（双向增量同步）
- [x] 用户注册/登录/游客模式
- [x] NFC+BLE P2P 分享
- [x] 二维码生成与扫描
- [x] 题目归档与筛选
- [x] 自定义主题（EyeCareNote / BlueNote / GreenNote）
- [x] 流式边框输入框动画
- [x] Markdown + LaTeX 渲染

### 待完成/优化
- [ ] ReviewViewModel / FocusViewModel 业务逻辑补全（目前为骨架）
- [ ] 批量 NFC 分享（Phase 2）
- [ ] 图片原图 P2P 传输
- [ ] 分享加密与会话有效期
- [ ] 后端 Token 安全增强（JWT 替代当前简单 Token）
- [ ] HTTPS 部署配置
- [ ] 单元测试与仪器测试补全
- [ ] Play Store 或自有渠道发布

## 10. 开发环境

- **IDE**：Android Studio
- **JDK**：17
- **Kotlin**：通过 Compose Compiler Plugin 配置
- **Python**：3.x（后端）
- **后端启动**：`cd backend && python app.py`（监听 0.0.0.0:8080）
- **DEBUG 模式**：`http://10.129.215.233:8080/`（内网后端地址）
