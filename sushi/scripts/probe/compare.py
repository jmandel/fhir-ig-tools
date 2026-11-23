#!/usr/bin/env python3
"""Compare the TS and Java halves of the WP-8 lookup probe for one or more IGs.

usage: compare.py <probe-dir> <IG> [<IG> ...]   (reads <IG>.ts.ndjson, <IG>.java.ndjson, <IG>.ts.log)
Prints per IG: loaded packages (name#version, status) TS vs Java, the "Loaded ... with N resources" lines, and for the
recorded lookups: unique lookups, identical, different (winner = first row for findResourceInfo; full ordered list for
findResourceInfos), with the first differences. --json writes a machine-readable summary.
"""
import json
import re
import sys


def load(path):
    with open(path, encoding='utf-8') as f:
        return [json.loads(line) for line in f if line.strip()]


def qkey(e):
    return json.dumps([e['fn'], e['key'], e.get('opt')], sort_keys=True)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    as_json = '--json' in sys.argv
    pdir, igs = args[0], args[1:]
    summary = {}
    for ig in igs:
        ts = load(f'{pdir}/{ig}.ts.ndjson')
        jv = load(f'{pdir}/{ig}.java.ndjson')
        ts_loads = [(e['ev'], e['name'], e['version'], e.get('status')) for e in ts if e['ev'] in ('load', 'vload')]
        jv_loads = [(e['ev'], e['name'], e['version'], e.get('status')) for e in jv if e['ev'] in ('load', 'vload')]
        ts_q = {qkey(e): e for e in ts if e['ev'] == 'q'}
        jv_q = {qkey(e): e for e in jv if e['ev'] == 'q'}
        pat = r'(info|warn|error) +(Loaded|Resolved|Failed|Falling|Found|Downloaded|Cached|Attempting to download|.*already loaded)'
        jv_loaded = [re.sub(r' +', ' ', l['line']) for l in jv if l['ev'] == 'log' and re.match(pat, l['line'])]
        ts_loaded = []
        try:
            with open(f'{pdir}/../../sushi-spike/runs/{ig}-probe/sushi.log', encoding='utf-8') as f:
                for line in f:
                    line = re.sub(r'^ *[0-9.]+ ', '', line.rstrip('\n'))
                    if re.match(pat, line):
                        ts_loaded.append(re.sub(r' +', ' ', line))
        except OSError:
            ts_loaded = None
        same, diff, weighted_same, weighted_diff = 0, [], 0, 0
        for k, e in ts_q.items():
            j = jv_q.get(k)
            if j is not None and j['res'] == e['res']:
                same += 1
                weighted_same += e.get('n', 1)
            else:
                diff.append((e, j))
                weighted_diff += e.get('n', 1)
        s = {
            'loads_identical': ts_loads == jv_loads,
            'ts_loads': ts_loads, 'java_loads': jv_loads,
            'lookups': len(ts_q), 'identical': same, 'different': len(diff),
            'calls': weighted_same + weighted_diff, 'calls_identical': weighted_same,
            'diffs': [{'fn': e['fn'], 'key': e['key'], 'opt': e.get('opt'), 'n': e.get('n'), 'ts': e['res'],
                       'java': j['res'] if j else 'MISSING'} for e, j in diff],
            'java_load_log': jv_loaded, 'ts_load_log': ts_loaded,
            'load_log_identical': ts_loaded == jv_loaded,
        }
        summary[ig] = s
        if not as_json:
            print(f'== {ig}')
            print(f'  package loads identical: {s["loads_identical"]}')
            if not s['loads_identical']:
                for a, b in zip(ts_loads + [None] * 50, jv_loads + [None] * 50):
                    if a is None and b is None:
                        break
                    print(f'    TS {a}  |  Java {b}' + ('' if a == b else '   <==='))
            print(f'  package-loading log lines identical: {s["load_log_identical"]}')
            if not s['load_log_identical'] and ts_loaded is not None:
                import difflib
                for d in difflib.unified_diff(ts_loaded, jv_loaded, 'TS', 'Java', lineterm='', n=0):
                    print('    ' + d[:300])
            print(f'  lookups (unique): {s["lookups"]}, identical {same}, different {len(diff)};'
                  f' calls: {s["calls"]}, identical {weighted_same}')
            for d in s['diffs'][:15]:
                print(f'    {d["fn"]} {d["key"]!r} {d["opt"]} (x{d["n"]})')
                print(f'      TS:   {json.dumps(d["ts"])[:400]}')
                print(f'      Java: {json.dumps(d["java"])[:400]}')
    if as_json:
        json.dump(summary, sys.stdout, indent=1)


if __name__ == '__main__':
    main()
