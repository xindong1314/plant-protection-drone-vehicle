# PPDV · 植保无人车云平台

面向设施农业/大棚的植保无人车软件栈，包含相互独立的两侧：云平台（`PPDVapi` 后端、`PPDVweb` 业务前端、`PPDVadmin` 管理后台）与车载端 `PPDVedge/`（ROS2）。本文档主体描述云平台；车载端见 [PPDVedge/README.md](PPDVedge/README.md)。

业务主线为演示闭环：

**区域建档 → 巡检采集 → 图像识别 → 区域风险评分 → 点喷任务 → 喷后复查 → 档案与报表**

定位是清水模拟点喷、可演示可记录，不承诺真实农药喷洒、全自动授粉/采摘或工业级无人化。

## 范围边界

- 云平台（`PPDVapi`、`PPDVweb`、`PPDVadmin`）与车载端（`PPDVedge/`）相互独立：不共享代码、配置或部署，只通过 MQTT 协议交互（见 [docs/protocol.md](docs/protocol.md)）。
- 云平台当前主线是演示闭环（见下）；车载端处于起步阶段：`PPDVedge/comm/` 已落地可运行的 ROS2 通信上报包 `ppdv_comm`（MQTT 遥测/指令/目标跟踪观测上行/断网补传，`tele/track` 默认关闭），其余子模块仍为设计文档与硬件实测记录。

## 系统架构

```
PPDVweb ── REST + WebSocket ─┐
                             ▼
PPDVadmin ── REST ──────► PPDVapi :8080
                             │
        ┌─────────────┬──────┴───────┬──────────────┐
        ▼             ▼              ▼              ▼
      MySQL         Redis        TDengine       MQTT / EMQX
   （业务/RBAC）    （会话）      （遥测时序）          │
                                                      ▼
                                         设备 / 车载端 PPDVedge（外部，独立方向）
```

- 上行遥测：设备 → EMQX → `PPDVapi` → 更新 MySQL 设备快照、写 TDengine、广播 WebSocket。
- 下行控制：`PPDVapi` → EMQX → `ppdv/v1/{deviceId}/cmd/req|task/assign|spray/req` → 设备。
- 两个前端不共享登录：`PPDVadmin` 令牌在 Cookie `Admin-Token`，`PPDVweb` 在 localStorage `ppdv_web_token`。

## 目录

| 目录 | 说明 |
|---|---|
| `PPDVapi/` | Spring Boot 3.2.5 / Java 17 云平台后端：Spring Security + JWT、JPA、Flyway、Redis、TDengine、EMQX MQTT |
| `PPDVweb/` | 业务前端：Vue 3 + Vite 5 + Pinia + Element Plus + ECharts + 天地图（CGCS2000） |
| `PPDVadmin/` | 管理后台：RuoYi-Vue3（Vue 3 + Vite 6 + Element Plus），动态菜单与 RBAC |
| `PPDVedge/` | 车载端 ROS2 方向：`comm/` 已落地 `ppdv_comm` 通信上报包（MQTT 遥测/指令/目标跟踪观测上行/断网补传，`tele/track` 默认关闭），其余为设计文档与硬件实测记录，与云平台相互独立 |
| `docker-compose.yml` | 本地 MySQL / TDengine / EMQX / Redis 编排 |
| `docker/emqx/` | EMQX 一机一密初始化脚本与引导密钥 |

## 快速开始

环境要求：JDK 17、Maven 3.9、Node 18+、Docker。

```bash
# 1. 中间件
docker compose up -d
docker compose ps                  # 4 个服务 healthy

# 2. 后端（首次启动 Flyway 建表并注入 admin/admin123）
cd PPDVapi
mvn spring-boot:run                # → 8080

# 3. 配置 EMQX 一机一密（后端起好后执行一次）
cd ..
bash docker/emqx/bootstrap-emqx.sh

# 4. 管理后台（admin 未跟踪 package-lock.json——被 PPDVadmin/.gitignore 忽略，干净检出用 npm install）
cd PPDVadmin
npm install
npm run dev                        # → 8081

# 5. 业务前端
cd ../PPDVweb
npm install
npm run dev                        # → 8082
```

默认账号：`admin/admin123`。登录验证码默认关闭（`ppdv.captcha.enabled=false`）。

> 本机若有系统代理，`curl` 访问本地服务会返回 502，需加 `--noproxy 127.0.0.1,localhost`。

## 端口与默认配置

| 组件 | 端口 | 说明 |
|---|---|---|
| PPDVapi | 8080 | REST、WebSocket `/ws/telemetry` |
| PPDVadmin | 8081（开发） | 若依管理后台 |
| PPDVweb | 8082（开发） | 业务前端 |
| MySQL 8 | 13306 → 容器 3306 | 宿主机 3306 已被占用，故映射到 13306 |
| TDengine 3 | 6041（REST）、6030 | taosAdapter REST，免原生客户端 |
| EMQX 5 | 1883、18083、8083、8883 | MQTT、Dashboard、MQTT over WebSocket、TLS |
| Redis 7 | 6379 | 登录会话、验证码、在线用户 |

前端开发环境统一请求 `/dev-api`，由 Vite 代理到 `127.0.0.1:8080`；生产构建使用 `/prod-api`。仓库内没有 Nginx/网关配置，部署时需自行把 `/prod-api` 和 `/ws` 映射到后端。

## 核心业务闭环

| 环节 | 规则 |
|---|---|
| 区域 | 区域编号唯一；边界为 CGCS2000 的 `[[lng,lat],...]`，可在创建后补录（新增时边界可为空）；作业路线由区域页"录入路线"随 `tele/state` 遥测录制，按区域持久化到 `route_json`（一个区域一条路线，可复用，重新录入即覆盖）；无法转换的历史坏边界保留 GCJ-02，前端不渲染、编辑时需重画或清除边界；删除前检查巡检/图片/识别/点喷/复查引用；创建时同步建档案。 |
| 巡检 | 仅在线设备可开始；`IN_PROGRESS` 完成后变 `COMPLETED` 并累加区域档案；只有进行中的巡检能上传图片。 |
| 识别 | 识别类别是中文自由字符串，风险引擎只认 **虫害/病斑/萎蔫/黄化/斑点**；新增类别需同步 `RiskEvaluationService.CATEGORY_KEY` 和 `risk.score.*` 配置，置信度必须在 `[0,1]`。 |
| 风险 | 规则在 `coer_risk_config`（Flyway V5 种子）：检测分上限 80，阈值 `suspected=21`、`high=51`；状态为 `NORMAL` / `SUSPICIOUS` / `HIGH_RISK`，复查还可能写入 `SPRAYED_PENDING_REVIEW`。 |
| 点喷 | 仅 `HIGH_RISK` 区域可创建；同一区域同时只允许一个活动任务；分配设备要求在线；执行只支持 `start`，时长 1–3600 秒，经 MQTT `spray/req` 下发。状态机：`PENDING→ASSIGNED→IN_PROGRESS→COMPLETED/FAILED`（`PENDING`/`ASSIGNED` 也可直接转 `FAILED`）；`COMPLETED` 会自动生成一条 `NEED_RETRY` 复查记录并把区域置 `SPRAYED_PENDING_REVIEW`（该状态的主要来源），`FAILED` 将区域恢复 `HIGH_RISK`。 |
| 复查 | 仅 `COMPLETED` 的点喷任务可录入；喷前/喷后图片必须属于该区域且不能相同。结论联动：`PASSED→NORMAL`、`FAILED→HIGH_RISK`、`NEED_RETRY→SPRAYED_PENDING_REVIEW`。 |
| 档案/报表 | 巡检、异常、点喷由 `ArchiveService` 累加到区域档案（次数与最近时间）；复查不进入档案计数，只通过风险状态联动刷新档案的 `lastRiskStatus`，并计入 `ReportService` 汇总。`ReportService` 返回 JSON 汇总，CSV 由 `PPDVweb` 报表页在浏览器端生成。 |

修改状态机或校验规则时，必须同步更新 `PlantProtectionServiceTest`、`RiskEvaluationServiceTest`、`InspectionServiceTest`、`AreaServiceTest`、`RouteRecordingServiceTest`、`ArchiveServiceTest`、`RiskConfigServiceTest`、`FileStorageServiceTest` 以及 `PPDVweb` 的标签和按钮（admin 仅 device/realtime，无这些业务页面）。

## 后端 API 与权限

统一响应：

- 成功：`{ "code": 200, "msg": "...", "data": ... }`
- 分页：`{ "code": 200, "rows": [...], "total": N }`

| 前缀 | 用途 |
|---|---|
| `/login`、`/getInfo`、`/getRouters`、`/logout` | 若依契约登录与会话 |
| `/system/**` | 用户、角色、菜单、部门、岗位、字典、参数、公告、个人中心 |
| `/monitor/**` | 在线用户、操作日志、登录日志 |
| `/ppdv/device/**` | 设备台账、密钥、控制指令、任务下发、遥测历史 |
| `/ppdv/area/**`、`/ppdv/inspection/**` | 区域（含路线录入/停止/取消/查询/批量路线）、巡检、图片、识别结果 |
| `/ppdv/track/**` | 跟踪会话与轨迹查询（车端 `tele/track`，P1） |
| `/ppdv/risk/**` | 风险配置与区域评分 |
| `/ppdv/task/spray/**`、`/ppdv/task/review/**` | 点喷任务与喷后复查 |
| `/ppdv/archive/**`、`/ppdv/report` | 区域档案、汇总报表 |
| `/api/emqx/**` | EMQX 设备认证/授权回调（无 JWT） |
| `/uploads/**` | 巡检图片静态访问 |

业务接口统一使用 `@PreAuthorize("@ss.hasPermi('ppdv:<模块>:<动作>')")`。新增权限点必须在新的 Flyway 迁移里补 `sys_menu` 与角色授权。分页参数为 1 基的 `pageNum/pageSize`，`pageSize` 上限 500，排序用 `orderByColumn/isAsc`。

## 前端说明

### PPDVweb（业务前端）

12 个页面：监控总览 `/dashboard`、数字驾驶舱 `/cockpit`、作业规划 `/planning`、区域管理 `/area`、区域档案 `/archive`、巡检管理 `/inspection`、跟踪记录 `/track`、点喷任务 `/spray`、喷后复查 `/review`、风险规则 `/risk`、设备管理 `/device`、数据报表 `/report`。

- 路由写在 `src/router/index.js` 的 `pageRoutes`，同时要在 `src/layout/index.vue` 的 `navItems` 增加导航项，两处 `permissions` 保持一致。
- 权限来自登录后的 `/getInfo`，使用 `useUserStore().hasPermission()` / `hasAllPermissions()`。
- 实时数据经 `/ws/telemetry?token=JWT` 接收；管理后台实时页默认是手动刷新，打开“自动刷新(5s)”开关后才每 5 秒轮询。
- 地图依赖 `VITE_TIANDITU_KEY`（`.env.development`，到 lbs.tianditu.gov.cn 免费申请）；未配置时地图降级为占位，其余功能正常。天地图使用 CGCS2000，遥测 WGS84 视为一致直接叠加。
- 视觉使用 `src/styles/index.scss` 的 `--ag-*` 变量（主色 `#76b900`），桌面端布局，最小宽度 1080px。
- 已知重复模块：`src/api/device.js` 与 `src/api/ppdv/device.js` 是两套设备 API（函数名也不同：`stateHistory` vs `getStateHistory`，且两套都导出同名 `listDevice`）；`src/store/user.js`（单数）与 `src/stores/telemetry.js`（复数）并存。改接口先看页面实际 import。
- 前端没有 lint、单测或 e2e 脚本，生产构建是验证门槛。

### PPDVadmin（管理后台）

- 使用 RuoYi-Vue3 的动态路由，菜单来自后端 `/getRouters` 与 `sys_menu` 表；令牌存 Cookie `Admin-Token`。
- 业务视图只有 `device` 与 `realtime` 两个，均注册了页面菜单；区域、巡检、点喷、风险、档案等业务能力仅在业务前端 PPDVweb 提供（admin 的 6 个未注册业务视图及其 `api/ppdv` 文件已清理）。
- admin 已无地图页；天地图与地理解析工具（`tianditu.js`/`geo.js`）仅 PPDVweb 使用，admin 侧副本已随 v1.3 清理删除。
- 若依的定时任务、代码生成页面与 API 已随接口清理删除（后端本就无对应实现；仅 `src/utils/generator/` 表单构建工具残留，零引用死代码）；Swagger 视图为 iframe 占位，无后端支撑，不能视为可用模块。
- `npm run build:stage` 仍使用若依默认的 `/stage-api` 和品牌配置，当前不作为可用环境；日常使用 `npm run build:prod`。

## 车云通信（MQTT）

统一主题前缀 `ppdv/v1/{deviceId}/...`，消息信封为 `{msgId, deviceId, ts, type, data}`。

当前代码实际订阅/发布的主题：

| 方向 | 主题 |
|---|---|
| 上行 | `status`、`tele/state`、`tele/work`、`tele/env`、`tele/zone`、`event/alarm`、`task/progress`、`cmd/ack`、`tele/track`（云端 `ppdv.track.enabled` 与车端 `track_enabled` 均默认关闭） |
| 下行 | `cmd/req`、`task/assign`、`spray/req` |

实现边界（字段级消费明细见 [docs/protocol.md](docs/protocol.md) 各消息的"云平台消费"标注，协议草案已升 v0.4 与实现对齐）：

- `event/alarm` 只记日志并广播，不落库；`cmd/ack` 只记日志，不处理 `refId`；`task/progress` 只广播，不更新任务状态。
- `tele/env`（温度/湿度/光照/土壤湿度/CO2）写 TDengine `env_sensor`；`tele/zone` 的 `zoneCode` 更新 `device.current_zone`；`spray/req` 由点喷任务执行链路下发（`action=start`、`areaCode`、`duration` 1–3600 秒）。
- `config/set` 只有常量定义，没有调用方；`ota/notify` 尚未实现。
- 入站解析只读取 `data` 与 `ts`，`deviceId` 从主题解析，不校验 `msgId`/`deviceId`/`type`，没有去重存储。`status` 必须用信封格式（裸 `{"online":true}` 无 `data` 包裹会被误判为下线，见 protocol.md §2）。例外：`tele/track` 走独立 `TrackIngestService`（见下条）。
- `tele/track`（P1 目标跟踪数据上云，默认关闭，协议 v0.4）：`ppdv.track.enabled=true` 时才订阅与处理。`msgId` Redis 去重（键 `track:msg:{deviceId}:{msgId}`，TTL 24h，处理失败回删键让 QoS1 补传可重放）+ DB 唯一键兜底；校验信封 deviceId、区域（`areaId` → `device.currentZone`，都不可用则丢弃，但携带 `sessionEnd=true` 时即使 `tracks=[]` 或全部 track 校验失败也会结束既有会话：区域可解析照常 `apply`，不可解析走 `endSession` 兜底）、15 类类别（未知落 `__unknown__`、旧 5 类中文名拒识）、置信度/bbox/`tracks ≤ 50`/单条 ≤256KB。身份模型为"一次出现=一条观测"（episode）：按 `(deviceId, trackSessionId, episode_key)` upsert `coer_detection_track`，同一 episode 取最早 firstSeen/最新 lastSeen/max 置信度、bbox/图像尺寸/轨迹缺省不覆盖已有值、`__unknown__` 不覆盖已知类别、乱序不新建行，不同 episode 即使 `trackId` 相同也新增一行（`trackId` 仅展示/关联，`track_key` 仅含于复合普通索引 `idx_episode_track(device_id, track_session_id, track_key)`，无 track_key 前导索引）；`episodeId` 缺失时兜底 `trackId@firstSeenTs` 并告警（`firstSeenTs` 缺失/非法或兜底键超 64 字符时直接拒收该 track，`ppdv.track.require-episode=true` 时一律拒收）。巡检会话由 data 级 `sessionEnd=true` 结束（`end_reason=session_end`，不可复归，迟到补传仅落库不回退状态；单条 episode `trackState=ENDED` 只表示本次出现结束）或云端空闲超时结束（`end_reason=idle`，`TrackSessionIdleCleaner` 按 `ppdv.track.session-idle-minutes`（默认 15 分钟）清扫，空闲判定按服务器侧最后活动时间 `last_activity_ts`（apply 时写入，车端时钟超前不影响；时钟滞后的误判由可复归兜底），收到新活动自动复归 ACTIVE；先被 idle 结束的会话再收车端 `sessionEnd` 会把 `end_reason` 升级为 `session_end`、`endTs` 取消息 ts，之后不可复归）；会话 `areaId` 跟随最新消息。**帧数（`frameCount`）已删除：不再上传、不再落库、不再展示**。事务提交后经 WebSocket 广播 `kind=tele/track` 摘要（`episodeCount`=会话累计出现次数，状态只下发 `status`；空闲清扫结束的会话同样广播），并以异步去抖任务（同区域持续有消息时每满 `ppdv.track.shadow-max-delay-ms`（默认 30 秒）强制刷新一次）输出影子分日志：每条 episode 一份证据、同类跨 episode 按 `episode-aggregation`（`NOISY_OR` 默认有界 / `SUM` 封顶 80）聚合、contributions 按 `class@sessionId@episodeKey`（不改变风险总分；`shadow-in-evaluate=true` 时追加 `trackScore/trackContributions/trackEvidenceStatus` 三个字段，总分不变）。查询接口 `GET /ppdv/track/**` 需 `ppdv:track:query`（会话列表时间筛选按"会话区间与查询区间有交集"命中，类别筛选用 EXISTS 子查询下推数据库，口径为会话级（任意时刻出现过该类即命中，不随 from/to 收紧）；会话聚合走数据库 GROUP BY 投影，汇总返回 `episodeCount` 出现次数与 `distinctTrackCount` 去重目标数；会话详情最多返回前 2000 条出现记录（`MAX_DETAIL_TRACKS`），摘要 `episodeCount/distinctTrackCount/maxConfidence` 仍为数据库累计值）；类别目录 `GET /ppdv/track/classes` 为临时 15 类常量，待 Q1 类别注册表落地后替换。并发限定：落库按单 MQTT 回调线程串行消费设计，多实例/并行消费下同一 episode 并发 INSERT 可能撞唯一键 `uk_episode`（当前不触发）。注意：MQTT 为 `cleanSession=false`，关闭 `ppdv.track.enabled` 重启后 broker 持久会话仍残留 `tele/track` 订阅并继续推送（后端无消费方仅 debug 忽略），彻底停推需在 broker 端清理该客户端的持久订阅。
- 作业路线不再网页手绘：区域页"录入路线"由后端随 `tele/state` 遥测录制小车坐标（单实例内存会话，`RouteRecordingService`，点"结束"后落库），按区域持久化到 `coer_greenhouse_area.route_json`（一个区域一条路线，可复用，重新录入即覆盖，不落中间表）；会话有空闲超时（10 分钟，按服务器时钟的最近有效遥测判定——静止停车、点数/字节封顶、补传旧点都不会误清会话）/最大时长（2 小时）；补传旧遥测只按设备时钟的最近入库点 ts 丢弃（首点无条件接受，设备时钟偏斜不丢点）。删除区域先删区域、成功后再联动取消会话（删除被关联数据 409 拒绝时会话保留），删除设备联动取消会话；点数不足时"结束"保留会话可继续补点，取消幂等；"结束"的落库在会话锁内完成，与取消/删区域并发不会出现"取消成功但路线仍被保存"。`/ppdv/area/list` 只返回路线元信息（hasRoute/routePointCount/routeRecordedAt，点数读 `route_point_count` 冗余列，不下发 routeJson 全文），完整路线经 `GET /ppdv/area/{id}/route` 按 id 拉取，区域页地图经 `GET /ppdv/area/routes` 一次批量拉取全部已录路线。作业规划从数据库读取该路线与区域边界组装 `field/route/params` 下发到 `task/assign`（正在录制路线的设备返回 409，不允许下发；下发的任务本身仍不落库）；点喷任务则是持久化闭环（`coer_spray_task` → 状态机 → `coer_review_record`），三者不要混淆。
- `tele/state` 的 MySQL 快照更新经纬度、电量、模式，并同时把设备置为 `ONLINE`、刷新 `lastSeenAt`（非数值经纬度按缺失处理，不回落 (0,0)）；`rtkFix`、`taskId` 等字段未持久化。路线录制会话期间的 `tele/state` 坐标按 7 位小数取整后累积到对应区域的路线（仅内存，结束录入后落库，补传旧遥测按 ts 丢弃）。
- 区域边界为 CGCS2000（V10 迁移已把存量 GCJ-02 逆变换；无法转换的非法 JSON/顶点格式/少于 3 点的行保留 GCJ-02，前端不叠加显示，编辑时需重新圈定）；遥测协议约定 WGS84，CGCS2000 与 WGS84 视为一致，直接叠加显示。区域可先建后补边界；新增边界与已录路线一律按 CGCS2000 存储（遥测 WGS84 视同）。编辑已有边界的区域时边界与坐标系绑定：保持原边界（null）或原样回传同值旧边界不允许改坐标系（同值回传 + 改标 CGCS2000 返回 400），重新圈定或显式清除边界（`boundaryJson` 传空字符串）后才可归位 CGCS2000；带新边界但未声明坐标系时后端按 CGCS2000 存储。

EMQX 一机一密：设备 `username=deviceId`、`password=设备密钥`；设备只能访问自己的主题前缀；后端服务账号为 superuser。设备首次遥测会自动建档（无密钥），正式接入应在管理后台登记设备。

本地模拟车端：

```bash
NET=$(docker inspect ppdv-emqx --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
docker run --rm --network "$NET" eclipse-mosquitto mosquitto_pub \
  -h ppdv-emqx -p 1883 -u PPDV-0001 -P <SECRET> -q 1 \
  -t ppdv/v1/PPDV-0001/tele/state \
  -m '{"deviceId":"PPDV-0001","ts":1785000000000,"type":"state","data":{"lat":30.12,"lon":120.65,"battery":88,"mode":"AUTO"}}'
```

## 数据存储

- MySQL：业务与 RBAC 数据，Flyway `V1`–`V15` 管理表结构，`ddl-auto=none`。不要修改已执行迁移；校验和失败需重建数据库（V10 为原地修正，已执行旧 V10 的环境需重建库或 `flyway repair`；V11 新增 `coer_greenhouse_area.route_json/route_recorded_at` 并删除 A1~B3 默认区域及其档案行；V12 置空指向已删除区域的 `device.current_zone` 悬空引用；V13 新增路线点数冗余列 `route_point_count` 并回填，由 `saveRecordedRoute` 维护；V14 新增跟踪会话表 `coer_track_session`（含 `end_reason` 结束来源列）、跟踪观测表 `coer_detection_track`（唯一键 `(device_id, track_session_id, episode_key)`，含 `episode_no`，无帧数列，`track_key` 仅含于复合普通索引 `idx_episode_track(device_id, track_session_id, track_key)`（无 track_key 前导索引）+ `track_session_id` 单列索引 `idx_session`），另新增 `ppdv:track:query` 权限点；V15 为 V14 的修订增量，补 `coer_track_session.last_activity_ts`（服务器侧最后活动时间列，空闲清扫按它判定）并把两张跟踪表 `device_id` 放宽为 `VARCHAR(64)`（与 `device.device_id` 一致）——已执行过旧版 V14 的环境启动时自动补 V15，无需重建库）。
- TDengine：超级表 `tele_state`、`tele_work`、`env_sensor`，按设备建子表。
- 本地上传：巡检图片写入 `ppdv.upload.base-dir`（默认后端工作目录下 `./uploads`），按日期分目录；只接受 JPEG/PNG/GIF/WebP 并校验文件头；通过 `/uploads/**` 静态访问。
- Redis：JWT 登录会话、验证码、在线用户。

## 构建与测试

```bash
# 后端：164 个单测（13 个 Mockito 类 + 1 个最小 Spring 上下文冒烟测试），不需要 Docker 或 Spring Boot 应用上下文
cd PPDVapi
mvn test
mvn -Dtest=AreaServiceTest test

# 业务前端
cd PPDVweb
npm run build

# 管理后台（没有 build 脚本）
cd PPDVadmin
npm run build:prod
```

若 `mvn test` 的 testCompile 报“主代码包不存在”，先 `mvn clean test` 清掉陈旧 `target/`。

车载端 `PPDVedge/comm` 的单测与运行方式见 [PPDVedge/comm/README.md](PPDVedge/comm/README.md)，不套用云平台构建门槛。

## 已知缺口

- 告警、指令回执未持久化；`task/progress` 只广播，不回写点喷任务状态。
- 作业规划下发的任务没有可查询的生命周期（`task/progress` 只广播，不回写；点喷任务有完整闭环）；区域路线与边界已按区域持久化（`route_json`/`boundary_json`）。
- 视觉识别模型/服务不在本仓库。
- 跟踪数据（`tele/track`）为 P1：云端只做能收/能存/能查/能实时看到，未接关键帧/图片、未参与风险总分（影子分仅日志），P2 正式评分待 Q1 证据聚合落地。车端 `PPDVedge/comm` 已按协议 v0.4 实现 `episodeId`（一次出现一个、再次出现换新）与 `sessionEnd` 上报（`track_enabled` 默认关闭），但板卡 ROS2 冒烟与云端联调尚未完成；旧车端缺失 `episodeId` 时云端兜底 `trackId@firstSeenTs`（告警 `TRACK_EPISODE_FALLBACK`；`firstSeenTs` 缺失/非法或兜底键超 64 字符时直接拒收），联调通过后再置 `ppdv.track.require-episode=true` 收紧。同一 episode 的类别归属（当前取最新非 `__unknown__`）与置信度归属（当前取 max）可能来自不同观测，P2 正式评分接入前需业务定规则。
- 对象存储、OTA、视频流、告警通知未接入。
- 前端没有组件测试或端到端测试。

## 维护约定

- 以代码为事实来源：迁移看 `PPDVapi/src/main/resources/db/migration/`，MQTT 主题看 `PPDVapi/src/main/java/com/ppdv/api/protocol/Topics.java`，前端路由看 `PPDVweb/src/router/index.js`，菜单/权限看 Flyway 种子与 `@PreAuthorize`。
- 新增/修改模块、迁移、主题、页面路由或权限点时，同一提交更新本 README（协议与长语义同步 `docs/protocol.md`；代理规则变化才更新 `AGENTS.md`，两份文档不重复记录同一事实）。
- README 记录稳定结论，不复制容易变化的完整清单；具体接口与实现以代码为准。
