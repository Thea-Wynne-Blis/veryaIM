#!/usr/bin/env bash
# 验证退出登录后 Token 在服务端立即失效
set -uo pipefail
BASE="http://127.0.0.1:8899/api/v1"
U="logout$(date +%s)"

curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"password123\",\"nickname\":\"登出测试\"}" >/dev/null

TOK=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"password123\",\"deviceId\":\"lo-$U\",\"platform\":\"WEB\",\"deviceName\":\"t\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

echo "① 登出前访问 /auth/me"
curl -s -o /dev/null -w "   HTTP %{http_code}\n" "$BASE/auth/me" -H "Authorization: Bearer $TOK"

echo "② 调用 /auth/logout"
curl -s -o /dev/null -w "   HTTP %{http_code}\n" -X POST "$BASE/auth/logout" -H "Authorization: Bearer $TOK"

echo "③ 用同一个 Token 再访问 /auth/me"
BODY=$(curl -s -w '\n%{http_code}' "$BASE/auth/me" -H "Authorization: Bearer $TOK")
CODE=$(echo "$BODY" | tail -1)
MSG=$(echo "$BODY" | head -1)
echo "   HTTP $CODE"
echo "   响应 $MSG"
