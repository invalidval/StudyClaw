# 智学伴侣 Android 项目模块与目录结构

## 主要模块
- **数据层（data）**：负责本地数据库（Room）、数据访问对象（DAO）、数据仓库（Repository）等。
- **领域层（domain）**：定义核心业务模型（如 Question）、用例（UseCases），实现业务逻辑解耦。
- **网络层（network）**：封装 AI、OCR 等外部 API 的 Retrofit 接口。
- **依赖注入层（di）**：Hilt 依赖注入配置，集中管理单例、网络、数据库等依赖。
- **界面层（ui）**：按功能模块分包（如 question、review、nfc、focus），每个模块包含 ViewModel、Screen、组件等。
- **工具层（utils）**：二维码、NFC、权限等通用工具类。
- **主入口与导航（MainActivity, NavGraph）**：应用启动入口和全局导航。

## 推荐目录结构
- app/
  - src/
    - main/
      - java/com/zlearn/
        - data/
          - database/
            - QuestionEntity.kt
            - QuestionDao.kt
            - AppDatabase.kt
          - repository/
            - QuestionRepository.kt
        - domain/
          - model/
            - Question.kt
          - usecase/
            - QuestionUseCases.kt
        - network/
          - AiApiService.kt
          - OcrApiService.kt
        - di/
          - AppModule.kt
          - NetworkModule.kt
        - ui/
          - question/
            - viewmodel/
              - QuestionViewModel.kt
            - screens/
              - QuestionListScreen.kt
              - QuestionDetailScreen.kt
            - components/
              - QuestionCard.kt
          - review/
            - viewmodel/
              - ReviewViewModel.kt
            - screens/
              - ReviewScreen.kt
          - nfc/
            - viewmodel/
              - NfcViewModel.kt
            - screens/
              - NfcScreen.kt
          - focus/
            - viewmodel/
              - FocusViewModel.kt
            - screens/
              - FocusScreen.kt
          - MainActivity.kt
          - NavGraph.kt
        - utils/
          - QrCodeUtil.kt
          - NfcUtil.kt
          - PermissionUtil.kt
      - res/
        - layout/（如需XML布局）
        - drawable/
        - values/
      - AndroidManifest.xml
  - build.gradle.kts
  - proguard-rules.pro
- build.gradle.kts
- settings.gradle.kts
- gradle/
- local.properties

## 说明
- 每个功能模块（如 question、review、nfc、focus）都包含自己的 ViewModel、界面和组件，便于维护和扩展。
- data、domain、network、di、utils 等分层清晰，符合 MVVM/Clean Architecture 最佳实践。
- 主入口 MainActivity 和全局导航 NavGraph 独立，方便管理应用路由。

如需生成具体文件内容或进一步细化，请告知。
