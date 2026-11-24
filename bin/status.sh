#!/usr/bin/env bash
# VERYAL 运维脚本
set -uo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT=veryal-backend
APP_PORT=8080
WEB_PORT=8899

c_ok()  { printf '\033[1;32m%s\033[0m\n' "$*"; }
c_inf() { printf '\033[1;36m%s\033[0m\n' "$*"; }

env_value() {
  awk -F= -v k="$1" '$1==k{sub(/^[^=]*=/,""); gsub(/^["\x27]|["\x27]$/,""); print; exit}' \
    "${APP_HOME}/config/.env" 2>/dev/null
}

echo "──── 服务状态 ────"
printf '%-18s %s\n' "backend"        "$(systemctl is-active $UNIT 2>/dev/null)"
printf '%-18s %s\n' "nginx"          "$(systemctl is-active nginx 2>/dev/null)"
printf '%-18s %s\n' "docker mysql"   "$(docker inspect -f '{{.State.Status}}' veryal-mysql 2>/dev/null)"
printf '%-18s %s\n' "docker redis"   "$(docker inspect -f '{{.State.Status}}' veryal-redis 2>/dev/null)"
printf '%-18s %s\n' "docker rabbit"  "$(docker inspect -f '{{.State.Status}}' veryal-rabbitmq 2>/dev/null)"
printf '%-18s %s\n' "docker s3"      "$(docker inspect -f '{{.State.Status}}' veryal-s3 2>/dev/null)"

echo
echo "──── 健康检查 ────"
h=$(curl -fsS -m 3 "http://127.0.0.1:${APP_PORT}/actuator/health" 2>/dev/null) && echo "backend    $h" || echo "backend    不可达"
s3=$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:9103/health 2>/dev/null)
echo "object-s3  HTTP ${s3:-000}（9103/health）"

echo
echo "──── 文件存储 ────"
STORAGE_TYPE="$(env_value VERYAL_STORAGE_TYPE)"
printf '%-10s %s\n' "类型" "${STORAGE_TYPE:-local}"
case "${STORAGE_TYPE:-local}" in
  minio)
    printf '%-10s %s\n' "endpoint" "$(env_value VERYAL_MINIO_ENDPOINT)"
    BUCKET="$(env_value VERYAL_MINIO_BUCKET)"
    docker exec veryal-s3 /garage bucket info "${BUCKET:-veryal}" 2>/dev/null | sed -n '1,6p' \
      | sed 's/^/           /'
    ;;
  oss)
    printf '%-10s %s\n' "endpoint" "$(env_value VERYAL_OSS_ENDPOINT)"
    printf '%-10s %s\n' "bucket"   "$(env_value VERYAL_OSS_BUCKET)"
    ;;
  *)
    printf '%-10s %s\n' "root" "$(env_value VERYAL_STORAGE_LOCAL_ROOT)"
    du -sh "${APP_HOME}/data/files" 2>/dev/null | cut -f1
    ;;
esac

echo
echo "──── 端口监听 ────"
ss -lntp 2>/dev/null | grep -E ":(${WEB_PORT}|${APP_PORT}|9100|9103)\b" || echo "未监听"

echo
echo "──── 资源 ────"
ps -o pid,rss,etime,cmd -C java --no-headers 2>/dev/null \
  | awk '{printf "java  PID %s  内存 %.0fMB  运行 %s\n", $1, $2/1024, $3}'
for c in veryal-mysql veryal-redis veryal-rabbitmq veryal-s3; do
  mem=$(docker stats --no-stream --format '{{.MemUsage}}' "$c" 2>/dev/null | cut -d/ -f1 | tr -d ' ')
  [ -n "$mem" ] && printf '%-10s %s\n' "${c#veryal-}" "$mem"
done

echo
echo "──── 入口 ────"
printf '页面   HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${WEB_PORT}/ || echo 000)"
printf 'API    HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${WEB_PORT}/api/v1/auth/login || echo 000)"
printf '健康   HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${WEB_PORT}/actuator/health || echo 000)"

echo
echo "──── 日志体积 ────"
du -sh "${APP_HOME}/logs" 2>/dev/null | cut -f1
