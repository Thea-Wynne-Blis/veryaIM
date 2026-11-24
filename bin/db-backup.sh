#!/usr/bin/env bash
# VERYAL 数据库逻辑备份：mysqldump --single-transaction → gzip，保留 7 天。
set -uo pipefail
APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$APP_HOME/backups/db"
KEEP_DAYS="${KEEP_DAYS:-7}"
TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$DEST/veryal_im-$TS.sql.gz"

[ -f "$APP_HOME/config/.env" ] || { echo "缺少 $APP_HOME/config/.env"; exit 1; }
DBP=$(grep '^VERYAL_DB_PASSWORD=' "$APP_HOME/config/.env" | cut -d= -f2- | tr -d '"' | tr -d "'")
[ -n "$DBP" ] || { echo "未取到 VERYAL_DB_PASSWORD"; exit 1; }

mkdir -p "$DEST"
if docker exec -e MYSQL_PWD="$DBP" veryal-mysql \
      mysqldump -uveryal --single-transaction --quick --routines --events --databases veryal_im \
      2>/dev/null | gzip -6 > "$OUT"; then
  echo "backup ok: $OUT ($(du -h "$OUT" | cut -f1))"
else
  rm -f "$OUT"; echo "backup FAILED"; exit 1
fi

# 只保留最近 N 天
find "$DEST" -name 'veryal_im-*.sql.gz' -mtime +"$KEEP_DAYS" -delete
ls -1t "$DEST" | tail -n +1 | sed -n '1,10p'
