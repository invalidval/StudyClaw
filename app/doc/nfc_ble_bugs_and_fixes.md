# NFC + BLE 错题分享 —— 典型 Bug 与修复记录

## 概述

本项目使用 **NFC 握手 + BLE 传输** 实现两部 Android 设备之间的错题分享。NFC 负责设备发现和会话 ID 交换，BLE GATT 负责实际数据（JSON payload）传输。

传输层经历三次架构迭代：

1. **READ 模式**（单特征值）：受 Long Read 深度和 native 截断限制，不可用
2. **多特征值 READ 模式**：受特征值数量 / GATT 缓存限制，不可用
3. **NOTIFY 模式**（最终方案）：发送端推送、接收端订阅，无上述限制

---

## Bug 1：BLE 读取后 GATT 连接被立即关闭

### 现象

接收端 logcat：

```
characteristic read success(deprecated), bytes=600
isLeEnabled(): ON
could not find callback wrapper
close()
unregisterApp() - mClientIf=7
```

BLE 特征值读取成功（600 字节），但紧接着 `close()` 被调用，连接断开。

### 根因

`BleShareTransport.kt` 中 `onCharacteristicRead` 回调和 `NfcViewModel` 协程存在竞态条件。

**回调内**（BLE callback 线程，tid=9653）：

```kotlin
onDataReceived?.invoke(data)
stopScanning(closeGatt = false)  // 线程 A: isScanning = true
```

**协程内**（Main 线程，tid=9617）：

```kotlin
// onDataReceived lambda -> viewModelScope.launch -> sessionId 校验失败 ->
BleShareTransport.stopScanning()  // 线程 B: isScanning = true, closeGatt = true
```

`stopScanning()` 中 `isScanning` 和 `scanCallback` 无同步保护，两个线程同时读取 `isScanning == true`，均尝试 `scanner?.stopScan(callback)`。线程 B 的 `closeGatt = true` 直接关闭了 GATT，此时数据尚未完成处理。

### 修复

移除 `onCharacteristicRead` 回调内的 `stopScanning(closeGatt = false)` 调用，由 `NfcViewModel` 协程统一负责扫描停止和 GATT 关闭，消除多线程竞态。

**文件**：`BleShareTransport.kt`

---

## Bug 2：NFC 读到交通卡/门禁卡导致 sessionId 校验失败

### 现象

接收端 tap 发送端手机后，`current.payload` 不是合法的 UUID，而是交通卡数据或 Tag ID。BLE 数据到达后，`payload.sessionId != current.payload`，校验失败，UI 显示"接收数据失败或sessionId不匹配"。

### 根因

发送端手机自带的交通卡（小米钱包/华为钱包等）存储在 **安全元件（Secure Element）** 中，属于 Off-Host 卡模拟模式，NFC Reader Mode 在 tap 时会优先读到安全元件中的卡片数据，而非应用通过 HCE 暴露的 sessionId。

同时，`NfcViewModel` 的 sessionId 校验逻辑过于严格——只要 NFC payload 非空且不以 `TAG_ID:` 开头，就必须与 BLE 数据中的 `sessionId` 精确匹配：

```kotlin
val shouldValidateSession = current.payload.isNotBlank()
    && !current.payload.startsWith("TAG_ID:")
if (payload != null && (!shouldValidateSession || payload.sessionId == current.payload)) {
```

### 修复

只有当 NFC payload 是合法 UUID 格式时才进行 session 精确匹配。如果是交通卡数据等非 UUID 内容，跳过校验，直接接受 BLE 传输的完整 `SharePayload`。

```kotlin
val nfcPayloadIsSessionId = current.payload.isNotBlank()
    && !current.payload.startsWith("TAG_ID:")
    && isValidUuid(current.payload)  // 新增：仅 UUID 才校验
if (payload != null && (!nfcPayloadIsSessionId || payload.sessionId == current.payload)) {
```

**文件**：`NfcViewModel.kt`

---

## Bug 3：BLE 特征值读取数据被截断（JSON 不完整）

### 现象

发送端数据为 1061 字节，接收端 logcat：

```
decode FAILED: JSONException: Unterminated string at character 366
raw data(len=366), last 80 chars: >>...即'公有制为主�<<
```

JSON 在中文字符中间截断，`JSONObject(raw)` 抛出 `JSONException`。BLE 读取报告 600 字节，但实际 JSON 需要 1061 字节。

### 根因（双重原因）

#### 第一层：native BLE 层截断 `characteristic.value`

发送端将 JSON 完整字节数组赋值给 `characteristic.value`：

```kotlin
characteristic.value = data.toByteArray(Charsets.UTF_8)  // 1061 字节
```

当 `gattServer.addService(service)` 被调用时，Android native BLE 栈将 `characteristic.value` 注册到属性表。该设备上的 BLE 属性值上限为 600 字节，多余字节被静默丢弃。接收端永远只能读到 600 字节。

#### 第二层：Long Read 深度限制

将数据移出 `characteristic.value` 后，服务端在 `onCharacteristicReadRequest` 中按 MTU-1 = 516 字节分片响应，依赖客户端 Long Read 机制（Read Request + Read Blob Request）自动组装。然而该设备的 Android BLE 栈 Long Read 深度限制为 2 次读取（1 Read + 1 Read Blob），即最多传输 2 × 516 = 1032 字节，第 3 片（剩余 29 字节）不会被请求，导致 JSON 仍不完整。

### 修复（历经三种方案）

#### 方案 A：多特征值 READ（失败）

将数据拆分为 3 个特征值，每个 ≤ 500 字节，单次 Read 无需 Long Read。但 GATT 服务添加第 3、4 个特征值后在接收端 discovery 时特征值不可见（`r1=false, r2=false`），`gatt.refresh()` 清缓存也无效，疑似该设备 BLE 栈对单服务的特征值数量存在限制。

#### 方案 B：NOTIFY 推送（最终方案）

重构传输层为 **NOTIFY 模式**：

- 发送端：单个 NOTIFY 特征值 + CCCD 描述符。接收端订阅 CCCD 后，发送端通过 `notifyCharacteristicChanged` 逐片推送完整数据，每片 ≤ 500 字节。
- 接收端：`onCharacteristicChanged` 中累积数据片，当累积长度达到首片的 4 字节总长字段时，拼接为完整 JSON 并解码。

协议格式：

```
首片: [4 字节 Big-Endian 总长度] + [数据 bytes 0..N]
后续片: [数据 bytes N+1..end]
```

**文件**：`BleShareTransport.kt`（完全重写）

---

## Bug 4：`notifyCharacteristicChanged` 分片大小超限崩溃

### 现象

发送端在 `notifyCharacteristicChanged` 调用时崩溃：

```
java.lang.IllegalArgumentException: notification should not be longer than
max length of an attribute value
at BluetoothGattServer.notifyCharacteristicChanged(BleShareTransport.kt:209)
```

### 根因

NOTIFY 分片大小计算为 `serverMtu - 3 = 514` 字节（MTU 517 时）。但该设备的 Android BLE 实现对 `notifyCharacteristicChanged` 的参数大小有独立的硬限制（非标准 MTU-3 计算公式），当值超过上限时抛出 `IllegalArgumentException`。

### 修复

将分片大小上限固定为 500 字节：

```kotlin
val maxChunkSize = minOf((serverMtu - 3).coerceAtLeast(20), 500)
```

**文件**：`BleShareTransport.kt`

---

## Bug 5：接收端 BLE 扫描发现特征值不可见

### 现象

发送端 GATT 服务注册了 3+ 个特征值（3 个 READ + 1 个 WRITE ACK），但接收端 `onServicesDiscovered` 后 `service.getCharacteristic(uuid)` 对新增特征值返回 `null`：

```
readCharacteristic requested: r0=true r1=false r2=false, expected=1
```

### 根因

可能的原因（未最终确认）：

1. **GATT 缓存**：接收端之前连接过同一设备，Android BLE 栈缓存了旧的服务结构（仅有 2 个特征值），`discoverServices` 直接返回缓存结果。调用 `gatt.refresh()` 未必生效。
2. **设备 BLE 栈限制**：部分 Android 设备的 GATT 实现对单服务的最大特征值数量存在隐式上限。

### 修复

重构为 NOTIFY 模式后，GATT 服务仅需 2 个特征值（1 NOTIFY + 1 WRITE ACK），避开了特征值数量限制问题。

---

## 总结

| Bug | 类别 | 核心教训 |
|---|---|---|
| GATT 竞态关闭 | 并发安全 | BLE 回调和协程之间共享可变状态必须同步，清理逻辑应由单一线程负责 |
| SessionId 校验失败 | 业务逻辑 | NFC Reader Mode 无法绕开 SE 安全元件，校验逻辑需对非预期 NFC 数据做降级处理 |
| 数据截断 | 平台限制 | `characteristic.value` 在 native 层有设备相关的大小上限，大数据不可依赖此字段；Long Read 深度不可控 |
| 通知分片崩溃 | 平台差异 | `notifyCharacteristicChanged` 的值大小限制因设备而异，非标准 MTU-3 公式 |
| 特征值不可见 | 平台限制 | GATT 缓存和特征值数量均存在设备相关限制，服务设计应保持最小特征值数量 |

### 最终架构

```
NOTIFY 推送模式
─────────────────
GATT 服务:
  - Char 0: NOTIFY (推送数据) + CCCD 描述符
  - Char 1: WRITE  (ACK 回传)

协议:
  - 首片: [4B 总长] + [数据]
  - 后续片: [数据]
  - 片大小上限: 500 字节
  - ACK: "ACK:{sessionId}" 写入 Char 1
```

两台设备均需使用最终版本代码以正常工作。
