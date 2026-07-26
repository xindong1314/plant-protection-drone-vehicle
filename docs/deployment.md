# 本地部署与联调指南

植保无人车云平台本地一体化启动步骤：中间件（Docker）→ 后端（PPDVapi）→ 管理后台（PPDVadmin）→ 车云联调。

## 组件与端口

| 组件 | 说明 | 端口 |
|------|------|------|
| MySQL 8 | 业务库 + 若依 RBAC（Flyway 建表） | 宿主机 **13306** → 容器 3306 |
| TDengine 3 | 遥测时序库（REST/taosAdapter） | 6041（REST）、6030 |
| EMQX 5 | 车云 MQTT broker（一机一密） | 1883（MQTT）、18083（Dashboard） |
| Redis 7 | 登录令牌 / 验证码 / 在线用户 | 6379 |
| PPDVapi | Spring Boot 后端（含 WebSocket `/ws/telemetry`） | 8080 |
| PPDVadmin | 若依 Vue3 管理后台 | 8081（开发） |
| PPDVweb | 业务前端（监控大屏/驾驶舱/作业规划） | 8082（开发） |

> 说明：宿主机已有本地 MySQL 占用 3306，故容器 MySQL 映射到 13306（3307 段被 Windows 保留）。

## 1. 起中间件

```bash
docker compose up -d
docker compose ps          # 4 个服务 healthy
```

## 2. 起后端

```bash
cd PPDVapi
mvn spring-boot:run
```

- 首次启动 Flyway 自动建表并注入种子（管理员 `admin/admin123`）。
- `ppdv.tdengine.enabled=true` 时启动自动建时序库 `ppdv_tsdb` 与超级表。
- `ppdv.mqtt.enabled=true` 时连接 EMQX 并订阅上行主题。

## 3. 配置 EMQX 一机一密（一次性）

后端起好后执行，令 EMQX 通过 HTTP 回调后端做设备认证/授权：

```bash
bash docker/emqx/bootstrap-emqx.sh
```

- 认证：`username=deviceId`、`password=设备密钥`（设备登记时自动生成）。
- 授权：设备仅能收发自己的 `ppdv/v1/{deviceId}/#` 主题。
- 后端服务账号（`ppdv-backend`）标记为 superuser。

## 4. 起管理后台

```bash
cd PPDVadmin
npm install
npm run dev          # http://localhost:8081
```

浏览器打开 `http://localhost:8081`，用 `admin/admin123` 登录，可见「系统管理 / 系统监控 / 植保无人车」动态菜单。

## 4b. 起业务前端 PPDVweb（监控大屏/驾驶舱/作业规划）

```bash
cd PPDVweb
npm install
npm run dev          # http://localhost:8082
```

- 用 `admin/admin123` 登录，含「监控大屏 / 驾驶舱 / 作业规划」三个页面。
- 实时数据经后端 WebSocket `/ws/telemetry`（握手用 `?token=JWT`）推送。
- **地图需高德 Key**：到 [console.amap.com](https://console.amap.com) 申请「Web端(JS API)」Key，填入 `PPDVweb/.env.development` 的 `VITE_AMAP_KEY`（如开启了安全密钥再填 `VITE_AMAP_SECURITY`）。未配置时页面其余功能正常，仅地图区域提示占位。

## 5. 车云联调（用 mosquitto 模拟车载端）

在管理后台「植保无人车 → 设备管理」登记一台设备（如 `PPDV-0001`），复制其密钥 `<SECRET>`，然后：

```bash
NET=plantprotectiondronevehicle_default
# 上线 + 状态遥测（正确密钥）
docker run --rm --network $NET eclipse-mosquitto mosquitto_pub \
  -h ppdv-emqx -p 1883 -u PPDV-0001 -P <SECRET> -q 1 \
  -t ppdv/v1/PPDV-0001/tele/state \
  -m '{"deviceId":"PPDV-0001","ts":1785000000000,"type":"state","data":{"lat":30.12,"lon":120.65,"battery":88,"mode":"AUTO"}}'

# 错误密钥将被拒绝：Connection Refused: not authorised
```

- 「实时监控」页可见设备在线、电量、位置，「遥测历史」读 TDengine。
- 「设备管理 → 下发指令」会发布到 `ppdv/v1/{deviceId}/cmd/req`，车载端订阅接收。

## 常见问题

- **curl 访问 localhost:8080 返回 502**：本机有系统代理拦截，加 `--noproxy 127.0.0.1,localhost`。
- **EMQX 回调不到后端**：EMQX 容器经 `host.docker.internal:8080` 访问宿主机后端（compose 已配 `extra_hosts`）。
- **改了已执行的 Flyway 迁移**：需 `DROP DATABASE ppdv` 重建后再启动（否则校验和不一致）。
