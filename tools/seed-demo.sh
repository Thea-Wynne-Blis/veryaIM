#!/usr/bin/env bash
# ============================================================
# VERYAL 演示数据播种（不依赖关联数组）
#   账号：demo_alice / demo_bob / demo_carol / demo_dave / demo_erin
#   密码：password123
# ============================================================
set -uo pipefail

BASE="http://127.0.0.1:8899/api/v1"
TS=$(date +%s)
PASS="password123"
KEYS="alice bob carol dave erin"

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
say() { printf '\033[1;36m%s\033[0m\n' "$*"; }
ok()  { printf '  \033[1;32m✓\033[0m %s\n' "$*"; }
warn(){ printf '  \033[1;33m·\033[0m %s\n' "$*"; }

nick_of() {
  case "$1" in
    alice) echo "爱丽丝";; bob) echo "鲍勃";; carol) echo "卡萝";;
    dave) echo "戴维";; erin) echo "艾琳";;
  esac
}

tok() { eval "echo \${TOK_$1:-}"; }
uid() { eval "echo \${UIDV_$1:-}"; }

say "① 创建账号并登录"
for k in $KEYS; do
  nick=$(nick_of "$k")
  user="demo_$k"
  reg=$(curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$PASS\",\"nickname\":\"$nick\"}")
  login=""
  for attempt in 1 2 3; do
    login=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
      -d "{\"username\":\"$user\",\"password\":\"$PASS\",\"deviceId\":\"seed-$k-$TS-$attempt\",\"platform\":\"WEB\",\"deviceName\":\"$nick 的浏览器\"}")
    t=$(echo "$login" | jget "d['data']['token']")
    if [ -n "$t" ]; then
      u=$(echo "$login" | jget "d['data']['user']['userId']")
      eval "TOK_$k='$t'"
      eval "UIDV_$k='$u'"
      ok "$user ($nick) userId=$u"
      break
    fi
    warn "  $user 登录失败（第 $attempt 次），10s 后重试"
    sleep 10
  done
  [ -z "$(tok "$k")" ] && warn "$user 最终未登录成功"
  sleep 6
done

say "② 建立好友关系"
make_friend() {
  local from="$1" to="$2"
  local ft=$(tok "$from") tt=$(tok "$to") tu=$(uid "$to") fu=$(uid "$from")
  if [ -z "$ft" ] || [ -z "$tt" ]; then warn "$from ↔ $to 跳过（缺 token）"; return; fi
  curl -s -X POST "$BASE/friends/requests" -H "Authorization: Bearer $ft" \
    -H 'Content-Type: application/json' \
    -d "{\"toUserId\":$tu,\"message\":\"你好，我是 $(nick_of "$from")\"}" >/dev/null
  local rid
  rid=$(curl -s "$BASE/friends/requests" -H "Authorization: Bearer $tt" | python3 -c "
import sys,json
try:
    d = json.load(sys.stdin).get('data') or []
except Exception:
    d = []
c = [r for r in d if str(r.get('fromUserId')) == '$fu' and r.get('status') == 'PENDING']
print(c[0]['requestId'] if c else '')
" 2>/dev/null)
  if [ -n "$rid" ]; then
    curl -s -X POST "$BASE/friends/requests/$rid/accept" -H "Authorization: Bearer $tt" >/dev/null
    ok "$from ↔ $to 成为好友"
  else
    warn "$from ↔ $to 已存在关系或无需处理"
  fi
}
make_friend alice bob
make_friend alice carol
make_friend bob dave
make_friend carol erin
make_friend alice erin

say "③ 创建群聊「产品设计小组」"
TA=$(tok alice)
GID=""
if [ -n "$TA" ]; then
  GID=$(curl -s -X POST "$BASE/groups" -H "Authorization: Bearer $TA" \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"产品设计小组 $TS\",\"memberIds\":[$(uid bob),$(uid carol),$(uid dave)]}" | jget "d['data']['groupId']")
  [ -n "$GID" ] && ok "群已创建 groupId=$GID" || warn "群创建失败"
fi

send() {
  local who="$1" conv="$2" text="$3"
  local t=$(tok "$who")
  [ -z "$t" ] || [ -z "$conv" ] && return
  local body
  body=$(python3 -c 'import json,sys;print(json.dumps(sys.argv[1],ensure_ascii=False))' "$text")
  curl -s -X POST "$BASE/conversations/$conv/messages" -H "Authorization: Bearer $t" \
    -H 'Content-Type: application/json' \
    -d "{\"conversationId\":\"$conv\",\"clientMessageId\":\"s-$TS-$RANDOM\",\"type\":\"TEXT\",\"content\":$body}" >/dev/null
  sleep 0.3
}

private_conv() {
  local a="$1" b="$2"
  curl -s -X POST "$BASE/conversations" -H "Authorization: Bearer $(tok "$a")" \
    -H 'Content-Type: application/json' -d "{\"peerUserId\":$(uid "$b")}" | jget "d['data']['conversationId']"
}

say "④ 群聊消息"
if [ -n "$GID" ]; then
  send alice "$GID" "早上好，今天过一下新版会话列表的交互稿。"
  send bob   "$GID" "收到，我先看一版。"
  send carol "$GID" "我把移动端的窄屏状态也补上了。"
  send alice "$GID" "重点是留白和未读提示的位置，别太挤。"
  send dave  "$GID" "同意，列表一屏大概 10 条比较舒服。"
  send bob   "$GID" "那我把详情面板默认收起来，聊天区能宽一点。"
  ok "群消息 6 条"
fi

say "⑤ 私聊消息"
CV_AB=$(private_conv alice bob)
if [ -n "$CV_AB" ]; then
  send alice "$CV_AB" "这版会话列表我已经合进交互稿，今天一起过一下？"
  send bob   "$CV_AB" "好呀，我下午把稿子发你。"
  send bob   "$CV_AB" "顺便把深色模式也补一版。"
  send alice "$CV_AB" "太好了，深色模式记得对齐一下描边亮度。"
  ok "爱丽丝 ↔ 鲍勃 4 条（鲍勃侧未读）"
fi
CV_AC=$(private_conv alice carol)
if [ -n "$CV_AC" ]; then
  send carol "$CV_AC" "文件中心的列表要不要加个类型标签？"
  send alice "$CV_AC" "加，用 FILE/IMG/VID 这种小标签就行。"
  send carol "$CV_AC" "明白，我顺手把大小和时间也对齐成一列。"
  ok "爱丽丝 ↔ 卡萝 3 条"
fi

say "⑥ 演示文件"
echo "VERYAL 交互走查清单

1. 会话列表一屏 10 条
2. 气泡圆角按发送方收窄
3. 深色模式描边亮度对齐
4. 未读角标不遮挡时间" > /tmp/走查清单.md
printf 'version,size,latency_p99\nv1.0.0,86MB,42ms\n' > /tmp/perf.csv
for f in /tmp/走查清单.md /tmp/perf.csv; do
  n=$(curl -s -X POST "$BASE/files" -H "Authorization: Bearer $TA" -F "file=@$f" | jget "d['data']['name']")
  [ -n "$n" ] && ok "已上传 $n" || warn "上传失败 $f"
done

say "⑦ 汇总"
printf '\n\033[1;32m测试账号（密码统一 password123）\033[0m\n'
printf '  %-14s %-8s %s\n' "用户名" "昵称" "用户 ID"
for k in $KEYS; do
  printf '  %-14s %-8s %s\n' "demo_$k" "$(nick_of "$k")" "$(uid "$k")"
done
printf '\n  群聊: %s\n' "${GID:-（未创建）}"
