# NFC P2P 错题分享实施计划

## 目标与范围
- 目标：用户在两台手机上通过 NFC 轻触完成“错题分享”。
- 范围：支持单题分享、批量分享、导入确认、去重与失败重试。
- 非目标（首期）：跨平台（iOS）、后台静默导入、超大附件原图直传。

## 先决结论（必须先对齐）
- Android Beam 已废弃，不能依赖旧的系统级点对点推送。
- 建议采用 **NFC 唤醒 + 会话握手 + 数据通道传输** 的混合方案：
  - NFC 负责唤醒对端 App 与交换短 token。
  - 真正数据通过 BLE/Wi-Fi Direct/HTTPS（同网）传输。
- 这样能满足“通过 NFC 唤醒”的产品体验，同时避免 NFC 载荷过小的问题。

## 数据协议设计（V1）
### 1. 分享对象
- 以 `QuestionEntity` 为基础字段：
  - `id`（仅本地映射，不作为全局主键）
  - `imagePath`（首期可选，默认不传原图）
  - `ocrText`
  - `aiAnalysis`
  - `summary`
  - `subject`
  - `difficulty`
  - `createTime`
  - `isArchived`
  - `archiveType`

### 2. 传输包结构
```json
{
  "schemaVersion": 1,
  "sessionId": "uuid",
  "senderDevice": "device_alias",
  "createdAt": 1710000000000,
  "items": [
    {
      "contentHash": "sha256(ocrText+summary+subject)",
      "ocrText": "...",
      "summary": "...",
      "subject": "...",
      "difficulty": 3,
      "aiAnalysis": "...",
      "isArchived": false,
      "archiveType": ""
    }
  ]
}
```

### 3. 去重策略
- 优先按 `contentHash` 去重。
- 冲突策略：
  - 默认“保留本地，跳过重复”，并展示跳过数量。
  - 用户可选“仍导入为新条目”。

## 分阶段实施

## Phase 0：技术预研（1-2 天）
- 验证目标机型 NFC 能力（NfcA/Ndef/ReaderMode/HCE）。
- 选定数据通道（建议顺序）：
  1. HTTPS（已有后端可用时）
  2. 局域网直连
  3. BLE（作为复杂兜底）
- 输出：技术结论与风险清单。

## Phase 1：最小可用（NFC 唤醒 + 单题分享）
### 功能
- 发送端在题目详情点击“分享到附近设备（NFC）”。
- 通过 NFC 传递 `sessionId + deepLink + nonce`。
- 接收端被唤醒到 `NfcScreen`，显示“检测到分享请求”。
- 用户确认后拉取/接收单题数据并导入。

### 代码改动建议
- `app/src/main/AndroidManifest.xml`
  - 增加 NFC feature/permission、intent-filter、tech-filter。
- `app/src/main/java/com/zlearn/MainActivity.kt`
  - 处理 NFC intent（`NfcAdapter.ACTION_TAG_DISCOVERED` 等）。
- `app/src/main/java/com/zlearn/ui/nfc/screens/NfcScreen.kt`
  - 从占位页改为“会话状态 + 导入确认 + 结果反馈”。
- `app/src/main/java/com/zlearn/ui/question/screens/QuestionDetailScreen.kt`
  - 新增“分享”入口。
- 新增
  - `app/src/main/java/com/zlearn/ui/nfc/viewmodel/NfcViewModel.kt`
  - `app/src/main/java/com/zlearn/domain/model/SharePayload.kt`
  - `app/src/main/java/com/zlearn/utils/NfcShareCodec.kt`

### 验收
- 两台 Android 设备：轻触后可唤醒并导入 1 条错题。
- 失败场景有明确提示（NFC 关闭/超时/重复导入）。

## Phase 2：批量分享与导入体验
### 功能
- 在 `QuestionListScreen` 支持多选后批量分享。
- 接收端展示导入预览（条数、学科、重复数量）。
- 用户可勾选“跳过重复 / 全部导入”。

### 验收
- 批量 10-50 条稳定导入。
- 导入耗时、失败重试、取消流程可控。

## Phase 3：增强能力
- 可选分享原图（压缩后、分片传输）。
- 传输加密（会话密钥 + 有效期）。
- 分享历史记录（最近发送/接收）。

## 安全与风控
- 用户必须明确确认导入，禁止静默写库。
- 限制单次最大条目数与载荷大小（防止卡死）。
- 所有输入做 schema 校验，未知字段忽略。
- 记录导入日志（成功/失败/跳过原因）。

## 测试计划
### 单元测试
- `SharePayload` 序列化/反序列化。
- `contentHash` 去重逻辑。
- schemaVersion 兼容与降级。

### 集成测试
- `MainActivity` NFC intent 解析。
- `NfcViewModel` 状态流转（idle -> handshake -> importing -> result）。

### 真机联调
- 品牌覆盖：小米/华为/OPPO/三星（至少 2-3 台）。
- 系统版本：Android 10/12/14。

## 里程碑与交付
- M1（预研结论）
- M2（单题可用）
- M3（批量稳定）
- M4（增强版）

## 风险清单
- 机型 NFC 行为差异大：需要真机回归。
- 大载荷传输不稳定：必须走 NFC+数据通道混合。
- 权限/系统限制导致唤醒不一致：需做清晰失败引导。

## 建议下一步（可直接开工）
1. 先实现 `NfcScreen` 状态机与 `NfcViewModel`。
2. 同步补 `Manifest` 与 `intent-filter`。
3. 在 `QuestionDetailScreen` 做单题分享按钮打通 M2。
