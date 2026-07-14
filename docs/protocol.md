# 车云通信协议草案（MQTT）

> 状态：**草案 v0.1（draft）**，供 `PPDVedge/comm`（ROS2）与 `PPDVapi` 物联接入模块共同评审。字段与主题命名确定后升为 v1.0 冻结。

## 1. 概览

- 传输：车载端（`PPDVedge`）与云平台（`PPDVapi`）之间经 4G/5G 通过 **MQTT** 通信。
- 角色：车载端为 MQTT Client（既发布遥测也订阅指令）；云平台侧由 MQTT Broker + `PPDVapi` 消费/下发。
- 载荷格式：**JSON（UTF-8）**。二进制大数据（点云、视频）不走 MQTT，另用视频流 / 对象存储，MQTT 只传其引用地址。
- 时间：所有时间戳为 **Unix epoch 毫秒（UTC）**。
- 坐标：位置用 **WGS84** 经纬度（RTK 增强），单位度；朝向 heading 单位度（正北 0，顺时针）；速度 m/s。

## 2. 连接约定

| 项 | 约定 |
|----|------|
| Broker 地址 | 由部署环境提供（如 `mqtts://broker.example.com:8883`） |
| 传输加密 | 生产环境启用 **TLS（8883）**；开发可用 1883 |
| ClientId | `edge-{deviceId}`，全局唯一 |
| 认证 | username = `deviceId`，password = 设备密钥 / Token（一机一密） |
| KeepAlive | 30s |
| CleanSession | false（保留订阅与离线消息） |
| 遗嘱 LWT | Topic `ppdv/v1/{deviceId}/status`，payload `{"online":false,...}`，retained=true，QoS=1 |

设备上线后应主动向 `ppdv/v1/{deviceId}/status` 发布 `{"online":true}`（retained）覆盖遗嘱。

## 3. 主题（Topic）命名

统一前缀 `ppdv/v1/{deviceId}/...`（`v1` 为协议版本，`deviceId` 为车辆唯一编号）。多租户如需要，可扩展为 `ppdv/v1/{tenantId}/{deviceId}/...`。

### 上行（车载端 → 云平台，device publish）

| Topic | 说明 | QoS | 频率 |
|-------|------|-----|------|
| `ppdv/v1/{deviceId}/status` | 上下线状态（retained + LWT） | 1 | 变更时 |
| `ppdv/v1/{deviceId}/tele/state` | 状态遥测（位置/速度/电量/模式） | 0 | 1~5 Hz |
| `ppdv/v1/{deviceId}/tele/work` | 作业遥测（喷洒流量/药量/已作业面积） | 1 | 1 Hz |
| `ppdv/v1/{deviceId}/event/alarm` | 告警事件 | 1 | 触发时 |
| `ppdv/v1/{deviceId}/task/progress` | 任务进度上报 | 1 | 变更时 |
| `ppdv/v1/{deviceId}/cmd/ack` | 指令执行回执 | 1 | 收到指令后 |

### 下行（云平台 → 车载端，device subscribe）

| Topic | 说明 | QoS |
|-------|------|-----|
| `ppdv/v1/{deviceId}/cmd/req` | 控制指令（启停/急停/返航/切换模式） | 1 |
| `ppdv/v1/{deviceId}/task/assign` | 任务下发（航线 / 处方图引用） | 1 |
| `ppdv/v1/{deviceId}/config/set` | 参数下发 | 1 |
| `ppdv/v1/{deviceId}/ota/notify` | OTA 升级通知 | 1 |

## 4. 消息信封

所有消息共用统一信封，业务字段放在 `data` 内：

```json
{
  "msgId": "9f1c2e3a-...",
  "deviceId": "PPDV-0001",
  "ts": 1721030400000,
  "type": "state",
  "data": { }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `msgId` | string(UUID) | 是 | 消息唯一 ID，用于去重与回执关联 |
| `deviceId` | string | 是 | 设备编号 |
| `ts` | long | 是 | 发送时间戳（epoch ms, UTC） |
| `type` | string | 是 | 消息类型，见各章 |
| `data` | object | 是 | 业务载荷 |

## 5. 上行消息

### 5.1 上下线 status
```json
{ "deviceId":"PPDV-0001","ts":1721030400000,"type":"status",
  "data":{ "online":true, "fw":"1.2.0", "ip":"10.0.0.5" } }
```

### 5.2 状态遥测 tele/state
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"state",
  "data":{
    "lat":30.123456, "lon":120.654321, "alt":12.3,
    "heading":87.5, "speed":1.4,
    "rtkFix":"FIX",            // NONE | FLOAT | FIX
    "mode":"AUTO",             // MANUAL | AUTO | PAUSED | ESTOP
    "battery":78,              // %
    "taskId":"T-20260715-01"   // 无任务时可为 null
  } }
```

### 5.3 作业遥测 tele/work
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"work",
  "data":{
    "spraying":true,
    "flow":2.4,                // L/min
    "pressure":0.30,           // MPa
    "tankLevel":62,            // %
    "sprayedArea":1.85,        // 亩（累计）
    "dosage":3.1               // L（累计用药）
  } }
```

### 5.4 告警 event/alarm
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"alarm",
  "data":{
    "code":"GEOFENCE_BREACH",  // 见告警码表
    "level":"CRITICAL",        // INFO | WARN | CRITICAL
    "msg":"车辆越过电子围栏，已触发急停",
    "lat":30.123456, "lon":120.654321
  } }
```

### 5.5 任务进度 task/progress
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"taskProgress",
  "data":{
    "taskId":"T-20260715-01",
    "status":"RUNNING",        // ASSIGNED | RUNNING | PAUSED | DONE | ABORTED
    "progress":42,             // %
    "coveredArea":8.6          // 亩
  } }
```

## 6. 下行消息

### 6.1 控制指令 cmd/req
```json
{ "msgId":"c-8837","deviceId":"PPDV-0001","ts":1721030400000,"type":"cmd",
  "data":{
    "cmd":"START",             // START|PAUSE|RESUME|STOP|ESTOP|RTL|SET_MODE
    "args":{ "mode":"AUTO" }   // 可选，随 cmd 而定
  } }
```
车载端收到后**必须**回 `cmd/ack`（见 7）。`ESTOP`（急停）优先级最高，安全模块须立即执行。

### 6.2 任务下发 task/assign
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"task",
  "data":{
    "taskId":"T-20260715-01",
    "fieldId":"F-1001",
    "routeUrl":"https://oss.example.com/routes/T-...json",   // 航线文件引用
    "prescriptionUrl":"https://oss.example.com/rx/T-...json",// 处方图引用（可选）
    "params":{ "speed":1.5, "dosePerMu":0.9 }
  } }
```
> 航线点与处方图数据量大，通过 URL 引用，车载端自行下载，不塞进 MQTT 载荷。

### 6.3 参数下发 config/set
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"config",
  "data":{ "teleRateHz":2, "geofence":"F-1001", "maxSpeed":1.8 } }
```

### 6.4 OTA 通知 ota/notify
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"ota",
  "data":{ "version":"1.3.0","url":"https://oss.example.com/fw/1.3.0.bin","md5":"...","force":false } }
```

## 7. 指令回执 cmd/ack

车载端对每条下行 `cmd/req`（以及可要求回执的 task/config）回执，用 `refId` 关联原指令 `msgId`：

```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400500,"type":"ack",
  "data":{
    "refId":"c-8837",
    "code":0,                  // 0 成功，非 0 见错误码
    "msg":"OK"
  } }
```

## 8. QoS、去重与断网补传

- **QoS**：控制指令、告警、任务、回执用 QoS 1（至少一次）；高频状态遥测用 QoS 0（可容忍丢帧）。
- **去重**：接收方按 `msgId` 去重（QoS 1 可能重复投递）。
- **断网缓存**：车载端离线时将遥测/告警按时间落本地缓存，恢复连接后按序补传（`ts` 为原采集时间，非补发时间）。
- **指令时效**：控制指令可含隐式时效，车载端对过期指令（如网络恢复后收到很久前的 START）应结合当前状态判断是否执行，安全相关指令从严。

## 9. 待定 / 评审点（TODO）

- [ ] 是否引入 `tenantId` 层级（多租户）。
- [ ] 认证方案细化：一机一密 vs. 动态 Token / mTLS 证书。
- [ ] 告警码表（`code`）与错误码表（ack `code`）单独成附录并统一维护。
- [ ] 遥测频率与字段裁剪（流量成本 vs. 监控精度）。
- [ ] 航线 / 处方图文件格式（`routeUrl` / `prescriptionUrl` 指向的 JSON schema）单独定义。
- [ ] 视频流协议（RTSP/WebRTC）与地址下发方式。

## 10. 附录：告警码（初稿）

| code | 含义 | level |
|------|------|-------|
| `ESTOP_TRIGGERED` | 急停触发 | CRITICAL |
| `GEOFENCE_BREACH` | 越过电子围栏 | CRITICAL |
| `OBSTACLE_STOP` | 障碍物紧急制动 | CRITICAL |
| `LOW_BATTERY` | 低电量 | WARN |
| `LOW_TANK` | 药箱不足 | WARN |
| `RTK_LOST` | RTK 失锁 | WARN |
| `COMM_RECOVERED` | 通信恢复（补传标记） | INFO |
