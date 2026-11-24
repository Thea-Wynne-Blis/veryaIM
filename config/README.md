# config/ —— 运行时配置

部署时**只改 `.env`**，其它文件交给脚本处理。

```
config/
├─ .env.example                    # 配置样例，复制成 .env 后填真实值
├─ application-local.yaml.example  # 模板：用 ${KEY:default} 引用 .env 里的变量
├─ render-config.sh                # 扫描 .env → 生成 application-local.yaml
└─ start.sh                        # 渲染 + 导出环境变量 + 启动 jar
```

## 首次部署

```bash
cp config/.env.example config/.env
chmod 600 config/.env
vi config/.env           # 填数据库密码、令牌密钥等
```

## 启动

```bash
bash config/start.sh
```

脚本会：

1. 扫描 `config/.env`（没有就找项目根目录 `.env`）；
2. 用里面的值渲染 `config/application-local.yaml`（权限 600，不提交）；
3. 把 `.env` 逐行 `export` 成环境变量，再带
   `--spring.profiles.active=prod,local` 与
   `--spring.config.additional-location=file:config/` 启动。

systemd 只需要把 `ExecStart` 指向 `config/start.sh`：

```ini
[Service]
WorkingDirectory=/home/ubuntu/veryal
ExecStart=/home/ubuntu/veryal/config/start.sh
```

## 约定

- `.env` 与 `application-local.yaml` 都在 `.gitignore` 里，**不要提交**。
- 模板里写成 `${KEY:default}`：`.env` 有 `KEY` 就替换，没有就保留占位符，
  由 Spring 用 `default` 解析。所以 `.env` 只写要覆盖的项即可，
  生成后仍残留的占位符会在 stderr 里列出来，方便核对。
- 想加新配置：在 `.env.example` 加一行，再在
  `application-local.yaml.example` 里用 `${KEY:default}` 引用。
- 改完 `.env` 重启服务即可，不需要重新打包。

## 监听地址

裸机部署时 HTTP 与 WS 网关都只绑回环，对外只有 nginx：

| 变量 | 默认 | 说明 |
|---|---|---|
| `VERYAL_HTTP_BIND` | `127.0.0.1` | Spring `server.address` |
| `VERYAL_WS_BIND` | `127.0.0.1` | Netty 网关监听地址 |

容器部署（`deploy/docker`）不加载 `local` profile、也不设这两项，保持监听所有网卡，
这样 `-p 127.0.0.1:8080:8080` 的端口映射才连得上。需要临时放开（例如调试直连
9098）时把它设成空值即可：`VERYAL_WS_BIND=`。

## 文件存储

`VERYAL_STORAGE_TYPE` 三选一：`local`（落盘）、`minio`（任意 S3 兼容服务，线上是 Garage）、
`oss`（阿里云 OSS）。三者可以随时切换，旧文件按 `im_file.storage_type` 路由，不需要迁移。

`minio` 需要的最小配置：

| 变量 | 线上值 | 说明 |
|---|---|---|
| `VERYAL_MINIO_ENDPOINT` | `http://127.0.0.1:9100` | 后端访问地址（回环，容器只绑本机） |
| `VERYAL_MINIO_REGION` | `us-east-1` | 必须显式给；不配会先向服务端询问 region，签名也多一次网络往返 |
| `VERYAL_MINIO_ACCESS_KEY` / `_SECRET_KEY` | 见 `config/.s3-credentials` | 后端专用 key，只对 `veryal` 桶有 RWO |
| `VERYAL_MINIO_BUCKET` | `veryal` | 不存在时首次上传自动创建 |
| `VERYAL_MINIO_PUBLIC_ENDPOINT` | 空 | 留空时 `/files/{id}/url` 返回 `/api/v1/files/{id}/content`，走应用流式下载 |

`oss` 对应 `VERYAL_OSS_ENDPOINT` / `_ACCESS_KEY_ID` / `_ACCESS_KEY_SECRET` / `_BUCKET`。
切换后 `bash bin/restart.sh` 即可，`bin/health.sh` 会按当前类型检查对应后端。
