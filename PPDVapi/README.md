# PPDVapi · 云平台后端

植保无人车云平台后端，基于 **Spring Boot 3.2 / Java 17**。在自研骨架上按需嫁接了若依风格 RBAC、JWT 鉴权、MySQL、TDengine 时序库与 EMQX 车云接入，接口对齐若依前端契约（`code:200` + AjaxResult/TableDataInfo），供 [PPDVadmin](../PPDVadmin/) 直接对接。协议见 [../docs/protocol.md](../docs/protocol.md)。

## 技术栈

- Spring Boot 3.2（web / validation / data-jpa / integration / security / aop / data-redis）
- 鉴权：Spring Security + JWT（jjwt），令牌/验证码/在线用户走 Redis
- 关系库：MySQL 8，Flyway 管理表结构与种子数据
- 时序库：TDengine 3（REST 连接，遥测明细）
- 车云通信：Spring Integration MQTT（Eclipse Paho）接 EMQX，一机一密
- Lombok

## 运行

先起中间件（见 [../docs/deployment.md](../docs/deployment.md)）：`docker compose up -d`，然后：

```bash
cd PPDVapi
mvn spring-boot:run
```

- 端口 `8080`；首次启动 Flyway 自动建表 + 种子（管理员 `admin/admin123`）。
- 起好后执行 `bash ../docker/emqx/bootstrap-emqx.sh` 配置 EMQX 一机一密。

> 提示：本机若有系统代理，`curl` 访问 `localhost:8080` 需加 `--noproxy 127.0.0.1,localhost`。

## 关键配置（application.yml）

| 键 | 说明 |
|----|------|
| `spring.datasource` | MySQL（默认 `localhost:13306/ppdv`，宿主机 3306 被占用故用 13306） |
| `ppdv.jwt.*` | JWT 密钥与有效期 |
| `ppdv.captcha.enabled` | 登录验证码开关（默认 false） |
| `ppdv.mqtt.*` | EMQX 接入（`enabled`/`url`/服务账号） |
| `ppdv.tdengine.*` | TDengine（`enabled`/`url`/`database`） |

## 接口概览

统一响应：成功 `{ "code":200, "msg":"...", "data":... }`；分页 `{ "code":200, "rows":[...], "total":N }`。

**鉴权/RBAC（若依契约）**：`/login`、`/logout`、`/getInfo`、`/getRouters`、`/captchaImage`；`/system/user|role|menu|dept|post|dict|config/**`。

**业务（植保无人车）**：

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/ppdv/device/list` | `ppdv:device:list` | 设备列表 |
| GET | `/ppdv/device/{deviceId}` | `ppdv:device:query` | 设备详情 |
| POST | `/ppdv/device` | `ppdv:device:add` | 登记设备（自动生成一机一密密钥） |
| DELETE | `/ppdv/device/{ids}` | `ppdv:device:remove` | 删除设备 |
| PUT | `/ppdv/device/{deviceId}/resetSecret` | `ppdv:device:edit` | 重置密钥 |
| POST | `/ppdv/device/{deviceId}/command` | `ppdv:device:command` | 下发指令 |
| GET | `/ppdv/device/{deviceId}/telemetry/state` | `ppdv:device:query` | 状态遥测历史（TDengine） |

**EMQX 回调（无 JWT，内部）**：`/api/emqx/auth`、`/api/emqx/acl`。

## 代码结构

```
com.ppdv.api
├── common/       统一响应 AjaxResult/TableDataInfo、分页、异常、Redis、Web 基类
├── config/       Jackson、Redis 序列化
├── security/     Spring Security + JWT + 登录/权限（LoginUser/TokenService/...）
├── system/       若依式 RBAC（entity/repository/service/controller）
├── protocol/     Topics 主题约定、Envelope 消息信封
├── device/       设备管理（含一机一密 secret）
├── telemetry/    上行遥测入库 + TDengine 时序读写
├── command/      下行指令
├── mqtt/         EMQX 接入配置与收发（按 ppdv.mqtt.enabled 开关）
└── emqx/         EMQX 认证/授权 HTTP 回调（一机一密）
```

## 待办

- [ ] 数据范围（data scope）细化：当前部门过滤为简化版（精确匹配）。
- [ ] 监控模块（在线用户/操作日志/登录日志）查询接口与 AOP 记录。
- [ ] 若依周边：Excel 导入导出、代码生成、定时任务（按需）。
- [ ] 遥测告警落库、任务调度、地图航线、OTA 等业务模块。
