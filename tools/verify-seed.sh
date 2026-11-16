#!/usr/bin/env bash
# 以 demo_alice 身份验证种子数据是否可见
set -uo pipefail
BASE="http://127.0.0.1:8899/api/v1"

sudo rm -f /home/ubuntu/veryal/web/_boot.html
echo "已清理临时登录引导页"

TOK=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo_alice\",\"password\":\"password123\",\"deviceId\":\"verify-$(date +%s)\",\"platform\":\"WEB\",\"deviceName\":\"verify\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

pp() { python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(json.dumps(d,ensure_ascii=False,indent=2)[:1200])
"; }

echo
echo "=== 会话卡片 ==="
curl -s "$BASE/conversations/cards" -H "Authorization: Bearer $TOK" | pp

echo
echo "=== 通讯录 ==="
curl -s "$BASE/contacts" -H "Authorization: Bearer $TOK" | pp

echo
echo "=== 我的文件 ==="
curl -s "$BASE/files/mine" -H "Authorization: Bearer $TOK" | pp

echo
echo "=== 隐私设置 ==="
curl -s "$BASE/users/me/privacy" -H "Authorization: Bearer $TOK" | pp

echo
echo "=== 好友申请（demo_bob 侧应有 alice 的历史记录）==="
TB=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"demo_bob\",\"password\":\"password123\",\"deviceId\":\"verify-b-$(date +%s)\",\"platform\":\"WEB\",\"deviceName\":\"verify\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
curl -s "$BASE/friends/requests" -H "Authorization: Bearer $TB" | pp
