# PPDVadmin · 植保无人车管理后台

`PPDVadmin` 是植保无人车云平台的管理端前端。项目以 RuoYi-Vue3 的界面和权限模型为基础，连接本仓库的 `PPDVapi` 后端，不使用若依官方后端。

主要用于：

- 用户、角色、菜单、部门、岗位、字典、参数和公告管理
- 植保无人车设备登记、密钥维护与指令下发
- 设备实时状态和遥测历史查看

区域、档案、巡检、点喷、风险等业务能力仅在业务前端 PPDVweb 提供；admin 的业务视图只有设备管理与实时状态两个。

项目级架构、端口和业务说明见根 [README.md](../README.md)。上游若依模板中存在的页面，不代表本项目后端已经实现对应能力。

## 技术栈

- Vue 3
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios

## 本地运行

先按根 [README.md](../README.md) 启动 Docker 基础设施和 `PPDVapi` 后端，然后执行：

```bash
cd PPDVadmin
npm install
npm run dev
```

访问 <http://localhost:8081>，本地开发账号为 `admin/admin123`。

> 仓库未跟踪 `package-lock.json`，干净检出时请使用 `npm install`；只有本地已有 lockfile 时才适合 `npm ci`。

开发服务器会把 `/dev-api` 请求代理到 `http://127.0.0.1:8080`。相关配置位于：

- [`.env.development`](.env.development)
- [`vite.config.js`](vite.config.js)

## 构建

```bash
# 生产环境
npm run build:prod

# 预发布环境（当前仍使用若依默认 /stage-api 与品牌配置，未接入本项目后端）
npm run build:stage

# 本地预览构建结果
npm run preview
```

构建产物输出到 `dist/`。日常验证以 `npm run build:prod` 为准。

## 代码入口

| 路径 | 说明 |
|---|---|
| `src/api/ppdv/` | 植保业务接口封装 |
| `src/api/system/`、`src/api/monitor/` | 系统权限与监控接口封装 |
| `src/views/ppdv/` | 植保业务页面：device、realtime |
| `src/views/system/` | 系统权限、基础资料、公告 |
| `src/router/` | 常量路由；业务路由由后端 `/getRouters` 动态生成 |
| `src/store/` | Pinia 状态管理 |
| `src/utils/request.js` | Axios 请求与鉴权处理 |

## 业务页面现状

- 已注册菜单、可直接访问：设备管理 `/ppdv/device`、实时状态 `/ppdv/realtime`；v1.3 已删除六个未注册 `sys_menu` 的业务视图（区域、档案、巡检、点喷、风险配置、风险评价）。
- 实时状态页默认是手动刷新；打开“自动刷新(5s)”开关后才每 5 秒轮询，不是 WebSocket。
- 若依的定时任务、代码生成页面与 API 已随 v1.3 清理删除（后端本就无对应实现）；仅 `src/utils/generator/` 表单构建工具残留（零引用死代码，与根 README 一致）。Swagger 视图为 iframe 占位，不要假设可用。

## 开发约定

- 后端统一响应和权限标识遵循 `PPDVapi` 的若依兼容契约。
- 新增业务页面时，应同步补充 API 封装、页面视图、`sys_menu` 菜单迁移和角色授权，并更新根 README（代理规则本身变化时才改 `AGENTS.md`）。
- 不要把后端地址、生产账号或真实密钥硬编码到源码中。
- 提交前至少执行 `npm run build:prod`。

仓库级架构、启动顺序和维护约定请从根 [README.md](../README.md) 开始阅读。
