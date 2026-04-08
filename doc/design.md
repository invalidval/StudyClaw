### 项目名称：智学伴侣

#### 项目概述
智学伴侣是一款面向学生的Android智能学习辅助应用。它旨在打通纸质试卷与数字智能之间的壁垒，通过集成AI大模型、OCR技术、NFC近场通信和二维码技术，实现“拍照解题-智能整理-物理标记-便捷复习”的完整闭环。用户可以通过拍照获取AI解析，生成专属二维码贴在纸质试卷上，或通过NFC标签分享笔记，实现高效的混合式学习体验。

#### 技术栈
- **开发语言**：Kotlin
- **架构模式**：MVVM
- **UI框架**：Jetpack Compose (推荐) 或 XML
- **依赖注入**：Hilt
- **网络请求**：Retrofit + OkHttp
- **图片加载**：Glide 或 Coil
- **本地数据库**：Room
- **异步处理**：Coroutines + Flow
- **AI服务**：讯飞星火/文心一言 API (RESTful)
- **硬件交互**：Android NFC API, CameraX, ZXing (二维码)

#### 核心功能模块
**AI解题与错题录入**
- **拍照/相册选择**：使用CameraX或系统Intent调用相机拍摄题目，或从相册选择图片。
- **OCR识别**：调用OCR SDK（如ML Kit）提取图片中的文字。
- **AI解析生成**：将识别出的文字发送至大模型API，获取结构化的解析内容（考点、步骤、答案）。
- **错题保存**：将题目原图路径、AI解析文本、科目、难度等级存入本地Room数据库。

**智能错题本管理**
- **列表展示**：以卡片形式展示错题，支持按科目、时间筛选。
- **详情查看**：点击卡片进入详情页，查看题目原图和AI解析。
- **编辑与备注**：支持用户手动修改AI解析，或添加语音/文字备注。
- **删除与归档**：支持删除错题或将其移入“已掌握”归档区。

**二维码生成与深度链接**
- **二维码生成**：基于错题ID生成唯一二维码图片（使用ZXing库）。
- **深度链接配置**：配置AndroidManifest，支持`studyapp://review?id={questionId}`协议。
- **扫码跳转**：集成扫码功能，扫描应用内生成的二维码后，直接跳转至对应错题的详情页面。
- **导出打印**：支持将二维码图片保存至相册，方便用户打印并粘贴在纸质试卷上。

**NFC碰一碰分享**
- **NFC写入**：将错题ID或简短笔记内容写入NDEF格式的NFC标签。
- **NFC读取**：当手机触碰NFC标签时，读取ID并自动跳转至对应错题详情页。
- **状态检测**：实时检测NFC开关状态，引导用户开启NFC功能。

**辅助功能**
- **专注模式**：基于加速度传感器检测手机移动，配合白噪音播放器（MediaPlayer），帮助用户保持专注。

#### 数据结构设计
**错题实体**
```kotlin
@Entity(tableName = "questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String,      // 题目图片本地路径
    val ocrText: String,        // OCR识别的原始文本
    val aiAnalysis: String,     // AI生成的解析内容
    val subject: String,        // 科目：Math, English, etc.
    val difficulty: Int,        // 难度：1-5
    val createTime: Long,       // 创建时间戳
    val isArchived: Boolean     // 是否已归档
)
```

#### 开发注意事项
- **权限管理**：需在Manifest中声明`CAMERA`, `NFC`, `READ_EXTERNAL_STORAGE`, `INTERNET`等权限，并在运行时动态申请。
- **API密钥管理**：AI接口密钥不应硬编码在代码中，建议使用`local.properties`或`BuildConfig`进行管理。
- **后台任务**：OCR和AI请求属于耗时操作，必须在协程的`Dispatchers.IO`中执行，避免阻塞主线程。
- **NFC兼容性**：需在应用启动时检查设备是否支持NFC硬件。

#### 预期交付成果
- 完整的Android项目源码。
- 可运行的APK安装包。
- 项目README文档，包含功能截图和API配置说明。
  你觉得这份文档的技术细节（如数据库字段、架构模式）符合你的预期吗？需要我帮你做进一步优化吗？
  比如：
1. **补充UI设计描述**：需要我增加关于界面布局（如卡片样式、配色方案）的详细描述吗？
2. **细化API交互**：需要我补充具体的AI接口请求示例（JSON格式）吗？
3. **增加测试要求**：需要加入单元测试或UI测试的具体指标吗？
   随时告诉我，我来帮你完善！

