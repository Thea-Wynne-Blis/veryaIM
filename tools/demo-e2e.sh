#!/usr/bin/env bash
# VERYAL 端到端业务验证：好友申请全流程 + 备注 + 文件中心 + 私聊消息
set -uo pipefail

BASE="http://127.0.0.1:8899/api/v1"
TS=$(date +%s)
UA="alice$TS"; UB="bob$TS"
PASS="password123"

j() { python3 -c "import sys,json;a=json.load(sys.stdin);print(json.dumps(a,ensure_ascii=False))" 2>/dev/null; }
field() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1" 2>/dev/null; }

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$*"; }
ok()   { printf '  \033[1;32m✓\033[0m %s\n' "$*"; }
bad()  { printf '  \033[1;31m✗\033[0m %s\n' "$*"; }

# ---------- 注册 ----------
step "1. 注册两个账号"
A=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UA\",\"password\":\"$PASS\",\"nickname\":\"爱丽丝\"}")
B=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UB\",\"password\":\"$PASS\",\"nickname\":\"鲍勃\"}")
AID=$(echo "$A" | field "['data']['userId']")
BID=$(echo "$B" | field "['data']['userId']")
echo "  爱丽丝 userId=$AID"
echo "  鲍勃   userId=$BID"

TA=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UA\",\"password\":\"$PASS\",\"deviceId\":\"web-a-$TS\",\"platform\":\"WEB\",\"deviceName\":\"Alice\"}" | field "['data']['token']")
TB=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$UB\",\"password\":\"$PASS\",\"deviceId\":\"web-b-$TS\",\"platform\":\"WEB\",\"deviceName\":\"Bob\"}" | field "['data']['token']")
[ -n "$TA" ] && [ -n "$TB" ] && ok "两人登录成功" || bad "登录失败"

# ---------- 搜索 ----------
step "2. 爱丽丝搜索「鲍勃」"
S=$(curl -s -G "$BASE/users/search" --data-urlencode "keyword=鲍勃" --data-urlencode "limit=10" -H "Authorization: Bearer $TA")
echo "  $(echo "$S" | j)"
echo "$S" | grep -q "鲍勃" && ok "搜索命中" || bad "搜索未命中"

# ---------- 好友申请 ----------
step "3. 爱丽丝向鲍勃发起好友申请"
R=$(curl -s -X POST "$BASE/friends/requests" -H "Authorization: Bearer $TA" \
  -H 'Content-Type: application/json' -d "{\"toUserId\":$BID,\"message\":\"我是爱丽丝\"}")
echo "  $(echo "$R" | j)"

step "4. 鲍勃查看收到的申请"
RB=$(curl -s "$BASE/friends/requests" -H "Authorization: Bearer $TB")
echo "  $(echo "$RB" | j)"
REQID=$(echo "$RB" | python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print(next((r['requestId'] for r in d if r['status']=='PENDING'),''))" 2>/dev/null)
[ -n "$REQID" ] && ok "申请已到达，requestId=$REQID" || bad "未看到申请"

step "5. 鲍勃同意申请"
curl -s -X POST "$BASE/friends/requests/$REQID/accept" -H "Authorization: Bearer $TB" | j

step "6. 爱丽丝的好友列表"
FA=$(curl -s "$BASE/friends" -H "Authorization: Bearer $TA")
echo "  $(echo "$FA" | j)"
echo "$FA" | grep -q "鲍勃" && ok "鲍勃已成为好友" || bad "好友列表不含鲍勃"

step "7. 爱丽丝给鲍勃设置备注「老鲍」"
curl -s -X PUT "$BASE/friends/$BID" -H "Authorization: Bearer $TA" \
  -H 'Content-Type: application/json' -d '{"remark":"老鲍"}' | j
FA2=$(curl -s "$BASE/friends" -H "Authorization: Bearer $TA")
echo "$FA2" | grep -q "老鲍" && ok "备注生效" || bad "备注未生效"

# ---------- 通讯录 ----------
step "8. 通讯录聚合"
C=$(curl -s "$BASE/contacts" -H "Authorization: Bearer $TA")
echo "  $(echo "$C" | j)"

# ---------- 文件中心 ----------
step "9. 上传文件并查看「我的文件」"
echo "VERYAL 文件中心测试 $(date)" > /tmp/demo.txt
FU=$(curl -s -X POST "$BASE/files" -H "Authorization: Bearer $TA" -F "file=@/tmp/demo.txt")
echo "  上传: $(echo "$FU" | j)"
MINE=$(curl -s "$BASE/files/mine" -H "Authorization: Bearer $TA")
echo "  我的文件: $(echo "$MINE" | j)"
echo "$MINE" | grep -q "demo.txt" && ok "文件中心返回了刚上传的文件" || bad "文件中心未返回"

# ---------- 会话与消息 ----------
step "10. 创建私聊并发送消息"
CV=$(curl -s -X POST "$BASE/conversations" -H "Authorization: Bearer $TA" \
  -H 'Content-Type: application/json' -d "{\"peerUserId\":$BID}")
CID=$(echo "$CV" | field "['data']['conversationId']")
echo "  会话 ID: $CID"
curl -s -X POST "$BASE/conversations/$CID/messages" -H "Authorization: Bearer $TA" \
  -H 'Content-Type: application/json' \
  -d "{\"conversationId\":\"$CID\",\"clientMessageId\":\"demo-$TS\",\"type\":\"TEXT\",\"content\":\"你好，这是端到端演示消息\"}" | j

step "11. 爱丽丝的会话卡片"
curl -s "$BASE/conversations/cards" -H "Authorization: Bearer $TA" | j

step "12. 鲍勃的会话卡片（应有 1 条未读）"
curl -s "$BASE/conversations/cards" -H "Authorization: Bearer $TB" | j

step "13. 鲍勃读取消息后未读清零"
curl -s -X POST "$BASE/conversations/$CID/read" -H "Authorization: Bearer $TB" \
  -H 'Content-Type: application/json' -d "{\"readSequence\":1}" >/dev/null
curl -s "$BASE/conversations/cards" -H "Authorization: Bearer $TB" | \
  python3 -c "import sys,json;d=json.load(sys.stdin)['data'];print('  未读数:',[c['unreadCount'] for c in d])"

printf '\n\033[1;32m演示账号（可直接在网页登录）\033[0m\n'
echo "  爱丽丝  $UA / $PASS"
echo "  鲍勃    $UB / $PASS"
