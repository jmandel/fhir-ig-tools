#!/usr/bin/env python3
"""Generates jekyll/tests/kramdown/cases.jsonl (oracle format, type: kramdown).

Sources: kramdown-2.5.2 test/testcases/**/*.text (+ .options / dir 'options'), kramdown-parser-gfm-1.1.0
test/testcases, and targeted cases (targeted.md: cases separated by lines '=== <id> [config-json]').
Every upstream/targeted text is emitted with two configs: the upstream options (or kramdown's test
default {auto_ids: false, footnote_nr: 1}), and the typical IG config merged with those options.
"""
import glob, json, os, re, sys, yaml

ROOT = os.path.dirname(os.path.abspath(__file__))
SURF = os.path.abspath(os.path.join(ROOT, '..', '..', '..'))
IG = {"toc_levels": "1..3", "smart_quotes": ["apos", "apos", "quot", "quot"],
      "typographic_symbols": {"laquo": "<<", "raquo": ">>"}}
SKIP_OPTS = {'template', 'html_to_native'}  # not ported (see NOTES-kramdown.md)
# Outputs differ from Ruby only by Rouge token spans outside <pre class="highlight"><code> (inline
# code spans with a language, or a non-wrapping Rouge formatter). D7 drops token spans, and
# DiffRunner only strips them inside <pre class="highlight"><code>; these go to cases-d7.jsonl.
D7_SPAN_CASES = {'kd:block/06_codeblock/rouge/multiple', 'kd:span/03_codespan/highlighting',
                 'kd:span/03_codespan/rouge/simple', 't:codespan-lang-json'}


def norm(v):
    if isinstance(v, dict):
        return {str(k).lstrip(':'): norm(x) for k, x in v.items()}
    if isinstance(v, list):
        return [norm(x) for x in v]
    if isinstance(v, str) and v.startswith(':') and re.match(r'^:\w+$', v):
        return v[1:]
    return v


def load_opts(path):
    with open(path, encoding='utf-8') as f:
        d = yaml.safe_load(f.read()) or {}
    return norm(d)


def upstream(base, prefix, default):
    out = []
    for text_file in sorted(glob.glob(os.path.join(base, '**', '*.text'), recursive=True)):
        rel = os.path.relpath(text_file, base)[:-5]
        opts_file = text_file[:-5] + '.options'
        if not os.path.exists(opts_file):
            opts_file = os.path.join(os.path.dirname(text_file), 'options')
        opts = load_opts(opts_file) if os.path.exists(opts_file) else dict(default)
        if any(k in SKIP_OPTS for k in opts) or opts.get('syntax_highlighter') == 'minted':
            continue
        with open(text_file, encoding='utf-8') as f:
            text = f.read()
        out.append((prefix + rel, text, opts))
    return out


def targeted():
    out = []
    with open(os.path.join(ROOT, 'targeted.md'), encoding='utf-8') as f:
        data = f.read()
    for chunk in re.split(r'^=== ', data, flags=re.M)[1:]:
        head, _, body = chunk.partition('\n')
        parts = head.split(' ', 1)
        cid = parts[0]
        cfg = json.loads(parts[1]) if len(parts) > 1 else {}
        if cid == 'crlf-input':
            body = body.replace('\n', '\r\n')
        if cid == 'empty-input':
            body = ''
        out.append(('t:' + cid, body, cfg))
    return out


def main():
    cases = []
    d7 = []
    kd = os.path.join(SURF, 'third_party/kramdown/test/testcases')
    gfm = os.path.join(SURF, 'third_party/kramdown-parser-gfm/test/testcases')
    srcs = upstream(kd, 'kd:', {"auto_ids": False, "footnote_nr": 1}) + \
        upstream(gfm, 'gfm:', {"auto_ids": False, "footnote_nr": 1}) + targeted()
    for cid, text, opts in srcs:
        target = d7 if cid in D7_SPAN_CASES else cases
        target.append({"id": cid + "#opts", "type": "kramdown", "text": text, "config": opts})
        ig = dict(IG)
        ig.update({k: v for k, v in opts.items() if not (k == 'auto_ids' and v is False and cid.startswith(('kd:', 'gfm:')))})
        target.append({"id": cid + "#ig", "type": "kramdown", "text": text, "config": ig})
    # every Rouge lexer tag/alias: text effects of Rouge that survive span stripping
    rouge_keys = open(os.path.join(ROOT, 'rouge-registry.txt'), encoding='utf-8').read().split()
    tricky = 'a\tb  \n<x> & "q" \'s\' &amp; \\ \u00e9\U0001F600\n\n  trailing   \n\t\ttabs\n# comment // x /* y */ -- z\n'
    for k in rouge_keys:
        if k == 'stan':
            continue  # Rouge 4.7.0's stan lexer raises "unknown state: :string" on this text (lexers not ported, D7)
        cases.append({"id": "rouge:" + k, "type": "kramdown", "text": "```" + k + "\n" + tricky + "```\n\n~~~ " + k + "\nno trailing newline\n~~~\n", "config": IG})
    with open(os.path.join(ROOT, 'cases.jsonl'), 'w', encoding='utf-8') as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False) + '\n')
    with open(os.path.join(ROOT, 'cases-d7.jsonl'), 'w', encoding='utf-8') as f:
        for c in d7:
            f.write(json.dumps(c, ensure_ascii=False) + '\n')
    print(len(cases), 'cases;', len(d7), 'D7 span-only cases')


main()
