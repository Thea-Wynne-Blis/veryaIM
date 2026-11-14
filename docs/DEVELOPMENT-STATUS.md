# VERYAL 开发状态

> 基线：`docs/VERYAL-V1.0-技术架构与开发设计文档.md`（V1.0，开发基线）
> 当前实现：V0.1 通信核心 + V0.2 基础 IM 业务 + V0.3 消息可靠性核心
> 最后更新：2026-11-14

## 一、总体进度

| 阶段 | 目标 | 状态 |
|---|---|---|
| V0.1 | 通信核心（Netty / Protobuf / 拆包 / 心跳 / 认证 / 连接管理） | 已实现并通过端到端测试 |
| V0.2 | 基础 IM（用户 / 设备 / 单聊 / 群聊 / 会话 / 消息持久化 / ACK / 幂等 / 历史） | 单聊、群聊基础与游标同步已实现 |
| V0.3 | 可靠性（RabbitMQ / Outbox / 重试 / DLQ / 跨节点投递） | Outbox + RabbitMQ 投递/重试/DLQ/多实例广播已实现；Redis 路由优化待 V0.4 |
| V0.4 | 分布式集群（多 Gateway / Redis 路由 / 群聊扇出） | 群聊、群管理（含群主转让/解散）、Redis 网关路由、策略模式限流已实现；大群扇出、TLS/审计待继续 |
| V0.5 | 性能与商业化（文件、多租户、SDK、开放平台） | 未开始 |

## 二、第一交付清单对照

### 工程基础

- [x] Maven 多模块项目（6 个代码模块 + bootstrap 启动模块 + 聚合父工程，统一 parent + BOM 版本管理）
- [x] 统一依赖版本管理（Spring Boot 3.3.5 BOM + protobuf / MyBatis-Plus / Hutool 固定版本）
- [x] 统一异常体系（`ErrorCode` + Business/System/Protocol/Authentication/Persistence）
- [x] 统一日志体系（`TraceContext` MDC、`logback-spring.xml`、统一字段）
- [x] 统一配置管理（`application.yml` + dev/test/prod profile）
- [x] Docker 开发环境（`deploy/docker`，MySQL 8.0.30 / Redis / RabbitMQ / MinIO / 应用镜像）
- [x] 数据库版本化迁移（Flyway `V1__init.sql`、`V2__outbox.sql`）
- [x] Lombok 消除 POJO / 构造器 / 日志样板代码，Hutool 提供 ID 与字符串工具

### 通信基础

- [x] Netty TCP Gateway（9099）
- [x] WebSocket Gateway（9098，`/ws`，Binary Frame）
- [x] Protobuf Envelope（`veryal.proto`）
- [x] 自定义 16 字节协议帧（Magic/Version/Flags/Command/Sequence/BodyLength）
- [x] 拆包 / 粘包处理、非法帧与超大帧拒绝
- [x] 心跳（读空闲 90s 断开，HEARTBEAT/HEARTBEAT_ACK）
- [x] 认证（HS256 JWT，10s 登录超时，设备绑定）
- [x] 连接管理（本地连接注册表、多端连接、登录/断开回调、在线状态）
- [x] 业务线程池隔离（有界队列、拒绝时返回 SERVICE_UNAVAILABLE）
- [x] 限流（策略模式：令牌桶 / 滑动窗口 / 固定窗口；HTTP 登录、HTTP API、TCP 登录、消息发送）

### 基础业务

- [x] 用户管理（注册 / 登录 / 资料 / 状态）
- [x] 设备管理（设备绑定、最后登录时间、设备列表）
- [x] 单聊（确定性会话 ID，一对用户唯一会话）
- [x] 群聊基础（建群、加成员、群主/管理员权限、群会话与群消息）
- [x] 群管理（管理员设置、改名、踢人、退群、群主转让、解散群）
- [x] 会话（成员、读游标、同步游标、列表、未读数）
- [x] 消息持久化（MySQL，事务内分配序号 + 写入）
- [x] 历史消息（游标分页，无 OFFSET）
- [x] 消息序号（`im_conversation_sequence` 行锁原子分配）
- [x] 发件箱 / 收件箱实时推送（单机直投或 Outbox + MQ）
- [x] 投递记录查询（按消息查询设备投递状态）
- [ ] 大群异步扇出（成员分页拉取）

### 可靠性

- [x] Server ACK（持久化后返回）
- [x] Delivery ACK（设备投递记录持久化 + 推进同步游标）
- [x] Read ACK（推进读游标，只前进）
- [x] 消息幂等（客户端消息 ID + 唯一索引兜底）
- [x] Outbox（消息与事件同事务写入，发布器抢占 + Publisher Confirm）
- [x] RabbitMQ 异步投递（每实例独立队列广播，手动 ACK + eventId 去重）
- [x] 有限重试 / 死信队列（指数退避 + TTL 重试队列 + 共享 DLQ）
- [x] 离线同步（客户端游标 + `GET /api/v1/sync/messages`）

### 基础设施

- [x] MySQL 8.0.30（MyBatis-Plus + Flyway DDL、UTC DATETIME(3)）
- [x] Redis（在线状态 Hash，connectionId 校验防旧连接误删；生产 profile 启用）
- [x] Caffeine（用户资料 L1 缓存，`@Cacheable` / `@CacheEvict`）
- [x] RabbitMQ（Outbox 发布、每实例投递队列、重试队列、死信队列）
- [ ] MinIO（部署文件已提供，文件域在 V0.5）
- [x] Prometheus 指标（连接数、连接建立/关闭、心跳超时、消息接收、协议错误）
- [ ] Grafana（按部署环境接入）
- [x] Actuator health / info / metrics

### 测试

- [x] 单元测试：雪花 ID、Envelope 编解码、帧编解码（拆包/粘包/非法帧/超大帧）、JWT、PBKDF2、Outbox 事件 JSON、限流算法（令牌桶/滑动窗口/固定窗口/注册表）
- [x] 限流集成测试：消息发送超过令牌桶容量返回 429
- [x] HTTP 集成测试：注册→登录→建会话→发消息→幂等→历史→同步→已读→越权/未认证，并校验 Outbox 事件写入
- [x] 群聊集成测试：建群、成员权限、群消息、群事件类型、非成员越权
- [x] 群管理集成测试：管理员设置、改名、踢人、退群、群主/管理员权限边界
- [x] 群生命周期集成测试：群主转让、解散群、解散后会话不可用
- [x] TCP 端到端测试：真实 Socket、认证、心跳、发送、对端实时推送、投递记录与查询
- [x] WebSocket 端到端测试：握手、登录、心跳
- [ ] Testcontainers（当前环境无 Docker，集成测试使用 H2 MySQL 模式）
- [ ] 压力测试 / 72 小时长稳（待 V0.5 与独立压测环境）

## 三、相对设计文档的结构调整

| 调整 | 原因 |
|---|---|
| 模块收敛为 6 + 1 | 设计文档中的 `common-* / protocol-* / access-*` 子模块收敛为 `veryal-common / protocol / domain / application / infrastructure / access` + `bootstrap`，模块内部以包边界隔离。分层约束不变，构建速度与查阅成本明显降低 |
| `veryal-protocol` 合并 Netty 编解码 | 帧编解码属于协议实现细节，与 Protobuf 定义同模块维护；`protocol.api / protobuf / netty` 仍保持包级隔离 |
| `veryal-access` 合并 core/tcp/ws/http | 连接注册表、认证、心跳、命令分发由 TCP/WebSocket/HTTP 共用，放在同一模块内复用，避免跨模块循环依赖 |
| `im_user.password_hash` | 文档核心表未包含认证字段，V0.1 需要账号密码登录；后续可拆分为独立凭证表 |
| 群 ID = 群会话 ID | 群与群会话一一对应，建群/加成员同时写群成员与会话成员；消息权限统一由会话成员校验 |
| 群成员上限 200 | V1 群消息采用接收者快照事件；超过上限的大群后续改为消费者分页拉取 + 异步扇出 |
| 投递记录表 | 文档未定义投递表，新增 `im_message_delivery`（message_id + device_id 唯一），投递序号只前进 |
| 单机实时推送 | 未开启 MQ 时，发送方在持久化后直接向本节点在线连接推送；开启 MQ 后统一由 Outbox 消费投递 |
| 每实例独立投递队列 | 已替换为“单投递队列 + Redis 路由 + 网关推送队列”：投递消费者按 gatewayId 定向推送，避免每实例重复消费 |
| Delivery ACK 内存跟踪 | 文档未定义投递记录表；当前使用内存 `DeliveryTracker`，后续替换为投递记录表 |

## 四、已知限制与风险

1. **路由依赖 Redis presence**：网关路由取自 presence 租约，节点宕机后需要主动清理/补偿；推送失败会进入死信队列，客户端仍以同步游标补偿。
2. **会话序号死锁重试未实现**：高并发同一会话写入可能触发死锁，当前依赖数据库自动回滚，重试策略待补齐。
3. **Redis 在线状态过期清理**：读空闲断开会主动删除，但节点宕机场景需要周期补偿任务。
4. **无 TLS**：TCP/WebSocket 明文传输，部署层需接入 Nginx / TLS 或 Netty SslHandler。
5. **限流与风控缺失**：登录失败次数、发送速率、连接数均未限流。
6. **大群扇出未实现**：群成员上限 200，群消息事件携带接收者快照；超过上限的大群需改为消费者分页拉取成员并异步扇出。
7. **Docker 未在开发机验证**：开发环境无 Docker，`deploy/docker` 为交付文件，需在具备 Docker 的机器上执行 `docker compose up` 验证。
8. **会话列表存在 N+1 查询**：`list` 对每个会话查询一次最大序号，后续改为批量聚合查询。
9. **投递记录查询能力缺失**：投递状态已持久化，但尚无查询接口与未读数聚合；后续补充。
10. **限流为单机内存实现**：多实例部署时每个节点独立计数，集群级限流需切换 Redis 计数/滑动窗口；连接数上限暂未单独限制。
11. **dev/test 会累积 Outbox**：MQ 关闭时 Outbox 只写不发，便于本地排查；生产环境正常发布。

## 五、验证记录（2026-11-14）

### 本地构建与测试

- 环境：Windows 11、JDK 21.0.11、Maven 3.9.4。
- `mvn -B -ntp verify`：6 个代码模块 + bootstrap + 父工程全部构建成功。
- 测试共 41 个，全部通过：
  - `veryal-common`：雪花 ID 唯一性/单调性/节点范围、限流算法（令牌桶/滑动窗口/固定窗口/注册表）（12）。
  - `veryal-protocol`：Envelope 编解码、未知指令、版本校验、帧头格式、半包/粘包、非法魔数、超大帧、指令不一致（12）。
  - `veryal-application`：JWT 签发/过期/篡改/错误密钥、PBKDF2、Outbox 事件 JSON（9）。
  - `veryal-bootstrap`：HTTP 全流程（含 Outbox）、群聊、群管理、群生命周期、限流、TCP 端到端、WebSocket 端到端（8）。
- 可执行 jar 使用默认 `dev` profile 启动验证：H2 内存库 + 内存在线状态，`/actuator/health=UP`，注册接口返回成功。

### MySQL 8.0.30 + Redis 实际环境验证

使用 MySQL 8.0.30 与 Redis 7.4 容器（`prod` profile，经 SSH 隧道连接）启动打包产物：

- 数据访问层切换 MyBatis-Plus 后重新回归：注册、登录、创建会话、发送消息、幂等重发、历史查询链路在 MySQL 8.0.30 上全部通过。
- Flyway 自动执行 `V1__init.sql`、`V2__outbox.sql`、`V3__group.sql`、`V4__message_delivery.sql`。
- `GET /actuator/health` 返回 `UP`（MySQL + Redis）。
- HTTP 流程验证通过：注册 → 登录 → 创建单聊 → 发送消息（seq=1）→ 重复发送命中幂等（`duplicate=true`，同 messageId/seq）→ 历史消息 1 条 → 同步 1 条 → 已读上报。
- 数据库核对：`im_user`、`im_conversation_sequence.last_seq=1`、`im_message` 记录存在。
- 裸 TCP 客户端（Python，独立于服务端代码）完成 LOGIN，收到 `LOGIN_RESPONSE`（command=0x11）。
- 连接在线期间 Redis `veryal:presence:user:{userId}` Hash 存在设备元数据（deviceId/gatewayId/connectionId/status），TTL 约 300s；断开后由服务端清理。

### Outbox + RabbitMQ 端到端验证

使用 RabbitMQ 3.13 容器（`veryal.mq.enabled=true`，MySQL/Redis 同时开启）启动打包产物：

- Flyway 自动升级到 V2，`im_outbox` 表创建成功。
- HTTP 发送消息后，`im_outbox` 记录 status=1（已发布）、retry_count=0、published_at 非空。
- 每个 Gateway 实例声明独立投递队列；`rabbitmqctl list_queues` 显示实例队列消费者数=2、消息积压=0。
- 接收方通过裸 TCP 客户端连接，成功收到 `MESSAGE_NOTIFY`（command=0x0102），验证“持久化 → Outbox → RabbitMQ → 消费者 → 本节点连接”完整链路。
- 重试队列与死信队列为空（无失败消息），拓扑创建成功。

### 群聊 + MQ 验证

- Flyway 自动升级到 V3，`im_group`、`im_group_member` 表创建成功。
- HTTP 建群（3 人）、群主发送群消息、非成员读/发返回 403、群主添加成员后新成员可发送，全部通过。
- 群消息写入 `message.group.created` 类型 Outbox；接收方 TCP 客户端收到 `MESSAGE_NOTIFY`，队列积压为 0。

### 多 Gateway 跨节点路由验证

使用两个应用实例（gateway-a:8080/9099、gateway-b:8081/9199）连接同一 MySQL/Redis/RabbitMQ：

- 接收方登录 gateway-b，presence 记录 gatewayId=gateway-b。
- 发送方通过 gateway-a HTTP 发送消息；全局单投递队列只消费一次，按 Redis 路由拆分到 `gateway.push.gateway-b`。
- gateway-b 的推送队列消费后向本节点连接写入 `MESSAGE_NOTIFY`，接收方成功收到消息。
- RabbitMQ 拓扑：单投递队列、共享重试队列、共享死信队列、每实例网关推送队列，消费与积压正常。

> 验证用容器与数据库不包含仓库代码，仅用于确认 MySQL 方言、Flyway、Redis 在线状态与 RabbitMQ 投递链路在真实环境可用。

## 六、下一步计划（建议顺序）

1. 大群异步扇出（成员分页拉取，替代接收者快照）与服务发现。
2. TLS、审计日志、连接数上限。
3. Testcontainers 集成测试矩阵 + 压测脚本（连接数、吞吐、P99）。
4. 集群级限流（Redis 计数/滑动窗口）。
