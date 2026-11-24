# 车云通信协议草案（MQTT）

> 状态：**草案 v0.4（draft）**，供 `PPDVedge/comm`（ROS2）与 `PPDVapi` 物联接入模块共同评审。字段与主题命名确定后升为 v1.0 冻结。
>
> v0.2（2026-11-02）：对齐云平台已落地实现——收录 `tele/env`、`tele/zone`、`spray/req` 三个已实现主题；status 统一为信封格式（v0.1 的裸 LWT 格式与 §5.1 自相矛盾，且云平台只解析信封）；标注 `config/set`、`ota/notify` 及各字段的云平台消费边界。
>
> v0.3（2026-11-11）：收录车端 YOLO 目标跟踪数据上云的 `tele/track` 主题（§5.6，云端 P1 已实现），并补充该主题的 `msgId` 去重实现边界（§8）。
>
> v0.4（2026-11-11）：`tele/track` 身份模型修订（§5.6）——以摄像头连续可见的一次"出现片段（episode）"为最小单位，新增 `episodeId`/`episodeNo`/`sessionEnd`，**删除 `frameCount`（不再上传/不再落库/不再展示）**；`trackId` 仅展示/关联，`trackState=ENDED` 只表示本次出现结束。
>
> v0.4 修订（2026-11-12）：云平台消费边界更新（§5.6、§8，**车端报文格式不变**）——`msgId` 去重键改为 `track:msg:{deviceId}:{msgId}` 且处理失败回删；`episodeId` 兜底收紧（`firstSeenTs` 缺失/非法或兜底键超 64 字符时直接拒收该 track）；会话结束区分 `session_end`（车端，不可复归）/`idle`（云端空闲，新活动可复归）；空闲清扫按服务器侧最后活动时间（apply 写入 `last_activity_ts`）判定；`sessionEnd=true` 时即使全部 track 非法或 `tracks=[]` 也仍结束会话，区域不可解析走 `endSession` 兜底；先被 idle 结束的会话再收 `sessionEnd` 会把 `end_reason` 升级为 `session_end`（不可复归）；WS 摘要 `episodeCount` 改为会话累计。

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
| 遗嘱 LWT | Topic `ppdv/v1/{deviceId}/status`，信封格式 `{"msgId":"...","deviceId":"...","ts":...,"type":"status","data":{"online":false,...}}`，retained=true，QoS=1（**v0.2 起统一信封格式**：云平台只解析 `data.online`，v0.1 的裸 `{"online":false}` 遗嘱会被 `asBoolean(false)` 默认值"蒙对"下线，但裸 `{"online":true}` 上线会被误判为下线，故必须携带信封） |

设备上线后应主动向 `ppdv/v1/{deviceId}/status` 发布信封格式 `{"msgId":"...","deviceId":"...","ts":...,"type":"status","data":{"online":true}}`（retained）覆盖遗嘱。另注：收到 `tele/state` 时云平台也会把设备置 ONLINE（`DeviceService.updateState`），status 是唯一的显式下线通道；云平台无定时器把静默设备置 OFFLINE。

## 3. 主题（Topic）命名

统一前缀 `ppdv/v1/{deviceId}/...`（`v1` 为协议版本，`deviceId` 为车辆唯一编号）。多租户如需要，可扩展为 `ppdv/v1/{tenantId}/{deviceId}/...`。

### 上行（车载端 → 云平台，device publish）

| Topic | 说明 | QoS | 频率 |
|-------|------|-----|------|
| `ppdv/v1/{deviceId}/status` | 上下线状态（retained + LWT） | 1 | 变更时 |
| `ppdv/v1/{deviceId}/tele/state` | 状态遥测（位置/速度/电量/模式） | 0 | 1~5 Hz |
| `ppdv/v1/{deviceId}/tele/work` | 作业遥测（喷洒流量/药量/已作业面积） | 1 | 1 Hz |
| `ppdv/v1/{deviceId}/tele/env` | 环境传感器遥测（温度/湿度/光照/土壤湿度/CO2） | 1 | 按传感器采样 |
| `ppdv/v1/{deviceId}/tele/zone` | 区域进入上报（设备进入某区域编号） | 1 | 变更时 |
| `ppdv/v1/{deviceId}/event/alarm` | 告警事件 | 1 | 触发时 |
| `ppdv/v1/{deviceId}/task/progress` | 任务进度上报 | 1 | 变更时 |
| `ppdv/v1/{deviceId}/cmd/ack` | 指令执行回执 | 1 | 收到指令后 |
| `ppdv/v1/{deviceId}/tele/track` | YOLO 目标跟踪观测（按出现片段 episode 聚合，非逐帧） | 1 | 每时间窗 1–5s 或会话结束 |

### 下行（云平台 → 车载端，device subscribe）

| Topic | 说明 | QoS |
|-------|------|-----|
| `ppdv/v1/{deviceId}/cmd/req` | 控制指令（启停/急停/返航/切换模式） | 1 |
| `ppdv/v1/{deviceId}/task/assign` | 任务下发（地块边界/路线/作业参数，内联 JSON） | 1 |
| `ppdv/v1/{deviceId}/spray/req` | 点喷指令（start/stop，携带区域编号） | 1 |
| `ppdv/v1/{deviceId}/config/set` | 参数下发（**仅预留**，云平台暂无调用方） | 1 |
| `ppdv/v1/{deviceId}/ota/notify` | OTA 升级通知（**仅预留**，云平台未实现） | 1 |

> 云平台订阅侧对上述全部上行主题统一按 QoS 1 订阅（`MqttConfig` 的 adapter `setQos(1)`）；`tele/state` 标注 QoS 0 指车端**发布**建议，实际投递 QoS 取两端协商结果。

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
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"status",
  "data":{ "online":true, "fw":"1.2.0", "ip":"10.0.0.5" } }
```

> 云平台消费：读 `online`（缺失按 false）与 `fw`；`ip` 不消费。`online=true` 置 ONLINE 并刷新 `lastSeenAt`，`online=false` 置 OFFLINE。

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

> 云平台消费：`lat/lon/battery/mode` 更新 MySQL 设备快照（非数值经纬度按缺失处理）；`lat/lon/alt/heading/speed/battery/mode` 写 TDengine `tele_state`；`lat/lon` 进入路线录制会话（§11.1）；收到即置 ONLINE。**`rtkFix`、`taskId` 当前云平台全链路不消费**，车载端可暂缓实现这两个字段。

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

> 云平台消费：全字段写 TDengine `tele_work`，无单位换算与校验（`sprayedArea` 亩 / `dosage` L 的单位约定靠收发双方自觉）。未启用 TDengine 时仅记 debug 日志。

### 5.3a 环境遥测 tele/env（v0.2 收录，随云平台实现）
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"env",
  "data":{
    "temperature":24.6,        // ℃
    "humidity":63.2,           // %
    "light":18500,             // lx
    "soilMoisture":41.5,       // %
    "co2":620                  // ppm
  } }
```

> 云平台消费：全字段写 TDengine `env_sensor`（未启用 TDengine 时仅记日志）。type 建议取 `env`（云平台不校验 type）。

### 5.3b 区域进入上报 tele/zone（v0.2 收录，随云平台实现）
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"zone",
  "data":{
    "zoneCode":"AREA-12"       // 设备当前进入的区域编号（对应设备表 current_zone）
  } }
```

> 云平台消费：读 `zoneCode` 更新 `device.current_zone`；缺失时仅记日志不更新。

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

> 云平台消费：`code/level/msg` 仅记 WARN 日志并经 WebSocket 广播到大屏；`lat/lon` 不消费；**不落库**（无告警表），前端只保留内存环形缓冲。§10 告警码表仅供车端参考，云平台不做码值校验。

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

> 云平台消费：仅 debug 日志 + WebSocket 原样透传广播，**不解析任何字段、不更新任务状态**（下发的任务本身不落库，见 §6.2 实现说明）。

### 5.6 目标跟踪观测 tele/track（v0.3 收录，v0.4 身份模型修订，云平台 P1 已实现）
```json
{ "msgId":"9f1c2e3a-7b1d-4f9a-9c2e-1a2b3c4d5e6f","deviceId":"PPDV-0001","ts":1793706366894,"type":"track",
  "data":{
    "trackSessionId":"TS-PPDV-0001-20261111-001",  // 一次巡检/跟踪会话（容器，可含多次出现），≤64
    "areaId":12,                                    // 可选；缺失时用 device.currentZone 反查
    "sessionEnd":false,                             // 本次巡检是否结束；最后一条消息置 true（可配 tracks=[]）
    "inspectionId":null,                            // 可选；车端无 JWT 时为空
    "windowIndex":3,                                // 可选；仅供联调/乱序诊断
    "imageRef":null,                                // 预留：关键帧引用（P3），P1 不消费
    "tracks":[
      {
        "trackId":"T-7",                            // 跟踪器 ID，仅展示/关联，不保证跨出现稳定
        "episodeId":"EP-7-20261111-001",            // 一次出现ID（身份键），≤64；同出现内稳定，再次出现必须换新
        "episodeNo":0,                              // 可选；同 trackId 第几次出现（从 0 递增），仅展示/诊断
        "class":"leaf_late_blight", "confidence":0.86,
        "firstSeenTs":1793706355000,                // 本次出现首帧时间；同一 episode 周期上传时保持不变
        "lastSeenTs":1793706366000,                 // 本次出现末帧/最新帧时间；周期更新可推进
        "boxX":120, "boxY":80, "boxW":96, "boxH":72, "imageWidth":1280, "imageHeight":720,
        "trajectory":[[140,90,1793706355000],[130,85,1793706360000],[120,80,1793706366000]],
        "trackState":"ACTIVE",                      // ENDED=本次出现结束，不结束巡检会话
        "severity":null,
        "lat":30.123456, "lon":120.654321, "positionSource":"RTK"  // 可选；仅展示，不参与身份与风险，云端本轮不落库
      }
    ]
  } }
```

**v0.4 身份模型（出现片段 episode，取代 v0.3"同一 trackId 聚合"）**：

- 目标从进入画面到离开为一次**出现（episode）**：第一次出现生成新 `episodeId`，持续期间不变（周期更新 `lastSeenTs`/bbox/轨迹）；离开/丢失后本次 episode 置 `trackState=ENDED`；**再次出现（即使 `trackId` 相同、地点相同）必须换新 `episodeId`、重新上传一条**。
- `firstSeenTs` = 本次出现首帧时间（episode 内保持不变）；`lastSeenTs` = 本次出现末帧/最新帧时间（episode 结束后定格）。持续时长如需展示，用 `lastSeenTs − firstSeenTs` 计算。
- `trackSessionId` 只作为容器（一次巡检可含多次出现）；会话结束用 data 级 `sessionEnd=true`（可用 `tracks=[]` + `sessionEnd=true`），**与单条 episode 的 `trackState=ENDED` 是两回事**。
- **`frameCount` 已删除**：不再上传、不再落库、不再展示；旧报文若携带，云端忽略不报错。
- `lat/lon/positionSource` 可选保留，仅展示/地图，不参与唯一键与风险聚合（不同位置自然对应不同 episode）。

> 云平台消费（`TrackIngestService`，受 `ppdv.track.enabled` 开关控制）：`msgId` Redis 去重（键 `track:msg:{deviceId}:{msgId}`，TTL 24h；处理失败回删键，QoS 1 补传可重放，落库幂等）→ 校验 deviceId/区域/类别/置信度/bbox/条数（单条 ≤256KB、`tracks ≤ 50`、`trajectory ≤ 20` 点）→ `areaId`/`device.currentZone` 解析区域（都不可用则丢弃并告警；携带 `sessionEnd=true` 时即使 `tracks=[]` 或全部 track 校验失败也仍结束既有会话——区域可解析照常落库结束，不可解析调用云端 `endSession` 兜底，无既有会话才丢弃）→ 按 `episodeId` upsert `coer_track_session` + `coer_detection_track`（唯一键 `(device_id, track_session_id, episode_key)`，V14；同一 episode 取最早 firstSeen/最新 lastSeen/max 置信度，bbox/图像尺寸/轨迹缺省不覆盖已有值、`__unknown__` 不覆盖已知类别，乱序不新建行；不同 episode 即使 trackId 相同也新增一行；会话 `areaId` 跟随最新消息）→ 事务提交后 WebSocket 广播 `kind=tele/track` 摘要（`episodeCount`=会话累计出现次数，状态只下发 `status`；空闲清扫结束的会话同样广播）并异步刷新影子分。**会话结束只认 `sessionEnd=true`**（`end_reason=session_end`，不可复归；若会话已被空闲清扫结束，再收 `sessionEnd` 会把 `end_reason` 更新为 `session_end`、`endTs` 取消息 ts，之后不可复归；单条 episode `ENDED` 不结束会话，会话 `ENDED` 后迟到补传只落库、不回退状态）；车端未发 `sessionEnd` 时，最近活动超过 `ppdv.track.session-idle-minutes`（默认 15 分钟）的会话由云端定时清扫置 `ENDED`（`end_reason=idle`，**收到新活动自动复归 ACTIVE**；空闲判定按会话的服务器侧 `last_activity_ts`（apply 时写入，车端时钟超前不影响；时钟滞后的误判由可复归兜底），`end_ts` 取该服务器侧最后活动时间）。`episodeId` 缺失时兜底 `trackId@firstSeenTs` 并告警 `TRACK_EPISODE_FALLBACK`；`firstSeenTs` 缺失/非法（兜底键随消息漂移）时直接拒收该 track（告警 `TRACK_EPISODE_FALLBACK_UNSTABLE`），`episodeId` 或兜底键超过 64 字符同样拒收，`ppdv.track.require-episode=true` 时一律拒收（车端升级后置 true）。`class` 采用 15 类规范 key（6 物候 + 9 病虫害）；未知 key 落 `__unknown__` 并告警，旧 5 类中文名（虫害/病斑/萎蔫/黄化/斑点）拒识。`imageRef` 本轮不落库（P3 接关键帧另立方案）。
>
> 传输约定：JSON 走 MQTT（符合 §1"二进制大数据只传引用"）；车端按"一次出现一条观测"聚合后才上传（同一 episode 内取 max 置信度/最后 bbox/首末时间/轨迹抽稀），不要逐帧发检测。云端影子分按**每条 episode 一份证据**聚合（同类跨 episode：`ppdv.track.episode-aggregation=NOISY_OR`（默认，有界）或 `SUM`（封顶 80）；contributions 按 `class@sessionId@episodeKey`，跨会话复用 `episodeId` 不会互相覆盖），影子分不改变风险总分，P2 待 Q1 证据聚合落地后接入。

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

> 枚举说明：`cmd` 枚举见 6.1 代码注释（START|PAUSE|RESUME|STOP|ESTOP|RTL|SET_MODE），云平台不做枚举校验、任意字符串透传；`args` 自由 Map 透传（SET_MODE 时为 `{"mode":"AUTO"}`）。`STOP`/`SET_MODE` 在云平台前端（PPDVweb 驾驶舱）未提供按钮，仅管理后台全量提供。

### 6.2 任务下发 task/assign

```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"task",
  "data":{
    "taskId":"T-20260715-01",
    "fieldId":"AREA-12",          // 区域来源为 AREA-{areaId}（v1.1 起无本地手绘地块）
    "areaId":12,                  // 关联区域 ID，无关联可为 null
    "route":[[120.654321,30.123456],[120.655001,30.123100]],   // 作业路线 [[lng,lat],...]，至少 2 点
    "field":[[120.653800,30.122100],[120.656500,30.122300],[120.656100,30.124600]], // 地块边界 [[lng,lat],...]，区域未圈定时为 []
    "params":{ "speed":1.5, "dosePerMu":0.9 }
  } }
```

> 实现说明：`route`/`field` 为**内联** `[[lng,lat],...]` 数组（CGCS2000 存储，与遥测 WGS84 视同），不再是草案初稿设想的 `routeUrl`/`prescriptionUrl` 文件引用。路线来自云平台按区域持久化的已录路线（`coer_greenhouse_area.route_json`，由区域页"录入路线"随 `tele/state` 遥测录制），地块边界来自 `boundary_json`；区域未圈定边界时 `field` 为空数组。云平台后端对该 payload 不做字段校验，原样透传给车载端；payload 由 PPDVweb 作业规划页经 REST（`GET /ppdv/area/{id}/route` 等）从数据库拉取后组装（`taskId` 形如 `T-{时间戳}`、`fieldId` 形如 `AREA-{areaId}`），组装语义与 §11.1 一致。

### 6.3 点喷指令 spray/req（v0.2 收录，随云平台实现）
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"spray",
  "data":{
    "action":"start",          // start | stop（云平台当前业务只允许 start）
    "areaCode":"A1",           // 点喷目标区域编号（coer_greenhouse_area.area_code）
    "duration":300             // 作业时长（秒），1~3600；stop 时可省略
  } }
```

> 实现说明：由点喷任务执行链路（`PlantProtectionService.executeSprayTask` → `CommandService.sendSpray`）下发，`coer_spray_task` 状态机与 `coer_review_record` 复查闭环见 README"核心业务闭环"；与 §6.2 的一次性任务下发、§11.1 的路线录制是三条互不混淆的链路。车载端应回 `cmd/ack`（`refId` 关联本消息 `msgId`）。

### 6.4 参数下发 config/set（**仅预留，未实现**）
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"config",
  "data":{ "teleRateHz":2, "geofence":"F-1001", "maxSpeed":1.8 } }
```

> 云平台现状：仅 `Topics.configSet` 常量定义，无任何调用方；schema 未评审，字段以将来实现为准。

### 6.5 OTA 通知 ota/notify（**仅预留，未实现**）
```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400000,"type":"ota",
  "data":{ "version":"1.3.0","url":"https://oss.example.com/fw/1.3.0.bin","md5":"...","force":false } }
```

> 云平台现状：主题常量与发布链路均不存在，纯草案设想。

## 7. 指令回执 cmd/ack

车载端对每条下行 `cmd/req`（以及可要求回执的 task/spray/config）回执，用 `refId` 关联原指令 `msgId`：

```json
{ "msgId":"...","deviceId":"PPDV-0001","ts":1721030400500,"type":"ack",
  "data":{
    "refId":"c-8837",
    "code":0,                  // 0 成功，非 0 见错误码
    "msg":"OK"
  } }
```

> 云平台消费：仅 debug 日志，**不读 `refId`/`code`、不回填指令状态、不广播**；下发接口返回的 `msgId` 目前仅供前端展示。车载端仍应按本节回执（协议先约定，云平台消费后续补齐）。

## 8. QoS、去重与断网补传

- **QoS**：控制指令、告警、任务、回执用 QoS 1（至少一次）；高频状态遥测建议 QoS 0（可容忍丢帧）。云平台订阅侧统一按 QoS 1 订阅、下行发布统一 QoS 1（`MqttPublisher` 走默认 QoS 1），实际投递 QoS 以发布方为准。
- **去重**：接收方按 `msgId` 去重（QoS 1 可能重复投递）。**云平台现状：仅 `tele/track` 实现去重**（Redis `track:msg:{deviceId}:{msgId}`，TTL 24h，DB 唯一键兜底；处理失败回删去重键，QoS 1 补传可重放）；`ppdv.track.msg-dedup-ttl-hours ≤0` 表示关闭去重；其余上行主题仍只读 `data` 与 `ts`，`msgId`/`deviceId`/`type` 均不校验（deviceId 从主题解析），没有去重存储；`tele/state` 侧的重复防护仅限路线录制的 `ts < lastFixTs` 补传过滤（§11.1）。
- **断网缓存**：车载端离线时将遥测/告警按时间落本地缓存，恢复连接后按序补传（`ts` 为原采集时间，非补发时间）。
- **指令时效**：控制指令可含隐式时效，车载端对过期指令（如网络恢复后收到很久前的 START）应结合当前状态判断是否执行，安全相关指令从严。

## 9. 待定 / 评审点（TODO）

- [ ] 是否引入 `tenantId` 层级（多租户）。
- [ ] 认证方案细化：一机一密 vs. 动态 Token / mTLS 证书。
- [ ] 告警码表（`code`）与错误码表（ack `code`）单独成附录并统一维护（当前附录 §10 仅为初稿，云平台不做码值校验/存储）。
- [ ] 遥测频率与字段裁剪（流量成本 vs. 监控精度）；`tele/state` 的 `rtkFix`/`taskId`、`status` 的 `ip` 云平台暂不消费，由本 TODO 统一裁决保留或删除。
- [x] 航线/地块内联 schema 已随实现落地（见 6.2 的 `route`/`field`）；处方图（`prescriptionUrl`）与文件引用方式仍未实现。
- [ ] 视频流协议（RTSP/WebRTC）与地址下发方式。
- [ ] 云平台按 `refId` 回填指令执行状态（§7 回执目前仅记日志）。
- [ ] `config/set` schema 评审与调用方实现；`ota/notify` 落地。

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

> 云平台现状：无告警表、无码值校验，`code/level/msg` 仅 WARN 日志 + WebSocket 透传；本表仅约束车端取值。

## 11. 云平台实现语义（路线录制与边界坐标）

> 本节记录云平台已落地的路线录制与边界坐标系实现语义，供车载端对齐；与代码冲突时以 `RouteRecordingService` / `AreaService` 为准。

### 11.1 作业路线录制（`RouteRecordingService`，随 `tele/state` 遥测）

- 作业路线不网页手绘：区域页"录入路线"由 `RouteRecordingService` 随 `tele/state` 遥测在单实例内存会话里累积坐标，结束后按区域落库到 `coer_greenhouse_area.route_json`——一个区域一条路线，重新录入即覆盖，不落中间表；后端重启丢失未结束的会话。
- 坐标按 7 位小数取整；append 有 20000 点与 512KB 字节预算双上限；补传旧点按设备时钟 `lastFixTs` 过滤（`ts < lastFixTs` 丢弃）且首点无条件接受（设备时钟偏斜不丢点）。
- 会话空闲 10 分钟 / 最大 2 小时超时按服务器时钟 `lastSeenAt` 判定——静止停车、点数/字节封顶、补传旧点不会误清会话；`hasActiveSessionForDevice` 先懒清理过期会话。
- stop 的落库在会话全局锁内完成（与 cancel/删区域串行化），remove 一律两参 `remove(key, value)` 原子删除；删除区域先删区域、成功后再联动幂等取消，删除设备联动取消。
- 点数不足时 stop 保留会话可继续补点，cancel 幂等成功。
- 作业规划从数据库读取路线/边界组装内联 payload 发到 `task/assign`（录制中的设备下发任务返回 409 互斥；下发的任务本身不落库）；点喷任务走 `coer_spray_task`/`coer_review_record` 的持久化闭环，录制落库的路线、下发的任务、点喷闭环三者不要混淆。
- 区域列表接口只返回路线元信息（hasRoute/routePointCount/routeRecordedAt，点数读 V13 的 `route_point_count` 冗余列），完整路线经 `GET /ppdv/area/{id}/route` 按 id 拉取，全部已录路线经 `GET /ppdv/area/routes` 批量拉取；录制状态快照时间字段为 `lastFixTs`（设备时钟，0=尚无有效点）。

### 11.2 区域边界坐标系（CGCS2000）

- 区域边界为 CGCS2000（V10 迁移把存量 GCJ-02 逆变换为 CGCS2000；无法转换的非法 JSON/顶点格式/少于 3 点的行保留 GCJ-02），遥测协议 WGS84 与天地图 CGCS2000 视为一致、直接叠加显示；新增边界与已录路线一律按 CGCS2000 存储（路线取自遥测录制，WGS84 视同）。
- 区域可先建后补边界（`boundary_json` 可空，前端空边界提交 null 不能传 `"[]"`）；前端对非 CGCS2000 区域不渲染，编辑时必须重新圈定并以 CGCS2000 保存。
- 编辑已有边界的区域时边界与坐标系绑定（PUT 语义）：`null`=保持原边界且不允许改坐标系（否则 400）；原样回传与库内同值的旧边界视同未重画，同样拒绝改标；`""`=显式清除边界并归位 CGCS2000；重画边界才能改坐标系；带新边界但未声明坐标系时后端补默认 CGCS2000，未变化的边界保持库内坐标系。后端有守卫、前端（web）有拦截。
