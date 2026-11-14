# VERYAL Backend

VERYAL 是高性能、跨平台、可扩展的即时通讯基础设施。本仓库是服务端实现，采用 Java 21 + Netty + Spring Boot + Protobuf + MyBatis-Plus + MySQL + Redis + Caffeine，按照 `docs/VERYAL-V1.0-技术架构与开发设计文档.md` 的模块与分层约束落地。

当前版本完成了 **V0.1 通信核心** 与 **V0.2 基础 IM 业务** 的可运行纵向切片：

- 自定义 16 字节二进制协议帧 + Protobuf Envelope，TCP 与 WebSocket 共用。
- Netty TCP Gateway（默认 9099）与 WebSocket Gateway（默认 9098，`/ws`）。
- 登录认证（HS256 JWT）、心跳租约、连接注册表、读空闲断开、命令分发、业务线程池隔离。
- 用户 / 设备 / 单聊会话 / 消息 / 会话序号 / 历史消息 / 幂等发送 / Server ACK / Delivery ACK / Read ACK / 游标同步。
- HTTP API（8080）：注册、登录、资料、设备、会话、消息、同步。
- MySQL 8.0.30 + MyBatis-Plus + Flyway；本地开发使用 H2（MySQL 兼容模式）零依赖启动。
- Redis 在线状态（生产 profile）+ 内存实现（开发/测试 profile），Caffeine 用户资料缓存。
- Prometheus / Actuator 指标、统一日志字段（traceId / userId / connectionId 等）。

## 模块结构

按“6 个代码模块 + 1 个启动模块”组织，每个模块内部再按业务能力分包，避免 20+ 个小模块带来的跳转成本：

```text
veryal-backend/
├── veryal-common/          基础能力：异常、ID、JSON、日志上下文（无 Spring 依赖）
├── veryal-protocol/        协议：Command、Protobuf 定义、16 字节帧编解码
├── veryal-domain/          领域层：identity / device / conversation / message / presence（纯 Java）
├── veryal-application/     应用层：identity / conversation / message / sync（用例编排、事务）
├── veryal-infrastructure/  基础设施：mysql(MyBatis-Plus) / redis / cache（实现领域端口）
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
│   ├── core            ApiResponse / CursorPage / TimeProvider / Preconditions / TenantIds
│   ├── exception       ErrorCode / VeryalException / Business / System / Protocol / Auth / Persistence
│   ├── id              IdGenerator / SnowflakeIdGenerator
│   ├── json            JsonUtils
│   └── observability   TraceContext / LogFields / MetricNames
├── protocol
│   ├── api             Command / ProtocolConstants / ProtocolFrame / ProtocolMessage
│   ├── protobuf        veryal.proto 生成类 + EnvelopeCodec
│   └── netty           VeryalFrameDecoder/Encoder / EnvelopeDecoder/Encoder
├── domain
│   ├── identity        User / UserStatus / UserRepository / PasswordHasher
│   ├── device          Device / Platform / DeviceRepository
│   ├── conversation    Conversation / ConversationMember / ConversationRepository / ConversationIds
│   ├── message         Message / MessageType / MessagePolicy / MessageRepository / ConversationSequenceRepository
│   └── presence        PresenceRecord / PresenceRepository
├── application
│   ├── identity        AuthApplicationService / SessionApplicationService / UserApplicationService / JwtTokenService
│   ├── conversation    ConversationApplicationService / ConversationView / ConversationSummary
│   ├── message         MessageApplicationService / SendMessageCommand / SendMessageResult / DeliveryTracker
│   └── sync            SyncApplicationService
├── infrastructure
│   ├── mysql
│   │   ├── mapper      UserMapper / DeviceMapper / ConversationMapper / ConversationMemberMapper / MessageMapper / ConversationSequenceMapper
│   │   ├── po          UserPO / DevicePO / ConversationPO / ConversationMemberPO / MessagePO
│   │   ├── repository  MyBatis*Repository
│   │   ├── MyBatisPlusConfig / SqlTime
│   ├── redis           RedisKeys / RedisPresenceRepository / InMemoryPresenceRepository
│   └── cache           CacheConfig
├── access
│   ├── core            Connection / ConnectionRegistry / GatewayHandlers / handler / protocol / push / metrics
│   ├── tcp             TcpGatewayServer / TcpGatewayProperties
│   ├── websocket       WebSocketGatewayServer / codec
│   └── http            controller / dto / security / error / config
└── bootstrap           VeryalApplication / CoreConfig / resources
```

> 设计文档中的 `protocol-api / protocol-protobuf / protocol-netty` 与 `access-core / access-tcp / access-websocket / access-http` 已收敛为 `veryal-protocol`、`veryal-access` 内部的包边界；分层约束不变，构建与查阅成本更低。


## 快速开始（无需 MySQL / Redis）

```bash
# JDK 21 + Maven 3.9+
mvn -B -DskipTests package
java -jar veryal-bootstrap/target/veryal-bootstrap-1.0.0-SNAPSHOT.jar
```

默认激活 `dev` profile：H2 内存库 + 内存在线状态，Flyway 关闭。

```bash
curl -s http://127.0.0.1:8080/actuator/health
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","nickname":"Alice"}'
```

## 使用 Docker（MySQL 8.0.30 + Redis）

```bash
cd deploy/docker
cp .env.example .env
# 修改 .env 中的密码与 VERYAL_TOKEN_SECRET
docker compose up -d --build
```

应用启动时由 Flyway 执行 `db/migration/V1__init.sql` 建表；MySQL 使用 UTC、utf8mb4。

## HTTP API

所有业务接口都需要 `Authorization: Bearer <token>`，响应统一为：

```json
{"code":0,"message":"success","data":{},"traceId":"..."}
```

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录并绑定设备 |
| GET | `/api/v1/auth/me` | 当前用户 |
| GET / PUT | `/api/v1/users/me` | 查询 / 修改资料 |
| GET | `/api/v1/users/me/devices` | 设备列表 |
| POST | `/api/v1/conversations` | 创建单聊 `{"peerUserId":1002}` |
| GET | `/api/v1/conversations` | 会话列表（含读游标与最大序号） |
| GET | `/api/v1/conversations/{id}` | 会话详情 |
| POST | `/api/v1/conversations/{id}/messages` | 发送消息 |
| GET | `/api/v1/conversations/{id}/messages?cursor=0&limit=50` | 历史消息（游标） |
| POST | `/api/v1/conversations/{id}/read` | 上报已读序号 |
| GET | `/api/v1/sync/messages?conversationId=...&cursor=...&limit=...` | 离线同步 |

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

命令码：`LOGIN 0x0010`、`SEND_MESSAGE 0x0100`、`SERVER_ACK 0x0101`、`MESSAGE_NOTIFY 0x0102`、`DELIVERY_ACK 0x0110`、`READ_ACK 0x0111`、`HEARTBEAT 0x0001`、`ERROR 0x01FF`。完整定义见 [veryal.proto](veryal-protocol/src/main/proto/veryal.proto)。

## 一致性设计

- 消息写入：校验成员 → 幂等检查 → 分配会话序号（`SELECT ... FOR UPDATE`）→ 插入消息 → 更新会话时间，同一数据库事务内完成。
- 会话序号按会话独立分配，原子递增，不使用全局序号。
- 消息 ID 使用雪花 ID；单聊会话 ID 由 `(tenant, userA, userB)` 确定性生成，保证一对用户只有一个会话。
- 消息持久化先于 ACK；实时推送失败不影响可靠性，客户端通过同步游标补偿。
- Delivery ACK 推进成员同步游标，Read ACK 推进读游标，游标只前进。

## 测试

```bash
mvn test
```

- 单元测试：雪花 ID、Envelope 编解码、Netty 帧拆包/粘包/非法帧、JWT、PBKDF2。
- 集成测试（`veryal-bootstrap`，H2 + 随机端口）：完整 HTTP 业务流程、TCP 端到端（登录/心跳/发送/实时推送/回执）、WebSocket 握手与登录心跳。

集成测试使用 `test` profile，Netty Gateway 端口配置为 0（系统分配），不占用本机端口。

## 开发约定

- `Domain` 不依赖 Spring / Netty / MyBatis / Redis。
- 禁止在 Netty EventLoop 上执行阻塞操作，业务处理进入有界线程池。
- 禁止无界队列、无界线程池。
- Redis Key 统一由 `RedisKeys` 生成；协议指令码与字段编号不复用。
- 数据库时间统一 UTC，客户端自行转换时区。
- 消息排序只信任服务端分配的会话序号。

## 后续路线

- V0.3：RabbitMQ + Outbox + 重试/DLQ + 跨 Gateway 投递 + 多端离线补偿。
- V0.4：多 Gateway、Redis 路由、服务发现、群消息异步扇出。
- V0.5：Caffeine 两级缓存完善、MinIO 文件服务、多租户、开放 API / SDK。
- V1.0：管理后台、完整群聊/好友体系、监控告警、私有化部署。

详细进度与差距见 [docs/DEVELOPMENT-STATUS.md](docs/DEVELOPMENT-STATUS.md)。
