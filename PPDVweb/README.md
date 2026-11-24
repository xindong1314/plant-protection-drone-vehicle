# PPDVweb · 植保无人车业务前端

植保无人车云平台的业务前端，面向监控大屏、数字驾驶舱、作业规划、巡检、风险、点喷和复查。基于 Vue 3 + Vite 5 + Pinia + Element Plus + ECharts + 天地图（CGCS2000），复用 `PPDVapi` 的 JWT 与业务接口。

项目级架构、端口和后端说明见根 [README.md](../README.md)。

## 页面

| 页面 | 路由 | 主要能力 |
|---|---|---|
| 监控总览 | `/dashboard` | 设备/在线/作业/告警 KPI、状态与电量图表、实时事件 |
| 数字驾驶舱 | `/cockpit` | 选车、实时状态、电量仪表、历史轨迹、远程控制 |
| 作业规划 | `/planning` | 选择已录入路线的区域与在线设备、预览边界/航线、参数配置、任务下发（未持久化） |
| 区域管理 | `/area` | 区域列表、CGCS2000 地块绘制/回显、维护与受保护删除 |
| 区域档案 | `/archive` | 区域风险状态、巡检/异常/点喷汇总与业务动态（复查不计入档案） |
| 巡检管理 | `/inspection` | 巡检分页、启动/完成、图片上传、识别结果录入 |
| 跟踪记录 | `/track` | 跟踪会话分页与筛选（区域/时间/设备/类别）、出现次数与去重目标、详情出现记录与轨迹查看 |
| 点喷任务 | `/spray` | 高风险区域任务、在线设备分配、执行和状态流转 |
| 喷后复查 | `/review` | 待复查任务、录入结论并联动区域风险 |
| 风险规则 | `/risk` | 查看/修改风险规则、重新计算区域风险 |
| 设备管理 | `/device` | 设备检索、状态、电量、模式、区域、遥测详情 |
| 数据报表 | `/report` | 时间范围汇总、区域风险表、浏览器端 CSV 导出 |

## 本地运行

先启动中间件和 `PPDVapi`（见根 [README.md](../README.md)），然后：

```bash
cd PPDVweb
npm install
npm run dev          # http://localhost:8082
```

开发服务器将 `/dev-api` 代理到 `127.0.0.1:8080`，并将 `/ws`（WebSocket）代理到后端。

- 登录账号：`admin/admin123`。
- 天地图：在 `.env.development` 填入 `VITE_TIANDITU_KEY`（到 lbs.tianditu.gov.cn 免费申请）。未配置时地图区域降级为占位，其余功能正常。天地图使用 CGCS2000，遥测 WGS84 直接叠加。

## 构建

```bash
npm run build
npm run preview
```

构建产物输出到 `dist/`。生产构建使用 `/prod-api` 和 `/ws/telemetry`，需要部署网关把这两个路径映射到 `PPDVapi`；仓库内没有 Nginx 配置。

## 认证与权限

- 登录调用 `/login`，JWT 存在 localStorage `ppdv_web_token`，Axios 以 `Authorization: Bearer <token>` 发送。
- 登录后调用 `/getInfo` 获取角色和权限；`*:*:*` 视为全部权限。
- 路由是静态的 `pageRoutes`，每条路由带 `meta.permissions`；守卫在 `src/router/index.js`。
- 页面导航在 `src/layout/index.vue` 的 `navItems`，新增页面必须同时修改路由和导航两处，`permissions` 保持一致。
- 页面内用 `useUserStore().hasPermission()` / `hasAllPermissions()` 控制操作按钮。

## 实时数据

- 监控总览和驾驶舱通过 `src/utils/ws.js` 连接 `/ws/telemetry?token=JWT`，断线 3 秒重连。
- 遥测 store 在 `src/stores/telemetry.js`，处理 `status`、`tele/state`、`tele/work`、`tele/zone`、`tele/track`（受 `ppdv.track.enabled` 控制）、`event/alarm`、`task/progress`。
- 管理后台的实时页默认手动刷新，打开“自动刷新(5s)”开关后才轮询；本前端使用 WebSocket。

## 代码入口

| 路径 | 说明 |
|---|---|
| `src/router/index.js` | 静态路由、权限守卫 |
| `src/layout/index.vue` | 侧边栏导航 |
| `src/api/` | 业务接口封装 |
| `src/store/user.js` | 登录态与权限 |
| `src/stores/telemetry.js` | 实时遥测状态 |
| `src/utils/request.js` | Axios 与错误处理 |
| `src/utils/ws.js` | WebSocket 连接与重连 |
| `src/utils/tianditu.js` | 天地图 JS API 加载 |
| `src/styles/index.scss` | 全局样式与 `--ag-*` 设计变量 |

## 注意事项

- 存在两套设备 API：`src/api/device.js`（`stateHistory`、`assignTask`）与 `src/api/ppdv/device.js`（`getStateHistory`）；**两套都导出同名 `listDevice`**，不同页面各用一套；改接口先看页面实际 import。
- `src/store/user.js`（单数）与 `src/stores/telemetry.js`（复数）并存，不要误以为可以合并。
- 大部分页面是纯 JS `<script setup>`；`dashboard`、`device` 和 `report` 使用 `<script lang="ts" setup>`，修改时保留 TS。
- 视觉使用 `--ag-*` 变量（主色 `#76b900`），桌面端布局，默认最小宽度 1080px（窗口 ≤1180px 时为 960px）。
- 没有 lint、单元测试或 e2e 脚本；提交前执行 `npm run build`。
- 作业规划下发的是内联地块/航线，后端不持久化任务。
