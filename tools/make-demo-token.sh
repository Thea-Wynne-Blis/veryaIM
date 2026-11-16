#!/usr/bin/env bash
# 生成一次性登录引导页，并输出演示账号的 token（仅用于截图，用完即删）
set -uo pipefail

WEB=/home/ubuntu/veryal/web
BASE="http://127.0.0.1:8899/api/v1"

cat | sudo tee "$WEB/_boot.html" >/dev/null <<'HTML'
<!doctype html>
<meta charset="utf-8">
<title>boot</title>
<script>
const h = location.hash.slice(1).split('&');
const token = decodeURIComponent(h[0] || '');
const user = decodeURIComponent(h[1] || '');
const dest = decodeURIComponent(h[2] || '/app/chat');
if (token) {
  localStorage.setItem('veryal.token', token);
  localStorage.setItem('veryal.user', user);
}
location.replace(dest);
</script>
HTML

echo "BOOT_READY"

U="shot$(date +%s)"
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"password123\",\"nickname\":\"演示用户\"}" >/dev/null

curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$U\",\"password\":\"password123\",\"deviceId\":\"shot-$U\",\"platform\":\"WEB\",\"deviceName\":\"Demo\"}" \
  > /tmp/shot-login.json

python3 - <<'PY'
import json
d = json.load(open('/tmp/shot-login.json'))['data']
print('TOKEN=' + d['token'])
print('USER=' + json.dumps(d['user'], ensure_ascii=False, separators=(',', ':')))
PY
