# comm · 通信上报（ppdv_comm）

车载端与云平台（`PPDVapi`）之间的通信通道，已落地为可运行的 ROS2 (Python) 包
`ppdv_comm`：经 MQTT 主题 `ppdv/v1/{deviceId}/...`（协议见根
[docs/protocol.md](../../docs/protocol.md) v0.4；已覆盖遥测/指令主题与
v0.3/v0.4 新增的 `tele/track` 跟踪观测上行）
完成遥测上行、指令下行、跟踪观测上行、断网缓存补传与指令回执。传输/上行/下行/缓存逻辑不
import rclpy（依赖注入 clock/sender/logger），`comm_node.py` 只做参数读取、
订阅接线与生命周期管理。

## 职责（与设计定位一致）

- 通过 4G/5G 链路使用 MQTT 收发（EMQX 一机一密：`username=deviceId`、
  `password=设备密钥`，设备需先在管理后台登记，EMQX 需已执行
  `docker/emqx/bootstrap-emqx.sh` 配好认证/授权回调）
- 上报：状态/作业遥测、区域进入、告警、任务进度、指令回执、目标跟踪观测（`tele/track`，默认关闭）
- 接收：控制指令、任务下发、参数下发、点喷指令（ota/notify 只记日志）
- 断网缓存与重连补传（协议 §8：离线缓存按序补传，ts 为原采集时间）

## 已实现（P0 传输层 + `tele/track` 跟踪上行）

| 能力 | 说明 |
|---|---|
| 连接 | ClientId `edge-{deviceId}`、CleanSession=false、KeepAlive 30、LWT→`status`（信封格式，retained，QoS1）、上线发 `online:true` 覆盖遗嘱、1~30s 指数退避重连、重连后恢复 5 类下行订阅 |
| 上行 | `status`(retained)、`tele/state`(QoS0)、`tele/work`(QoS1)、`tele/zone`(QoS1，当前区域变化时)、`event/alarm`、`task/progress`、`cmd/ack`；`tele/env` 保留发送接口（QoS1、不缓存）本次不接数据源 |
| 跟踪上行 | `tele/track`（QoS1、不 retain、**默认关闭**）：感知跟踪事件聚合为巡检会话（`trackSessionId`）+ 出现（`episode`），按窗口分片发送；断网按会话合并补传，QoS1 PUBACK 确认后才回收缓存；15 类规范 key；不组装 `frameCount`/`lat`/`lon`/`positionSource`/`severity`/`inspectionId`/`imageRef` |
| 信封 | 固定键序 `{msgId, deviceId, ts, type, data}`；ts=采集时间 epoch ms，补传不改 ts |
| 缓存 | status 与 tele/env 不缓存，其余离线缓存（QoS0 的 tele/state 同样进缓存）；恢复后按序补传，中途失败保留剩余下轮再试；本轮补传条数 >0 即发 `COMM_RECOVERED` 告警（部分成功也发） |
| 下行 | `cmd/req`、`task/assign`、`config/set`、`spray/req`（`ota/notify` 只记日志）；每条回 `cmd/ack`，refId=下行 msgId；码表 0 成功 / 1 拒绝 / 2 过期 |
| 指令时效 | START/RESUME/RTL 超过 `stale_cmd_max_age_s`（默认 300s，参数可调）拒绝并回 2；ts 缺失/非数值不判过期 |
| ESTOP | 最高优先级：跳过下行串行队列、在 MQTT 回调线程立即执行并置抢占门——紧随其后的第一条 START/RESUME/RTL（即排队迟到的旧指令）被拒绝（ack code=1）且该条被拒即关门，此后云端可正常 START 恢复；ESTOP 本身不判时效 |
| 下行并发 | 下行经**单线程串行执行器**（SerialExecutor）按到达顺序处理（防旧指令晚于急停落地）；点喷 duration（1~3600 秒）由 comm 层计时到点自动补发 stop（模拟器 /api/spray 无时长参数，duration 是点喷唯一的自动停喷机制） |
| 去重 | 下行 msgId 512 容量滑动窗口；淘汰时机为窗口满后新 id 入队同时最旧 id 出队；出窗 id 的重复包视为新消息正常处理 |
| task/assign | 按 §6.2 解析**内联** `route`/`field`（[[lng,lat],...]，经度在前）与 `params`（taskId/fieldId/areaId 另取），不读 routeUrl；route 至少 2 点、field 允许空数组；解析结果发布到 ROS2 话题 `ppdv/task/assigned`（std_msgs/String，JSON）供导航消费；只解析、校验、打日志、回 ack，不映射导航执行 |
| config/set | `teleRateHz` 动态调整遥测频率（0.1~10 clamp，0/负值也收敛到 0.1）；`maxSpeed`/`geofence` 只记录、不执行 |
| 定位门控 | 快照 lat/lon 缺失（None）时跳过该轮 state/work 上报，不回填 (0,0)（云侧只把"非数值"当缺失，数值 0.0 会被当真实坐标入库）；zone/alarm 侧信道照常排空（progress 依赖快照与 taskId，无定位轮次随快照跳过）；断网恢复补传在快照检查之前执行（源暂无快照也不饿死补传） |

## 目标跟踪上行 `tele/track`（协议 §5.6，默认关闭）

`track_enabled=true` 时订阅 `ros_topic_track_events`（`std_msgs/String`，一条消息
一个 JSON 事件），聚合后经 QoS1 上报 `ppdv/v1/{deviceId}/tele/track`。

事件输入契约：

- `event`：`detection` / `session_start` / `session_end`（大小写不敏感）。
- `detection` 必填 `trackId`（1~64）、`class`（15 类规范 key）、`confidence`（0~1，
  拒绝 NaN/Infinity）、`frameTs`（正整数，与接收时刻偏差 ≤1 小时）。
- 可选 `boxX/boxY/boxW/boxH`（四字段齐全且非负才更新 bbox/轨迹）、
  `imageWidth/imageHeight`（正整数）、`state`（`ACTIVE`/`LOST`/`ENDED`/`REAPPEAR`）。
- 非法输入只丢弃并计数（同原因 10 秒一条 WARN），不抛异常、不中断会话。
- 本模块是事件契约的定义方：感知端尚未落地，联调时用 `ros2 topic pub` 注入。

会话与出现语义：

- `session_start` 只登记 `areaId`（ARMED）；**首条合法 detection 才创建会话**
  （不产生 0 出现的空会话）。`session_end` / `track_session_idle_s` 空闲 /
  节点关停结束会话；`trackSessionId` 形如 `TS-{deviceId}-{epochMs}-{seq}`，
  ≤64 且永不复用。
- **会话进行中收到 `session_start` 只记 WARN 并忽略，`areaId` 不切换**（感知端必须
  保证 start/end 成对；否则下一次巡检的区域会被记到上一个区域）。是否改为
  "先结束旧会话再开新会话"待感知端落地时拍板，改动须同步本节与单测。
- 同一 `trackId` 从进入到离开为一次出现；结束后再现必换新 `episodeId` 且
  `episodeNo + 1`；`trackState` 只发 `ACTIVE`/`ENDED`。
- 轨迹采样以"最后一次真正追加点"为基准：距该点不足 `track_trajectory_sample_ms`
  时只覆盖轨迹末点坐标（不改采样基准、不改该点时间戳），因此 10~30 FPS 的真实
  帧率也能得到多点轨迹；发送前按 `track_max_trajectory_points` 抽稀（首末点保留）。
- 结束序列：先把 ACTIVE 全置 ENDED 发送数据分片（`sessionEnd=false`），再发
  `tracks=[] + sessionEnd=true`；任一数据分片未确认时结束报文强制入缓存，保证
  "数据在前、结束在后"。

线程与发送：

- 跟踪事件回调只把报文原文 `post()` 入队（队列上限 200），由 `ppdv_comm.track`
  内部独立线程串行处理事件并按 500ms 驱动窗口。**PUBACK 等待（3s/条）不在 ROS
  单线程执行器上**，不会阻塞 `state/work/zone/alarm/progress` 订阅与
  `simulate_offline` 服务；`tick` 本身仍可能被一次发送序列拖长，但只占用该线程。
- 在线发送等 QoS1 PUBACK 才返回成功；超时/离线转本地缓存。离线补传按
  `trackSessionId` 合并旧窗口（缓存不随离线窗口数线性增长），`pending` 超过 200
  条时丢最旧并计数；`flush` 中 track 条目有 3s 总预算，剩余条目下轮再试，
  不会按 3s×N 拖住遥测线程。

## 数据源（source 参数可插拔）

- `source:=sim`：每个遥测轮轮询一次小车模拟器 `GET /api/state`
  （实际频率=遥测频率；`sim_poll_hz` 仅在高于遥测频率时作提示阈值，见参数表）
  组装快照，并把下行映射为模拟器 REST：
  - `cmd/req` → `POST /api/command {cmd, via:"local", args}`（via 必须 local，避免回环）
  - `spray/req` → `POST /api/spray {action:"on"|"off"}`（改名：start→on、stop→off）；
    duration 由 comm 层计时到点自动补发 stop（1~3600 秒，缺省/非法=持续喷至 stop）
  - `config/set` → 只调本节点遥测频率（不发 `/api/rate`）；maxSpeed/geofence 记录不执行
  - `task/assign` → **不转发**（模拟器纯本地模式下收不到 MQTT；其 `/api/task`
    只是推进当前任务的接口，不是任务下发）
  - zone 按模拟器 `/api/state` 返回的 `zones` 包围盒本地判定（复刻模拟器语义）；
    sim 源不产 `event/alarm`（模拟器 REST 拿不到内部告警队列）
- `source:=ros`：真车接口骨架（`sources/ros_state.py`）。话题名全部参数化，
  本次以 JSON String 过渡（字段名与协议 §5.2/5.3/5.5 一致），假定：

  | 话题（参数默认值） | 消息 | 协议字段 |
  |---|---|---|
  | `/ppdv/vehicle/state`（`ros_topic_state`） | std_msgs/String(JSON) | lat/lon/alt/heading/speed/rtkFix/mode/battery/taskId |
  | `/ppdv/vehicle/work`（`ros_topic_work`） | std_msgs/String(JSON) | spraying/flow/pressure/tankLevel/sprayedArea/dosage |
  | `/ppdv/vehicle/zone`（`ros_topic_zone`） | std_msgs/String | zoneCode |
  | `/ppdv/vehicle/alarm`（`ros_topic_alarm`） | std_msgs/String(JSON) | code/level/msg/lat/lon |
  | `/ppdv/vehicle/progress`（`ros_topic_progress`） | std_msgs/String(JSON) | taskId/taskStatus/progress/coveredArea |

  接真车时：建 `ppdv_msgs` 自定义消息后替换 `_build_ros_source` 里的适配层即可。
  cmd/spray 的真车执行接口 `RosStateSource.apply_command/apply_spray` 已注入
  DownlinkHandler（与 sim 源一致），真车控制模块接入前返回拒绝（ack code=1，
  有明确回执）。快照采用不可变合并（work/progress/state 三话题并发到达不互相
  覆盖、不撕裂）；work 先于 state 到达时暂存为占位快照，定位缺失不上报 (0,0)。

## 参数（`config/comm.yaml`，凭据绝不写文件）

| 参数 | 默认 | 说明 |
|---|---|---|
| `broker_host` / `broker_port` / `broker_tls` | localhost / 1883 / false | EMQX 地址 |
| `keepalive` | 30 | MQTT KeepAlive（协议 §2） |
| `device_id` | PPDV-0001 | 设备编号；环境变量 `PPDV_DEVICE_ID` 优先；为空拒绝启动 |
| `device_secret` | （空） | 设备密钥；**ROS2 参数可用**（`-p device_secret:=...`），环境变量 `PPDV_DEVICE_SECRET` 优先；为空拒绝启动（退出码 2） |
| `fw` | 1.3.0 | status 上报的固件版本 |
| `tele_rate_hz` | 1.0 | 状态遥测频率（0.1~10，config/set 可动态改） |
| `log_messages` | true | 报文级 TX/RX 日志（INFO 级，`[trace]` 前缀） |
| `log_level` | info | 节点日志级别（debug/info/warn/error，launch 已接线） |
| `source` | sim | 数据源：sim \| ros（其他值拒绝启动） |
| `sim_base_url` | http://127.0.0.1:8090 | 模拟器地址 |
| `sim_poll_hz` | 2.0 | 模拟器轮询上限（1~5；轮询在遥测轮内完成，实际频率=遥测频率，超出部分仅提示） |
| `stale_cmd_max_age_s` | 300 | START/RESUME/RTL 指令时效（负值按 0 处理=全部判过期） |
| `cache_dir` | cache | 断网缓存目录（`{cache_dir}/{device_id}.jsonl`） |
| `ros_topic_*` | 见上 | 真车 ROS2 话题名 |
| `track_enabled` | false | `tele/track` 总开关；false 时不订阅事件、不聚合、不发报文 |
| `ros_topic_track_events` | /ppdv/perception/track_events | 感知跟踪事件话题（`std_msgs/String` JSON），为空时跟踪上行保持关闭 |
| `track_window_s` | 2.0 | 聚合窗口长度（1.0~5.0） |
| `track_lost_timeout_ms` | 2000 | 无新样本判定一次出现结束（500~10000） |
| `track_session_idle_s` | 120 | 无合法 detection 自动结束会话（0~3600；0=禁用） |
| `track_trajectory_sample_ms` | 500 | 轨迹采样间隔（50~5000） |
| `track_max_trajectory_points` | 20 | 单条轨迹点数上限（2~20） |
| `track_max_tracks_per_message` | 50 | 单报文 `tracks` 上限（1~50） |

ROS2 服务：`ppdv/comm/simulate_offline`（std_srvs/SetBool）——断网演练开关。

## 运行（ROS2 Humble / Ubuntu 或板卡）

```bash
# 依赖：Python 3.10、paho-mqtt>=2.1,<3（pip install "paho-mqtt>=2.1,<3"，勿用
# rosdep——Humble 的 apt python3-paho-mqtt 是 1.x，不兼容 paho 2.x 回调 API）、
# std_msgs/std_srvs（rosdep 可装）、ROS2 Humble
cd PPDVedge/comm
colcon build --packages-select ppdv_comm
source install/setup.bash

# 方式一：launch（Node action 在 OpaqueFunction 内组装；参数文件默认取 share
# 里的 comm.yaml，source/device_id/sim_base_url/log_level 可用 launch 参数覆盖）
export PPDV_DEVICE_ID=PPDV-0001
export PPDV_DEVICE_SECRET=<管理后台登记的设备密钥>
ros2 launch ppdv_comm comm.launch.py source:=sim sim_base_url:=http://127.0.0.1:8090

# 方式二：直接 run，凭据用参数或环境变量（env 优先）
ros2 run ppdv_comm comm_node --ros-args --params-file config/comm.yaml \
  -p device_secret:=$PPDV_DEVICE_SECRET -p source:=sim
```

凭据注入优先级：环境变量 `PPDV_DEVICE_ID` / `PPDV_DEVICE_SECRET` > ROS2 参数
`device_id` / `device_secret` > `config/comm.yaml` 默认值；secret 为空时节点
以退出码 2 拒绝启动（systemd 可识别失败）。

断网演练：

```bash
ros2 service call /ppdv/comm/simulate_offline std_srvs/srv/SetBool "{data: true}"   # 断网（缓存）
ros2 service call /ppdv/comm/simulate_offline std_srvs/srv/SetBool "{data: false}"  # 恢复（按序补传 + COMM_RECOVERED）
```

## 联调前提（无 ROS2 环境时见下节）

1. `docker compose up -d` 起 EMQX（1883）；`PPDVapi` 先启动（EMQX 认证回调依赖后端）；
   执行 `bash docker/emqx/bootstrap-emqx.sh`。
2. 管理后台登记设备（如 `test01`），复制密钥填入 `PPDV_DEVICE_SECRET`。
3. 联调用本地模拟器（端口 8090），让它以 **「纯本地模式」**运行（把其
   `config.yaml` 的 broker 指向不可达端口即可，页面显示未连接）——由
   ppdv_comm **独占 MQTT 连接**，两者 clientId 均为 `edge-{deviceId}`，
   同时在线会互踢；模拟器的 `/api/dup`、`/api/bad` 是它自己上行链路的
   注入接口，本次用不上。
4. **同一 clientId 只能有一个模拟器实例在线**。

## 无 ROS2 环境的验证（Windows 开发机）

单测不依赖 broker/ROS：

```bash
cd PPDVedge/comm
python -m pytest test -q
```

真实链路联调用本地联调 runner（不入库）：复刻 `comm_node.py` 的接线
（MQTT/core 部分与 ROS 包完全一致，仅参数读取与话题发布被替换），在
Windows 上连真实 EMQX+PPDVapi+模拟器完成闭环验证；`comm_node.py` 本体的
ROS 接线（rclpy 参数/话题/服务）在板卡上冒烟。

## 已知缺口

- 云平台 `cmd/ack` 目前只记日志、不读 refId 回填指令状态；`task/progress`
  只广播不回写任务状态（根 README「已知缺口」）——车端按协议正常回执，不改云端。
- `tele/env` 有发送接口但未接数据源（QoS1 对齐协议 §3）；`ota/notify` 只记日志。
- `tele/track` 默认关闭；事件生产方（感知/YOLO）尚未落地，本包只固化输入契约，
  联调依赖板卡 `ros2 topic pub` 注入。
- `tele/track` 的 ROS2 冒烟（事件订阅、独立窗口线程、关停时结束序列上云）与云端
  落库/WS/前端联调尚未完成；已通过无 ROS 单测与报文级断言。
- 会话进行中收到 `session_start` 按"重复、忽略"处理（见上节契约）；感知端落地时
  需确认是否改为"先结束旧会话再开新会话"。
- `source:=ros` 为骨架：真车话题类型（ppdv_msgs）待建；cmd/spray 已接线到
  `RosStateSource.apply_*`（当前拒绝，ack code=1），真车控制模块接入后即生效。
- colcon build 与板卡冒烟未在 Windows 开发机执行（无 ROS2），见版本记录。
