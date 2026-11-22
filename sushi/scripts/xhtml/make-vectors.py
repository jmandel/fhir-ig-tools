# Build the committed JUnit vector sample from differential inputs + JS oracle results (deterministic selection).
# usage: python3 make-vectors.py out.json corpus fuzz1 fuzz4 ...   (base names in cwd: <b>.ndjson + <b>.js.out)
import json, sys
out = []
for b in sys.argv[2:]:
    ins = [l for l in open(b + '.ndjson', encoding='utf-8').read().split('\n') if l]
    res = [l for l in open(b + '.js.out', encoding='utf-8').read().split('\n') if l]
    step = 8 if b == 'corpus' else 150
    for i in range(0, len(ins), step):
        v = json.loads(ins[i]); r = json.loads(res[i])
        if len(v) > 4000: continue
        if r[1] is not None and ('<%' in v or '<?' in v or 'htmlmin:ignore' in v): continue  # random ids in message
        out.append([v] + r)
json.dump(out, open(sys.argv[1], 'w', encoding='utf-8'), ensure_ascii=True, indent=0)
print(len(out))
