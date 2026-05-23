# 互传功能 —— 架构与协议设计文档

## 概述

互传功能允许两部 Android 设备之间分享错题数据。发送方将一道或多道题目打包为 JSON payload，接收方通过 NFC 或 BLE 扫描发现发送方，获取 payload 后导入本地题库。

传输层核心架构为 **NFC / BLE 扫描用于设备发现 + BLE GATT NOTIFY 用于数据传输**。

## 运行模式

### 发送端

在题目详情页点击「分享」，弹出模式选择对话框：

| 模式 | 路由 | 发现方式 | HCE | Scan Response |
|---|---|---|---|---|
| **NFC 碰触** | `nfc/sender-nfc` | NFC HCE | 启用 | 不携带 sessionId |
| **广播模式** | `nfc/sender-broadcast` | BLE 扫描 | 关闭 | 携带 16 字节 raw UUID |

两种模式都启动相同的 BLE GATT 服务，**仅发现阶段不同**。后续 NOTIFY 推送、sessionId 校验、ACK 回传流程完全一致。

### 接收端

从底部导航栏「互传」进入，同时展示两个入口：

- **NFC 碰触唤醒**：保持手机靠近发送方，NFC 读卡器模式自动检测 HCE 标签
- **广播搜索**：点击「开始扫描」搜索附近 BLE 广播设备，手动选择后连接

接收端检测到 NFC 合法 sessionId 后自动弹出「确认导入」界面；广播模式显示设备列表供用户选择。

## 系统组件

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                             │
│  QuestionDetailScreen ─── 发送入口，模式选择对话框         │
│  NfcScreen ─── 发送/接收状态展示，设备列表                │
│  MainActivity ─── NFC Reader Mode + Foreground Dispatch  │
├─────────────────────────────────────────────────────────┤
│                     业务逻辑层                            │
│  NfcViewModel ─── 状态机，sessionId 校验，数据导入        │
│  NfcUtil ─── Flow 状态管理，NFC/NDEF/IsoDep 标签解析     │
│  NfcShareCodec ─── JSON 编解码                           │
├─────────────────────────────────────────────────────────┤
│                      传输层                               │
│  BleShareTransport (singleton)                           │
│  ├── 发送端: GATT Server (NOTIFY + ACK)                 │
│  └── 接收端: GATT Client (扫描 + 订阅 + 累积)           │
│  ShareHceService ─── HCE HostApduService                 │
├─────────────────────────────────────────────────────────┤
│                      数据模型                             │
│  SharePayload / ShareItem                                │
└─────────────────────────────────────────────────────────┘
```

## GATT 服务定义

发送端 BLE GATT 服务包含两个特征值：

| 属性 | UUID | 类型 | 用途 |
|---|---|---|---|
| Service | `12345678-1234-1234-1234-123456789abc` | PRIMARY | 服务容器 |
| Characteristic 0 | `87654321-4321-4321-4321-cba987654321` | NOTIFY | 数据推送，带 CCCD 描述符 |
| Characteristic 1 | `11111111-2222-3333-4444-555555555555` | WRITE | ACK 回传 |

CCCD 描述符使用标准 UUID `00002902-0000-1000-8000-00805f9b34fb`。

## 协议详细设计

### 一、设备发现阶段

#### 1.1 NFC 模式

**发送端**通过 `ShareHceService`（`HostApduService`）将 `sessionId` 作为 NFC 标签内容暴露：

```
SELECT APDU (接收端发送):
  00 A4 04 00 07 F0 5A 4C 45 41 52 4E 00
  响应: 90 00 (AID 确认)

READ BINARY APDU (接收端发送):
  00 B0 00 00 00
  响应: <sessionId UTF-8 bytes> 90 00
```

AID `F05A4C4541524E` 在 `aid_filter.xml` 中注册，`category="other"` 避免与支付类应用冲突。IsoDep 超时 3000ms。

接收端 `MainActivity` 以 Reader Mode 持续监听 NFC 标签，优先尝试 `IsoDep.get(tag)` 读取 HCE 数据，失败则降级读取 NDEF 或 Tag ID。

#### 1.2 广播模式

**发送端**在 BLE 广播的 Scan Response 中携带 sessionId 的原始 16 字节 UUID（manufacturer-specific data，厂商 ID `0xFFFD`）。

**接收端**通过 `startBroadcastScan()` 扫描所有广播 `SERVICE_UUID` 的设备，从 scan record 的 manufacturer data 中解码 sessionId。发现设备后以列表展示设备名称和就绪状态。

此模式不启用 HCE，仅依赖 BLE 完成全部通信。

广播数据布局：

```
Advertising Data:
  ┌─ Flags: 3 bytes
  └─ Complete 128-bit Service UUID: 18 bytes (SERVICE_UUID)

Scan Response (仅广播模式):
  └─ Manufacturer Specific Data:
       ┌─ Type: 0xFF (1 byte)
       ├─ Length: variable
       ├─ Company ID: 0xFFFD (2 bytes)
       └─ Data: sessionId raw UUID (16 bytes)
```

### 二、连接建立阶段

接收端无论通过 NFC 还是广播模式获得 `sessionId` 后，均执行相同的 BLE 连接流程：

1. **连接**：`BluetoothDevice.connectGatt(ctx, false, callback, TRANSPORT_LE)`
2. **MTU 协商**：`gatt.requestMtu(517)`
3. **服务发现**：`gatt.discoverServices()`
4. **订阅通知**：
   - `gatt.setCharacteristicNotification(notifyChar, true)` — 注册应用层回调
   - 写入 CCCD：`BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE` — 使能链路层通知
5. **等待数据**：`onCharacteristicChanged` 累积数据片段

### 三、数据传输阶段

发送端收到 CCCD 写入事件后，延迟 300ms（给连接参数协商窗口），然后通过 `notifyCharacteristicChanged` 逐片推送数据。使用 Indication（`confirm=true`）确保 ATT 层确认送达。

**分片协议**：

```
│ Byte 0-3         │ Byte 4...N       │    ← 第一片
│ 总长度 (BigEndian)│ 数据 bytes[0..N] │
├──────────────────┼──────────────────┤
│ bytes[N+1..M]    │                  │    ← 后续片
│ 数据             │                  │
└──────────────────┴──────────────────┘
```

- 片大小上限：`min(MTU - 3, 500)` 字节
- 片间间隔：40ms
- 接收端按首片的 4 字节总长字段累积，达到总长后拼接为完整 JSON

### 四、数据校验与导入

接收端收到完整 JSON 后：

1. **解码**：`NfcShareCodec.decode(data)` → `SharePayload`
2. **sessionId 校验**：`payload.sessionId == nfcPayload` （NFC 模式）或 `payload.sessionId == scanSessionId`（广播模式）
3. **校验通过后**逐条构建 `QuestionEntity` 写入本地数据库

校验失败则显示错误信息（含双方 sessionId），拒绝导入。

### 五、ACK 确认阶段

导入完成后，接收端将 `"ACK:{sessionId}"` 写入 ACK 特征值通知发送端：

```
接收端 ── gatt.writeCharacteristic(ackChar, "ACK:550e8400-...") ──→ 发送端
发送端 ── onCharacteristicWriteRequest → onAckReceived(sessionId) ──→ UI 更新为"已完成"
```

发送端收到 ACK 后清理 HCE 和 BLE 广播，UI 显示发送成功。

## 安全与校验链

```
NFC sessionId ─┐
               ├──→ 精确匹配 ──→ 导入允许
BLE sessionId ─┘        │
                        mismatch ──→ 拒绝导入
```

广播模式同理：

```
Scan Response sessionId ─┐
                         ├──→ 精确匹配 ──→ 导入允许
BLE NOTIFY sessionId ────┘        │
                                  mismatch ──→ 拒绝导入
```

- NFC 模式通过 `enableForegroundDispatch` 在发送端拦截 NFC Intent，防止跳转到第三方应用
- 接收端检测到非 UUID 格式的 NFC payload（交通卡/门禁卡等）时不显示确认按钮，提示用户更换 tap 位置
- 45 秒超时保护防止资源泄漏

## 状态管理

`NfcUtil` 以 Kotlin StateFlow 管理跨组件的状态：

| Flow | 类型 | 用途 |
|---|---|---|
| `incomingPayload` | `SharedFlow<String>` | NFC 检测到的 payload（replay=1） |
| `receiverEnabled` | `StateFlow<Boolean>` | 控制 MainActivity Reader Mode |
| `senderEnabled` | `StateFlow<Boolean>` | 控制 MainActivity Foreground Dispatch |
| `outgoingPayload` | `StateFlow<String?>` | 发送的 sessionId |
| `senderShareState` | `StateFlow<SenderShareState>` | 发送方等待/完成/错误状态 |

`NfcViewModel` 管理接收方 UI 状态机：`Idle → Detected∣BroadcastDiscovering → Importing → Result∣Error`。

## 已知限制与处理

| 限制 | 影响 | 处理方式 |
|---|---|---|
| SE 交通卡硬件路由优先级高于 HCE | NFC 模式可能读到交通卡而非 sessionId | 接收端检测非 UUID payload 时提示"未识别到分享信号，请换个位置轻触" |
| 部分国产机 BLE 芯片 Notification 不实际发射 | 广播模式接收端收不到数据 | 使用 Indication (`confirm=true`) 替代 Notification |
| `notifyCharacteristicChanged` 分片大小硬限制 | 特定设备上 514 字节封顶 | 片大小上限固定 500 字节 |
| BLE 连接参数协商期发通知可能丢失 | 发送端通知接收端未触发 `onCharacteristicChanged` | 延迟 300ms 后再发首片 |
| GATT 缓存服务结构过期 | 多次连接同一设备后特征值不可见 | 清理缓存（`gatt.refresh()`），广播模式新连接无需清理 |
