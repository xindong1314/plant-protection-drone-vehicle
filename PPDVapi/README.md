# PPDVapi · 云平台后端

植保无人车云平台后端，基于 **Spring Boot 3.2 / Java 17**。在自研骨架上嫁接若依风格 RBAC、JWT 鉴权、MySQL、TDengine 时序库与 EMQX 车云接入，接口对齐若依前端契约（`code:200` + AjaxResult/TableDataInfo），供 [PPDVadmin](../PPDVadmin/) 和 [PPDVweb](../PPDVweb/) 对接。

项目级架构、端口、业务闭环和车云通信说明见根 [README.md](../README.md)。

## 技术栈

- Spring Boot 3.2（web / validation / data-jpa / integration / security / aop / data-redis）
- 鉴权：Spring Security + JWT（jjwt），令牌/验证码/在线用户走 Redis
- 关系库：MySQL 8，Flyway 管理表结构与种子数据
- 时序库：TDengine 3（REST 连接，遥测明细）
- 车云通信：Spring Integration MQTT（Eclipse Paho）接 EMQX，一机一密
- Lombok

## 运行

先起中间件（见根 [README.md](../README.md)）：`docker compose up -d`，然后：

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
| `ppdv.track.*` | 车端目标跟踪上云开关：`enabled` 默认 false，关闭时不订阅 `tele/track` |
| `ppdv.upload.base-dir` | 巡检图片本地存储目录，默认 `./uploads` |

## 接口概览

统一响应：成功 `{ "code":200, "msg":"...", "data":... }`；分页 `{ "code":200, "rows":[...], "total":N }`。

**鉴权/RBAC（若依契约）**：`/login`、`/logout`、`/getInfo`、`/getRouters`、`/captchaImage`；`/system/user|role|menu|dept|post|dict|config|notice/**`；`/monitor/online|operlog|logininfor/**`。

**业务（植保无人车）**：

| 前缀 | 权限 | 说明 |
|------|------|------|
| `/ppdv/device` | `ppdv:device:*` | 设备台账、一机一密、密钥重置、控制指令 |
| `/ppdv/device/{deviceId}/task` | `ppdv:device:command` | 作业任务下发（内联地块/航线，未持久化） |
| `/ppdv/device/{deviceId}/telemetry` | `ppdv:device:query` | 状态/环境遥测历史（TDengine） |
| `/ppdv/area` | `ppdv:area:*` | 区域 CRUD、CGCS2000 边界、删除保护 |
| `/ppdv/inspection` | `ppdv:inspection:query/upload` | 巡检、图片上传、识别结果 |
| `/ppdv/track` | `ppdv:track:query` | 跟踪会话、出现记录与类别目录查询（`ppdv.track.enabled` 开关，P1） |
| `/ppdv/risk`、`/ppdv/risk/config` | `ppdv:risk:evaluate`、`ppdv:risk:config:*` | 区域风险评分与规则配置 |
| `/ppdv/task/spray`、`/ppdv/task/review` | `ppdv:spray:*`、`ppdv:review:add` | 点喷任务、喷后复查 |
| `/ppdv/archive` | `ppdv:archive:query` | 区域档案与数字地图聚合 |
| `/ppdv/report` | `ppdv:inspection:query` | 时间范围汇总报表（JSON） |

所有业务接口使用 `@PreAuthorize("@ss.hasPermi('...')")`；新增权限点必须在新 Flyway 迁移中补 `sys_menu` 与角色授权。

**EMQX 回调（无 JWT，内部）**：`/api/emqx/auth`、`/api/emqx/acl`。

## 图片上传

巡检图片保存在 `ppdv.upload.base-dir`（默认后端工作目录下 `./uploads`），按 `yyyy-MM-dd` 分目录；仅接受 JPEG/PNG/GIF/WebP，并校验文件头；通过 `/uploads/**` 静态访问。单文件上限 50MB，单请求上限 100MB。未接入对象存储。

## 代码结构

```
com.ppdv.api
├── common/       统一响应 AjaxResult/TableDataInfo、分页、异常、Redis、Web 基类
├── config/       Jackson、Redis、WebSocket、静态资源、上传配置
├── security/     Spring Security + JWT + 登录/权限（LoginUser/TokenService/...）
├── system/       若依式 RBAC、公告、登录/操作/在线用户
├── protocol/     Topics 主题约定、Envelope 消息信封
├── device/       设备管理（含一机一密 secret）
├── telemetry/    遥测入库、TDengine 读写、WebSocket 广播
├── command/      下行指令与任务下发
├── mqtt/         EMQX 接入配置与收发（按 ppdv.mqtt.enabled 开关）
├── emqx/         EMQX 认证/授权 HTTP 回调（一机一密）
├── greenhouse/   区域（历史包名）
├── inspection/   巡检、图片、识别结果
├── track/        目标跟踪（tele/track 会话/出现记录入库与查询）
├── risk/         风险规则与评分
├── task/         点喷任务、喷后复查
├── archive/      区域档案
├── report/       汇总报表
└── storage/      巡检图片本地存储
```

## 测试

```bash
mvn test
mvn -Dtest=AreaServiceTest test
```

当前 14 个测试类、146 个测试方法（surefire 执行数 164）：其中 13 个为纯 JUnit/Mockito，`RouteRecordingServiceContextTest` 启动最小 Spring 上下文做构造器注入冒烟；不需要 Docker 或 Spring Boot 应用上下文。

## 待办

- [ ] 告警与指令回执落库，补齐告警中心/回执查询。
- [ ] 作业任务持久化：任务表、进度、车端回执闭环。
- [ ] 接入真实视觉识别服务，定义模型版本、置信度阈值和人工复核流程。
- [ ] 接入对象存储、OTA、视频流。
- [ ] 数据范围（data scope）细化：当前部门过滤为简化版。
- [ ] 若依周边：代码生成、定时任务、服务器/缓存监控。
