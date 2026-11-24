#!/usr/bin/env bash
# VERYAL 运维脚本
# 一键停止：后端服务 + 全部 veryal 容器（mysql / redis / rabbitmq / 对象存储）
# nginx 默认不停（只影响静态页，停了首页就打不开）；需要连 nginx 一起停：bash stop-all.sh --nginx
set -uo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT=veryal-backend
APP_PORT=8080
WEB_PORT=8899
# 按依赖倒序停止：对象存储最后停
CONTAINERS=(veryal-rabbitmq veryal-redis veryal-mysql veryal-s3)

STOP_NGINX=0
for arg in "$@"; do
  case "$arg" in
    --nginx) STOP_NGINX=1 ;;
    -h|--help)
      echo "用法: bash stop-all.sh [--nginx]"
      echo "  默认停后端 + veryal 容器，保留 nginx；-nginx 连 nginx 一起停"
      exit 0
      ;;
    *) echo "未知参数: $arg" >&2; exit 2 ;;
  esac
done

c_ok()  { printf '\033[1;32m%s\033[0m\n' "$*"; }
c_inf() { printf '\033[1;36m%s\033[0m\n' "$*"; }
c_err() { printf '\033[1;31m%s\033[0m\n' "$*"; }

# ---------- 1. 后端 ----------
c_inf "停止后端…"
if systemctl is-active --quiet "$UNIT"; then
  sudo systemctl stop "$UNIT"
fi
if systemctl is-active --quiet "$UNIT"; then
  c_err "后端仍在运行，请检查 systemctl status ${UNIT}"
else
  c_ok "后端已停止"
fi

# ---------- 2. 容器 ----------
c_inf "停止容器（${CONTAINERS[*]}）…"
for name in "${CONTAINERS[@]}"; do
  if ! docker inspect "$name" >/dev/null 2>&1; then
    c_err "${name}: 容器不存在，跳过"
    continue
  fi
  state=$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null)
  if [ "$state" = "running" ]; then
    docker stop "$name" >/dev/null 2>&1
    state=$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null)
    if [ "$state" = "exited" ] || [ "$state" = "created" ]; then
      c_ok "${name}: 已停止"
    else
      c_err "${name}: 停止失败（当前 ${state}）"
    fi
  else
    c_ok "${name}: 本来就没运行（${state}）"
  fi
done

# ---------- 3. nginx ----------
if [ "$STOP_NGINX" = "1" ]; then
  c_inf "停止 nginx…"
  sudo systemctl stop nginx
  c_ok "nginx 已停止（首页也会打不开）"
else
  c_inf "nginx 保持运行（只提供静态页 + 反代，API 会返回 502）"
fi

# ---------- 4. 汇总 ----------
echo
echo "──── 停止后状态 ────"
printf '%-18s %s\n' "backend"  "$(systemctl is-active $UNIT 2>/dev/null)"
printf '%-18s %s\n' "nginx"    "$(systemctl is-active nginx 2>/dev/null)"
for name in "${CONTAINERS[@]}"; do
  printf '%-18s %s\n' "docker $name" "$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
done
c_ok "全部已停止。重新启动: bash ${APP_HOME}/bin/startup-all.sh"
