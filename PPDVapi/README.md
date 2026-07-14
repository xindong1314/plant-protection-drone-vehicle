# PPDVapi · 云平台后端

植保无人车云平台后端，基于 **Spring Boot 3.2 / Java 17**。当前为初始骨架，实现"设备管理 + MQTT 车云接入（遥测入库 + 指令下发）"最小闭环，接口对齐 [../docs/protocol.md](../docs/protocol.md)。

## 技术栈

- Spring Boot 3.2（web / validation / data-jpa / integration）
- Spring Integration MQTT（Eclipse Paho）
- H2 内存库（开发用，后续可换 MySQL/PostgreSQL）
- Lombok

## 运行

```bash
cd PPDVapi
mvn spring-boot:run
```

默认端口 `8080`，H2 控制台 `http://localhost:8080/h2-console`（JDBC URL `jdbc:h2:mem:ppdv`）。

MQTT 默认关闭，无 broker 也能启动。接入车载端时在 `application.yml` 打开并配置：

```yaml
ppdv:
  mqtt:
    enabled: true
    url: tcp://<broker-host>:1883
    username: <user>
    password: <pass>
```

## 已实现接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/devices` | 设备列表 |
| GET | `/api/devices/{deviceId}` | 设备详情（含最近遥测快照） |
| POST | `/api/devices` | 登记设备，body `{deviceId,name,model}` |
| POST | `/api/devices/{deviceId}/commands` | 下发指令，body `{cmd,args}`（需开启 MQTT） |

统一响应格式：`{ "code": 0, "message": "OK", "data": ... }`。

### 示例

```bash
# 登记设备
curl -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"PPDV-0001","name":"1号车","model":"X1"}'

# 查询设备
curl http://localhost:8080/api/devices/PPDV-0001

# 下发启动指令（需 MQTT 已启用）
curl -X POST http://localhost:8080/api/devices/PPDV-0001/commands \
  -H "Content-Type: application/json" \
  -d '{"cmd":"START","args":{"mode":"AUTO"}}'
```

## MQTT 行为

- **上行**：订阅 `ppdv/v1/+/status`、`ppdv/v1/+/tele/state` 等，解析信封后更新设备在线状态与最近遥测快照（未登记设备自动登记）。
- **下行**：`POST .../commands` 按信封格式发布到 `ppdv/v1/{deviceId}/cmd/req`，返回 `msgId`（车载端回执以此为 `refId`）。

## 代码结构

```
com.ppdv.api
├── PpdvApiApplication      启动类
├── common/                 统一响应 ApiResponse、全局异常处理
├── protocol/               Topics 主题约定、Envelope 消息信封
├── device/                 设备管理（entity/repository/service/controller）
├── telemetry/              上行遥测入库 TelemetryIngestService
├── command/                下行指令 CommandService/Controller
└── mqtt/                   MQTT 接入配置与收发（按 ppdv.mqtt.enabled 开关）
```

## 待办

- [ ] 换生产数据库（MySQL/PostgreSQL），加 Flyway 迁移
- [ ] 遥测明细落时序库（InfluxDB/TDengine），当前仅存最近快照
- [ ] 指令回执 `cmd/ack` 落库与超时管理
- [ ] 鉴权（设备一机一密、后台用户 JWT/RBAC）
- [ ] 任务调度、地图航线、告警通知等模块
