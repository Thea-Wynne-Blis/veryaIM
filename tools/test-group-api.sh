#!/usr/bin/env bash
# 直接验证群接口
set -uo pipefail
BASE="http://127.0.0.1:8899/api/v1"

T=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo_alice\",\"password\":\"password123\",\"deviceId\":\"gt-$(date +%s)\",\"platform\":\"WEB\",\"deviceName\":\"t\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
echo "token 长度: ${#T}"

echo
echo "=== POST /groups ==="
curl -s -o /tmp/g.json -w 'HTTP %{http_code}\n' -X POST "$BASE/groups" \
  -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
  -d '{"name":"curl测试群","memberIds":[]}'
head -c 400 /tmp/g.json; echo

echo
echo "=== GET /groups ==="
curl -s -o /tmp/g2.json -w 'HTTP %{http_code}\n' "$BASE/groups" -H "Authorization: Bearer $T"
head -c 400 /tmp/g2.json; echo

echo
echo "=== GET /events（前端在调但 404） ==="
curl -s -o /dev/null -w 'HTTP %{http_code}\n' "$BASE/events" -H "Authorization: Bearer $T"

echo
echo "=== 后端最近 ERROR ==="
grep -c ERROR /home/ubuntu/veryal/logs/error.log 2>/dev/null || echo 0
