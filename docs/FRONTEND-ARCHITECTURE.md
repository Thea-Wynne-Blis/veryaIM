# VERYAL 前端架构设计

> 版本：V1.1  
> 产品方向：微信 / QQ 类消费级社交 IM  
> 不在范围：多租户、企业组织、OA、开放平台、支付/发票  
> 对应总开发文档：[VERYAL-V1.0-技术架构与开发设计文档.md](VERYAL-V1.0-技术架构与开发设计文档.md)

---

## 1. 设计结论

前端不再拆成“平台端 + 租户端 + IM 端”三套，而是拆成：

```text
1. IM 客户端       面向普通用户
2. 运营后台         面向平台运营 / 审核 / 管理员
3. 共享基础包       两端复用
```

```text
frontend/
├─ apps/
│  ├─ im-client/           IM 客户端（Web / H5）
│  └─ operations-console/  运营后台
└─ packages/
   ├─ api-client/
   ├─ realtime/
   ├─ auth/
   ├─ chat-ui/
   ├─ ui/
   ├─ i18n/
   └─ shared/
```

没有：

- 租户管理端；
- 平台租户开通/停用/配额/导出/删除；
- 多租户切换；
- 租户管理员；
- 计费/套餐/发票页面。

> 后端已移除 `tenantCode`、`tenant_id` 与全部租户接口；单平台单空间。
> 前端一期不展示租户概念，注册/登录不要求填写租户码。

---

## 2. 技术选型

| 领域 | 推荐 |
|---|---|
| 框架 | Vue 3 + TypeScript |
| 构建 | Vite |
| 路由 | Vue Router |
| 状态 | Pinia |
| 服务端状态 | Vue Query / TanStack Query |
| UI | Element Plus（运营后台） |
| 聊天 UI | 自研 |
| 本地缓存 | IndexedDB + Dexie |
| 实时 | 原生 WebSocket |
| 测试 | Vitest + Playwright |
| 仓库 | pnpm workspace + Turborepo |

如果团队使用 React，可替换为 React + Vite + React Router + Zustand + TanStack Query + Ant Design，分层不变。

---

## 3. IM 客户端

### 3.1 页面结构

```text
/login
/register
/app
  /app/chat
  /app/chat/c/:conversationId
  /app/contacts
  /app/contacts/friends
  /app/contacts/requests
  /app/contacts/groups
  /app/contacts/blocked
  /app/groups/:groupId
  /app/groups/:groupId/settings
  /app/files
  /app/search
  /app/me
  /app/me/devices
  /app/me/privacy
  /app/me/security
  /app/me/reports
```

### 3.2 主界面

```text
┌──────┬───────────────┬──────────────────────────┬─────────────┐
│ 导航 │ 会话列表       │ 聊天主窗口                │ 右侧信息栏   │
│      │               │                          │             │
│ 消息 │ 私聊/群聊      │ 消息流 + 输入框           │ 群成员/文件  │
│ 通讯 │ 未读/最后消息  │ 文本/图片/语音/视频/文件  │ 公告/设置    │
│ 文件 │               │ @/撤回/已读/搜索          │             │
│ 我的 │               │                          │             │
└──────┴───────────────┴──────────────────────────┴─────────────┘
```

移动端：

- 单栏切换；
- 会话列表 → 聊天页；
- 右侧信息栏改为抽屉；
- 输入框固定底部；
- 支持安全区与手势返回。

### 3.3 功能模块

#### 认证

- 注册：用户名 / 手机号 + 密码 + 昵称；
- 登录：用户名 / 手机号 + 密码；
- 不显示租户码；
- 设备 ID 生成并持久化；
- Token 过期重新登录；
- 多标签页登录态同步；
- 退出清理本地缓存。

#### 会话

- 私聊 / 群聊；
- 最后一条消息；
- 未读数；
- 最后活跃时间；
- 置顶、免打扰（后端接口待补）；
- 会话内搜索；
- 左滑删除 / 置顶（移动端）。

#### 消息

| 类型 | 渲染 |
|---|---|
| `TEXT` | 文本、链接、@ 高亮 |
| `EMOJI` | 表情 / 贴纸 |
| `IMAGE` | 预览、缩放、保存 |
| `AUDIO` | 播放、时长、进度 |
| `VIDEO` | 播放、封面 |
| `FILE` | 文件名、大小、下载 |
| `SYSTEM` | 系统提示 |

操作：

- 发送、重试；
- 撤回（2 分钟内）；
- 复制；
- 删除本地；
- 多选（后续）；
- 转发 / 引用（后端待补）。

#### 输入框

- 文本；
- 表情；
- @ 群成员；
- 图片；
- 文件；
- 语音；
- 视频；
- 粘贴图片；
- 拖拽上传；
- 发送快捷键。

#### 好友

- 搜索用户；
- 发送好友申请；
- 接受 / 拒绝 / 取消；
- 好友列表；
- 备注；
- 删除好友；
- 拉黑 / 解除；
- 黑名单。

#### 通讯录

- 好友；
- 群；
- 待处理申请；
- 在线状态（若用户允许展示）。

#### 群

- 建群；
- 邀请成员；
- 群详情；
- 成员列表；
- 管理员设置；
- 改名；
- 踢人；
- 退群；
- 转让群主；
- 解散群；
- 群公告；
- 全员禁言；
- 成员禁言；
- @ 成员；
- 群文件。

#### 文件

- 普通上传；
- 分片上传；
- 断点续传；
- 进度、取消；
- 预览、下载；
- 会话文件列表。

#### 隐私与安全

- 手机号搜索开关；
- 用户名搜索开关；
- 陌生人加好友策略；
- 在线状态展示；
- 已读回执开关；
- 是否允许被邀请进群；
- 设备列表 / 撤销设备；
- 举报记录；
- 账号注销（后续）。

#### 举报

- 举报用户；
- 举报群；
- 举报消息；
- 选择原因；
- 补充说明；
- 提交后进入运营审核。

---

## 4. 运营后台

运营后台不是“多租户平台管理”，而是服务整个产品的运营系统。

### 4.1 页面结构

```text
/login
/dashboard
/users
/users/:id
/groups
/groups/:id
/reports
/moderation
/content
/admins
/audit
/security
```

### 4.2 页面职责

| 页面 | 功能 |
|---|---|
| 登录 | 运营账号 + 密码 + MFA |
| 概览 | 用户数、群数、消息量、举报量、在线数 |
| 用户 | 搜索、详情、封禁/解封、禁言、重置资料 |
| 群 | 搜索、详情、解散、禁言、查看成员 |
| 举报 | 待处理、已处理、详情、处罚 |
| 内容审核 | 敏感内容、图片审核（后续） |
| 运营账号 | 创建、启停、角色、重置密码 |
| 审计 | 运营操作日志 |
| 安全 | MFA、密码、登录记录 |

### 4.3 角色

| 角色 | 权限 |
|---|---|
| `OPS_ADMIN` | 全部运营权限 |
| `MODERATOR` | 举报、内容、用户/群只读或部分处罚 |
| `SUPPORT` | 只读查询 |

前端需要：

- 路由守卫；
- 按钮级权限；
- 危险操作二次确认（封号、解散群）；
- 操作原因必填；
- 所有操作可审计。

### 4.4 运营接口（已实现）

现有后端已提供：

| 接口 | 用途 |
|---|---|
| `GET /api/v1/platform/ops/users?keyword=` | 用户搜索 |
| `GET /api/v1/platform/ops/users/{id}` | 用户详情 |
| `POST /api/v1/platform/ops/users/{id}/ban` | 封号 |
| `POST /api/v1/platform/ops/users/{id}/unban` | 解封 |
| `POST /api/v1/platform/ops/users/{id}/mute` | 禁言 |
| `POST /api/v1/platform/ops/users/{id}/unmute` | 解除禁言 |
| `GET /api/v1/platform/ops/groups?keyword=` | 群搜索 |
| `GET /api/v1/platform/ops/groups/{id}` | 群详情/成员 |
| `POST /api/v1/platform/ops/groups/{id}/disband` | 解散群 |
| `GET /api/v1/platform/ops/reports?status=` | 举报列表 |
| `GET /api/v1/platform/ops/reports/{id}` | 举报详情 |
| `POST /api/v1/platform/ops/reports/{id}/handle` | 处理举报 |
| `GET /api/v1/platform/ops/dashboard` | 运营概览 |
| `GET /api/v1/platform/audit-logs` | 运营审计 |

`SUPPORT` 只读，`PLATFORM_ADMIN` 可执行封禁、解散群、处理举报等写操作。

后续可补：用户禁言、内容审核、举报原因统计、审计筛选/分页。

---

## 5. 共享基础包

### 5.1 `api-client`

```text
packages/api-client/src/
├─ http.ts
├─ auth.ts
├─ users.ts
├─ friends.ts
├─ conversations.ts
├─ messages.ts
├─ groups.ts
├─ files.ts
├─ reports.ts
├─ ops/
│  ├─ users.ts
│  ├─ groups.ts
│  ├─ reports.ts
│  ├─ dashboard.ts
│  └─ audit.ts
├─ types/
└─ errors.ts
```

职责：

- Base URL；
- Token 注入；
- 401 / 403 / 409 / 423 / 429 处理；
- 类型定义；
- 上传 / 分片；
- 请求取消；
- 重试。

### 5.2 `realtime`

- WebSocket 单例；
- 登录；
- 心跳；
- 重连；
- 事件总线；
- 消息去重；
- 断线同步；
- 待发队列。

### 5.3 `chat-ui`

- ConversationList；
- ChatHeader；
- MessageList；
- MessageBubble；
- MessageRenderer；
- MessageComposer；
- MentionPicker；
- EmojiPicker；
- FileUploader；
- ImagePreview；
- VideoPlayer；
- VoicePlayer；
- ReadReceipt；
- GroupMemberPanel；
- GroupAnnouncement；
- MutePanel。

### 5.4 `auth`

- 登录态；
- Token；
- 设备 ID；
- 路由守卫；
- 退出；
- 多标签页同步。

### 5.5 `ui` / `i18n` / `shared`

- 通用组件；
- 错误码文案；
- 时间、文件大小、头像；
- 权限模型；
- 常量。

---

## 6. 路由与权限

### 6.1 IM 端

```ts
meta: { requiresAuth: true }
```

`/app/*` 需要登录。

普通用户与群主/管理员的能力差异在群设置页做按钮级控制。

### 6.2 运营后台

```ts
meta: { requiresOps: true, roles: ['OPS_ADMIN'] }
```

`MODERATOR` / `SUPPORT` 按权限矩阵控制。

### 6.3 登录后跳转

```ts
if (isOperationsAccount) {
  router.replace('/dashboard')
} else {
  router.replace('/app/chat')
}
```

运营后台与 IM 客户端建议使用不同域名 / 不同应用，避免 Token 混用。

---

## 7. 状态管理

| 状态 | 方案 |
|---|---|
| 登录用户、Token | Pinia `authStore` |
| 会话、消息 | Pinia `chatStore` + IndexedDB |
| 好友、群、申请 | Vue Query |
| 运营用户/群/举报 | Vue Query |
| WebSocket 状态 | Pinia `connectionStore` |
| UI 弹窗/侧栏 | Pinia `uiStore` |
| 上传任务 | `uploadStore` |

本地消息表：

```text
conversations
messages:{conversationId}
pendingMessages
syncCursor
```

刷新流程：

1. 读本地消息；
2. 先渲染；
3. 拉服务端增量；
4. 按 `seq` 合并；
5. 清理已确认待发消息。

---

## 8. 实时通信

```text
登录成功
  → 建立 WebSocket
  → LOGIN(token, deviceId)
  → LOGIN_RESPONSE
  → 心跳
  → 订阅事件
```

事件：

| 事件 | 行为 |
|---|---|
| `SERVER_ACK` | 消息已持久化 |
| `MESSAGE_NOTIFY` | 新消息、未读、滚动 |
| `MESSAGE_RECALL` | 撤回提示 |
| `READ_NOTIFY` | 已读更新 |
| `FRIEND_NOTIFY` | 好友申请/接受/拉黑 |
| `ERROR` | 错误提示 |

重连：

1. 指数退避；
2. 重新 LOGIN；
3. 拉取最后 `seq` 之后的消息；
4. 重发待发消息；
5. 去重、排序、恢复 UI。

浏览器只使用 WebSocket；原生客户端可选 TCP。

---

## 9. 消息与 @

普通文本直接发送字符串：

```json
"hello"
```

需要 @ 时发送 JSON：

```json
{"text":"@alice 看一下","mentions":[1002]}
```

前端：

1. 输入 `@` 弹出群成员；
2. 选择成员；
3. 生成 `mentions`；
4. 发送；
5. 消息气泡高亮被 @ 用户。

@ 未读提醒 / “提及我的消息”后端待补。

### 消息操作能力（右键菜单）

| 操作 | 现有接口 | 结论 |
|---|---|---|
| 复制 | 无（纯前端） | 前端本地完成 |
| 转发 | `POST /conversations/{id}/messages` 复用发送接口 | 可行，但不叫转发；附件消息受「文件必须属于发送者」校验，别人的图片/文件无法转发到其它会话 |
| 引用 | 无 | `im_message` 没有 `reply_to` / `quote_id` 列，历史消息也不返回引用信息，需加列 + DTO + 渲染 |
| 删除 | 仅 `POST /conversations/{id}/messages/{messageId}/recall` | 只能撤回自己 2 分钟内的消息；没有通用删除接口，也没有「仅删除我这边」的会话级可见性表 |
| 翻译 | 无 | 需要接入翻译服务并新增接口，属于外部依赖 |

撤回规则：`MessageApplicationService.RECALL_WINDOW = 2 分钟`，仅发送者本人可撤回；撤回后消息保留占位，写 `recalled_at` / `recalled_by`，并广播 `MESSAGE_RECALLED` 事件。

补充方向（按优先级）：

1. `im_message` 增加 `reply_to_id`，发送 DTO 增加可选 `replyToId`，历史接口返回被引用消息的精简快照；
2. 增加 `im_user_message_visibility(user_id, message_id, hidden_at)`，实现「删除（仅我）」而不影响他人；
3. 转发落一条 `forwarded_from` 字段用于溯源，避免与普通发送混淆；
4. 翻译按 `messageId + targetLang` 缓存结果，避免重复调用外部服务。

---

## 10. 文件上传

### 普通上传

```http
POST /api/v1/files
Content-Type: multipart/form-data
```

### 分片上传

```http
POST   /api/v1/files/uploads
PUT    /api/v1/files/uploads/{uploadId}/chunks/{index}
GET    /api/v1/files/uploads/{uploadId}
POST   /api/v1/files/uploads/{uploadId}/complete
DELETE /api/v1/files/uploads/{uploadId}
```

前端：

- 计算分片；
- 逐片上传；
- 保存 `uploadId`；
- 刷新后查询进度；
- 只补缺失分片；
- 完成后发送文件消息。

---

## 11. 错误码

| 错误码 | 场景 | 前端行为 |
|---|---|---|
| `401` | Token 失效 | 跳登录 |
| `403` | 无权限 | 403 页面 |
| `40305` | 对方拒收 | 消息失败提示 |
| `40306` | 群禁言 | 禁用输入框 |
| `423` | 账号锁定 | 显示倒计时 |
| `429` | 限流 | 显示重试倒计时 |
| `409` | 冲突 | 提示刷新 |
| `404` | 资源不存在 | 空状态 |

---

## 12. 后端待补接口

### 社交（已实现）

| 接口 | 用途 |
|---|---|
| `GET /api/v1/users/search?keyword=` | 搜索用户 |
| `GET /api/v1/users/{id}/profile` | 查看资料 |
| `GET /api/v1/users/me/privacy` | 查询隐私设置 |
| `PUT /api/v1/users/me/privacy` | 更新隐私设置 |
| `POST /api/v1/reports` | 举报 |
| `GET /api/v1/reports/me` | 我的举报记录 |

### 群

| 接口 | 用途 |
|---|---|
| `GET /api/v1/groups/{id}/members` | 群成员列表 |
| `POST /api/v1/groups/join` | 加群（后续） |
| `GET /api/v1/groups/search` | 群搜索（后续） |

### 运营（已实现，详见 4.4）

已提供用户、群、举报、概览、禁言、敏感词和审计接口。后续可补：

- 图片/视频内容审核；
- 举报原因统计；
- 审计筛选/分页。

### 推送（已实现设备注册）

| 接口 | 用途 |
|---|---|
| `POST /api/v1/push/devices` | 注册推送 token |
| `GET /api/v1/push/devices` | 我的推送设备 |
| `DELETE /api/v1/push/devices/{deviceId}` | 注销 token |

APNs/FCM 真实投递由后端 provider 完成，前端只需注册设备 token。

### Web Push（浏览器离线推送，已实现）

不需要任何服务商账号：浏览器自带推送服务，后端用自生成 VAPID 密钥签名。

| 接口 | 用途 |
|---|---|
| `GET /api/v1/push/web/config` | 查 `{enabled, publicKey}` |
| `POST /api/v1/push/web/subscriptions` | 上报订阅 |
| `DELETE /api/v1/push/web/subscriptions?endpoint=` | 退订 |

**要求 HTTPS（`localhost` 除外）。**

#### 1. Service Worker（`public/sw.js`）

```js
self.addEventListener('push', (event) => {
  const payload = event.data ? event.data.json() : {};
  event.waitUntil(self.registration.showNotification(payload.title || 'VERYAL', {
    body: payload.body || '',
    data: payload.data || {},
    tag: payload.data?.conversationId || 'veryal',
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  event.waitUntil(clients.openWindow(data.conversationId
    ? `/app/chat/c/${data.conversationId}` : '/app/chat'));
});
```

#### 2. 订阅流程

```ts
const toBase64Url = (buffer: ArrayBuffer) => {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => { binary += String.fromCharCode(b); });
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
};

const fromBase64Url = (value: string) => {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/')
    .padEnd(value.length + (4 - (value.length % 4)) % 4, '=');
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
};

export async function enableWebPush(api: ApiClient) {
  const { enabled, publicKey } = (await api.get('/api/v1/push/web/config')).data;
  if (!enabled) return false;
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return false;

  const registration = await navigator.serviceWorker.register('/sw.js');
  await navigator.serviceWorker.ready;

  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return false;

  const existing = await registration.pushManager.getSubscription();
  const subscription = existing ?? await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: fromBase64Url(publicKey),
  });

  await api.post('/api/v1/push/web/subscriptions', {
    endpoint: subscription.endpoint,
    p256dh: toBase64Url(subscription.getKey('p256dh')!),
    auth: toBase64Url(subscription.getKey('auth')!),
    userAgent: navigator.userAgent,
  });
  return true;
}
```

#### 3. 注意事项

- **必须在用户手势（点击）里调用** `Notification.requestPermission()`，否则桌面 Chrome 会静默拒绝。
- 订阅接口幂等：同一 `endpoint` 重复上报只更新，不会重复堆积。
- 后端对 404/410 订阅自动清理，前端不需要额外处理失效。
- 用户退订：`subscription.unsubscribe()` 后调 `DELETE /api/v1/push/web/subscriptions?endpoint=`。
- 后端 `veryal.push.web-push.enabled=false` 时，`config` 返回 `enabled=false`，前端应直接跳过，不弹权限。

#### 4. 当前触发范围

离线推送目前只由**好友事件**触发（申请/接受/拒绝/取消/拉黑/解除）。聊天消息的离线推送尚未接入。

---

## 13. 实施顺序

### Phase 1：账号与 IM 外壳

- 登录/注册；
- 应用外壳；
- 会话列表；
- WebSocket 连接；
- 文本消息。

### Phase 2：好友与单聊

- 好友申请；
- 通讯录；
- 单聊；
- 历史/同步；
- 未读/已读。

### Phase 3：消息能力

- 图片/语音/视频/文件；
- 分片上传；
- 撤回；
- 搜索；
- @。

### Phase 4：群聊

- 建群；
- 群成员；
- 群管理；
- 公告/禁言；
- 群文件。

### Phase 5：隐私与运营后台

- 隐私设置；
- 举报；
- 用户/群管理；
- 内容审核；
- 运营审计。

### Phase 6：质量

- Playwright E2E；
- 断网重连；
- 多端同步；
- 移动端适配；
- 性能优化。

---

## 14. 测试与部署

### 测试

- 单元：错误码、消息合并、上传分片、权限判断；
- 组件：消息气泡、输入框、会话列表；
- E2E：注册→登录→加好友→单聊→建群→发消息→撤回→举报；
- 断网/重连/多标签页；
- 运营后台：登录 MFA→封号→举报处理→审计。

### 部署

| 应用 | 部署 |
|---|---|
| IM Web | `im.example.com` |
| 运营后台 | `ops.example.com` |
| API | 同域 `/api` 或独立 `api.example.com` |
| WebSocket | 同域 `/ws` |

### 安全

- IM 与运营后台不同域；
- 运营 Token 不放 `localStorage`；
- 严格 CSP；
- 文件预览沙箱；
- 禁止 `v-html` 渲染消息；
- 敏感操作二次确认；
- 退出清理缓存。

---

## 15. 明确不做

- 租户管理端；
- 多租户切换；
- 租户配额/计费/导出/删除；
- 企业组织架构；
- 审批/日历/文档；
- 支付/红包/转账；
- 朋友圈；
- 音视频通话；
- 开放平台/SDK；
- 私有化部署控制台。

这些能力不进入一期前端，后续另立项目设计。
