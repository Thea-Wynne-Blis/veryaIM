# VERYAL 开发状态

> 基线：`docs/VERYAL-V1.0-技术架构与开发设计文档.md`（V1.0，开发基线）
> 当前实现：V0.1 通信核心 + V0.2 基础 IM 业务 + V0.3 消息可靠性核心 + V0.4 分布式路由/群管理 + V0.5 文件存储/多消息类型
> 最后更新：2026-11-14

## 一、总体进度

| 阶段 | 目标 | 状态 |
|---|---|---|
| V0.1 | 通信核心（Netty / Protobuf / 拆包 / 心跳 / 认证 / 连接管理） | 已实现并通过端到端测试 |
| V0.2 | 基础 IM（用户 / 设备 / 单聊 / 群聊 / 会话 / 消息持久化 / ACK / 幂等 / 历史） | 单聊、群聊、多消息类型与游标同步已实现 |
| V0.3 | 可靠性（RabbitMQ / Outbox / 重试 / DLQ / 跨节点投递） | Outbox + RabbitMQ 投递/重试/DLQ、Redis 路由 + 单投递队列已实现 |
| V0.4 | 分布式集群（多 Gateway / Redis 路由 / 群聊扇出） | 群聊、群管理、群消息分页扇出、Redis 路由、限流、审计写入/自查、TLS、连接数上限已实现；大群压测待继续 |
| V0.5 | 性能与商业化（文件、多租户、SDK、开放平台） | 文件存储/分片/图片压缩、基础多租户、平台管理员账号、好友/黑名单/通讯录已实现；开放 API/SDK、计费未开始 |

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
- [x] 虚拟线程业务执行器（可选 platform/virtual，信号量限并发；生产默认 virtual，HTTP 开启虚拟线程）
- [x] 限流（策略模式：令牌桶 / 滑动窗口 / 固定窗口；HTTP 登录、HTTP API、TCP 登录、消息发送）
- [x] 审计日志（登录、群创建/改名/成员变更/踢人/退群/转让/解散；独立事务写入）
- [x] 可选 TLS（TCP/WebSocket，TLS 1.2/1.3，PEM 证书；HTTP 通过 Spring Boot server.ssl）
- [x] 连接数上限（单节点总连接数 + 单 IP 连接数，超限关闭并计数）

### 基础业务

- [x] 用户管理（注册 / 登录 / 资料 / 状态）
- [x] 设备管理（设备绑定、最后登录时间、设备列表）
- [x] 单聊（确定性会话 ID，一对用户唯一会话）
- [x] 群聊基础（建群、加成员、群主/管理员权限、群会话与群消息）
- [x] 群管理（管理员设置、改名、踢人、退群、群主转让、解散群）
- [x] 会话（成员、读游标、同步游标、列表、未读数）
- [x] 消息持久化（MySQL，事务内分配序号 + 写入）
- [x] 多消息类型策略工厂（TEXT / EMOJI / IMAGE / AUDIO / VIDEO / FILE / SYSTEM；文件归属校验）
- [x] 消息撤回（仅发送者、2 分钟窗口、历史隐藏原文、审计）
- [x] 文件会话归属（上传绑定 conversationId，会话成员可下载/引用）
- [x] 消息搜索（会话内 TEXT/EMOJI，倒序游标，通配符转义，撤回排除）
- [x] 已读回执（读游标推进写 Outbox，Gateway 广播 READ_NOTIFY，HTTP 查询）
- [x] 贴纸 stickerId、语音时长、视频封面/缩略图字段校验
- [x] 分片上传/断点续传（初始化、分片幂等、进度查询、合并、取消）
- [x] 图片压缩（JPEG/PNG 超过 1920px 等比缩放、JPEG 重编码）
- [x] 历史消息（游标分页，无 OFFSET）
- [x] 消息序号（`im_conversation_sequence` 行锁原子分配）
- [x] 发件箱 / 收件箱实时推送（单机直投或 Outbox + MQ）
- [x] 投递记录查询（按消息查询设备投递状态）
- [x] 群消息分页扇出（事件不携带接收者快照，按 user_id 分页拉取成员）
- [x] 群成员上限提升到 1000（建群/加成员接口支持批量列表）
- [ ] 大群压测（1000 人群扇出）
- [x] 多租户（租户表、租户码注册/登录、启用/停用、用户/群/存储配额）
- [x] 好友（申请/接受/拒绝/取消、备注、删除）
- [x] 好友实时通知（friend.notify Outbox + FRIEND_NOTIFY，MQ/本机直推；含拉黑/解除）
- [x] 平台管理员账号（独立 im_platform_admin、平台登录 scope/role、平台 Token 保护租户接口）
- [x] 平台管理员 CRUD（创建/启停/重置密码/角色调整，PLATFORM_ADMIN/SUPPORT 鉴权）
- [x] 平台操作审计（V12，租户/管理员操作写库并可查询）
- [x] 黑名单（拉黑/解除，私聊拦截，拉黑解除好友与待处理申请）
- [x] 通讯录（好友/群/待处理申请/在线状态聚合）

### 可靠性

- [x] Server ACK（持久化后返回）
- [x] Delivery ACK（设备投递记录持久化 + 推进同步游标）
- [x] Read ACK（推进读游标，只前进）
- [x] 消息幂等（客户端消息 ID + 唯一索引兜底）
- [x] Outbox（消息与事件同事务写入，发布器抢占 + Publisher Confirm）
- [x] RabbitMQ 异步投递（单投递队列 + Redis 路由，手动 ACK + eventId 去重）
- [x] 有限重试 / 死信队列（指数退避 + TTL 重试队列 + 共享 DLQ）
- [x] 离线同步（客户端游标 + `GET /api/v1/sync/messages`）

### 基础设施

- [x] MySQL 8.0.30（MyBatis-Plus + Flyway DDL、UTC DATETIME(3)）
- [x] Redis（在线状态 Hash，connectionId 校验防旧连接误删；生产 profile 启用）
- [x] Caffeine（用户资料 L1 缓存，代码显式配置：10 万条 / 10 分钟 / 统计；`@Cacheable` / `@CacheEvict`）
- [x] RabbitMQ（Outbox 发布、单投递队列 + Redis 路由、重试队列、死信队列）
- [x] 文件存储策略（local / MinIO / 阿里云 OSS；上传、下载、删除、临时 URL、SHA-256、元数据落库）
- [x] Prometheus 指标（连接数、连接建立/关闭、心跳超时、消息接收、协议错误）
- [ ] Grafana（按部署环境接入）
- [x] Actuator health / info / metrics

### 测试

- [x] 单元测试：雪花 ID、Envelope 编解码、帧编解码（拆包/粘包/非法帧/超大帧）、JWT、PBKDF2、Outbox 事件 JSON、限流算法（令牌桶/滑动窗口/固定窗口/注册表）
- [x] 限流集成测试：消息发送超过令牌桶容量返回 429
- [x] 审计集成测试：登录、建群、踢人写入审计表
- [x] TLS 配置测试：关闭时不加 SslHandler、开启但证书缺失时快速失败
- [x] TLS 手工验证：自签证书下 TCP TLS 1.3 登录成功、WebSocket `wss://` 登录成功
- [x] 连接数限制测试：单 IP 上限下第二个连接被拒绝，释放后可重新接入
- [x] 缓存配置测试：用户缓存容量 10 万、过期时间 10 分钟
- [x] 文件测试：本地存储读写/删除/路径穿越、文件 API 上传下载权限删除、策略选择与 OSS 预签名
- [x] 多消息类型测试：文本/表情/图片/语音/视频发送，图片缺 fileId 返回 400，引用他人文件返回 403
- [x] 消息撤回测试：非发送者 403、发送者撤回后历史 content 为空、重复撤回幂等
- [x] 文件会话成员访问测试：绑定会话后成员可下载、非成员 403
- [x] 消息搜索测试：文本命中、通配符转义、非成员 403、撤回排除
- [x] 已读回执测试：读游标推进、回执查询、Outbox 事件唯一
- [x] 贴纸/语音时长/视频封面字段校验测试
- [x] 分片上传测试：小分片初始化、续传、缺片 409、合并下载
- [x] 图片压缩测试：3000×1000 JPEG 缩放到 ≤1920 且体积下降
- [x] 虚拟线程执行器测试：并发上限拒绝、释放后可继续提交、配置选择 virtual
- [x] 多租户测试：平台开通、同名用户跨租户、跨租户私聊拒绝、配额 403、停用租户登录 403
- [x] 好友/黑名单测试：申请-接受-备注-通讯录；拉黑后好友移除、私聊 403、解除拉黑
- [x] 云服务器 MySQL 实测：V9/V10 迁移、租户开通、租户码登录、好友接受、拉黑后私聊 403
- [x] 好友实时通知测试：双方 TCP 在线，申请方/接受方收到 FRIEND_NOTIFY
- [x] 平台管理员测试：平台登录返回 scope=PLATFORM/role，平台 Token 调用租户接口；租户登录 scope=TENANT
- [x] 平台管理员生命周期测试：SUPPORT 只读鉴权、创建/重置密码/停用/自我停用拒绝
- [x] 平台审计测试：ADMIN_CREATE / ADMIN_PASSWORD_RESET / ADMIN_STATUS 可查询
- [x] 拉黑/解除实时通知测试：BLOCKED=5、UNBLOCKED=6
- [x] HTTP 集成测试：注册→登录→建会话→发消息→幂等→历史→同步→已读→越权/未认证，并校验 Outbox 事件写入
- [x] 群聊集成测试：建群、成员权限、群消息、群事件类型、非成员越权
- [x] 群管理集成测试：管理员设置、改名、踢人、退群、群主/管理员权限边界
- [x] 群生命周期集成测试：群主转让、解散群、解散后会话不可用
- [x] TCP 端到端测试：真实 Socket、认证、心跳、发送、私聊/群聊实时推送、投递记录与查询
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
| 群成员上限 1000 | 群消息分页拉取成员；建群/加成员接口接收列表，大群压测待执行 |
| 投递记录表 | 文档未定义投递表，新增 `im_message_delivery`（message_id + device_id 唯一），投递序号只前进 |
| 单机实时推送 | 未开启 MQ 时，发送方在持久化后直接向本节点在线连接推送；开启 MQ 后统一由 Outbox 消费投递 |
| 每实例独立投递队列 | 已替换为“单投递队列 + Redis 路由 + 网关推送队列”：投递消费者按 gatewayId 定向推送，避免每实例重复消费 |
| Delivery ACK 内存跟踪 | 文档未定义投递记录表；当前使用内存 `DeliveryTracker`，后续替换为投递记录表 |

## 四、已知限制与风险

1. **路由依赖 Redis presence**：网关路由取自 presence 租约，节点宕机后需要主动清理/补偿；推送失败会进入死信队列，客户端仍以同步游标补偿。
2. **会话序号死锁重试未实现**：高并发同一会话写入可能触发死锁，当前依赖数据库自动回滚，重试策略待补齐。
3. **Redis 在线状态过期清理**：读空闲断开会主动删除，但节点宕机场景需要周期补偿任务。
4. **无 TLS**：TCP/WebSocket 明文传输，部署层需接入 Nginx / TLS 或 Netty SslHandler。
5. **风控仅基础限流**：已有限流与连接数上限，但异常登录/设备风控、内容安全与敏感词检测未实现。
6. **群规模上限 1000，未做压测**：群消息已分页拉取成员，但 1000 人群的扇出与建群/批量加成员性能尚未验证。
7. **Docker 未在开发机验证**：开发环境无 Docker，`deploy/docker` 为交付文件，需在具备 Docker 的机器上执行 `docker compose up` 验证。
8. **会话列表存在 N+1 查询**：`list` 对每个会话查询一次最大序号，后续改为批量聚合查询。
9. **投递查询已提供**：可按消息查询设备投递记录；跨会话的批量未读聚合仍只在会话列表维度。
10. **限流为单机内存实现**：多实例部署时每个节点独立计数，集群级限流需切换 Redis 计数/滑动窗口；连接数上限同样按单节点统计。
11. **dev/test 会累积 Outbox**：MQ 关闭时 Outbox 只写不发，便于本地排查；生产环境正常发布。
12. **审计查询仅限本人**：已提供 `/users/me/audit-logs`；管理后台级查询、保留策略与归档任务尚未实现。
13. **TLS 暂不支持热加载与双向认证**：证书在首次连接时构建，更新证书需重启；暂未实现 mTLS。
14. **文件能力仍是基础版**：绑定会话的文件对成员开放，未绑定会话仅上传者；未实现分片上传、断点续传、病毒扫描与 CDN；阿里云 OSS 仅完成预签名单测，未做真实凭证联调。
15. **消息类型校验较严格**：图片/语音/视频/文件必须发送 JSON 元数据并引用可见文件；旧客户端直接发送二进制内容会被拒绝，需要按新协议升级。
16. **撤回通知已实时化**：撤回写 `message.recalled` Outbox；MQ 开启时广播 `MESSAGE_RECALL`，MQ 关闭时本机直推。
17. **已读回执已支持单机直推**：MQ 开启时广播 `READ_NOTIFY`，MQ 关闭时由本机 Notifier 直接推送；回执查询始终可用。
18. **搜索范围有限**：仅支持会话内 TEXT/EMOJI 内容，不含文件名、图片 OCR 与跨会话搜索。
19. **分片暂存需共享卷才能跨节点续传**：分片写在 `veryal.upload.staging-path`，指向 NFS/CephFS 等共享卷即可多节点续传；完成时会把全部分片读入内存（默认上限 64MB）。
20. **图片压缩仅覆盖 JPEG/PNG**：其他格式原样存储；不做视频转码、缩略图生成或时长探测。
21. **虚拟线程收益未压测**：TCP 虚拟执行器/HTTP 虚拟线程已提供，但未做平台线程 vs 虚拟线程的吞吐/P99 对比；Hikari、Redis、RabbitMQ 仍是主要瓶颈。
22. **多租户仍是基础版**：有租户表、租户码、启停与用户/群/存储配额；缺少租户管理员、账单、数据导出/删除与租户级配置。
23. **好友/黑名单能力边界**：好友事件已实时推送；黑名单只过滤私聊，不过滤群消息；拉黑不删除历史会话。
24. **平台管理员仍是基础版**：支持 CRUD/角色/审计，但只有启动引导创建首个管理员；无多因素认证、无刷新 Token、无平台侧租户用量报表。

## 五、验证记录（2026-11-14）

### 本地构建与测试

- 环境：Windows 11、JDK 21.0.11、Maven 3.9.4。
- `mvn -B -ntp verify`：6 个代码模块 + bootstrap + 父工程全部构建成功。
- 测试共 68 个，全部通过：
  - `veryal-common`：雪花 ID 唯一性/单调性/节点范围、限流算法（令牌桶/滑动窗口/固定窗口/注册表）（12）。
  - `veryal-protocol`：Envelope 编解码、未知指令、版本校验、帧头格式、半包/粘包、非法魔数、超大帧、指令不一致（12）。
  - `veryal-application`：JWT 签发/过期/篡改/错误密钥、PBKDF2、Outbox 事件 JSON（9）。
  - `veryal-bootstrap`：HTTP 全流程（含 Outbox）、多租户/平台管理员 CRUD/审计、好友/黑名单/通讯录/好友实时通知、群聊、群管理、群生命周期、限流、审计、TLS 配置、连接限制、缓存配置、文件 API/会话权限、存储策略、分片上传、图片压缩、多消息类型、消息撤回、消息搜索、已读回执、虚拟线程执行器、TCP 私聊/群聊端到端、WebSocket 端到端（35）。
- 可执行 jar 使用默认 `dev` profile 启动验证：H2 内存库 + 内存在线状态，`/actuator/health=UP`，注册接口返回成功。

### MySQL 8.0.30 + Redis 实际环境验证

使用 MySQL 8.0.30 与 Redis 7.4 容器（`prod` profile，经 SSH 隧道连接）启动打包产物：

- 数据访问层切换 MyBatis-Plus 后重新回归：注册、登录、创建会话、发送消息、幂等重发、历史查询链路在 MySQL 8.0.30 上全部通过。
- Flyway 自动执行 `V1__init.sql`、`V2__outbox.sql`、`V3__group.sql`、`V4__message_delivery.sql`、`V5__audit_log.sql`、`V6__file.sql`、`V7__message_recall_file_scope.sql`、`V8__upload_session.sql`、`V9__tenant.sql`、`V10__friend.sql`、`V11__platform_admin.sql`、`V12__platform_audit.sql`。
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

### 群消息分页扇出验证

- 群消息 Outbox 事件 `recipientUserIds` 为空，不再随成员数增长。
- 单机直投与 MQ 消费者均按 `user_id` 分页拉取会话成员后再路由。
- 真实 RabbitMQ 环境验证：群主通过 HTTP 发送群消息，群成员 TCP 客户端成功收到 `MESSAGE_NOTIFY`；队列无积压。

### TLS 验证

- 使用自签证书（PEM）启动 gateway：`VERYAL_TLS_ENABLED=true`，TCP 9443、WebSocket 9444。
- TCP 客户端完成 TLS 1.3 握手并登录成功（LOGIN_RESPONSE）。
- WebSocket 客户端通过 `wss://127.0.0.1:9444/ws` 完成 TLS 握手并登录成功。
- 证书缺失时应用快速失败；未开启 TLS 时原有明文测试全部通过。

### MinIO 文件存储验证

- 服务器启动 MinIO 容器，应用配置 `VERYAL_STORAGE_TYPE=minio` 指向该实例。
- HTTP 上传 `minio.txt` → 元数据写入 `im_file` → 应用下载内容一致 → 预签名 URL 直接 GET 成功 → 删除后下载返回 404。
- 本地存储策略在集成测试中覆盖：上传、下载、403 越权、临时 URL 回退、删除后 404、路径穿越拒绝。
- 阿里云 OSS：使用测试凭证生成本地预签名 URL（不访问外网）；真实 OSS 联调待提供凭证。
- 多消息类型：HTTP 发送图片/表情/语音/视频通过；图片缺少 `fileId` 返回 400；引用他人文件返回 403。
- 文件会话权限：绑定会话的文件成员可下载，非成员 403；消息可引用会话内其他成员上传的文件。
- 消息撤回：非发送者 403；发送者撤回后历史 `content` 为空、`recalled=true`；重复撤回幂等；`message.recalled` Outbox 事件唯一。
- 消息撤回/已读回执实时通知：MQ 关闭时本机直推；MQ 开启时由广播队列消费。
- 消息搜索：`hello` 命中两条、撤回后只剩一条；`%` 通配符被转义；非成员 403。
- 已读回执：阅读第二/第一条消息后，回执只记录最大 seq 且 Outbox 仅一条 `message.read`。
- 贴纸/语音/视频：合法 `stickerId`、`durationSeconds`、`coverFileId` 通过；非法 stickerId 返回 400。
- 分片上传：10 字节按 4 字节分片，缺片返回 409；补齐后合并下载内容一致；重复分片幂等。
- 多租户：平台开通租户、租户码注册登录、同名用户隔离；跨租户私聊返回 404；配额/停用返回 403。
- 好友：申请→接受→备注→通讯录；拉黑后好友关系移除，私聊发送返回 403（USER_BLOCKED）；解除后黑名单为空。
- 好友实时通知：双方 TCP 在线，申请方收到 REQUEST、接受方收到 ACCEPTED（FRIEND_NOTIFY）。
- 平台管理员：独立登录返回 `scope=PLATFORM`、`role=PLATFORM_ADMIN`；租户登录返回 `scope=TENANT`；平台 Token 可调用租户管理接口。
- 平台管理员 CRUD：创建 SUPPORT、SUPPORT 创建租户 403、重置密码旧密码失效、停用后登录 403、自我停用 400；审计可查询。
- 好友实时通知：申请/接受/拉黑/解除分别收到 FRIEND_NOTIFY，事件类型 1/2/5/6。
- 云服务器 MySQL 实测：V9/V10 迁移成功，租户/好友/黑名单链路通过。
- 图片压缩：3000×1000 JPEG 缩放后最长边 ≤1920，体积小于原图。

> 验证用容器与数据库不包含仓库代码，仅用于确认 MySQL 方言、Flyway、Redis 在线状态与 RabbitMQ 投递链路在真实环境可用。

## 六、下一步计划（建议顺序）

1. 平台管理员多因素认证、刷新 Token、租户用量报表。
2. 好友离线通知/推送、群消息 @ 与禁言。
3. 租户管理员、账单/用量统计、数据导出与删除。
4. 1000 人群压测、分片共享卷双节点验证。
5. 开放 API / SDK、管理后台；Testcontainers 与压测矩阵。
