# PPDVadmin · 植保无人车管理后台

`PPDVadmin` 是植保无人车云平台的管理端前端。项目以 RuoYi-Vue3 的界面和权限模型为基础，连接本仓库的 `PPDVapi` 后端，不使用若依官方后端。

主要用于：

- 用户、角色、菜单、部门、岗位、字典和参数管理
- 植保无人车设备登记、密钥维护与指令下发
- 设备实时状态和遥测历史查看
- 植保业务运营管理

实际支持的页面与接口以[项目模块台账](../docs/modules.md)为准。上游若依模板中存在的页面，不代表本项目后端已经实现对应能力。

## 技术栈

- Vue 3
- Vite
- Element Plus
- Pinia
- Vue Router
- Axios

## 本地运行

先按照[本地部署与联调指南](../docs/deployment.md)启动 Docker 基础设施和 `PPDVapi` 后端，然后执行：

```bash
cd PPDVadmin
npm ci
npm run dev
```

访问 <http://localhost:8081>，本地开发账号为 `admin/admin123`。

开发服务器会把 `/dev-api` 请求代理到 `http://127.0.0.1:8080`。相关配置位于：

- [`.env.development`](.env.development)
- [`vite.config.js`](vite.config.js)

## 构建

```bash
# 生产环境
npm run build:prod

# 预发布环境
npm run build:stage

# 本地预览构建结果
npm run preview
```

构建产物输出到 `dist/`。

## 代码入口

| 路径 | 说明 |
|---|---|
| `src/api/` | 后端接口封装 |
| `src/views/ppdv/` | 植保无人车业务页面 |
| `src/views/system/` | 系统权限与基础资料页面 |
| `src/router/` | 前端路由 |
| `src/store/` | Pinia 状态管理 |
| `src/utils/request.js` | Axios 请求与鉴权处理 |

## 开发约定

- 后端统一响应和权限标识遵循 `PPDVapi` 的若依兼容契约。
- 新增业务页面时，应同步补充 API 封装、菜单权限和模块台账。
- 不要把后端地址、生产账号或真实密钥硬编码到源码中。
- 提交前至少执行 `npm run build:prod`。

仓库级架构、启动顺序和 Pull Request 流程请从[根 README](../README.md)开始阅读。
