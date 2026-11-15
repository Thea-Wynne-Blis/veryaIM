# VERYAL Backend

VERYAL 是面向个人用户的消费级社交即时通讯产品（类微信 / QQ），当前仓库为服务端实现，采用 Java 21 + Netty + Spring Boot + Protobuf + MyBatis-Plus + MySQL + Redis + RabbitMQ + Caffeine + Lombok + Hutool，按照 [`docs/VERYAL-V1.0-技术架构与开发设计文档.md`](docs/VERYAL-V1.0-技术架构与开发设计文档.md) V1.1 的模块与分层约束落地。

> **产品方向（V1.1）**
>
> - VERYAL 定位为消费级社交 IM：账号、好友、单聊、群聊、富媒体、多端同步、离线推送、隐私与举报、运营后台。
> - **不做多租户 / 企业组织 / 飞书式协作 / 开放平台 / 私有化交付。**
> - 租户表、租户码登录、平台租户管理、租户管理员、租户用量/导出/删除接口已移除（V17）。
> - 数据库和部分领域对象仍保留固定 `tenant_id=1` 兼容列，产品层不可见，后续迁移删除。
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
- **多租户已移除（V17）**：租户表、租户码、租户管理接口、租户管理员、用量/导出/删除已删除；内部保留固定 `tenant_id=1` 兼容列，待后续迁移。
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
│   ├── core            ApiResponse / CursorPage / TimeProvider / Preconditions / TenantIds / RandomIds
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
public void acquire(long tenantId, long senderId) { ... }
```

- `policy` 对应 `veryal.rate-limit.policies` 中的策略；
- `dimension` 支持 `IP` / `USER` / `TENANT` / `GLOBAL` / `CUSTOM`；
- `CUSTOM` 使用 SpEL，`#p0`、参数名均可引用；
- 消息限流放在幂等与内容校验之后，避免重复/非法消息消耗额度；
- 登录由注解限流，`RateLimitFilter` 不再重复处理登录路径；
- 超限统一抛 `BusinessException(RATE_LIMITED)`，由全局异常处理返回 429。

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
| GET | `/api/v1/users/me/devices` | 设备列表 |
| GET | `/api/v1/users/me/audit-logs?limit=50` | 我的审计记录 |
| GET | `/api/v1/users/search?keyword=...` | 搜索用户 |
| GET | `/api/v1/users/{id}/profile` | 用户主页资料 |
| GET / PUT | `/api/v1/users/me/privacy` | 查询 / 更新隐私设置 |
| POST | `/api/v1/reports` | 举报用户 / 群 / 消息 |
| GET | `/api/v1/reports/me` | 我的举报记录 |
| GET | `/api/v1/platform/ops/dashboard` | 运营概览 |
| GET | `/api/v1/platform/ops/users?keyword=` | 运营用户搜索 |
| GET | `/api/v1/platform/ops/users/{id}` | 运营用户详情 |
| POST | `/api/v1/platform/ops/users/{id}/ban` | 封禁用户 |
| POST | `/api/v1/platform/ops/users/{id}/unban` | 解封用户 |
| GET | `/api/v1/platform/ops/groups?keyword=` | 运营群搜索 |
| GET | `/api/v1/platform/ops/groups/{id}` | 运营群详情/成员 |
| POST | `/api/v1/platform/ops/groups/{id}/disband` | 解散群 |
| GET | `/api/v1/platform/ops/reports?status=` | 举报列表 |
| GET | `/api/v1/platform/ops/reports/{id}` | 举报详情 |
| POST | `/api/v1/platform/ops/reports/{id}/handle` | 处理举报 |
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

好友事件在用户无在线连接时进入 `OfflinePushProvider`。默认 `none` 只记录日志；配置 `webhook` 后把
`userId/title/body/data` POST 到厂商网关，由网关适配 APNs / FCM / 华为 / 小米等：

```yaml
veryal:
  push:
    provider: none          # none | webhook
    webhook-url: ${VERYAL_PUSH_WEBHOOK_URL:}
    webhook-token: ${VERYAL_PUSH_WEBHOOK_TOKEN:}
```

直接对接 APNs/FCM 需要真实证书/Service Account，当前仓库不内置凭证，也未做设备 token 管理。

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

`clientMessageId` 在 `(tenant_id, sender_id)` 维度唯一。重复提交返回同一条消息与相同序号，`duplicate=true`。

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
- 对象键：`tenant/{tenantId}/{yyyy/MM/dd}/{fileId}.{ext}`；文件名做路径穿越与控制字符清理。
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
  - `friendPolicy`：`REJECT` / `VERIFY` / `DIRECT`
  - `showOnline` / `readReceipt` / `invitedToGroup`
- 好友申请会校验对方 `friendPolicy=REJECT`，拒绝时返回 403；
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
GET  /api/v1/platform/ops/groups?keyword=
GET  /api/v1/platform/ops/groups/{id}
POST /api/v1/platform/ops/groups/{id}/disband
GET  /api/v1/platform/ops/reports?status=PENDING
GET  /api/v1/platform/ops/reports/{id}
POST /api/v1/platform/ops/reports/{id}/handle
```

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

## 测试

```bash
mvn test
```

- 单元测试：雪花 ID、Envelope 编解码、Netty 帧拆包/粘包/非法帧、JWT、PBKDF2、Outbox 事件 JSON、限流算法（令牌桶/滑动窗口/固定窗口/注册表）、TLS 配置开关与快速失败、连接数限制、Caffeine 缓存配置、本地/云端存储策略。
- 集成测试（`veryal-bootstrap`，H2 + 随机端口）：完整 HTTP 业务流程（含 Outbox 写入与幂等）、运营管理员登录/MFA/Refresh Token/登录锁定/角色/审计、运营后台用户/群/举报处理、用户搜索/隐私/举报、好友申请/接受/拒绝/好友/黑名单/通讯录、好友实时通知、群建群/成员权限/群消息、群管理（管理员/改名/踢人/退群/转让/解散）、群公告/禁言、1000 人群容量与 100 并发发送冒烟、群消息分页扇出、限流 429、审计日志、连接数超限拒绝、文件上传/下载/权限/删除、文件会话成员访问、分片上传/续传、图片压缩、多消息类型与文件归属校验、消息撤回、消息搜索、已读回执、TCP 私聊/群聊端到端（登录/心跳/发送/实时推送/投递记录）、WebSocket 握手与登录心跳。

集成测试使用 `test` profile，Netty Gateway 端口配置为 0（系统分配），不占用本机端口。

`TestcontainersMatrixIntegrationTest` 使用真实 MySQL 8.0.30 / Redis 7.4 / RabbitMQ 3.13，启动完整应用并执行 Flyway V1–V16；已在云服务器 Docker 29.8.1 上通过（Testcontainers 2.0.5 / docker-java 3.7.1）。没有可用 Docker 的机器会自动跳过该测试。

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
