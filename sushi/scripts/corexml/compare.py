#!/usr/bin/env python3
"""Compare the port's (fhir-tool xmlToObj) and core's JSON for each predefined XML file as FHIR content.

usage: compare.py convert.ndjson out.json

Equivalence rules: key order ignored; numbers compared by value; the primitive + `_name` split is compared as is
(both sides are FHIR JSON); xhtml (`div`) compared after XML canonicalisation; "" / [] / {} / null removed
recursively (FHIR treats empty and absent alike). Every remaining difference is classified:
  comments          port-only `fhir_comments` (fhir-tool keeps XML comments; core drops them; not FHIR JSON since STU3);
                    removed from the port's JSON before the other comparisons
  string-ws         a string differs only in whitespace (XML attribute-value normalisation: a literal newline/tab in an
                    attribute is a space per XML 1.0 section 3.3.3; core applies it, sax as fhir-tool uses it does not)
  decimal-string    port has a decimal as a JSON string, core as a JSON number with the same value
  xhtml-ws          xhtml differs only in whitespace after canonicalisation
  xhtml             xhtml differs otherwise
  port-missing      element present in core's JSON, absent in the port's (fhir-tool's R4 type data has no such element)
  core-missing      element present in the port's JSON, absent in core's
  value             same element, different value
  cardinality       array on one side, single value on the other
Files where one side fails are classified by which side fails.
"""
import json, re, sys
from decimal import Decimal
from collections import Counter, defaultdict
import xml.etree.ElementTree as ET

def load(s):
    return json.loads(s, parse_float=Decimal, parse_int=Decimal)

def prune(v):
    if isinstance(v, dict):
        out = {}
        for k, x in v.items():
            x = prune(x)
            if x is None or x == "" or x == [] or x == {}:
                continue
            out[k] = x
        return out
    if isinstance(v, list):
        xs = [prune(x) for x in v]
        # keep positions (null placeholders matter for _x arrays), but an all-empty array is empty
        xs = [None if (x == "" or x == [] or x == {}) else x for x in xs]
        return [] if all(x is None for x in xs) else xs
    return v

def strip_comments(v):
    """remove fhir_comments recursively; returns (value, removed?)"""
    if isinstance(v, dict):
        hit = 'fhir_comments' in v
        out = {}
        for k, x in v.items():
            if k == 'fhir_comments':
                continue
            x, h = strip_comments(x); hit |= h; out[k] = x
        return out, hit
    if isinstance(v, list):
        hit = False; out = []
        for x in v:
            x, h = strip_comments(x); hit |= h; out.append(x)
        return out, hit
    return v, False

def c14n(s, ws):
    try:
        t = ET.canonicalize(s, strip_text=ws)
    except Exception:
        return None
    if ws:
        t = re.sub(r'\s+', ' ', t)
        t = re.sub(r'> <', '><', t)
    return t

def is_xhtml(path, a, b):
    return path.endswith('.div') and isinstance(a, str) and isinstance(b, str)

def diff(a, b, path, out):
    if isinstance(a, dict) and isinstance(b, dict):
        for k in sorted(set(a) | set(b)):
            p = path + '.' + k
            if k == 'fhir_comments' and k in a and k not in b:
                out.append(('comments', p, None, None)); continue
            if k not in a:
                out.append(('port-missing', p, None, b[k])); continue
            if k not in b:
                out.append(('core-missing', p, a[k], None)); continue
            diff(a[k], b[k], p, out)
        return
    if isinstance(a, list) and isinstance(b, list):
        for i in range(max(len(a), len(b))):
            x = a[i] if i < len(a) else None
            y = b[i] if i < len(b) else None
            if x is None and y is not None:
                out.append(('port-missing', path + '[]', None, y))
            elif y is None and x is not None:
                out.append(('core-missing', path + '[]', x, None))
            elif x is not None:
                diff(x, y, path + '[]', out)
        return
    if isinstance(a, list) != isinstance(b, list):
        out.append(('cardinality', path, a, b)); return
    if a == b:
        return
    if isinstance(a, str) and isinstance(b, Decimal):
        try:
            if Decimal(a) == b:
                out.append(('decimal-string', path, a, b)); return
        except Exception:
            pass
    if is_xhtml(path, a, b):
        if c14n(a, False) is not None and c14n(a, False) == c14n(b, False):
            return
        if c14n(a, True) is not None and c14n(a, True) == c14n(b, True):
            out.append(('xhtml-ws', path, a, b)); return
        out.append(('xhtml', path, a, b)); return
    if isinstance(a, str) and isinstance(b, str) and re.sub(r'\s+', ' ', a).strip() == re.sub(r'\s+', ' ', b).strip():
        out.append(('string-ws', path, a, b)); return
    out.append(('value', path, a, b))

def short(v, n=160):
    s = v if isinstance(v, str) else json.dumps(v, default=str, ensure_ascii=False)
    return s if len(s) <= n else s[:n] + '...'

def main():
    rows = [json.loads(l) for l in open(sys.argv[1], encoding='utf-8') if l.strip()]
    summary = Counter(); per_cat_files = defaultdict(set); per_ig = defaultdict(Counter); examples = defaultdict(list)
    path_counts = defaultdict(Counter); files = {}
    for r in rows:
        fid, ig = r['id'], r['id'].split('/')[0]
        pe, ce = r['port'].startswith('ERR:'), r['core'].startswith('ERR:')
        if pe and ce:
            cats = ['both-fail']
        elif pe:
            cats = ['port-fails']
        elif ce:
            cats = ['core-fails']
        else:
            out = []
            port, had_comments = strip_comments(load(r['port']))
            if had_comments:
                out.append(('comments', '', None, None))
            core = load(r['core'])
            diff(prune(port), prune(core), core.get('resourceType', ''), out)
            cats = sorted({c for c, *_ in out}) or ['equivalent']
            for c, p, a, b in out:
                path_counts[c][p] += 1
                if len(examples[c]) < 12 and not any(e['file'] == fid for e in examples[c]):
                    examples[c].append({'file': fid, 'path': p, 'port': short(a), 'core': short(b)})
        if cats[0] in ('both-fail', 'port-fails', 'core-fails'):
            examples[cats[0]].append({'file': fid, 'port': short(r['port'], 300) if pe else '', 'core': short(r['core'], 300) if ce else ''})
        files[fid] = {'baseline': r['baseline'], 'cats': cats, 'coreErrors': r.get('coreErrors', '')}
        for c in cats:
            per_cat_files[c].add(fid); per_ig[ig][c] += 1
        summary['files'] += 1
        strict = all(c in ('equivalent',) for c in cats)
        modulo = all(c in ('equivalent', 'comments', 'decimal-string', 'xhtml-ws', 'string-ws') for c in cats)
        summary['equivalent'] += strict
        summary['equivalent-but-representation'] += modulo and not strict
        summary['both-fail'] += cats == ['both-fail']
    res = {'summary': dict(summary), 'files_per_category': {c: len(v) for c, v in sorted(per_cat_files.items())},
           'per_ig': {ig: dict(c) for ig, c in sorted(per_ig.items())},
           'paths': {c: dict(v.most_common(40)) for c, v in path_counts.items()},
           'examples': examples, 'files': files}
    json.dump(res, open(sys.argv[2], 'w'), indent=1, ensure_ascii=False, default=str)
    print(json.dumps({'summary': res['summary'], 'files_per_category': res['files_per_category']}, indent=1))

main()
