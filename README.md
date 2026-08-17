# PlantProtectionDroneVehicle · 植保无人车软件系统

自主 / 远程作业的地面植保喷洒机器人整套软件栈，分为车载端（嵌入式实时控制）与云端 Web 系统两大部分。

## 目录

| 目录 | 说明 | 技术栈 |
|------|------|--------|
| [PPDVedge/](PPDVedge/) | 车载端 · 无人车本体嵌入式实时控制（感知/导航/控制/喷洒/安全/通信） | ROS2 |
| PPDVapi/ | 云平台 · 后端接口 | Spring Boot |
| PPDVweb/ | 业务前端 | Vue |
| PPDVadmin/ | 管理后台前端 | Vue |
| [docs/](docs/) | 文档 | — |

## 架构

系统分三层：应用前端 → 云平台后端 → 车载端。车载端通过 MQTT / 4G-5G 与云平台交换遥测与指令，前端通过 REST / WebSocket 访问云平台。

完整模块设计见 [docs/architecture.md](docs/architecture.md)。

## 协作开发

所有协作者必须从最新 `master` 创建独立功能分支，通过 PR 提交修改，禁止直接推送或强制更新 `master`。详细操作见 [Git 分支与 Pull Request 协作规范](docs/git-workflow.md)。
