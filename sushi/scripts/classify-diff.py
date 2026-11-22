#!/usr/bin/env python3
"""Classified byte-level diff of two fsh-generated/ trees (TS SUSHI reference vs Java port) and two SUSHI logs.

usage: classify-diff.py <ref-dir> <java-dir> [<ref-log> <java-log>] > report.json   (also prints a summary to stderr)
Classes per file: identical | missing (only in ref) | extra (only in java) | json-format (same JSON value and key
order, bytes differ: whitespace/number/escape formatting) | json-key-order (same value, different key order) |
json-value (values differ; first differing paths listed) | text (non-JSON files differ; first differing line).
Logs: SUSHI console lines compared after dropping the run-specific lines (timing, version banner, pun table art).
"""
import json, os, sys, hashlib
def files(root):
    out = {}
    for d, _, fs in os.walk(root):
        for f in fs:
            p = os.path.join(d, f); out[os.path.relpath(p, root)] = p
    return out
def ordered(v):  # JSON value keeping key order, for order-sensitive compare
    return json.loads(v, object_pairs_hook=lambda kv: ('{}', kv))
def paths(a, b, p='$', out=None, limit=8):
    out = [] if out is None else out
    if len(out) >= limit: return out
    if type(a) != type(b): out.append(p); return out
    if isinstance(a, dict):
        for k in list(dict.fromkeys(list(a) + list(b))):
            if k not in a or k not in b: out.append(f'{p}.{k}')
            else: paths(a[k], b[k], f'{p}.{k}', out, limit)
    elif isinstance(a, list):
        if len(a) != len(b): out.append(f'{p}[len {len(a)} vs {len(b)}]')
        for i, (x, y) in enumerate(zip(a, b)): paths(x, y, f'{p}[{i}]', out, limit)
    elif a != b: out.append(p)
    return out
def classify(r, j):
    br, bj = open(r, 'rb').read(), open(j, 'rb').read()
    if br == bj: return 'identical', None
    if r.endswith('.json'):
        try:
            vr, vj = json.loads(br), json.loads(bj)
        except ValueError:
            return 'text', 'unparseable JSON'
        if vr == vj:
            return ('json-format' if ordered(br) == ordered(bj) else 'json-key-order'), None
        return 'json-value', paths(vr, vj)
    lr, lj = br.decode('utf-8', 'replace').splitlines(), bj.decode('utf-8', 'replace').splitlines()
    for i, (x, y) in enumerate(zip(lr, lj)):
        if x != y: return 'text', f'line {i + 1}: {x[:120]!r} vs {y[:120]!r}'
    return 'text', f'length {len(lr)} vs {len(lj)} lines'
SKIP = ('Elapsed time', 'SUSHI v', 'version of SUSHI', '╔', '╚', '║')
def log_lines(p):
    return [l.rstrip() for l in open(p, encoding='utf-8', errors='replace') if not any(s in l for s in SKIP)]
def main():
    ref, jav = files(sys.argv[1]), files(sys.argv[2])
    rep = {'files': {}, 'summary': {}}
    for k in sorted(set(ref) | set(jav)):
        if k not in jav: c, d = 'missing', None
        elif k not in ref: c, d = 'extra', None
        else: c, d = classify(ref[k], jav[k])
        rep['summary'][c] = rep['summary'].get(c, 0) + 1
        if c != 'identical': rep['files'][k] = {'class': c, 'detail': d}
    if len(sys.argv) > 4:
        a, b = log_lines(sys.argv[3]), log_lines(sys.argv[4])
        import difflib
        rep['log'] = {'identical': a == b, 'diff': list(difflib.unified_diff(a, b, 'ts', 'java', n=0, lineterm=''))[:200]}
    json.dump(rep, sys.stdout, indent=1); print()
    print(' '.join(f'{k}={v}' for k, v in sorted(rep['summary'].items())) +
          ('' if 'log' not in rep else f" log={'same' if rep['log']['identical'] else 'differs'}"), file=sys.stderr)
main()
