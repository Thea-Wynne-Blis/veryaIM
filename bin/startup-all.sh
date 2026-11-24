#!/usr/bin/env bash
# VERYAL 运维脚本
# 一键启动：容器（mysql → redis → rabbitmq → 对象存储）→ 等容器真正可用 → 后端服务 → 确保 nginx
# 配合 stop-all.sh 使用；只重启后端请用 restart.sh
set -uo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT=veryal-backend
APP_PORT=8080
WEB_PORT=8899
# 按依赖顺序启动；对象存储最后起（后端启动时会连它）
CONTAINERS=(veryal-mysql veryal-redis veryal-rabbitmq veryal-s3)
declare -A PORTS=([veryal-mysql]=13306 [veryal-redis]=16379 [veryal-rabbitmq]=5672 [veryal-s3]=9100)

c_ok()  { printf '\033[1;32m%s\033[0m\n' "$*"; }
c_inf() { printf '\033[1;36m%s\033[0m\n' "$*"; }
c_warn() { printf '\033[1;33m%s\033[0m\n' "$*"; }
c_err() { printf '\033[1;31m%s\033[0m\n' "$*"; }

health() { curl -fsS -m 3 "http://127.0.0.1:${APP_PORT}/actuator/health" 2>/dev/null; }

env_value() {
  awk -F= -v k="$1" '$1==k{sub(/^[^=]*=/,""); gsub(/^["\x27]|["\x27]$/,""); print; exit}' \
    "${APP_HOME}/config/.env" 2>/dev/null
}

wait_ready() {
  local max="${1:-90}"
  for i in $(seq 1 "$max"); do
    if health >/dev/null; then
      c_ok "后端就绪（约 $((i * 2))s）"
      return 0
    fi
    sleep 2
  done
  c_err "启动超时，查看日志 ${APP_HOME}/logs/error.log"
  return 1
}

port_open() {
  timeout 1 bash -c "exec 3<>/dev/tcp/127.0.0.1/$1" 2>/dev/null
}

# 容器内部真实可用性（端口通了不代表服务能接受请求）
container_ready() {
  case "$1" in
    veryal-mysql)
      local pass
      pass=$(docker exec veryal-mysql printenv MYSQL_ROOT_PASSWORD 2>/dev/null)
      [ -n "$pass" ] || return 1
      docker exec veryal-mysql mysqladmin ping -uroot -p"$pass" --silent >/dev/null 2>&1
      ;;
    veryal-redis)
      [ "$(docker exec veryal-redis redis-cli ping 2>/dev/null | tr -d '\r')" = "PONG" ]
      ;;
    veryal-rabbitmq)
      docker exec veryal-rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1
      ;;
    veryal-s3)
      # Garage 管理 API 的 /health 无需 token；集群未就绪时不会返回 200
      [ "$(curl -s -o /dev/null -m 3 -w '%{http_code}' http://127.0.0.1:9103/health)" = "200" ]
      ;;
    *)
      port_open "${PORTS[$1]:-0}"
      ;;
  esac
}

wait_container() {
  local name="$1" max="${2:-60}"
  for i in $(seq 1 "$max"); do
    if container_ready "$name"; then
      c_ok "${name}: 已就绪（约 ${i}s）"
      return 0
    fi
    sleep 1
  done
  c_err "${name}: 等待超时（端口 ${PORTS[$name]}）"
  return 1
}

# ---------- 1. 容器 ----------
c_inf "启动容器（${CONTAINERS[*]}）…"
failed=0
for name in "${CONTAINERS[@]}"; do
  if ! docker inspect "$name" >/dev/null 2>&1; then
    c_err "${name}: 容器不存在（可能被删除了），需要重新创建"
    failed=1
    continue
  fi
  state=$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null)
  if [ "$state" = "running" ]; then
    c_ok "${name}: 已在运行"
  else
    docker start "$name" >/dev/null 2>&1
    state=$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null)
    if [ "$state" = "running" ]; then
      c_ok "${name}: 已启动"
    else
      c_err "${name}: 启动失败（当前 ${state}）"
      failed=1
    fi
  fi
done
[ "$failed" = "0" ] || { c_err "容器未全部启动，已中止，避免后端连不上依赖"; exit 1; }

# ---------- 2. 等容器真正可用 ----------
c_inf "等待容器就绪…"
for name in "${CONTAINERS[@]}"; do
  wait_container "$name" 60 || exit 1
done

# ---------- 2.1 对象存储：类型与桶 ----------
STORAGE_TYPE="$(env_value VERYAL_STORAGE_TYPE)"
if [ "${STORAGE_TYPE:-local}" = "minio" ]; then
  BUCKET="$(env_value VERYAL_MINIO_BUCKET)"
  if docker exec veryal-s3 /garage bucket list 2>/dev/null | grep -q "^  ${BUCKET:-veryal} "; then
    c_ok "对象存储: bucket ${BUCKET:-veryal} 存在"
  else
    c_warn "对象存储: bucket ${BUCKET:-veryal} 不存在（首次上传会自动创建，或手工 garage bucket create）"
  fi
fi

# ---------- 3. 后端 ----------
c_inf "启动后端…"
sudo systemctl start "$UNIT"
wait_ready 90 || exit 1

# ---------- 4. nginx ----------
c_inf "确保 nginx 运行…"
sudo systemctl start nginx

# ---------- 5. 汇总 ----------
echo
echo "──── 启动后状态 ────"
printf '%-18s %s\n' "backend"  "$(systemctl is-active $UNIT 2>/dev/null)"
printf '%-18s %s\n' "nginx"    "$(systemctl is-active nginx 2>/dev/null)"
for name in "${CONTAINERS[@]}"; do
  printf '%-18s %s\n' "docker $name" "$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
done
printf '%-18s %s\n' "storage" "${STORAGE_TYPE:-local}"
echo
printf '页面   HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${WEB_PORT}/ || echo 000)"
printf '健康   HTTP %s\n' "$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:${WEB_PORT}/actuator/health || echo 000)"
c_ok "全部已启动 → http://<服务器IP>:${WEB_PORT}/"
