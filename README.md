# VERYAL Backend

VERYAL 是面向个人用户的即时通讯产品，这个仓库是它的服务端：账号、好友、单聊、群聊、富媒体、多端同步、离线推送和运营后台都在这里。

**Java 21 · Spring Boot · Netty · Protobuf · MyBatis-Plus · MySQL 8 · Redis · RabbitMQ · Caffeine · Flyway**

目标不是演示通信流程，而是把 IM 里最容易出问题的部分——消息不丢、重复、乱序、多端同步、大群扇出、文件权限——按可上线的标准做完整。默认 `dev` profile 用 H2 + 内存实现，克隆下来就能启动。

## 能力

**消息**
- 文本、表情、图片、语音、视频、文件、系统消息，每种类型有独立的内容校验策略
- `clientMessageId` 幂等发送、会话内序号、Server ACK / Delivery ACK / Read ACK、离线游标同步
- 2 分钟内撤回、引用回复、翻译、会话内搜索、会话文件列表
- 会话级置顶、免打扰、不显示、手动未读；删除只清空本人一侧
- 消息收藏，收藏列表跨会话聚合

**群聊**
- 建群、加成员、群主/管理员、转让、解散、退群；成员上限 1000
- 群公告、全员禁言、单人禁言、@成员；禁言状态变化会留下系统消息
- 群消息按成员分页扇出，事件体不随群规模增长

**好友与社交**
- 好友申请、接受、拒绝、取消，好友备注与删除
- 黑名单拦截私聊；通讯录聚合好友、群、待处理申请
- 用户搜索、资料主页、隐私设置、举报；好友事件实时通知

**文件与媒体**
- `local` / `minio` / `oss` 三种存储策略，运行时切换
- 普通上传 32MB，分片上传 256MB 并支持断点续传；全程流式写入，不把整份文件读进堆
- 图片按最长边 1920px 压缩，缩略图落盘缓存；GIF / SVG / WebP 不重编码
- Range 下载、ETag、`304`；SVG 一律按附件下载，避免同源打开拿到 token
- 文件绑定会话后会话成员可访问，否则仅上传者可见

**实时通道**
- Netty WebSocket 网关（9098）：浏览器走 JSON 文本帧，原生客户端走二进制 Protobuf 帧，同端口并存
- 一次性 `ws-ticket` 建连、应用层心跳、读空闲断开、连接数上限、可选 TLS
- Redis 在线路由 + RabbitMQ fanout 支持多实例推送；大群推送退化为 `seq` 游标
- Netty EventLoop 不做阻塞调用；生产 profile 用虚拟线程承接 HTTP 与业务 IO

**运营与安全**
- 运营管理员与普通用户 Scope 完全分离；TOTP MFA、Refresh Token 轮换、登录失败锁定
- 用户 / 群 / 举报管理、敏感词 DFA、操作审计
- 策略化限流（令牌桶 / 滑动窗口 / 固定窗口）：登录按 IP、API 按用户、发消息按用户
- 离线推送支持 none / webhook / FCM / APNs，以及浏览器 Web Push（VAPID 自签密钥即可）

## 架构

按「6 个代码模块 + 1 个启动模块」组织，模块内再按业务能力分包：

```text
veryal-backend/
├── veryal-common/          基础能力：异常、ID、JSON、日志上下文（无 Spring 依赖）
├── veryal-protocol/        协议：Command、Protobuf 定义、16 字节帧编解码
├── veryal-domain/          领域层：identity / device / conversation / message / presence（纯 Java）
├── veryal-application/     应用层：身份 / 会话 / 群 / 消息 / 文件 / 同步（用例编排与事务）
├── veryal-infrastructure/  基础设施：mysql / redis / cache / storage / mq
├── veryal-access/          接入层：core / websocket / http（连接、认证、分发、API）
├── veryal-bootstrap/       启动装配、配置、Flyway 迁移、集成测试
└── deploy/docker/          Dockerfile、docker-compose、.env 示例
```

依赖只允许向下：

```text
bootstrap ──> access ──> application ──> domain ──> common
     └──────> infrastructure ──┘
access ──> protocol ──> common
```

边界约束：

- `domain` 不依赖 Spring / Netty / MyBatis，只放聚合、值对象、领域规则和仓储端口
- `application` 只编排用例与事务，不出现 SQL、HTTP、Netty 类型
- `infrastructure` 实现 `domain` 的端口，不反向依赖 application
- `access` 负责协议解析、认证、连接与参数校验，不直接碰 Mapper
- `bootstrap` 只做装配与配置

> 设计文档里的 `protocol-*` / `access-*` 子模块已收敛为 `veryal-protocol`、`veryal-access` 内部的包边界，分层约束不变。

## 快速开始

### 本地零依赖

```bash
# JDK 21 + Maven 3.9+
mvn -B -DskipTests package
java -jar veryal-bootstrap/target/veryal-bootstrap-1.0.0-SNAPSHOT.jar
```

默认 `dev` profile：H2 内存库 + 内存在线状态 + MQ 关闭 + Flyway 关闭。启动后 `GET /actuator/health` 返回 `UP` 即可。

### Docker Compose

```bash
cd deploy/docker
cp .env.example .env     # 改密码与 VERYAL_TOKEN_SECRET
docker compose up -d --build
```

带 MySQL / Redis / RabbitMQ / MinIO，Flyway 自动建表。生产 profile 默认开启 MQ 投递，可用 `VERYAL_MQ_ENABLED=false` 回退到单机直推。

### 裸机 systemd

运行时配置收在 `config/.env`，改配置不用重新打包：

```bash
cp config/.env.example config/.env
chmod 600 config/.env
vi config/.env          # 数据库、Redis、令牌密钥等

bash config/start.sh    # 渲染 application-local.yaml 并启动
```

`start.sh` 按模板里的 `${KEY:default}` 占位生成 `config/application-local.yaml`（权限 600、不提交），再以 `prod,local` 两个 profile 启动。systemd 只需把 `ExecStart` 指到脚本，细节见 [`config/README.md`](config/README.md)。

## 关键设计

### 一条消息的生命周期

发送不是「插入一行」：校验成员与禁言 → 幂等检查 → 分配会话序号 → 插消息 → 写 Outbox → 推进自己的读游标，全部在同一个数据库事务里完成。事务提交后才由 Outbox Publisher 发事件，投递失败不影响消息可靠性，客户端靠同步游标补齐。

- 会话序号按会话独立自增，排序只信服务端序号，客户端不做时间排序
- `(sender_id, client_message_id)` 唯一：重复提交返回同一条消息与相同序号，`duplicate=true`
- 单聊会话 ID 由两个用户确定性生成，保证一对用户只有一个会话
- 撤回只允许发送者本人、2 分钟内；撤回后保留下标并广播 `MESSAGE_RECALL`
- Outbox 发布使用 Publisher Confirm + 指数退避 + TTL 重试队列 + 死信队列，节点宕机遗留的 `PUBLISHING` 记录会被回收

### 多实例实时投递

```text
实例A ──本地直推──▶ 本机连接
      └──publish──▶ veryal.push.exchange
                        ├── veryal.push.<实例A>（独占、自动删除）
                        └── veryal.push.<实例B>
```

每个实例只推自己持有的连接；本机直推与 MQ 出箱共用 `eventId + connectionId` 去重。广播失败只记日志，不阻塞发送，跨节点漏推由客户端增量同步兜底。单机部署关掉 MQ 就只剩本机直推。

浏览器建连分两步：先换一张 60 秒有效、只能使用一次的 ticket，再连 `/ws?ticket=...&format=json`，token 不出现在 URL 里。群成员超过 200 人时推送退化为 `{kind:"cursor", seq}`，客户端按序号补拉，推送体积与群人数无关。

### 文件存储

`FileStorage` 是策略接口，`FileStorageProvider` 按配置选实现。上传先写对象存储再写 `im_file`，元数据失败会补偿删除对象；下载按大小分流读取，本地走 `FileChannel.position`，MinIO / OSS 走原生 Range。

分片上传的暂存目录默认在本机，把它指到 NFS / CephFS 就能多节点续传。合并时逐片追加再流式计算 SHA-256、写入当前存储策略，最后落元数据。12MB 以上的图片不做内存压缩，直接流式落盘。

### 性能

瓶颈定位靠 JFR：发送路径 50% 的样本阻塞在等 MySQL 返回，GC 只触发一次，所以优化集中在减少数据库往返，而不是换 JVM 参数。

| 场景 | 优化前 | 优化后 |
|---|---|---|
| 单条消息数据库往返 | ~12 次 | ~6–7 次 |
| 发消息（16 并发） | 115 QPS / 131ms | 184 QPS / 80ms |
| 会话卡片（30 个会话） | 168ms | 19ms |

做法：把发送前的 5 条校验 SQL 合并成一次联表查询；私聊接收者复用上下文；Token 校验的用户状态加 5 秒缓存（封禁最长延迟 5 秒生效）；序号分配改成单条 upsert；会话列表用一条联表查询替代 O(会话数) 的 N+1，且没有引入缓存。

MySQL 线上保持 `innodb_flush_log_at_trx_commit=1, sync_binlog=1`。压测显示 16 并发以上放宽持久化几乎没差别（binlog group commit 摊薄了 fsync），但低并发能快一倍——本项目的底线是消息不丢，所以不换。

### 运营与安全

运营管理员使用独立表与独立 Token，`PLATFORM_ADMIN` 与 `SUPPORT` 权限分离，跨 scope 不能互用。敏感词用 DFA Trie 扫描，词库变更实时重建。登录、建群、成员变更、踢人、转让、解散、撤回等写 `im_audit_log`，独立事务，失败不影响主业务。

限流策略集中在配置里，业务方法用 `@RateLimit` 声明维度与 key；超限统一返回 429 与 `Retry-After`。做发送压测前记得关掉发消息限流，否则 429 会污染吞吐数据。

## 工程约定

- `domain` 不依赖 Spring / Netty / MyBatis / Redis；禁止在 EventLoop 上执行阻塞操作
- 禁止无界队列与无界线程池；Redis Key 统一由 `RedisKeys` 生成
- 数据库时间统一 UTC，客户端自行转换时区
- 多表 JOIN、子查询、聚合写在 `resources/mapper/*.xml`；单表点查写在 Mapper 注解；写操作一律用注解，方便一眼看清事务与幂等
- 标量子查询依赖唯一约束（成员表、序号表、禁言表等），改表结构时必须一起确认，宁可写入时报 1062，也不要查询期才炸 1242
- POJO 与配置类用 Lombok，通用工具优先 Hutool

## 测试

```bash
mvn test
```

- 单元测试：雪花 ID、Envelope 编解码、Netty 拆包/粘包/非法帧、JWT、PBKDF2、限流算法、TLS 开关、连接数限制、缓存配置、存储策略
- 集成测试（H2 + 随机端口）：注册登录、好友与黑名单、群聊与群管理、消息类型与幂等、撤回/引用/翻译/搜索、文件与分片、Web Push、运营后台、限流、审计、WebSocket、Mapper XML 冒烟
- `TestcontainersMatrixIntegrationTest` 用真实 MySQL / Redis / RabbitMQ 跑完整 Flyway 迁移与 Outbox 链路，没有 Docker 时自动跳过

## 路线

V0.1–V0.5 的纵向切片已完成，Web 客户端与运营后台也已接上；接下来重点在移动端、真实 APNs/FCM 投递、运营后台打磨和长稳压测。

V1.1 明确不做：多租户、企业组织、飞书式协作、开放平台/SDK、私有化部署控制台。

## 文档

- [`docs/VERYAL-V1.0-技术架构与开发设计文档.md`](docs/VERYAL-V1.0-技术架构与开发设计文档.md)：模块划分、协议与数据模型
- [`docs/FRONTEND-ARCHITECTURE.md`](docs/FRONTEND-ARCHITECTURE.md)：客户端与运营后台的前端设计
- [`docs/DEVELOPMENT-STATUS.md`](docs/DEVELOPMENT-STATUS.md)：进度与差距
- [`config/README.md`](config/README.md)：`config/.env` 与 systemd 部署
