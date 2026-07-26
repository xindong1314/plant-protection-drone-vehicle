# 开发记录

> 本文件按时间倒序记录每次较大的开发里程碑。部署与联调步骤见 [deployment.md](deployment.md)，架构见 [architecture.md](architecture.md)。

---

## 2026-07-26 · PPDVweb 业务前端（大屏/驾驶舱/作业规划 + 实时 WebSocket）

在既有平台上新增业务前端 `PPDVweb`，并为后端补齐实时推送与任务下发。

### 关键决策

- **PPDVweb 独立于 PPDVadmin**：业务前端是可视化大屏/地图，不套若依，采用轻量 Vue3 + Vite + Pinia + Element Plus + ECharts + 高德 AMap；复用 PPDVapi 的 JWT 与业务接口，不重写后端。
- **实时用 WebSocket**：后端 `/ws/telemetry`（握手 `?token=JWT`，在 handler 内校验），遥测经 MQTT 到达即广播到大屏。

### 后端新增（PPDVapi）

- `spring-boot-starter-websocket`；`WebSocketConfig` + `TelemetryWebSocketHandler`（token 认证、会话集合、广播、自动清理）。
- `TelemetryIngestService` 在入库同时广播 `{deviceId,kind,ts,data}`（status/state/work/alarm/progress）。
- 任务下发：`CommandService.sendTask` + `TaskController` `POST /ppdv/device/{id}/task` → 发布 `task/assign`。
- `TokenService.getLoginUserByToken`（供 WS 用）；SecurityConfig 放行 `/ws/**`。

### 前端（PPDVweb）

- 工程骨架：登录（JWT）、布局导航、axios 拦截器、Pinia、路由守卫、vite 代理（`/dev-api`→8080，`/ws` WebSocket 代理），dev 端口 8082。
- **监控大屏**：KPI（总数/在线/作业中/告警）+ 高德地图全车位置 + 状态分布饼图 + 设备列表 + 实时告警流，WebSocket 驱动。
- **驾驶舱**：选车 + 实时状态仪表（ECharts 电量表）+ 历史轨迹地图 + 控制指令按钮。
- **作业规划**：高德 MouseTool 圈地块/画航线 → 组装任务 `POST /ppdv/device/{id}/task` 下发。
- 高德 Key 经 `.env` 的 `VITE_AMAP_KEY` 注入；未配置时地图降级为占位提示，其余功能正常。

### 联调验证（均通过）

| 项 | 结果 |
|----|------|
| 后端 build | 100 源文件编译通过 |
| 任务下发 `POST .../task` | 返回 msgId，发布到 task/assign |
| PPDVweb build | 2227 模块，全部页面编译通过 |
| dev 代理 | `/dev-api/login`、设备列表经 8082 代理返回 `code:200` |
| WebSocket 实时 | Node WS 客户端连上后，mosquitto 发一条 tele/state → 客户端即时收到广播 `WS_RECV{...}` |

### 遗留

- 高德地图需用户自备 Web 端 JS API Key（`VITE_AMAP_KEY`），未配置仅地图区域占位。
- 驾驶舱视频流、告警声光、作业报表导出等未做；作业规划的航线目前内联下发（未走 OSS routeUrl）。

---

## 2026-07-26 · 云平台全栈集成（若依 RBAC + MySQL/TDengine/EMQX/Redis）

提交：`0ca438d`（feat: 云平台全栈集成）。此前为 `e98215e`（Spring Boot 初始工程：设备管理 + MQTT 车云接入）。

### 目标

在保留 PPDVapi 自研 Spring Boot/JPA 骨架的前提下，按需嫁接生产级能力，并落地若依管理后台前端，打通"设备管理 + 实时监控 + 任务下发"云侧主线。

### 关键决策

- **后端保留自研骨架，不换若依 MyBatis 后端**：在自研 JPA 骨架上手写若依风格 RBAC 与接口契约（`code:200` + `AjaxResult`/`TableDataInfo`），使 RuoYi-Vue3 前端可开箱对接。
- **鉴权无状态化 + Redis 会话**：JWT 只携带登录标识 uuid，用户会话存 Redis，支持滑动续期、登出、在线用户。
- **时序与关系分库**：业务与 RBAC 走 MySQL（Flyway 版本化）；遥测明细走 TDengine（REST 连接，Windows 免原生客户端）。
- **一机一密走 EMQX HTTP 回调**：设备认证/授权由 EMQX 回调后端校验，单一数据源在后端 DB，无需向 broker 同步凭据。

### 已完成模块

**1. 基础设施（Docker）**
- 根 `docker-compose.yml`：MySQL 8、TDengine 3、EMQX 5、Redis 7，一键 `docker compose up -d`。
- `docker/emqx/api_keys.txt`（引导 API Key）、`docker/emqx/bootstrap-emqx.sh`（用 REST API 配置 EMQX HTTP 认证/授权）。

**2. 数据层（PPDVapi）**
- H2 → MySQL 8；引入 Flyway，`ddl-auto=none`。
- 迁移脚本：`V1__device.sql`（设备台账，含一机一密 `secret`）、`V2__rbac.sql`（若依 system 全表 + 种子）。
- 种子：管理员 `admin/admin123`、角色（admin/common）、部门/岗位、系统与业务菜单、常用字典（含 `ppdv_device_status`/`ppdv_work_mode`）。

**3. 安全与 RBAC（`security` / `system` 包）**
- Spring Security + JWT（jjwt 0.12），`TokenService`（Redis）、`JwtAuthenticationTokenFilter`、`SecurityConfig`（无状态、放行 `/login` `/captchaImage` `/api/emqx/**`）、`@ss` 权限 bean、`easy-captcha` 验证码（默认关闭）。
- 若依契约接口：`/login` `/logout` `/getInfo` `/getRouters` `/captchaImage`。
- 完整 system CRUD：用户、角色、菜单、部门、岗位、字典（类型/数据）、参数配置、个人中心。动态路由 `buildMenus` 对齐若依，支持目录/菜单/按钮与 ParentView 嵌套。

**4. 遥测时序（`telemetry` 包，TDengine）**
- `TdengineConfig`（独立 JdbcTemplate，不影响 MySQL 主源）、启动建库建超级表 `tele_state`/`tele_work`。
- 上行遥测在更新 MySQL 快照的同时写入 TDengine；`/ppdv/device/{id}/telemetry/state` 查询历史。

**5. 车云通信（`mqtt` / `emqx` 包，EMQX）**
- 后端 MQTT 客户端接 EMQX，订阅上行、发布下行。
- 一机一密：设备登记自动生成 `secret`；`/api/emqx/auth`（认证）、`/api/emqx/acl`（授权，设备仅限自身 `ppdv/v1/{deviceId}/#`）；后端服务账号标记 superuser。

**6. 管理后台（PPDVadmin）**
- 落地 RuoYi-Vue3；`.env.development` 标题改为"植保无人车管理后台"，dev 端口 8081，代理 `/dev-api` → `127.0.0.1:8080`。
- 新增业务页：`views/ppdv/device/index.vue`（设备管理：登记/删除/下发指令/重置密钥）、`views/ppdv/realtime/index.vue`（实时监控：状态/电量/位置 + 遥测历史），API 见 `api/ppdv/device.js`。

### 联调验证（均通过）

| 项 | 结果 |
|----|------|
| Flyway 迁移 + 启动 | V1/V2 应用成功，Tomcat 8080 起 |
| 登录链路 | `/login`→token、`/getInfo`（perms `*:*:*`、role admin）、`/getRouters`（完整路由树） |
| 系统列表/字典/部门/设备 CRUD | 均返回 `code:200` |
| 设备登记 | 自动生成一机一密 secret |
| 车云上行（正确密钥） | status/state/work 发布成功 → 设备转 ONLINE、快照更新、TDengine 有数据 |
| 车云上行（错误密钥） | `Connection Refused: not authorised`（一机一密生效） |
| 指令下行 | REST 返回 msgId，车端在 `cmd/req` 收到同一 msgId 信封 |
| 前端 build | 全部视图（含 ppdv 业务页）编译通过 |
| 前端代理 | `/dev-api/login`、`/captchaImage` 经 vite 代理打到后端返回 `code:200` |

### 环境与端口

MySQL 宿主机 **13306**（3306 被本地 mysqld 占用、3307–3406 段被 Windows 保留）、TDengine 6041、EMQX 1883/18083、Redis 6379、后端 8080、前端 8081。

> 本机存在系统 HTTP 代理，`curl` 测本地服务需加 `--noproxy 127.0.0.1,localhost`（否则 502）。

### 遗留 / 待办

- 业务模块：任务调度、地图航线/电子围栏、处方图、作业统计报表、告警通知、OTA（当前仅设备管理 + 监控）。
- 前端：PPDVweb 业务前端（监控大屏/驾驶舱）未建；实时监控暂为轮询，未接 WebSocket 与地图。
- 车载端：PPDVedge（ROS2）仅目录骨架。
- 若依周边：Excel 导入导出、代码生成器、定时任务、服务器/操作日志监控查询接口暂缓。
- 数据权限（data scope）为简化版（部门精确匹配），未做"本部门及以下"等完整隔离。
