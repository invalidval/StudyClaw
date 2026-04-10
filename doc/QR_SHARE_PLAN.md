# 二维码题目分享实施方案（替代 Review 模块）

## 目标与范围
- 目标：将原 `review/复习` 模块升级为“二维码题目分享”，支持生成二维码、扫码解码、用户确认导入题库。
- 范围（首版）：单题分享、扫码预览、手动确认入库、基础异常提示。
- 非目标（首版）：多题大包压缩、跨端签名验真、扫码历史中心。

## 模块命名与路由调整
- UI 名称：`题目二维码`
- 技术命名建议：`qrcode` 路由、`QrShareScreen`、`QrShareViewModel`
- 替换建议：逐步将 `review` 相关入口与文案迁移为 `qrcode`（保留兼容跳转一版）

## 数据模型（最小可用）
首版复用现有分享协议，避免重复建模。

- 复用：`SharePayload` / `ShareItem` / `NfcShareCodec`
- 二维码 payload 建议字段：
  - `schemaVersion`（兼容用）
  - `sessionId`
  - `senderDevice`
  - `createdAt`
  - `items`（首版仅 1 条）
- 每条题目字段：
  - 必填：`ocrText`
  - 可选：`summary`、`subject`、`difficulty`、`aiAnalysis`、`isArchived`、`archiveType`

## 流程设计

### 1) 题目信息编码
1. 在题目详情页点击“生成二维码”。
2. 将当前 `QuestionEntity` 映射为 `ShareItem`。
3. 组装 `SharePayload`（单题）。
4. 调用 `NfcShareCodec.encode(payload)` 得到字符串。
5. 使用二维码库将字符串渲染成图片展示。

### 2) 扫描并解码
1. 进入扫码页扫描二维码。
2. 读取文本后调用 `NfcShareCodec.decode(raw)`。
3. 校验：`schemaVersion`、`items` 非空、`ocrText` 非空。
4. 解析成功进入预览页；失败提示“二维码无效或版本不支持”。

### 3) 选择是否加入题库
1. 预览页展示：题干摘要、学科、难度、来源。
2. 用户点击“加入题库”时才执行入库。
3. 将 `ShareItem` 映射为 `QuestionEntity`。
4. 调用 `QuestionViewModel.addQuestion(...)`。
5. 成功提示并返回列表；取消则不写库。

## 与现有代码对接点
- 路由与入口：`app/src/main/java/com/zlearn/ui/NavGraph.kt`
- 原模块替换：`app/src/main/java/com/zlearn/ui/review/viewmodel/ReviewViewModel.kt`（后续改名为 `QrShareViewModel`）
- 题目来源：`QuestionDetailScreen`（增加“生成二维码”入口）
- 解码与协议：复用 `NfcShareCodec` 与分享模型
- 入库：`QuestionViewModel.addQuestion` + `loadQuestions`

## 首版迭代步骤（MVP）
1. 先完成路由改名与占位页更名（`review` -> `qrcode`）。
2. 完成“单题生成二维码”页面。
3. 完成“扫码解码 + 校验”页面。
4. 完成“导入预览 + 确认入库”交互。
5. 补齐错误态：二维码无效、字段缺失、解码失败。
6. 联调题目详情页入口与题库刷新。

## 验收标准
- 能从题目详情生成二维码并被另一设备扫码解析。
- 扫码后必须经过用户确认才能写入本地题库。
- 导入成功后在错题列表可见；取消导入不产生数据。
- 异常二维码有明确错误提示，不崩溃。

## 风险与后续优化
- 风险：二维码容量受限，长文本可能导致码密度过高。
- 建议优化：
  - 第二阶段支持多题压缩与分片。
  - 增加重复检测（`ocrText + summary + subject`）提示。
  - 增加签名与来源校验，防止篡改 payload。

