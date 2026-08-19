# PlantProtectionDroneVehicle · 植保无人车软件系统

面向地面植保喷洒机器人的车云一体化软件系统，覆盖设备接入、实时遥测、远程控制、作业规划、植保业务管理和数据报表。

系统分为应用前端、云平台后端和车载端三层。车载端通过 MQTT 与云平台交换遥测和指令，前端通过 REST API 与 WebSocket 访问云平台。

## 项目组成

| 目录 | 职责 | 技术栈 | 当前状态 |
|---|---|---|---|
| [`PPDVweb/`](PPDVweb/) | 业务前端：监控总览、驾驶舱、作业规划、设备和报表 | Vue 3、Vite、Element Plus | 可运行 |
| [`PPDVadmin/`](PPDVadmin/) | 管理后台：权限、设备台账、实时状态和运营管理 | Vue 3、Vite、Element Plus | 可运行 |
| [`PPDVapi/`](PPDVapi/) | 云平台后端：RBAC、设备、遥测、MQTT 和植保业务接口 | Java 17、Spring Boot 3.2 | 可运行 |
| [`PPDVedge/`](PPDVedge/) | 车载端：感知、导航、控制、喷洒、安全和通信 | ROS 2、C++/Python | 规划中，暂无可运行节点 |
| [`docker/`](docker/) | EMQX 初始化等本地基础设施脚本 | Docker、Shell | 可运行 |
| [`docs/`](docs/) | 架构、模块、部署、协议和协作规范 | Markdown | 持续维护 |

各模块的准确完成度和已知缺口见[项目模块台账](docs/modules.md)。

## 环境要求

开始前请安装：

- Git
- JDK 17 和 Maven 3.9+
- Node.js 当前 LTS 版本和 npm
- Docker Desktop 或其他支持 Docker Compose 的运行环境
- Git Bash 或 WSL（仅在 Windows 上执行 EMQX 初始化脚本时需要）

## 快速启动

以下命令均从仓库根目录开始执行。首次安装依赖和拉取镜像需要联网。

### 1. 启动基础设施

```bash
docker compose up -d
docker compose ps
```

确认 MySQL、Redis、TDengine 和 EMQX 正常运行后，再启动应用服务。

### 2. 启动后端

```bash
cd PPDVapi
mvn spring-boot:run
```

后端启动时会通过 Flyway 初始化本地数据库。

### 3. 启动管理后台

另开一个终端：

```bash
cd PPDVadmin
npm ci
npm run dev
```

### 4. 启动业务前端

另开一个终端：

```bash
cd PPDVweb
npm ci
npm run dev
```

完整的 EMQX 一机一密配置、地图配置和车云联调步骤见[本地部署与联调指南](docs/deployment.md)。

## 本地访问

| 服务 | 地址 | 说明 |
|---|---|---|
| 业务前端 | <http://localhost:8082> | 监控、驾驶舱、规划和报表 |
| 管理后台 | <http://localhost:8081> | 系统与设备管理 |
| 后端 API | <http://localhost:8080> | REST API 与 WebSocket |
| EMQX Dashboard | <http://localhost:18083> | MQTT 管理界面 |

本地开发环境的系统登录账号为 `admin`，密码为 `admin123`。该账号和仓库中的默认密码仅用于本地开发，不得用于生产环境。

## 常用验证

提交 PR 前，至少运行与改动范围对应的验证：

```bash
# 后端测试
cd PPDVapi
mvn test

# 管理后台生产构建
cd PPDVadmin
npm ci
npm run build:prod

# 业务前端生产构建
cd PPDVweb
npm ci
npm run build
```

## 配置与密钥

- 后端本地默认配置位于 [`PPDVapi/src/main/resources/application.yml`](PPDVapi/src/main/resources/application.yml)。
- 业务前端开发配置位于 [`PPDVweb/.env.development`](PPDVweb/.env.development)，地图功能需要填写高德 Web 端 Key。
- 管理后台开发配置位于 [`PPDVadmin/.env.development`](PPDVadmin/.env.development)。
- 不要提交真实的生产数据库密码、JWT 密钥、设备密钥或第三方平台密钥。
- 仓库中的默认口令只用于本地联调，部署前必须通过安全配置替换。

## 文档导航

| 文档 | 适合什么时候阅读 |
|---|---|
| [架构与模块设计](docs/architecture.md) | 了解系统分层、技术选型和子系统关系 |
| [项目模块台账](docs/modules.md) | 确认哪些功能已完成、部分完成或仍在规划 |
| [本地部署与联调指南](docs/deployment.md) | 启动完整环境、配置 EMQX、排查本地问题 |
| [车云通信协议](docs/protocol.md) | 开发设备接入、遥测、指令或任务下发 |
| [Git 与 Pull Request 规范](docs/git-workflow.md) | 创建分支、提交代码、发起和合并 PR |
| [开发记录](docs/dev-log.md) | 追溯阶段性实现与技术决策 |

推荐新协作者按以下顺序阅读：本 README → 架构设计 → 模块台账 → 与任务相关的专项文档。

## 协作开发

所有协作者必须从最新 `master` 创建独立功能分支，通过 Pull Request 提交修改，禁止直接推送或强制更新 `master`。

```bash
git switch master
git pull --ff-only origin master
git switch -c <类型>/<简短任务名>
```

分支命名、提交格式、PR 模板、审核条件和合并后清理方式，以 [Git 分支与 Pull Request 协作规范](docs/git-workflow.md)为准。
