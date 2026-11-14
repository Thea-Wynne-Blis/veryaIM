# VERYAL 开发状态

> 基线：`docs/VERYAL-V1.0-技术架构与开发设计文档.md`（V1.0，开发基线）
> 当前实现：V0.1 通信核心 + V0.2 基础 IM 业务纵向切片
> 最后更新：2026-11-14

## 一、总体进度

| 阶段 | 目标 | 状态 |
|---|---|---|
| V0.1 | 通信核心（Netty / Protobuf / 拆包 / 心跳 / 认证 / 连接管理） | 已实现并通过端到端测试 |
| V0.2 | 基础 IM（用户 / 设备 / 单聊 / 会话 / 消息持久化 / ACK / 幂等 / 历史） | 已实现单聊链路与游标同步，群聊待 V0.4 |
| V0.3 | 可靠性（RabbitMQ / Outbox / 重试 / DLQ / 跨节点投递） | 未开始 |
| V0.4 | 分布式集群（多 Gateway / Redis 路由 / 群聊扇出） | 未开始 |
| V0.5 | 性能与商业化（文件、多租户、SDK、开放平台） | 未开始 |

## 二、第一交付清单对照

### 工程基础

- [x] Maven 多模块项目（6 个代码模块 + bootstrap 启动模块 + 聚合父工程，统一 parent + BOM 版本管理）
- [x] 统一依赖版本管理（Spring Boot 3.3.5 BOM + protobuf / MyBatis 固定版本）
- [x] 统一异常体系（`ErrorCode` + Business/System/Protocol/Authentication/Persistence）
- [x] 统一日志体系（`TraceContext` MDC、`logback-spring.xml`、统一字段）
- [x] 统一配置管理（`application.yml` + dev/test/prod profile）
- [x] Docker 开发环境（`deploy/docker`，MySQL 8.0.30 / Redis / RabbitMQ / MinIO / 应用镜像）
- [x] 数据库版本化迁移（Flyway `V1__init.sql`）

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

### 基础业务

- [x] 用户管理（注册 / 登录 / 资料 / 状态）
- [x] 设备管理（设备绑定、最后登录时间、设备列表）
- [x] 单聊（确定性会话 ID，一对用户唯一会话）
- [x] 会话（成员、读游标、同步游标、列表）
- [x] 消息持久化（MySQL，事务内分配序号 + 写入）
- [x] 历史消息（游标分页，无 OFFSET）
- [x] 消息序号（`im_conversation_sequence` 行锁原子分配）
- [x] 发件箱 / 收件箱实时推送（单机，内存连接注册表）
- [ ] 群聊（V0.4，消息表与序号模型已具备扩展基础）

### 可靠性

- [x] Server ACK（持久化后返回）
- [x] Delivery ACK（内存投递跟踪 + 推进同步游标）
- [x] Read ACK（推进读游标，只前进）
- [x] 消息幂等（客户端消息 ID + 唯一索引兜底）
- [ ] Outbox（V0.3）
- [ ] RabbitMQ 异步投递（V0.3）
- [ ] 有限重试 / 死信队列（V0.3）
- [x] 离线同步（客户端游标 + `GET /api/v1/sync/messages`）

### 基础设施

- [x] MySQL 8.0.30（MyBatis-Plus + Flyway DDL、UTC DATETIME(3)）
- [x] Redis（在线状态 Hash，connectionId 校验防旧连接误删；生产 profile 启用）
- [x] Caffeine（用户资料 L1 缓存，`@Cacheable` / `@CacheEvict`）
- [ ] RabbitMQ（部署文件已提供，代码接入在 V0.3）
- [ ] MinIO（部署文件已提供，文件域在 V0.5）
- [x] Prometheus 指标（连接数、连接建立/关闭、心跳超时、消息接收、协议错误）
- [ ] Grafana（按部署环境接入）
- [x] Actuator health / info / metrics

### 测试

- [x] 单元测试：雪花 ID、Envelope 编解码、帧编解码（拆包/粘包/非法帧/超大帧）、JWT、PBKDF2
- [x] HTTP 集成测试：注册→登录→建会话→发消息→幂等→历史→同步→已读→越权/未认证
- [x] TCP 端到端测试：真实 Socket、认证、心跳、发送、对端实时推送、投递回执
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
| 单机实时推送 | V0.3 引入 RabbitMQ 前，发送方在持久化后直接向本节点在线连接推送；离线补偿由游标同步完成 |
| Delivery ACK 内存跟踪 | 文档未定义投递记录表；V0.1 使用内存 `DeliveryTracker`，V0.3 替换为投递记录表 + MQ 消费 |

## 四、已知限制与风险

1. **跨节点投递未实现**：多 Gateway 部署时，接收方若在其他节点将收不到实时推送；需 V0.3 RabbitMQ + Redis 路由。
2. **会话序号死锁重试未实现**：高并发同一会话写入可能触发死锁，当前依赖数据库自动回滚，重试策略在 V0.3 补齐。
3. **Redis 在线状态过期清理**：读空闲断开会主动删除，但节点宕机场景需要周期补偿任务。
4. **无 TLS**：TCP/WebSocket 明文传输，部署层需接入 Nginx / TLS 或 Netty SslHandler。
5. **限流与风控缺失**：登录失败次数、发送速率、连接数均未限流。
6. **群聊与好友关系缺失**：当前仅单聊，成员模型已支持多人扩展。
7. **Docker 未在开发机验证**：开发环境无 Docker，`deploy/docker` 为交付文件，需在具备 Docker 的机器上执行 `docker compose up` 验证。
8. **会话列表存在 N+1 查询**：`list` 对每个会话查询一次最大序号，V0.3 改为批量聚合查询。

## 五、验证记录（2026-11-14）

### 本地构建与测试

- 环境：Windows 11、JDK 21.0.11、Maven 3.9.4。
- `mvn -B -ntp verify`：6 个代码模块 + bootstrap + 父工程全部构建成功。
- 测试共 27 个，全部通过：
  - `veryal-common`：雪花 ID 唯一性/单调性/节点范围（3）。
  - `veryal-protocol`：Envelope 编解码、未知指令、版本校验、帧头格式、半包/粘包、非法魔数、超大帧、指令不一致（12）。
  - `veryal-application`：JWT 签发/过期/篡改/错误密钥、PBKDF2（8）。
  - `veryal-bootstrap`：HTTP 全流程、TCP 端到端、WebSocket 端到端（4）。
- 可执行 jar 使用默认 `dev` profile 启动验证：H2 内存库 + 内存在线状态，`/actuator/health=UP`，注册接口返回成功。

### MySQL 8.0.30 + Redis 实际环境验证

使用 MySQL 8.0.30 与 Redis 7.4 容器（`prod` profile，经 SSH 隧道连接）启动打包产物：

- 数据访问层切换 MyBatis-Plus 后重新回归：注册、登录、创建会话、发送消息、幂等重发、历史查询链路在 MySQL 8.0.30 上全部通过。
- Flyway 自动执行 `V1__init.sql`，`flyway_schema_history` 记录 version=1、success=1。
- `GET /actuator/health` 返回 `UP`（MySQL + Redis）。
- HTTP 流程验证通过：注册 → 登录 → 创建单聊 → 发送消息（seq=1）→ 重复发送命中幂等（`duplicate=true`，同 messageId/seq）→ 历史消息 1 条 → 同步 1 条 → 已读上报。
- 数据库核对：`im_user`、`im_conversation_sequence.last_seq=1`、`im_message` 记录存在。
- 裸 TCP 客户端（Python，独立于服务端代码）完成 LOGIN，收到 `LOGIN_RESPONSE`（command=0x11）。
- 连接在线期间 Redis `veryal:presence:user:{userId}` Hash 存在设备元数据（deviceId/gatewayId/connectionId/status），TTL 约 300s；断开后由服务端清理。

> 验证用容器与数据库不包含仓库代码，仅用于确认 MySQL 方言、Flyway 与 Redis 在线状态在真实环境可用。

## 六、下一步计划（建议顺序）

1. RabbitMQ + Outbox 发布器（`im_outbox` 表、Publisher Confirm、消费幂等）。
2. 投递记录表与跨 Gateway 推送（Redis 路由 + MQ 投递队列）。
3. 群组域与群消息异步扇出、群成员分页。
4. 限流（连接数、登录、发送）、TLS、审计日志。
5. Testcontainers 集成测试矩阵 + 压测脚本（连接数、吞吐、P99）。
