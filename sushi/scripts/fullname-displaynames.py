#!/usr/bin/env python3
"""Rewrite JUnit @DisplayName values on @Test methods to the full Jest fullName from tests/inventory.tsv.

usage: fullname-displaynames.py <Java test file> <TS test file relative to test/, e.g. import/FSHErrorListener.test.ts>
A method's display name X maps to the inventory fullName F of that TS file when F == X or F ends with " " + X; if
several match, the enclosing @Nested classes' display names (describe titles) must all occur in F. Unresolved names
are reported and left alone. Already-full names are left alone."""
import re, sys, os
here = os.path.dirname(os.path.abspath(__file__))
java, ts = sys.argv[1], sys.argv[2]
inv = [l.split('\t') for l in open(os.path.join(here, '..', 'tests', 'inventory.tsv')).read().splitlines()[1:]]
names = [r[2] for r in inv if r[1] == ts]
src = open(java).read().split('\n')
out, stack, unresolved, changed = [], [], [], 0
depth = 0
pending_class_dn = None
for i, line in enumerate(src):
    m = re.search(r'@DisplayName\("((?:[^"\\]|\\.)*)"\)', line)
    target = ''
    if m:
        rest = line[m.end():].strip()
        j = i
        while not rest or rest.startswith('@'):
            if rest.startswith('@') and ('class ' in rest or 'void ' in rest):
                break
            j += 1
            if j >= len(src): break
            rest = src[j].strip()
        target = rest
    if m and re.search(r'\bclass\s+\w+', target):
        pending_class_dn = m.group(1)
    elif m and re.search(r'\bvoid\s+\w+\s*\(', target):
        x = m.group(1).replace('\\"', '"').replace('\\\\', '\\')
        if x not in names:
            cands = [f for f in names if f.endswith(' ' + x)]
            if len(cands) > 1:
                ctx = [c for c, _ in stack if c]
                cands = [f for f in cands if all((' ' + c + ' ') in (' ' + f + ' ') for c in ctx)]
            if len(cands) == 1:
                full = cands[0].replace('\\', '\\\\').replace('"', '\\"')
                line = line[:m.start(1)] + full + line[m.end(1):]
                changed += 1
            else:
                unresolved.append((i + 1, x, len(cands)))
    if re.search(r'\bclass\s+\w+', line):
        stack.append((pending_class_dn, depth)); pending_class_dn = None
    depth += line.count('{') - line.count('}')
    while stack and depth <= stack[-1][1]:
        stack.pop()
    out.append(line)
open(java, 'w').write('\n'.join(out))
print(f'{java}: {changed} renamed, {len(unresolved)} unresolved')
for u in unresolved[:20]: print('  line %d: %r (%d candidates)' % u)
