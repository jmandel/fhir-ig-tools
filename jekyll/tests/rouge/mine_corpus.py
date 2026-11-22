#!/usr/bin/env python3
"""Mines highlighted code fragments from the cloned IG sources (repos/*): Markdown fences
(``` / ~~~ with an info string) and Jekyll {% highlight lang %} blocks under input/, as "rouge" oracle cases
(fences -> html_legacy as kramdown formats them, highlight -> html as Jekyll's tag does).

usage: mine_corpus.py <out-cases.jsonl> [max-per-lang]"""
import collections, hashlib, json, os, re, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
out_path = sys.argv[1]
cap = int(sys.argv[2]) if len(sys.argv) > 2 else 400
FENCE = re.compile(r'^([ \t]*)(`{3,}|~{3,})[ \t]*([^\n`]*?)[ \t]*\n(.*?)\n[ \t]*\2[ \t]*$', re.S | re.M)
HL = re.compile(r'\{%-?\s*highlight\s+([a-zA-Z0-9.+#_-]+)[^%]*?-?%\}(.*?)\{%-?\s*endhighlight\s*-?%\}', re.S)
seen = set()
per = collections.Counter()
cases = []
def add(lang, text, fmt, src):
    lang = lang.strip()
    key = hashlib.sha1((lang + '\0' + fmt + '\0' + text).encode()).hexdigest()
    if key in seen:
        return
    seen.add(key)
    per[lang.lower()] += 1
    if per[lang.lower()] > cap:
        return
    cases.append({'id': f'mined/{lang}/{key[:12]}', 'type': 'rouge', 'lang': lang, 'text': text, 'formatter': fmt, 'src': src})
for repo in sorted(os.listdir(os.path.join(ROOT, 'repos'))):
    base = os.path.join(ROOT, 'repos', repo)
    for dp, dn, fn in os.walk(base):
        dn[:] = [d for d in dn if d not in ('.git', 'output', 'temp', 'fsh-generated', 'node_modules', 'template')]
        for f in fn:
            if not f.endswith(('.md', '.html', '.liquid', '.xml')):
                continue
            p = os.path.join(dp, f)
            try:
                if os.path.getsize(p) > 3_000_000:
                    continue
                s = open(p, encoding='utf-8').read()
            except Exception:
                continue
            rel = os.path.relpath(p, ROOT)
            if '/input/' not in '/' + rel:  # only what the IG Publisher hands to Jekyll
                continue
            if f.endswith('.md'):
                for m in FENCE.finditer(s):
                    info = m.group(3).split()
                    if info:
                        add(info[0], m.group(4) + '\n', 'html_legacy', rel)
            for m in HL.finditer(s):
                code = re.sub(r'\A[\n\r]+|[\n\r]+\Z', '', m.group(2))
                add(m.group(1).lower(), code, 'html', rel)
with open(out_path, 'w') as o:
    for c in cases:
        o.write(json.dumps(c, ensure_ascii=False) + '\n')
print(len(cases), 'cases;', sum(per.values()), 'distinct fragments')
for k, v in per.most_common(80):
    print(f'{v:6d} {k}')
