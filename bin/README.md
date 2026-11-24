# bin/ · 裸机运维脚本

这些脚本就是线上 `/home/ubuntu/veryal/bin/` 里的那一份，纳入仓库避免「服务器上改了、仓库里没有」。

**部署位置约定**：整个目录拷到 `/home/ubuntu/veryal/bin/`，脚本用 `dirname $0/..` 推出 `APP_HOME`，所以它们必须和 `backend/`、`config/`、`logs/`、`storage/` 同级。

```bash
rsync -av bin/ ubuntu@host:/home/ubuntu/veryal/bin/
ssh ubuntu@host 'chmod +x /home/ubuntu/veryal/bin/*.sh'
```

| 脚本 | 作用 |
|---|---|
| `startup-all.sh` | 幂等启动：按 mysql → redis → rabbitmq → 对象存储 顺序起容器，等每个中间件**真正可用**（不是端口通）再起后端，最后确保 nginx；会检查存储类型与 bucket |
| `start-up.sh` | 只起后端（容器已在跑时用） |
| `stop-all.sh` | 先停后端再停容器，避免应用侧刷连接错误 |
| `stop.sh` | 只停后端 |
| `restart.sh` | 只重启后端，并等到 `/actuator/health` 返回 200 |
| `status.sh` | 容器状态 + 健康 + 文件存储（类型 / bucket 对象数）+ 端口 + 资源占用 |
| `health.sh` | 健康探针：后端 / 入口 / MySQL / Redis / RabbitMQ / 对象存储逐项检查，异常返回非 0 |
| `logs.sh` | 按关键字/时间过滤 `logs/` 下的日志 |
| `db-backup.sh` | `mysqldump --single-transaction` 到 `backups/db/*.sql.gz`，保留 7 天（cron 每日 03:20 UTC） |

注意：

- 脚本从 `config/.env` 读密码，**不要把密码写进脚本**。
- `startup-all.sh` 只启动已存在的容器；容器被删除会直接报错，不会偷偷重建（重建请用 `deploy/docker/docker-compose.yml`）。
- 对象存储容器固定叫 `veryal-s3`（S3 兼容，详见 `veryal-backend/README.md` 的「文件存储」一节），凭据在 `config/.s3-credentials`（600）。
- `db-backup.sh` 用 `--single-transaction` 做一致性快照，不锁表；恢复用 `zcat x.sql.gz | docker exec -i veryal-mysql mysql -uveryal -p`。
