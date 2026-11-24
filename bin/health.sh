#!/usr/bin/env bash
# VERYAL 运维脚本
# 健康探针：后端 / MySQL / Redis / RabbitMQ / 对象存储 / 对外入口，异常返回非 0
set -uo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT=veryal-backend
APP_PORT=8080
WEB_PORT=8899
FAILED=0

ok()   { printf '\033[1;32m  OK  \033[0m %s\n' "$*"; }
bad()  { printf '\033[1;31m FAIL \033[0m %s\n' "$*"; FAILED=$((FAILED + 1)); }

env_value() {
  awk -F= -v k="$1" '$1==k{sub(/^[^=]*=/,""); gsub(/^["\x27]|["\x27]$/,""); print; exit}' \
    "${APP_HOME}/config/.env" 2>/dev/null
}

H=$(curl -s -m 5 "http://127.0.0.1:${APP_PORT}/actuator/health" 2>/dev/null)
if [ -z "$H" ]; then
  bad "backend 不可达（${APP_PORT}/actuator/health）"
else
  ok "backend $H"
fi

for path in "/" "/api/v1/auth/login" "/actuator/health"; do
  code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "http://127.0.0.1:${WEB_PORT}${path}")
  case "$code" in
    2*|3*|4*) ok "入口 ${path} -> ${code}" ;;
    *)        bad "入口 ${path} -> ${code:-000}" ;;
  esac
done

echo
echo "依赖："
DB_PASS="$(env_value VERYAL_DB_PASSWORD)"
DB_USER="$(env_value VERYAL_DB_USER)"
if docker exec veryal-mysql mysql -u"${DB_USER:-veryal}" -p"${DB_PASS:-veryal}" -N -e 'select 1' veryal_im 2>/dev/null | grep -q '^1$'; then
  ok "MySQL 可连接（库 veryal_im）"
else
  bad "MySQL 连接失败"
fi

if [ "$(docker exec veryal-redis redis-cli ping 2>/dev/null | tr -d '\r')" = "PONG" ]; then
  ok "Redis PONG"
else
  bad "Redis 无响应"
fi

if docker exec veryal-rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1; then
  ok "RabbitMQ 存活"
else
  bad "RabbitMQ 无响应"
fi

echo
echo "文件存储："
STORAGE_TYPE="$(env_value VERYAL_STORAGE_TYPE)"
case "${STORAGE_TYPE:-local}" in
  minio)
    if [ "$(curl -s -o /dev/null -m 3 -w '%{http_code}' http://127.0.0.1:9103/health)" = "200" ]; then
      ok "对象存储 (Garage) 集群健康"
    else
      bad "对象存储 (Garage) 不健康（9103/health）"
    fi
    BUCKET="$(env_value VERYAL_MINIO_BUCKET)"
    if docker exec veryal-s3 /garage bucket list 2>/dev/null | grep -q "^  ${BUCKET:-veryal} "; then
      ok "bucket ${BUCKET:-veryal} 存在"
    else
      bad "bucket ${BUCKET:-veryal} 不存在"
    fi
    ;;
  oss)
    ok "存储类型 oss（endpoint $(env_value VERYAL_OSS_ENDPOINT)）"
    ;;
  *)
    ROOT="$(env_value VERYAL_STORAGE_LOCAL_ROOT)"
    if [ -d "${ROOT:-${APP_HOME}/data/files}" ]; then
      ok "存储类型 local（${ROOT:-${APP_HOME}/data/files}）"
    else
      bad "本地存储目录不存在：${ROOT}"
    fi
    ;;
esac

echo
if [ "$FAILED" = "0" ]; then
  ok "全部检查通过"
else
  bad "${FAILED} 项检查失败"
fi
exit "$([ "$FAILED" = "0" ] && echo 0 || echo 1)"
