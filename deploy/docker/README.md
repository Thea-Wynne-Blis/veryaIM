# compose 部署（中间件 + 应用）

> 线上当前形态是「中间件容器 + 裸机 systemd 应用」，见 `veryal-backend/README.md` 第 5.3 节。
> 这份 compose 是**完整的容器化形态**，用于新机器一次性拉起。

## 1. 准备

```bash
cd veryaI-backend/deploy/docker
cp .env.example .env && chmod 600 .env          # 改掉里面所有 change-me

cp garage.toml.example garage.toml
RPC_SECRET=$(openssl rand -hex 32) ADMIN_TOKEN=$(openssl rand -hex 24) \
  envsubst < garage.toml.example > garage.toml
chmod 600 garage.toml
```

## 2. 起中间件

```bash
docker compose up -d mysql redis rabbitmq s3
docker compose ps               # 四个都应该是 healthy
```

## 3. 初始化对象存储（只做一次）

```bash
docker exec veryal-s3 /garage status | awk '/^[0-9a-f]{16}/ {print $1; exit}'   # node id
docker exec veryal-s3 /garage layout assign -z dc1 -c 20G <node-id>
docker exec veryal-s3 /garage layout apply --version 1

docker exec veryal-s3 /garage key create veryal-app          # 记下 Key ID / Secret key
docker exec veryal-s3 /garage bucket create veryal
docker exec veryal-s3 /garage bucket allow veryal --key veryal-app --owner --read --write
```

把 Key ID / Secret 填进 `.env`：

```bash
VERYAL_S3_ACCESS_KEY=GK...
VERYAL_S3_SECRET_KEY=...
VERYAL_MINIO_BUCKET=veryal
```

> 为什么不是 MinIO：MinIO 官方 Docker 镜像与 `dl.min.io` 二进制在 2025 年已下架（拉取返回 `denied`，下载返回 410）。
> 后端走标准 S3 协议（配置键仍是 `veryal.storage.minio.*`，客户端是 `minio-java`），换成 Garage 对业务代码零改动。
> 想接阿里云 OSS 时把 `VERYAL_STORAGE_TYPE` 改成 `oss` 并填 `VERYAL_OSS_*` 即可，三套实现都在 `veryal-infrastructure/storage/`。

## 4. 起应用

```bash
docker compose up -d --build veryal-app
curl -s http://127.0.0.1:8080/actuator/health
```

## 5. 端口

| 服务 | 宿主监听 | 说明 |
|---|---|---|
| mysql | `127.0.0.1:3306` | 只回环，SSH 隧道访问 |
| redis | `127.0.0.1:6379` | 只回环 |
| rabbitmq | `127.0.0.1:5672` / `15672` | 管理台走隧道 |
| s3 | `127.0.0.1:9100` / `9101` / `9103` | S3 API / 控制台 / 管理 API |
| app | `127.0.0.1:8080` / `9098` / `9099` | HTTP / WS 网关 / 指标 |

对外只应有前置 nginx 的 `:8899`（静态资源 + `/api/` + `/ws` + `/actuator/health`）。
