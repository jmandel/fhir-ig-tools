#!/usr/bin/env python3
"""Recompute the log part of existing diff reports with the current classify-diff.py normalisation.
usage (in the image, VMDATA mounted): relog.py <classify-diff.py> <report.json>... (rewrites the reports' 'log')"""
import json, sys, difflib, types
src = open(sys.argv[1]).read().replace('\nmain()', '\n')
mod = types.ModuleType('cd'); exec(src, mod.__dict__)
for rp in sys.argv[2:]:
    r = json.load(open(rp)); ig = rp.rsplit('/', 1)[1][:-len('.json')]
    name, label = ig.rsplit('-', 1)
    ts = f'/srv/ig/sushi-spike/runs/{name}-{label}/sushi.log'
    import os
    if not os.path.exists(ts): ts = f'/srv/ig/sushi-spike/runs/{name}-ts-{label}/sushi.log'
    jv = f'/srv/ig/sushi-port/runs/{name}-{label}/sushi.log'
    if not (os.path.exists(ts) and os.path.exists(jv)): continue
    a, b = mod.log_lines(ts), mod.log_lines(jv)
    r['log'] = {'identical': a == b, 'diff': list(difflib.unified_diff(a, b, 'ts', 'java', n=0, lineterm=''))[:200]}
    json.dump(r, open(rp, 'w'), indent=1); open(rp, 'a').write('\n')
