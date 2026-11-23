#!/usr/bin/env python3
"""Per-lexer Rouge suite: Rouge's demo files and spec/visual/samples for every ported lexer, as
"rouge" oracle cases (see jekyll/oracle/oracle.rb), plus line/prefix slices and simple mutations.

usage: gen_cases.py > cases.jsonl   (then jekyll/oracle/oracle.sh cases.jsonl expected.jsonl)"""
import json, os, random, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
DEMOS = os.path.join(ROOT, 'third_party/rouge/lib/rouge/demos')
SAMPLES = os.path.join(ROOT, 'third_party/rouge/spec/visual/samples')
# tag -> extra language labels seen in IGs that resolve to it
LEXERS = ['plaintext', 'json', 'json-doc', 'json5', 'xml', 'html', 'css', 'javascript', 'typescript', 'http',
          'shell', 'batchfile', 'sql', 'python', 'java', 'yaml', 'markdown', 'diff', 'sparql', 'turtle',
          'properties', 'actionscript', 'ruby']
rnd = random.Random(int(sys.argv[1]) if len(sys.argv) > 1 else 1)
out = []
def add(id_, lang, text, fmt='html', **kw):
    c = {'id': id_, 'type': 'rouge', 'lang': lang, 'text': text, 'formatter': fmt}
    c.update(kw)
    out.append(c)
def read(p):
    with open(p, encoding='utf-8') as f:
        return f.read()
for tag in LEXERS:
    texts = []
    for d, kind in ((DEMOS, 'demo'), (SAMPLES, 'sample')):
        p = os.path.join(d, tag)
        if os.path.exists(p):
            texts.append((kind, read(p)))
    for kind, t in texts:
        add(f'{tag}/{kind}', tag, t)
        add(f'{tag}/{kind}/legacy', tag, t, 'html_legacy')
        lines = t.split('\n')
        # line windows
        for k in range(8):
            a = rnd.randrange(len(lines)); b = min(len(lines), a + rnd.randrange(1, 12))
            add(f'{tag}/{kind}/win{k}', tag, '\n'.join(lines[a:b]))
        # prefixes / random cuts (unterminated constructs)
        for k in range(6):
            n = rnd.randrange(len(t) + 1)
            add(f'{tag}/{kind}/cut{k}', tag, t[:n])
        # each line alone
        for k, ln in enumerate(lines[:400]):
            if ln.strip():
                add(f'{tag}/{kind}/line{k}', tag, ln + '\n')
    for k, (kind, t) in enumerate(texts[:1]):
        add(f'{tag}/{kind}/table', tag, t, 'html_table')
        add(f'{tag}/{kind}/mark', tag, t, 'html', mark_lines=[1, 3])
        add(f'{tag}/{kind}/tablemark', tag, t, 'html_table', mark_lines=[2])
for c in out:
    print(json.dumps(c, ensure_ascii=False))
