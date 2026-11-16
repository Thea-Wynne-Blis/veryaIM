#!/usr/bin/env bash
# 校验 OpenAPI 文档内容
set -uo pipefail

D=/tmp/docs.json
[ -f "$D" ] || { echo "缺少 $D"; exit 1; }

python3 - "$D" <<'PY'
import json, sys, collections

doc = json.load(open(sys.argv[1]))
paths = doc.get("paths", {})
ops = []
for path, item in paths.items():
    for method, op in item.items():
        if isinstance(op, dict):
            tags = op.get("tags") or ["(无标签)"]
            ops.append((tags[0], op.get("summary") or "", method.upper(), path))

print(f"接口总数      {len(ops)}")
print(f"标签数        {len(doc.get('tags', []))}")
print()

by_tag = collections.Counter(t for t, *_ in ops)
print("按标签分布：")
for tag, n in by_tag.most_common():
    print(f"  {tag:<12} {n}")
print()

no_summary = [o for o in ops if not o[1]]
print(f"缺少 summary  {len(no_summary)}")
for o in no_summary[:5]:
    print("   ", o)

schemas = doc.get("components", {}).get("schemas", {})
total_props = 0
described = 0
for name, schema in schemas.items():
    for prop, spec in (schema.get("properties") or {}).items():
        total_props += 1
        if spec.get("description"):
            described += 1
print()
print(f"Schema 数     {len(schemas)}")
print(f"字段总数      {total_props}")
print(f"有描述的字段  {described}  ({described * 100 // max(total_props,1)}%)")
print()
print("字段说明抽样：")
for name in ("ConversationCardResponse", "SendMessageRequest", "LoginRequest"):
    props = (schemas.get(name) or {}).get("properties") or {}
    if props:
        print(f"  {name}:")
        for k, v in list(props.items())[:4]:
            print(f"    {k:<22} {v.get('description','(无)')}")
PY
