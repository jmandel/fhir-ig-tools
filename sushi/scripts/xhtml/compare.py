# usage: python3 compare.py inputs.ndjson js.ndjson java.ndjson [max-shown]  -> summary + first diffs
import json, re, sys
# html-minifier replaces <% %> / <? ?> fragments by '\t' + id + n + id + '\t' with a RANDOM id (Math.random); it can
# surface in "Parse Error: ..." texts. The Java port uses a deterministic id, so error texts are compared modulo the id.
UID = re.compile(r'\t([a-z][a-z0-9]*?)([0-9]+)\1\t')
IGN = re.compile(r'<!--[a-z][a-z0-9]*[0-9]-->')
def norm(line):
    r = json.loads(line)
    if r[1] is not None:
        r[1] = UID.sub(lambda m: '\tUID' + m.group(2) + 'UID\t', r[1])
        r[1] = IGN.sub('<!--UIDIGN-->', r[1])
    return r
inp = open(sys.argv[1], encoding='utf-8').read().split('\n')
a = open(sys.argv[2], encoding='utf-8').read().split('\n')
b = open(sys.argv[3], encoding='utf-8').read().split('\n')
mx = int(sys.argv[4]) if len(sys.argv) > 4 else 10
inp = [l for l in inp if l]; a = [l for l in a if l]; b = [l for l in b if l]
assert len(inp) == len(a) == len(b), (len(inp), len(a), len(b))
kinds = {}; shown = 0; n = len(inp)
stats = {'minify_ok': 0, 'minify_err': 0, 'sax_ok': 0, 'sax_err': 0, 'sax_throw': 0}
for i, (x, y, z) in enumerate(zip(inp, a, b)):
    ja = json.loads(y)
    stats['minify_ok' if ja[0] is not None else 'minify_err'] += 1
    stats['sax_throw' if ja[3] is not None else ('sax_ok' if ja[2] is None else 'sax_err')] += 1
    if y == z: continue
    ja, jb = norm(y), norm(z)
    if ja == jb: continue
    k = ','.join(['minify', 'minifyErr', 'sax', 'saxThrow'][j] for j in range(4) if ja[j] != jb[j])
    kinds[k] = kinds.get(k, 0) + 1
    if shown < mx:
        shown += 1
        print(f'--- #{i} differs in {k}\ninput: {x[:600]}\njs:    {y[:600]}\njava:  {z[:600]}')
print(json.dumps({'cases': n, 'diffs': sum(kinds.values()), 'by_kind': kinds, 'js_stats': stats}))
