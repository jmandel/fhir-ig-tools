#!/usr/bin/env python3
"""Real front matter from work/golden/*/jekyll-in, repos/ and templates/ (Jekyll's YAML_FRONT_MATTER_REGEXP group 1 -> SafeYAML.load).
Writes jekyll/tests/yaml/frontmatter-cases.jsonl (distinct blocks only)."""
import json, os, re
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
# Ruby: /\A(---\s*\n.*?\n?)^((---|\.\.\.)\s*$\n?)/m  (Ruby /m = dotall; ^ $ are line anchors)
FM = re.compile(r'\A(---\s*\n.*?\n?)^((---|\.\.\.)\s*$\n?)', re.S | re.M)
seen = {}
bases = [os.path.join(ROOT, 'work', 'golden', ig, 'jekyll-in') for ig in sorted(os.listdir(os.path.join(ROOT, 'work', 'golden')))]
bases += [os.path.join(ROOT, d) for d in ('repos', 'templates')]
for base in bases:
    for dp, dn, fn in os.walk(base):
        dn[:] = [d for d in dn if not d.startswith(('_site', '.git', 'node_modules', 'output', 'temp', 'fsh-generated'))]
        for f in fn:
            if not f.endswith(('.html', '.md', '.markdown', '.xml', '.json')):
                continue
            p = os.path.join(dp, f)
            try:
                with open(p, 'rb') as fh:
                    head = fh.read(65536)
                if not head.startswith(b'---'):
                    continue
                text = open(p, encoding='utf-8').read()
            except (OSError, UnicodeDecodeError):
                continue
            m = FM.match(text)
            if m and m.group(1) not in seen:
                seen[m.group(1)] = os.path.relpath(p, ROOT)
out = os.path.join(ROOT, 'jekyll', 'tests', 'yaml', 'frontmatter-cases.jsonl')
with open(out, 'w') as f:
    for text, p in seen.items():
        f.write(json.dumps({'id': 'fm/' + p, 'type': 'yaml-typed', 'text': text}) + '\n')
print(len(seen), 'distinct front matter blocks ->', out)
