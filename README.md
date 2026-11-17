# VERYAL Backend

VERYAL 是面向个人用户的消费级社交即时通讯产品（类微信 / QQ），当前仓库为服务端实现，采用 Java 21 + Netty + Spring Boot + Protobuf + MyBatis-Plus + MySQL + Redis + RabbitMQ + Caffeine + Lombok + Hutool，按照 [`docs/VERYAL-V1.0-技术架构与开发设计文档.md`](docs/VERYAL-V1.0-技术架构与开发设计文档.md) V1.1 的模块与分层约束落地。

> **产品方向（V1.1）**
>
> - VERYAL 定位为消费级社交 IM：账号、好友、单聊、群聊、富媒体、多端同步、离线推送、隐私与举报、运营后台。
> - **不做多租户 / 企业组织 / 飞书式协作 / 开放平台 / 私有化交付。**
> - 租户表、租户码登录、平台租户管理、租户管理员、租户用量/导出/删除接口已移除（V17）。
> - 数据库 `tenant_id` 列与 Java `tenantId` 字段/参数已全部移除（V21 + 代码清理），单平台单空间。
> - 前端设计见 [`docs/FRONTEND-ARCHITECTURE.md`](docs/FRONTEND-ARCHITECTURE.md)（IM 客户端 + 运营后台）。

当前版本完成了 **V0.1 通信核心**、**V0.2 基础 IM 业务** 与 **V0.3 消息可靠性核心** 的可运行纵向切片：

- 自定义 16 字节二进制协议帧 + Protobuf Envelope，TCP 与 WebSocket 共用。
- Netty TCP Gateway（默认 9099）与 WebSocket Gateway（默认 9098，`/ws`）。
- 登录认证（HS256 JWT）、心跳租约、连接注册表、读空闲断开、命令分发、业务线程池隔离。
- 用户 / 设备 / 单聊 / **群聊** / 消息 / 会话序号 / 历史消息 / 幂等发送 / Server ACK / Delivery ACK / Read ACK / 游标同步。
- 群聊基础：建群、加成员、群主/管理员权限、群会话与群消息；群 ID 与群会话 ID 一致，消息权限复用会话成员校验。
- **群消息分页扇出**：群消息事件不携带接收者快照，本地投递与 MQ 消费者按 `user_id` 分页拉取成员，避免事件体积随群规模增长。
- 群管理：群主设置管理员、管理员/群主改名、踢人、成员退群、**群主转让、解散群**；群主不可直接退群；群成员上限 1000。
- **Delivery ACK 持久化 + 投递查询**：按消息 + 设备记录投递序号，幂等且只前进；提供消息投递记录查询与会话未读数。
- **Outbox + RabbitMQ 可靠投递**：消息与事件同事务落库，Publisher Confirm、指数退避重试、TTL 重试队列、死信队列。
- **Redis 网关路由 + 单投递队列**：投递消费者按在线路由把连接按 gatewayId 分组，通过网关交换机定向推送到对应实例，支持多 Gateway 跨节点实时推送。
- HTTP API（8080）：注册、登录、资料、设备、会话、消息、同步。
- MySQL 8.0.30 + MyBatis-Plus + Flyway；本地开发使用 H2（MySQL 兼容模式）零依赖启动。
- Redis 在线状态（生产 profile）+ 内存实现（开发/测试 profile）；Caffeine 用户资料缓存由 `CacheConfig` 在代码中显式配置（10 万条 / 10 分钟）。
- Prometheus / Actuator 指标、统一日志字段（traceId / userId / connectionId 等）。
- **策略模式限流**：令牌桶 / 滑动窗口 / 固定窗口可配置；HTTP 登录按 IP、HTTP API 按用户、TCP 登录按 IP、消息发送按用户。
- **审计日志**：登录、建群/改名/成员变更/踢人/退群/转让/解散写入 `im_audit_log`，独立事务、失败不影响主业务。
- **可选 TLS**：TCP 与 WebSocket Gateway 支持 TLS 1.2/1.3（PEM 证书链 + 私钥）；HTTP 通过 Spring Boot `server.ssl` 开启。
- **连接数治理**：单节点总连接数与单 IP 连接数可配置，准入处理器在 TLS/协议握手前拒绝超限连接，并输出拒绝指标。
- **文件存储策略**：`local` / `minio` / `oss` 三种实现，`veryal.storage.type` 选择；文件本体不入 MySQL，元数据与 SHA-256 落库，支持上传、下载、删除、临时 URL。
- **分片上传/断点续传**：初始化会话 → 逐片上传 → 查询已上传分片 → 完成后合并；图片上传自动缩放/压缩。
- **多租户已彻底移除（V17/V21 + Java 清理）**：租户表、租户码、租户管理接口、租户管理员、用量/导出/删除已删除；V21 删除全部 `tenant_id` 列，Java 领域对象、仓储、服务、控制器、连接、事件与测试不再包含 `tenantId` 字段或参数。
- **好友/黑名单/通讯录**：好友申请（发送/接受/拒绝/取消）、好友备注/删除、黑名单拦截私聊、通讯录聚合好友/群/待处理申请。
- **多消息类型**：TEXT / EMOJI / IMAGE / AUDIO / VIDEO / FILE / SYSTEM，采用策略 + 工厂校验内容格式与文件归属。
- Lombok 消除样板代码，Hutool 提供 ID / 字符串等基础工具。

## 模块结构

按“6 个代码模块 + 1 个启动模块”组织，每个模块内部再按业务能力分包，避免 20+ 个小模块带来的跳转成本：

```text
veryal-backend/
├── veryal-common/          基础能力：异常、ID、JSON、日志上下文（无 Spring 依赖）
├── veryal-protocol/        协议：Command、Protobuf 定义、16 字节帧编解码
├── veryal-domain/          领域层：identity / device / conversation / message / presence（纯 Java）
├── veryal-application/     应用层：identity / conversation / message / sync（用例编排、事务）
├── veryal-infrastructure/  基础设施：mysql(MyBatis-Plus) / redis / cache / mq(Outbox+RabbitMQ)
├── veryal-access/          接入层：core / tcp / websocket / http（连接、认证、分发、API）
├── veryal-bootstrap/       启动装配、application*.yml、Flyway 迁移、集成测试
└── deploy/docker/          Dockerfile、docker-compose、.env 示例
```

依赖方向（只允许向下依赖）：

```text
bootstrap ──> access ──> application ──> domain ──> common
     └──────> infrastructure ──┘
access ──> protocol ──> common
```

边界规则：

- `domain` 不依赖 Spring / Netty / MyBatis，只放聚合、值对象、领域规则和 Repository 端口。
- `application` 只编排用例与事务，不出现 SQL、HTTP、Netty 类型。
- `infrastructure` 实现 `domain` 的端口（MyBatis-Plus Mapper、Redis、Caffeine），不反向依赖 application。
- `access` 负责协议解析、认证、连接与参数校验，不直接操作 Mapper。
- `bootstrap` 只做装配与配置，不写业务逻辑。

## 包结构

```text
com.veryal
├── common
│   ├── core            ApiResponse / CursorPage / TimeProvider / Preconditions / RandomIds
│   ├── exception       ErrorCode / VeryalException / Business / System / Protocol / Auth / Persistence
│   ├── id              IdGenerator / SnowflakeIdGenerator
│   ├── json            JsonUtils
│   ├── ratelimit       RateLimitPolicy / RateLimiter / TokenBucket / SlidingWindow / FixedWindow / Registry
│   └── observability   TraceContext / LogFields / MetricNames
├── protocol
│   ├── api             Command / ProtocolConstants / ProtocolFrame / ProtocolMessage
│   ├── protobuf        veryal.proto 生成类 + EnvelopeCodec
│   └── netty           VeryalFrameDecoder/Encoder / EnvelopeDecoder/Encoder
├── domain
│   ├── identity        User / UserStatus / UserRepository / PasswordHasher
│   ├── device          Device / Platform / DeviceRepository
│   ├── conversation    Conversation / ConversationMember / ConversationRepository / ConversationIds
│   ├── message         Message / MessageType / MessagePolicy / Repository / 序号端口
│   │   ├── event       MessageCreatedEvent / MessageEvents
│   │   └── outbox      OutboxMessage / OutboxAppender
│   ├── group           Group / GroupMember / GroupRole / GroupRepository
│   ├── audit           AuditAction / AuditLog / AuditRepository
│   ├── file            StoredFile / FileStatus / FileStorage / FileStorageProvider / FileRepository / StorageType
│   ├── delivery        DeliveryRecord / DeliveryRepository / GatewayPushCommand / GatewayPushPort / RealtimeDeliveryPort
│   └── presence        PresenceRecord / PresenceRepository
├── application
│   ├── identity        AuthApplicationService / SessionApplicationService / UserApplicationService / JwtTokenService
│   ├── conversation    ConversationApplicationService / ConversationView / ConversationSummary
│   ├── group           GroupApplicationService / GroupView / GroupSummary
│   ├── message         MessageApplicationService / SendMessageCommand / SendMessageResult / strategy（多消息类型策略工厂）
│   ├── ratelimit       RateLimitProperties / RateLimitConfig / RateLimitCleanupTask
│   ├── audit           AuditRecorder
│   ├── file            FileApplicationService / FileView
│   └── sync            SyncApplicationService
├── infrastructure
│   ├── mysql
│   │   ├── mapper      UserMapper / DeviceMapper / ConversationMapper / GroupMapper / MessageMapper / DeliveryMapper / AuditLogMapper / OutboxMapper / ...
│   │   ├── po          UserPO / DevicePO / ConversationPO / GroupPO / MessagePO / DeliveryPO / AuditLogPO / OutboxPO / ...
│   │   ├── repository  MyBatis*Repository / MyBatisOutboxAppender
│   │   ├── MyBatisPlusConfig / SqlTime
│   ├── redis           RedisKeys / RedisPresenceRepository / InMemoryPresenceRepository
│   ├── cache           CacheConfig
│   ├── storage         StorageProperties / LocalFileStorage / MinioFileStorage / AliyunOssFileStorage / FileStorageProviderImpl
│   └── mq              RabbitMqConfig / OutboxPublisher / MessageDeliveryListener / GatewayRouter / GatewayPushListener / RabbitInstance
├── access
│   ├── core            Connection / ConnectionRegistry / GatewayHandlers / handler / protocol / push（RealtimeDelivery + LocalGatewayPush）/ tls / metrics
│   ├── tcp             TcpGatewayServer / TcpGatewayProperties
│   ├── websocket       WebSocketGatewayServer / codec
│   └── http            controller / dto / security（AuthenticationFilter + RateLimitFilter）/ error / config
└── bootstrap           VeryalApplication / CoreConfig / resources
```

> 设计文档中的 `protocol-api / protocol-protobuf / protocol-netty` 与 `access-core / access-tcp / access-websocket / access-http` 已收敛为 `veryal-protocol`、`veryal-access` 内部的包边界；分层约束不变，构建与查阅成本更低。


## 快速开始（无需 MySQL / Redis）

```bash
# JDK 21 + Maven 3.9+
mvn -B -DskipTests package
java -jar veryal-bootstrap/target/veryal-bootstrap-1.0.0-SNAPSHOT.jar
```

默认激活 `dev` profile：H2 内存库 + 内存在线状态 + MQ 关闭，Flyway 关闭。

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'
```

## 使用 Docker（MySQL 8.0.30 + Redis + RabbitMQ + MinIO）

```bash
cd deploy/docker
cp .env.example .env
# 修改 .env 中的密码与 VERYAL_TOKEN_SECRET
docker compose up -d --build
```

应用启动时由 Flyway 依次执行 `V1__init.sql`、`V2__outbox.sql` 建表；MySQL 使用 UTC、utf8mb4。生产 profile 默认开启 RabbitMQ 投递，可用 `VERYAL_MQ_ENABLED=false` 回退到单机直投。

## HTTP API

所有业务接口都需要 `Authorization: Bearer <token>`，响应统一为：

```json
{"code":0,"message":"success","data":{},"traceId":"..."}
```

触发限流时返回 `429` 与错误码 `42900`，并带 `Retry-After`、`X-RateLimit-Remaining` 响应头。

### 注解式限流

业务方法可直接用 `@RateLimit` 声明限流，由 `RateLimitAspect` 在调用前消费令牌：

```java
// 登录：按客户端 IP
@RateLimit(policy = "http-login", dimension = RateLimitDimension.IP)
public ApiResponse<AuthResponse> login(...) { ... }

// 消息发送：按发送者
@RateLimit(policy = "message-send", key = "#p0 + ':' + #p1")
public void acquire(long senderId) { ... }
```

- `policy` 对应 `veryal.rate-limit.policies` 中的策略；
- `dimension` 支持 `IP` / `USER` / `TENANT` / `GLOBAL` / `CUSTOM`；
- `CUSTOM` 使用 SpEL，`#p0`、参数名均可引用；
- 消息限流放在幂等与内容校验之后，避免重复/非法消息消耗额度；
- 登录由注解限流，`RateLimitFilter` 不再重复处理登录路径；
- 超限统一抛 `BusinessException(RATE_LIMITED)`，由全局异常处理返回 429。

限流额度可用环境变量覆盖（默认值与代码保持一致）：

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `VERYAL_RATE_LIMIT_ENABLED` | `true` | 全局开关；`false` 时过滤器与切面直接放行 |
| `VERYAL_RATE_LIMIT_LOGIN_PERMITS` | `10` | 登录：每 60 秒每 IP 次数 |
| `VERYAL_RATE_LIMIT_API_PERMITS` / `_REFILL` | `200` / `50` | 通用 API 令牌桶容量 / 每秒补充 |
| `VERYAL_RATE_LIMIT_MESSAGE_PERMITS` / `_REFILL` | `30` / `15` | 发消息令牌桶容量 / 每秒补充 |

> 做发送压测时必须先关掉发消息限流（默认 15 条/秒），否则 429 会污染吞吐数据：
> `VERYAL_RATE_LIMIT_ENABLED=false`，压测结束再恢复。

> **接口说明（V1.1）**
>
> 多租户相关接口已移除；`/api/v1/platform/*` 仅保留运营管理员登录、MFA、管理员管理与审计。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录并绑定设备 |
| GET | `/api/v1/auth/me` | 当前用户 |
| POST | `/api/v1/platform/auth/login` | 运营管理员登录（可带 `mfaCode`，返回 `scope=PLATFORM` + `role`） |
| POST | `/api/v1/platform/auth/refresh` | 刷新运营 Token（Refresh Token 轮换） |
| POST | `/api/v1/platform/auth/logout` | 注销 Refresh Token |
| POST | `/api/v1/platform/auth/mfa/setup` | 生成 TOTP 密钥与 otpauth URL |
| POST | `/api/v1/platform/auth/mfa/enable` | 校验动态码并启用 MFA |
| POST | `/api/v1/platform/auth/mfa/disable` | 校验动态码并关闭 MFA |
| GET | `/api/v1/platform/admins` | 运营管理员列表（仅 PLATFORM_ADMIN） |
| POST | `/api/v1/platform/admins` | 创建运营管理员 |
| POST | `/api/v1/platform/admins/{id}/disable` | 停用管理员 |
| POST | `/api/v1/platform/admins/{id}/enable` | 启用管理员 |
| POST | `/api/v1/platform/admins/{id}/password` | 重置管理员密码 |
| POST | `/api/v1/platform/admins/{id}/role` | 调整管理员角色 |
| GET | `/api/v1/platform/audit-logs` | 运营审计日志 |
| GET / PUT | `/api/v1/users/me` | 查询 / 修改资料 |
| GET | `/api/v1/users/me/devices` | 设备列表（同平台同名记录已合并） |
| DELETE | `/api/v1/users/me/devices/{deviceId}` | 移除登录设备（当前设备不可移除） |
| GET | `/api/v1/users/me/audit-logs?limit=50` | 我的审计记录 |
| GET | `/api/v1/users/search?keyword=...` | 搜索用户 |
| GET | `/api/v1/users/{id}/profile` | 用户主页资料 |
| GET / PUT | `/api/v1/users/me/privacy` | 查询 / 更新隐私设置 |
| POST | `/api/v1/reports` | 举报用户 / 群 / 消息 |
| GET | `/api/v1/reports/me` | 我的举报记录 |
| POST | `/api/v1/push/devices` | 注册推送设备 token |
| GET | `/api/v1/push/devices` | 我的推送设备 |
| DELETE | `/api/v1/push/devices/{deviceId}` | 注销推送设备 |
| GET | `/api/v1/push/web/config` | Web Push 开关与 VAPID 公钥 |
| POST | `/api/v1/push/web/subscriptions` | 注册浏览器推送订阅 |
| DELETE | `/api/v1/push/web/subscriptions?endpoint=` | 退订浏览器推送 |
| GET | `/api/v1/platform/ops/dashboard` | 运营概览 |
| GET | `/api/v1/platform/ops/users?keyword=` | 运营用户搜索 |
| GET | `/api/v1/platform/ops/users/{id}` | 运营用户详情 |
| POST | `/api/v1/platform/ops/users/{id}/ban` | 封禁用户 |
| POST | `/api/v1/platform/ops/users/{id}/unban` | 解封用户 |
| POST | `/api/v1/platform/ops/users/{id}/mute` | 禁言用户（分钟） |
| POST | `/api/v1/platform/ops/users/{id}/unmute` | 解除禁言 |
| GET | `/api/v1/platform/ops/groups?keyword=` | 运营群搜索 |
| GET | `/api/v1/platform/ops/groups/{id}` | 运营群详情/成员 |
| POST | `/api/v1/platform/ops/groups/{id}/disband` | 解散群 |
| GET | `/api/v1/platform/ops/reports?status=` | 举报列表 |
| GET | `/api/v1/platform/ops/reports/{id}` | 举报详情 |
| POST | `/api/v1/platform/ops/reports/{id}/handle` | 处理举报 |
| GET | `/api/v1/platform/ops/sensitive-words` | 敏感词列表 |
| POST | `/api/v1/platform/ops/sensitive-words` | 新增敏感词 |
| DELETE | `/api/v1/platform/ops/sensitive-words/{id}` | 删除敏感词 |
| POST | `/api/v1/friends/requests` | 发送好友申请 |
| GET | `/api/v1/friends/requests?direction=incoming` | 好友申请列表 |
| POST | `/api/v1/friends/requests/{requestId}/accept` | 接受好友申请 |
| POST | `/api/v1/friends/requests/{requestId}/reject` | 拒绝好友申请 |
| POST | `/api/v1/friends/requests/{requestId}/cancel` | 取消自己发出的申请 |
| GET | `/api/v1/friends` | 好友列表（含备注/在线状态） |
| PUT | `/api/v1/friends/{friendId}` | 修改好友备注 |
| DELETE | `/api/v1/friends/{friendId}` | 删除好友 |
| GET | `/api/v1/blocks` | 黑名单列表 |
| POST | `/api/v1/blocks/{userId}` | 拉黑（同时解除好友与待处理申请） |
| DELETE | `/api/v1/blocks/{userId}` | 解除拉黑 |
| GET | `/api/v1/groups/{id}/moderation` | 群公告与禁言状态 |
| PUT | `/api/v1/groups/{id}/announcement` | 更新群公告（群主/管理员） |
| POST | `/api/v1/groups/{id}/mute` | 全员禁言开关（群主/管理员） |
| POST | `/api/v1/groups/{id}/members/{userId}/mute` | 成员禁言分钟数，0 解除 |
| GET | `/api/v1/contacts` | 通讯录：好友/群/待处理申请数 |
| POST | `/api/v1/conversations` | 创建单聊 `{"peerUserId":1002}` |
| POST | `/api/v1/groups` | 创建群 `{"name":"...","memberIds":[1002,1003]}` |
| GET | `/api/v1/groups` | 我的群列表 |
| GET | `/api/v1/groups/{groupId}` | 群详情 |
| POST | `/api/v1/groups/{groupId}/members` | 添加群成员（群主/管理员） |
| PUT | `/api/v1/groups/{groupId}` | 修改群名（群主/管理员） |
| POST | `/api/v1/groups/{groupId}/leave` | 退群（群主不可直接退群） |
| DELETE | `/api/v1/groups/{groupId}/members/{userId}` | 踢人（群主/管理员） |
| PUT | `/api/v1/groups/{groupId}/members/{userId}/role` | 设置管理员/成员（仅群主） |
| POST | `/api/v1/groups/{groupId}/transfer` | 转让群主（仅当前群主） |
| DELETE | `/api/v1/groups/{groupId}` | 解散群（仅群主） |
| GET | `/api/v1/conversations` | 会话列表（含读游标与最大序号） |
| GET | `/api/v1/conversations/{id}` | 会话详情 |
| POST | `/api/v1/conversations/{id}/messages` | 发送消息 |
| POST | `/api/v1/conversations/{id}/messages/{messageId}/recall` | 撤回消息（仅发送者，2 分钟内） |
| GET | `/api/v1/conversations/{id}/messages?cursor=0&limit=50` | 历史消息（游标） |
| GET | `/api/v1/conversations/{id}/messages/search?keyword=...` | 会话内文本/表情搜索（倒序游标） |
| GET | `/api/v1/conversations/{id}/read-receipts` | 成员读游标 |
| GET | `/api/v1/conversations/{id}/messages/{messageId}/deliveries` | 消息设备投递记录 |
| POST | `/api/v1/conversations/{id}/read` | 上报已读序号 |
| GET | `/api/v1/sync/messages?conversationId=...&cursor=...&limit=...` | 离线同步 |
| POST | `/api/v1/files` | 上传文件（multipart，字段 `file`，可选 `conversationId` 绑定会话） |
| POST | `/api/v1/files/uploads` | 初始化分片上传 |
| PUT | `/api/v1/files/uploads/{uploadId}/chunks/{index}` | 上传分片（`application/octet-stream`） |
| GET | `/api/v1/files/uploads/{uploadId}` | 查询分片上传进度 |
| POST | `/api/v1/files/uploads/{uploadId}/complete` | 合并分片并生成文件 |
| DELETE | `/api/v1/files/uploads/{uploadId}` | 取消分片上传 |
| GET | `/api/v1/files/{fileId}` | 文件元数据 |
| GET | `/api/v1/files/{fileId}/content` | 下载文件（仅上传者） |
| GET | `/api/v1/files/{fileId}/url?ttlSeconds=600` | 临时访问 URL（MinIO/OSS 为预签名地址，本地为下载路径） |
| DELETE | `/api/v1/files/{fileId}` | 删除文件（仅上传者） |

### 好友离线推送

好友事件在用户无在线连接时按 `im_push_device` 中启用的设备 token 投递。支持：

- `none`：只记录日志；
- `webhook`：POST 到厂商网关，由网关调用 APNs/FCM；
- `fcm`：FCM HTTP v1，Service Account OAuth2；
- `apns`：APNs HTTP/2 + ES256 JWT。

配置：

```yaml
veryal:
  push:
    provider: ${VERYAL_PUSH_PROVIDER:none}   # none | webhook | fcm | apns
    fcm:
      project-id: ${VERYAL_PUSH_FCM_PROJECT_ID:}
      service-account-json: ${VERYAL_PUSH_FCM_SERVICE_ACCOUNT_JSON:}
    apns:
      team-id: ${VERYAL_PUSH_APNS_TEAM_ID:}
      key-id: ${VERYAL_PUSH_APNS_KEY_ID:}
      private-key-path: ${VERYAL_PUSH_APNS_PRIVATE_KEY_PATH:}
      topic: ${VERYAL_PUSH_APNS_TOPIC:}
      production: ${VERYAL_PUSH_APNS_PRODUCTION:false}
    webhook-url: ${VERYAL_PUSH_WEBHOOK_URL:}
    webhook-token: ${VERYAL_PUSH_WEBHOOK_TOKEN:}
```

设备注册：

```http
POST /api/v1/push/devices
{"deviceId":"...","platform":"ANDROID","token":"fcm-token"}
```

> 真实 APNs/FCM 投递需要项目/证书/Service Account 凭证；仓库不内置凭证。

### 浏览器 Web Push（无需任何服务商账号）

Web Push 走浏览器自带的推送服务（Chrome/Edge→FCM endpoint、Firefox→Mozilla），
**只需要自己生成一对 VAPID 密钥**，不需要 Firebase 项目、不需要 Apple 账号。

```yaml
veryal:
  push:
    web-push:
      enabled: ${VERYAL_PUSH_WEB_PUSH_ENABLED:false}      # 总开关，默认关闭
      subject: ${VERYAL_PUSH_WEB_PUSH_SUBJECT:mailto:admin@example.com}
      public-key: ${VERYAL_PUSH_WEB_PUSH_PUBLIC_KEY:}     # 留空则自动生成
      private-key: ${VERYAL_PUSH_WEB_PUSH_PRIVATE_KEY:}
      key-file: ${VERYAL_PUSH_WEB_PUSH_KEY_FILE:./data/vapid-keys.json}
      ttl-seconds: ${VERYAL_PUSH_WEB_PUSH_TTL:86400}
      max-subscriptions-per-user: ${VERYAL_PUSH_WEB_PUSH_MAX_SUBS:10}
```

- `enabled=false`（默认）时：`/push/web/config` 返回 `enabled=false`，订阅接口返回 404，不产生任何推送。
- `enabled=true` 且未配置密钥时：首次启动自动生成 P-256 密钥并写入 `key-file`，重启后复用，已有订阅不会失效。
- 加密遵循 RFC 8291（`aes128gcm`），VAPID 签名遵循 RFC 8292。
- 订阅失效（HTTP 404/410）时自动删除数据库记录。
- 要求 HTTPS（`localhost` 除外）。

对应表 `im_web_push_subscription`（Flyway V23），`endpoint` 做 SHA-256 后建唯一索引，避免超长索引。

### 群公告与禁言

- 群主/管理员可更新公告（≤2000 字）、开启全员禁言、对成员禁言（分钟数，0 解除）；
- 全员禁言时群主/管理员仍可发言；
- 成员禁言期间发送返回 `40306 GROUP_MUTED`；
- 文本消息可带 `mentions` 数组（最多 20 个），被 @ 的用户必须是本群成员：
  ```json
  {"text":"@alice 看一下","mentions":[1002]}
  ```
- 群公告/禁言状态通过 `GET /api/v1/groups/{id}/moderation` 获取。

### 消息类型

消息类型由 `MessageTypeStrategyFactory` 按类型选择策略校验，文件类消息只允许引用发送者自己上传且未删除的文件。

| type | code | content 格式 |
|---|---|---|
| `TEXT` | 1 | UTF-8 文本，最大 64KB |
| `IMAGE` | 2 | JSON：`{"fileId":123,"width":1080,"height":1920}` |
| `FILE` | 3 | JSON：`{"fileId":123,"fileName":"report.pdf"}` |
| `AUDIO`（语音） | 4 | JSON：`{"fileId":123,"durationSeconds":12}` |
| `VIDEO` | 5 | JSON：`{"fileId":123,"durationSeconds":30,"width":1080,"height":1920}` |
| `EMOJI` | 6 | UTF-8 表情，或 `{"stickerId":"emoji_01"}`（`[A-Za-z0-9._:-]{1,64}`） |
| `SYSTEM` | 9 | 服务端保留，客户端提交返回不支持 |

图片/视频的 `width`、`height` 与语音/视频的 `durationSeconds`（≤3600）、视频 `coverFileId` / `thumbnailFileId` 均为可选；`fileId` 必填。文本、表情以外的消息体最大 256KB。

消息撤回：仅发送者本人可在发送后 2 分钟内撤回；撤回后历史消息返回 `recalled=true` 且 `content` 为空，原文不再下发。撤回写入审计日志（`MESSAGE_RECALL`），并通过 `message.recalled` Outbox 广播 `MESSAGE_RECALL(0x0103)`；MQ 关闭时本机直接推送。

已读回执：客户端 `READ_ACK` 推进读游标后写入 Outbox（`message.read`），MQ 开启时各 Gateway 通过 `READ_NOTIFY(0x0112)` 广播，MQ 关闭时本机直接推送；也可通过 `/read-receipts` 查询成员读游标。

文件消息访问：上传时传 `conversationId` 绑定会话后，该会话成员可下载文件并可作为图片/语音/视频/文件消息引用；未绑定会话的文件仍仅上传者可访问。

发送消息示例：

```bash
curl -s -X POST http://127.0.0.1:8080/api/v1/conversations/p_1_1001_1002/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"conversationId":"p_1_1001_1002","clientMessageId":"m-1","type":"TEXT","content":"hello"}'
```

### 幂等

`clientMessageId` 在 `sender_id` 维度唯一。重复提交返回同一条消息与相同序号，`duplicate=true`。

### 实时推送（SSE）

浏览器端通过 `GET /api/v1/events`（需 Bearer）建立 SSE 长连接，服务端在消息落库后立即推送；前端据此增量刷新，不再依赖固定轮询。

事件示例（小群/单聊携带完整消息体）：

```text
event: message
data: {"kind":"message","conversationId":"p_1_1001_1002","messageId":"123456789",
       "seq":42,"senderId":"1001","clientMsgId":"m-1","messageType":"TEXT",
       "content":"hello","contentEncoding":"utf8","createdAt":"2026-11-16T11:20:00Z"}
```

- 成员数超过 `RealtimeNotice.FULL_PAYLOAD_MAX_RECIPIENTS`（200）时退化为游标事件：`{"kind":"cursor","conversationId":"...","seq":42}`，客户端按 `seq` 调 `GET /conversations/{id}/messages?cursor=<seq>` 补齐，推送体积与群人数无关。
- 每 15 秒发送一次 SSE 心跳注释；响应带 `X-Accel-Buffering: no`，避免 nginx 缓冲。
- 推送在线程池中执行（有界队列 + 满则丢弃最旧任务），慢连接不会拖慢发送接口；丢弃的推送由客户端重连后的增量同步补齐。
- 客户端断线自动重连，并保留低频轮询（SSE 健康时 60 秒一次）作为兜底。

### 跨实例推送（MQ fanout）

`VERYAL_MQ_ENABLED=true` 时，发送节点先在本节点直推，再把事件广播到 fanout 交换机 `veryal.push.exchange`：

```text
实例A ──本地直推──▶ 本机连接/SSE
      └──publish──▶ veryal.push.exchange
                        ├── veryal.push.<实例A>（独占、自动删除）
                        └── veryal.push.<实例B>
```

- 每个实例绑定一条非持久、独占、自动删除的队列（`RealtimeBusConfig`），实例下线自动清理。
- 每个实例收到事件后只推自己持有的连接；发送节点自己也会收到回环，由 `RealtimeDelivery` 按 `eventId` 去重（60s TTL、5 万条上限），不会重复推送。
- 广播失败只记日志，不阻塞发送；跨节点漏推由客户端增量同步补齐。
- 单机部署可用 `VERYAL_MQ_ENABLED=false`：此时只有本机直推，没有 fanout 广播。

## TCP / WebSocket 协议

固定 16 字节头，Big Endian：

```text
| Magic "VRYL"(4) | Version(1) | Flags(1) | Command(2) | Sequence(4) | BodyLength(4) | Envelope(N) |
```

`Envelope`（Protobuf）字段：`protocol_version / request_id / trace_id / command / payload`，业务消息放在 `payload` 中。TCP 帧与 WebSocket Binary Frame 的字节内容完全一致。

连接生命周期：

```text
connect → (10s 内) LOGIN(token, deviceId) → LOGIN_RESPONSE
        → HEARTBEAT / HEARTBEAT_ACK（读空闲 90s 断开）
        → SEND_MESSAGE → SERVER_ACK（已持久化） → 对端 MESSAGE_NOTIFY
        → DELIVERY_ACK / READ_ACK
```

命令码：`LOGIN 0x0010`、`SEND_MESSAGE 0x0100`、`SERVER_ACK 0x0101`、`MESSAGE_NOTIFY 0x0102`、`MESSAGE_RECALL 0x0103`、`DELIVERY_ACK 0x0110`、`READ_ACK 0x0111`、`READ_NOTIFY 0x0112`、`FRIEND_NOTIFY 0x0120`、`HEARTBEAT 0x0001`、`ERROR 0x01FF`。完整定义见 [veryal.proto](veryal-protocol/src/main/proto/veryal.proto)。

## TLS

TCP 与 WebSocket 共用一套可选 TLS 配置，开启后端口仍为 9099 / 9098，但客户端必须使用 TLS（TCP over TLS / `wss://`）：

```yaml
veryal:
  gateway:
    tls:
      enabled: true
      cert-path: /etc/veryal/tls/server.crt   # PEM 证书链
      key-path: /etc/veryal/tls/server.key    # PEM 私钥
      key-password:                           # 可选，加密私钥密码
```

对应环境变量：`VERYAL_TLS_ENABLED`、`VERYAL_TLS_CERT`、`VERYAL_TLS_KEY`、`VERYAL_TLS_KEY_PASSWORD`。未开启时保持明文，便于本地开发。

HTTP API 使用 Spring Boot 标准配置：

```yaml
server:
  ssl:
    enabled: true
    certificate: /etc/veryal/tls/server.crt
    certificate-private-key: /etc/veryal/tls/server.key
```

或设置 `SERVER_SSL_ENABLED=true`、`SERVER_SSL_CERTIFICATE`、`SERVER_SSL_CERTIFICATE_PRIVATE_KEY`。TLS 上下文在首次连接时构建，证书缺失会快速失败；暂不支持热加载证书。

### 连接数限制

```yaml
veryal:
  gateway:
    max-connections: 10000        # 0 表示不限制
    max-connections-per-ip: 20    # 0 表示不限制
```

环境变量：`VERYAL_GATEWAY_MAX_CONNECTIONS`、`VERYAL_GATEWAY_MAX_CONNECTIONS_PER_IP`。限制在 pipeline 最前端的 `ConnectionLimitHandler` 执行，超限连接直接关闭；拒绝次数记录在 `veryal.gateway.connections.rejected` 指标。

### 本地缓存

缓存策略集中写在 [CacheConfig.java](veryal-infrastructure/src/main/java/com/veryal/infrastructure/cache/CacheConfig.java)，不使用 `spring.cache.*` 自动配置：

- `user`：`maximumSize=100000`、`expireAfterWrite=10m`、开启统计。
- 用户资料读取使用 `@Cacheable`，资料更新使用 `@CacheEvict`；容量与 TTL 修改需显式改代码并经过评审。

### 文件存储

`FileStorage` 是策略接口，`FileStorageProvider` 按 `veryal.storage.type` 选择实现：

| type | 实现 | 说明 |
|---|---|---|
| `local` | `LocalFileStorage` | 本地磁盘，默认 `./data/files`，适合开发/单机 |
| `minio` | `MinioFileStorage` | MinIO / S3 兼容对象存储，自动创建 bucket，支持预签名 URL |
| `oss` | `AliyunOssFileStorage` | 阿里云 OSS，支持预签名 URL |

```yaml
veryal:
  storage:
    type: ${VERYAL_STORAGE_TYPE:local}
    local:
      root-path: ${VERYAL_STORAGE_LOCAL_ROOT:./data/files}
    minio:
      endpoint: ${VERYAL_MINIO_ENDPOINT:}
      access-key: ${VERYAL_MINIO_ACCESS_KEY:}
      secret-key: ${VERYAL_MINIO_SECRET_KEY:}
      bucket: ${VERYAL_MINIO_BUCKET:veryal}
    oss:
      endpoint: ${VERYAL_OSS_ENDPOINT:}
      access-key-id: ${VERYAL_OSS_ACCESS_KEY_ID:}
      access-key-secret: ${VERYAL_OSS_ACCESS_KEY_SECRET:}
      bucket: ${VERYAL_OSS_BUCKET:veryal}
```

- 文件大小上限 20MB（Spring multipart 与应用双重校验）。
- 对象键：`files/{yyyy/MM/dd}/{fileId}.{ext}`；文件名做路径穿越与控制字符清理。
- 上传先写对象存储，再写 `im_file`；元数据失败会补偿删除对象。
- 当前文件若绑定 `conversationId`，会话成员可下载；否则仅上传者可下载/删除。文件消息的成员引用权限与此一致。

### 分片上传与图片压缩

```yaml
veryal:
  upload:
    staging-path: ${VERYAL_UPLOAD_STAGING:./data/uploads}
    chunk-size-bytes: ${VERYAL_UPLOAD_CHUNK_SIZE:5242880}
    max-size-bytes: ${VERYAL_UPLOAD_MAX_SIZE:67108864}
```

- 流程：`POST /files/uploads` 初始化 → `PUT .../chunks/{index}` 逐片上传（重复上传幂等）→ `GET ...` 查看已上传索引 → `POST .../complete` 合并。
- 分片暂存在本机 `staging-path`；将其指向 NFS/CephFS 等共享卷即可支持多节点续传。完成后合并为完整对象并写入当前存储策略，再落 `im_file`。
- 普通上传仍限制 20MB；分片上传默认上限 64MB。
- JPEG/PNG 图片：最长边超过 1920px 时等比缩放；JPEG 以 0.85 质量重编码；解码失败或压缩变大时保留原图。

### 线程模型与虚拟线程

- **Netty EventLoop 固定使用平台线程**，只做解码/编码与投递，禁止阻塞调用。
- **TCP/WebSocket 业务执行器**可选 `platform`（有界线程池）或 `virtual`（虚拟线程 + 信号量并发上限）：
  ```yaml
  veryal:
    gateway:
      business-executor: ${VERYAL_GATEWAY_BUSINESS_EXECUTOR:platform}
      business-concurrency: ${VERYAL_GATEWAY_BUSINESS_CONCURRENCY:256}
  ```
  生产 profile 默认 `virtual`（并发 512）；虚拟线程适合 JDBC/Redis/RabbitMQ 等等待型 IO，并发仍受信号量、Hikari 连接池与下游容量约束。
- **HTTP**：生产 profile 开启 `spring.threads.virtual.enabled=true`，Tomcat 请求使用虚拟线程。
- **CPU 密集型操作**（JWT 签名、图片压缩、序列化）不会因虚拟线程变快；这类任务应继续受 CPU/有界执行器约束，并优先通过压测确认收益。
- 虚拟线程不是银弹：数据库连接池、Redis、RabbitMQ 的吞吐仍是瓶颈，建议先做连接数与 P99 压测再调整并发。

### 发送路径性能（实测）

部署环境：单机 2 vCPU / 3.7GB，JVM + MySQL + Redis + RabbitMQ + MinIO + nginx 同机。压测为服务器本机直连 `127.0.0.1:8080`，单会话、关闭发送限流、每条消息独立 HTTP 请求。

**先定位再优化**：JFR 采样显示 **50% 的样本阻塞在 `SocketInputStream.read`（等 MySQL 返回）**，GC 只触发 1 次。所以瓶颈是「每条消息的数据库往返次数」，不是磁盘、锁或 JVM。

据此做了四处改动：

| 改动 | 位置 | 效果 |
|---|---|---|
| 发送校验合并为一次查询 | `ConversationMapper.selectSendContext` | 会话类型 / 成员关系 / 禁言 / 私聊对端 / 对端拉黑，原来 5 条 SQL 合并为 1 条 |
| 私聊接收者复用上下文 | `MessageApplicationService.createEvent` | 不再为事件再查一次成员表 |
| Token 校验用户状态加 5 秒缓存 | `AuthApplicationService.authenticate` | 每个请求省一次用户表查询 + 一次只读事务的 BEGIN/COMMIT；**封禁/注销最长延迟 5 秒生效** |
| 序号分配单条 upsert / 活跃时间 2 秒节流 | `ConversationSequenceMapper` / `touchThrottled` | 行锁只在单语句内持有；同会话不再每条消息更新同一行 |
| 发送即推进自己的读游标 | `ConversationRepository.advanceReadCursor` | 单条 `UPDATE ... SET last_read_seq = GREATEST(...)`，多 1 次往返；发完消息会话列表不再给自己显示未读 |

单条消息的数据库往返从约 12 次降到约 6–7 次（认证 0 + 上下文 1 + 幂等 1 + 序号 2 + 消息 1 + Outbox 1 + 读游标 1）。

**实测（单会话，QPS / p50）**：

| 并发 | 优化前 | 优化后 |
|---|---|---|
| 顺序 | 41 QPS / 22ms | 41 QPS / 22ms |
| 8 | 55 QPS / 121ms | 70 QPS / 105ms |
| 16 | 115 QPS / 131ms | 184 QPS / 80ms |
| 32 | 130 QPS / 226ms | 207 QPS / 137ms |
| 64 | 119 QPS / 249ms | 183 QPS / 174ms |

**关于 MySQL 持久化配置**：同版本下对比 `innodb_flush_log_at_trx_commit=1, sync_binlog=1`（严格持久化）与 `=2, sync_binlog=1000`（放宽）：

| 并发 | 严格 `1/1` | 放宽 `2/1000` |
|---|---|---|
| 顺序 | 41 QPS / 22ms | 71 QPS / 12ms |
| 8 | 70 QPS / 105ms | 113 QPS / 60ms |
| 16 | 184 QPS / 80ms | 194 QPS / 68ms |
| 32 | 207 QPS / 137ms | 211 QPS / 126ms |
| 64 | 183 QPS / 174ms | 201 QPS / 142ms |

结论：**16 并发以上两者几乎无差别**（InnoDB binlog group commit 把 fsync 摊薄了），差异只出现在低并发与单条延迟上。由于本项目要求「消息不丢」，线上保持 `1/1` 严格持久化；只有在明确接受「断电/内核崩溃丢最近约 1 秒已提交事务」时才考虑放宽。

压测脚本见仓库外部工具或按下述方式自测：注册两个用户 → 建私聊 → 对同一会话并发 `POST /conversations/{id}/messages`，统计 QPS 与 p50/p95。

**发送即已读**：发送成功后会在**同一事务**内把发送者自己的 `last_read_seq` 推进到该消息的 `seq`（`GREATEST` 保证只前进），因此自己发的消息不会在会话列表里显示未读。这条语句每个会话只锁发送者自己的成员行，不涉及其他成员；不发已读回执事件，避免每条消息多写一条 Outbox。A/B 实测（旧 jar 与新 jar 交替 2 轮）吞吐差异在噪声范围内，没有可测回归。

### 读路径性能（实测）

会话列表原来是最典型的 N+1：`/conversations/cards` 对每个会话分别查成员列表、本人游标、对端资料、最大序号、最后一条消息（5 条 SQL）；`/conversations` 则是 3 条。成本随会话数线性增长。

改造后两个接口共用 `ConversationMapper.selectCardRows` 一条联表查询（`im_conversation` + 本人成员行 + 最后一条 `im_message` + `im_group`，单聊对端用 `CASE WHEN conversation_type = 1` 包住的子查询），数据库往返从 O(会话数) 降为 O(1)。**没有引入缓存**，因此不存在缓存失效与一致性问题。

实现上有三处刻意与旧语义对齐：

- `lastSeq` 取**真实消息的 `MAX(seq)`**，不取 `im_conversation_sequence.last_seq`。两者在正常路径相同，但一旦把「序号分配」和「消息插入」拆到不同事务（例如将来做异步持久化），序号表会领先于真实消息，未读数就会虚高。
- 最后一条消息的正文只取前 1KB（`LEFT(content, 1024)`）用于预览。预览本身只截 60 字符，这样即使有人发了 256KB 的长文本，200 个会话也不会把几十 MB 拉进堆内存。
- 仍沿用原来的 `LIMIT 200`（原 `selectByUser` 也有）。唯一差别是：超过 200 个会话时，取哪 200 条由「最近活跃」决定，而旧实现是「最近更新」；由于发消息会更新会话活跃时间（2 秒节流），两者实际上等价。

实测（同一用户、服务器本机、关闭限流，p50）：

| 接口 | 会话数 | 优化前 | 优化后 |
|---|---|---|---|
| `GET /conversations/cards` | 1 | — | 8–19ms |
| `GET /conversations/cards` | 30 | 168.1ms（5.60ms/会话） | **18.8ms（0.63ms/会话）** |
| `GET /conversations` | 30 | 61.6ms | **14.6ms** |
| `GET /conversations` | 1 | 15.4ms | 8.6ms |

30 个会话时卡片列表约 **9 倍**提升，且不再随会话数线性增长。返回结构（`ConversationCardResponse` 12 个字段）与未读数语义（`max(0, lastSeq - lastReadSeq)`）保持不变；其中发送者自己的游标已由发送流程推进（见「发送即已读」），所以自己刚发的消息不会算作未读。

### 运营管理员与审计

**运营管理员独立于普通用户**：

- 表 `im_platform_admin`，角色 `PLATFORM_ADMIN` / `SUPPORT`；
- `SUPPORT` 可查看审计，不能管理管理员或执行运营写操作；
- `PLATFORM_ADMIN` 可创建/启停管理员、重置密码、调整角色；
- 管理操作写入 `im_platform_audit_log`（V12），可通过 `/platform/audit-logs` 查询；
- 登录接口 `POST /api/v1/platform/auth/login` 返回：
  ```json
  {"scope":"PLATFORM","role":"PLATFORM_ADMIN","token":"...","adminId":1001,"username":"admin"}
  ```
- 普通用户登录返回 `scope=USER`、`role=USER`；
- 普通用户 JWT 不携带租户声明；服务端为单平台单空间，无租户上下文。
- 前端根据 `scope` 路由：`PLATFORM` 进入运营后台，`USER` 进入 IM 客户端；
- 两套 Token 完全分离，跨 scope 不能互用。
- 支持 TOTP MFA、Refresh Token 轮换、登录失败锁定。

启动引导运营管理员（首次启动自动创建，已存在则跳过）：

```yaml
veryal:
  platform:
    token-secret: ${VERYAL_PLATFORM_TOKEN_SECRET:}       # 为空时回退普通 Token 密钥
    bootstrap-username: ${VERYAL_PLATFORM_BOOTSTRAP_USERNAME:}
    bootstrap-password: ${VERYAL_PLATFORM_BOOTSTRAP_PASSWORD:}
    refresh-ttl: 14d
    max-login-attempts: 5
    lock-duration: 15m
```

### 用户搜索、隐私与举报

- `GET /api/v1/users/search?keyword=`：按用户名/昵称搜索，受隐私开关过滤；
- `GET /api/v1/users/{id}/profile`：查看主页资料，按隐私设置决定是否展示在线状态；
- `GET/PUT /api/v1/users/me/privacy`：隐私设置
  - `searchByUsername` / `searchByPhone`
  - `friendPolicy`：`REJECT`（拒绝）/ `VERIFY`（需验证）/ `DIRECT`（直接成为好友）
  - `showOnline` / `readReceipt` / `invitedToGroup`
- 好友申请会校验对方 `friendPolicy=REJECT`，拒绝时返回 403；`DIRECT` 时申请立即通过并建立双向好友关系；
- `POST /api/v1/reports`：举报用户/群/消息，写入 `im_report`；
- `GET /api/v1/reports/me`：我的举报记录。

### 运营后台

运营接口位于 `/api/v1/platform/ops/*`，由运营管理员 Bearer Token 保护：

- `SUPPORT`：只读用户/群/举报/概览；
- `PLATFORM_ADMIN`：封禁/解封用户、解散群、处理举报；
- 所有写操作写入 `im_platform_audit_log`。

```http
GET  /api/v1/platform/ops/dashboard
GET  /api/v1/platform/ops/users?keyword=
GET  /api/v1/platform/ops/users/{id}
POST /api/v1/platform/ops/users/{id}/ban
POST /api/v1/platform/ops/users/{id}/unban
POST /api/v1/platform/ops/users/{id}/mute
POST /api/v1/platform/ops/users/{id}/unmute
GET  /api/v1/platform/ops/groups?keyword=
GET  /api/v1/platform/ops/groups/{id}
POST /api/v1/platform/ops/groups/{id}/disband
GET  /api/v1/platform/ops/reports?status=PENDING
GET  /api/v1/platform/ops/reports/{id}
POST /api/v1/platform/ops/reports/{id}/handle
GET  /api/v1/platform/ops/sensitive-words
POST /api/v1/platform/ops/sensitive-words
DELETE /api/v1/platform/ops/sensitive-words/{id}
```

### 敏感词内容审核

- 词库存于 `im_sensitive_word`（V20），运营后台增删；
- 使用 **DFA Trie** 扫描文本，词库变更后重建内存 Trie；
- 文本消息（`TEXT`）命中敏感词返回 `40007 SENSITIVE_CONTENT`；
- 支持 JSON `{"text":"..."}` 与普通文本；
- 词库启动时加载，新增/删除后实时刷新。

### 好友与黑名单

- 好友申请/接受/拒绝/取消/拉黑/解除拉黑都会写 Outbox `friend.notify`，MQ 开启时广播 `FRIEND_NOTIFY(0x0120)`，关闭时本机直推；事件类型 `REQUEST=1, ACCEPTED=2, REJECTED=3, CANCELED=4, BLOCKED=5, UNBLOCKED=6`。
- 黑名单只拦截私聊发送（接收者拉黑发送者时 403）；群聊消息不按个人黑名单过滤。
- 通讯录在线状态来自 presence。

## 一致性设计

- 消息写入：校验成员 → 幂等检查 → 分配会话序号（`SELECT ... FOR UPDATE`）→ 插入消息 → 写入 Outbox → 更新会话时间，同一数据库事务内完成。
- 会话序号按会话独立分配，原子递增，不使用全局序号。
- 消息 ID 使用雪花 ID；单聊会话 ID 由 `(tenant, userA, userB)` 确定性生成，保证一对用户只有一个会话。
- 消息持久化先于 ACK；事务提交后才由 Outbox Publisher 发布事件，投递失败不影响消息可靠性，客户端通过同步游标补偿。
- 发布端：条件 UPDATE 抢占 Outbox 记录，Publisher Confirm 后置为已发布；失败按指数退避重试，超过上限进入失败状态，节点宕机遗留的 PUBLISHING 记录会被回收。
- 消费端：单投递队列手动 ACK，eventId 窗口去重；失败进入共享 TTL 重试队列，超过上限进入死信队列。
- 路由：投递消费者读取 Redis presence 的 `deviceId → gatewayId/connectionId`，按 gatewayId 分组，通过 `veryal.gateway.exchange` 的 `gateway.push.{gatewayId}` 发送推送命令；各实例只消费自己的网关队列并向本节点连接写 Channel。
- 群 ID 与群会话 ID 保持一致；建群/加成员同时写群成员表与会话成员表，消息权限只校验会话成员。
- 消息事件按会话类型使用 `message.private.created` / `message.group.created`，重试时保留原始 routing key。
- 群消息事件只包含消息本身，不包含接收者列表；投递时按会话成员分页拉取并逐页路由，私聊仍使用接收者快照。
- Delivery ACK 按 `(message_id, device_id)` 幂等持久化，投递序号只前进，并推进成员同步游标；Read ACK 推进读游标。

## SQL 组织约定

大 SQL 不进 Java 注解，按下面的规则分家：

| 语句类型 | 放置位置 | 理由 |
| --- | --- | --- |
| 多表 JOIN、标量子查询、多条件 SELECT、`GROUP BY`/聚合 | `veryal-infrastructure/src/main/resources/mapper/*.xml` | 可读、可单独 `EXPLAIN`、改 SQL 不用重编译 |
| 单表点查（`WHERE 主键/唯一键`） | Mapper 接口 `@Select` | 一行放得下，就近看参数绑定 |
| 所有写操作（`INSERT`/`UPDATE`/`DELETE`） | Mapper 接口注解 | 写入路径要一眼看清事务边界与幂等语义 |

`mapper-locations` 在 `application.yml` 中显式声明，测试与生产共用同一 classpath：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
```

当前 XML 覆盖的语句：

| XML | 语句 |
| --- | --- |
| `ConversationMapper.xml` | `selectByUser`、`selectSendContext`、`selectCardRows`、`selectMemberIdsPage` |
| `MessageMapper.xml` | `selectPageAfter`、`selectMaxSeq`、`searchText` |
| `GroupMapper.xml` | `selectByUser` |
| `FileMapper.xml` | `selectTotalBytes` |

### 标量子查询的唯一性前提

`selectSendContext` / `selectCardRows` 里用了标量子查询（`(SELECT ... AS mutedUntil)`）。标量子查询在 MySQL 中**最多只能返回 1 行**，否则直接报 `ERROR 1242 (21000): Subquery returns more than 1 row`——整个接口 500，不是返回脏数据。

因此这些 SQL 依赖下列唯一约束，改表结构前必须一起确认：

| 表 | 唯一约束 | 受益字段 |
| --- | --- | --- |
| `im_conversation_member` | `uk_conversation_user (conversation_id, user_id)` | `memberUserId`、`peerUserId`、`peerNickname` |
| `im_user_mute` | `PRIMARY KEY (user_id)` | `mutedUntil` |
| `im_message` | `uk_conversation_seq (conversation_id, seq)` | `MAX(seq)` 取最新消息 |
| `im_user` / `im_group` | `PRIMARY KEY (id)` | 联表取昵称/群名 |

`mutedUntil` 的子查询**故意不加 `LIMIT 1`**：`im_user_mute` 以 `user_id` 为主键，正常情况下永远只有 1 行；一旦唯一性被破坏（手工改表、迁移脚本漏主键），希望它立刻报错暴露问题，而不是静默返回任意一条禁言记录让用户被莫名禁言。

另外，`mutedUntil` 是**全局禁言**（运营后台写入 `im_user_mute`），与 `im_conversation_member.muted`（会话级免打扰）是两件事，不要混用。

### 线上取证与演示

在线上库用只读查询 + 临时表验证了上述前提：

```sql
-- 1) 真实结构：主键存在
SHOW CREATE TABLE im_user_mute;            -- PRIMARY KEY (`user_id`)
SELECT COUNT(*) FROM (SELECT user_id FROM im_user_mute GROUP BY user_id HAVING COUNT(*) > 1) t;  -- 0

-- 2) 破坏唯一性会怎样（临时表，无主键，同一 user 两行）
CREATE TEMPORARY TABLE tmp_mute_nopk (user_id BIGINT, muted_until DATETIME(3));
INSERT INTO tmp_mute_nopk VALUES (7,'2030-01-01'),(7,'2031-01-01');
SELECT (SELECT muted_until FROM tmp_mute_nopk WHERE user_id = 7);
-- ERROR 1242 (21000): Subquery returns more than 1 row

-- 3) 有主键时同样数据被挡在写入阶段
CREATE TEMPORARY TABLE tmp_mute_pk (user_id BIGINT PRIMARY KEY, muted_until DATETIME(3));
INSERT INTO tmp_mute_pk VALUES (7,'2030-01-01');
INSERT INTO tmp_mute_pk VALUES (7,'2031-01-01');
-- ERROR 1062 (23000): Duplicate entry '7' for key 'tmp_mute_pk.PRIMARY'
```

结论：**有唯一键时，脏数据根本写不进去（1062）；没有唯一键时，查询期才炸（1242）。** 所以「多加一个 `LIMIT 1`」只会掩盖问题，正确的做法是把唯一键补齐。

### 同一类风险的排查结果

- 全库 `NON_UNIQUE = 0` 索引核对：`uk_conversation_user`、`uk_blacklist_pair`、`uk_conversation_seq`、`uk_sender_client_msg`、`uk_group_user`、`uk_delivery_message_device`、`uk_friend_pair`、`uk_friend_request_direction`、`uk_device_id`、`uk_push_user_device`、`uk_web_push_endpoint_hash`、`uk_platform_admin_username`、`uk_platform_refresh_hash`、`uk_username` 全部存在。
- 代码里 18 处 `selectOne` 逐条核对，过滤条件全部命中唯一键；`MyBatisMessageRepository` 取「最新一条消息」属于非唯一场景，已显式加 `LIMIT 1`（`ORDER BY seq DESC LIMIT 1`）。

### XML 冒烟测试

SQL 从注解搬到 XML 后，编译期不再检查语句，写错 `namespace`/`id`/参数名只会在运行时抛 `Invalid bound statement`。`MapperXmlSmokeTest` 在 H2 上把每个 XML 语句都执行一次（用不存在的 id 查空结果），把这类错误前移到 `mvn test`：

```bash
mvn -pl veryal-bootstrap -am "-Dtest=MapperXmlSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

覆盖 9 条 XML 语句，断言「能执行 + 返回空/0」，不依赖库内数据，可长期作为回归用例。

## 测试

```bash
mvn test
```

- 单元测试：雪花 ID、Envelope 编解码、Netty 帧拆包/粘包/非法帧、JWT、PBKDF2、Outbox 事件 JSON、限流算法（令牌桶/滑动窗口/固定窗口/注册表）、TLS 配置开关与快速失败、连接数限制、Caffeine 缓存配置、本地/云端存储策略。
- 集成测试（`veryal-bootstrap`，H2 + 随机端口）：完整 HTTP 业务流程（含 Outbox 写入与幂等）、运营管理员登录/MFA/Refresh Token/登录锁定/角色/审计、运营后台用户/群/举报/敏感词、用户搜索/隐私/举报/推送设备、Web Push 订阅与 VAPID 配置、好友申请/接受/拒绝/好友/黑名单/通讯录、好友实时通知、群建群/成员权限/群消息、群管理、群公告/禁言、1000 人群容量与 100 并发发送冒烟、限流、审计、TLS、连接限制、缓存、文件、分片、图片压缩、多消息类型、撤回、搜索、已读回执、TCP/WebSocket、mapper XML 冒烟。

集成测试使用 `test` profile，Netty Gateway 端口配置为 0（系统分配），不占用本机端口。

`TestcontainersMatrixIntegrationTest` 使用真实 MySQL 8.0.30 / Redis 7.4 / RabbitMQ 3.13，启动完整应用并执行 Flyway V1–V22；已在云服务器 Docker 29.8.1 上通过，断言包括：22 个迁移全部成功、Redis 可读写、消息落库、Outbox 发布为 `status=1`（证明 RabbitMQ 链路真实可用）。没有可用 Docker 的机器会自动跳过该测试。

## 开发约定

- `Domain` 不依赖 Spring / Netty / MyBatis / Redis。
- 禁止在 Netty EventLoop 上执行阻塞操作，业务处理进入有界线程池。
- 禁止无界队列、无界线程池。
- Redis Key 统一由 `RedisKeys` 生成；协议指令码与字段编号不复用。
- 数据库时间统一 UTC，客户端自行转换时区。
- 消息排序只信任服务端分配的会话序号。
- 限流策略通过 `veryal.rate-limit.policies` 配置，每条策略可选择不同算法；当前为单机内存实现，集群化需切换 Redis 计数。
- POJO / 配置类使用 Lombok（`@Getter`、`@Setter`、`@RequiredArgsConstructor`、`@Slf4j`）；通用工具优先使用 Hutool，避免重复造轮子。

## 后续路线

- V0.3：已完成 Outbox + RabbitMQ + 重试/DLQ + 多实例实时投递；后续补充投递记录表与跨节点路由优化。
- V0.4：群聊、群管理（含群主转让/解散）、群消息分页扇出、Redis 网关路由、策略模式限流、审计日志/查询、可选 TLS、连接数上限已完成；1000 人容量与 100 并发冒烟已通过，独立压测待补。
- V0.5：文件/图片/语音/视频、分片上传、多消息类型、好友/黑名单/通讯录、群公告/禁言/@、离线推送抽象、隐私/举报、运营后台基础。
- V1.0：移动端 + Web 客户端、真实 APNs/FCM/厂商推送、运营后台完善、内容审核、压测与长稳。

**V1.1 明确不做：** 多租户、企业组织、飞书式协作、开放平台/SDK、私有化部署控制台、支付/发票。

详细进度与差距见 [docs/DEVELOPMENT-STATUS.md](docs/DEVELOPMENT-STATUS.md)。
