#!/usr/bin/env python3
"""Build yaml-typed cases from yaml-test-suite (third_party/yaml-test-suite, release data-2022-01-17).

Each in.yaml becomes two cases: "yaml-typed" (SafeYAML.load of the text) and "yaml-typed-file"
(SafeYAML.load_file of the file itself, raw bytes). Run from anywhere; writes jekyll/tests/yaml/suite-cases.jsonl.
"""
import json, os, sys
root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
suite = os.path.join(root, 'src', 'yaml-test-suite')
out = os.path.join(root, 'jekyll', 'tests', 'yaml', 'suite-cases.jsonl')
n = 0
with open(out, 'w') as f:
    for dirpath, dirs, files in sorted(os.walk(suite)):
        dirs.sort()
        if 'in.yaml' not in files:
            continue
        rel = os.path.relpath(dirpath, suite)
        p = os.path.join(dirpath, 'in.yaml')
        raw = open(p, 'rb').read()
        name = open(os.path.join(dirpath, '===')).read().strip() if os.path.exists(os.path.join(dirpath, '===')) else ''
        try:
            text = raw.decode('utf-8')
            f.write(json.dumps({'id': 'suite/' + rel + '/text', 'type': 'yaml-typed', 'text': text, 'name': name}) + '\n'); n += 1
        except UnicodeDecodeError:
            pass
        f.write(json.dumps({'id': 'suite/' + rel + '/file', 'type': 'yaml-typed-file', 'path': '/w/' + os.path.relpath(p, root), 'name': name}) + '\n'); n += 1
print(n, 'cases ->', out)
