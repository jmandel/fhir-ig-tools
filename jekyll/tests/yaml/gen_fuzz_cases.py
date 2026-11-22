#!/usr/bin/env python3
"""Differential fuzz cases for the YAML port -> <out>.jsonl (yaml-typed / yaml-typed-file).

usage: gen_fuzz_cases.py <seed> <count> <out.jsonl> [--files DIR]

Three generators, mixed:
  * grammar: random YAML documents (block/flow collections, all scalar styles, anchors/aliases,
    tags, merge keys, comments, directives/document markers, odd indentation and whitespace),
    with scalars drawn from typing-relevant shapes (numbers, dates, times, bools, nulls);
  * mutate: character-level mutations of the real configs/data in jekyll/tests/site/yaml-cases.jsonl
    and small real _data files (insert/delete/replace YAML-significant characters, re-indent,
    duplicate/drop/swap lines, truncate);
  * bytes: (with --files) byte-level mutations written to files and loaded with load_file
    (invalid UTF-8, BOMs, NULs, control characters, chunk-boundary effects).
"""
import json, os, random, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
seed, count, out = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
files_dir = None
if '--files' in sys.argv:
    files_dir = os.path.abspath(sys.argv[sys.argv.index('--files') + 1])
R = random.Random(seed)

SCALAR_SHAPES = [
    lambda: str(R.randint(-10**6, 10**6)), lambda: str(R.randint(0, 10**30)), lambda: '0x%X' % R.randint(0, 10**9),
    lambda: '0%o' % R.randint(0, 4096), lambda: '0b' + bin(R.randint(0, 255))[2:], lambda: '%d_%03d' % (R.randint(1, 999), R.randint(0, 999)),
    lambda: '%d,%03d' % (R.randint(1, 999), R.randint(0, 999)), lambda: '%d:%02d' % (R.randint(0, 200), R.randint(0, 59)),
    lambda: '%d:%02d:%02d.%d' % (R.randint(0, 99), R.randint(0, 59), R.randint(0, 59), R.randint(0, 99)),
    lambda: repr(R.uniform(-1e6, 1e6)), lambda: '%.*f' % (R.randint(0, 6), R.uniform(-100, 100)), lambda: '%de%+d' % (R.randint(1, 9), R.randint(-400, 400)),
    lambda: '%d.%de%+d' % (R.randint(0, 9), R.randint(0, 99), R.randint(-400, 400)), lambda: R.choice(['.inf', '-.Inf', '.NaN', '.nan', '+.inf', '.INF']),
    lambda: '.%d' % R.randint(0, 999), lambda: R.choice(['1_.5', '._5', '1.5_', '1__0.5', '0_0.5', '-.5', '+.5', '1.', '00.5']),
    lambda: R.choice(['~', 'null', 'Null', 'NULL', '', 'nil', 'None']),
    lambda: R.choice(['yes', 'No', 'ON', 'off', 'true', 'False', 'y', 'n', 'TRUE', 'oN']),
    lambda: '%04d-%02d-%02d' % (R.randint(0, 3000), R.randint(0, 13), R.randint(0, 32)),
    lambda: '%04d-%d-%d%s%d:%02d:%02d%s%s' % (R.randint(1500, 2100), R.randint(1, 12), R.randint(1, 31), R.choice(['T', 't', ' ', '  ']),
                                               R.randint(0, 25), R.randint(0, 61), R.randint(0, 61),
                                               R.choice(['', '.', '.%d' % R.randint(0, 10**9)]),
                                               R.choice(['', 'Z', ' Z', '+05:30', '-5', ' +0100', '-12:00', '+1', '+530', ' -0000'])),
    lambda: R.choice(['abc', 'a b', 'x y z', '<<', '-', '=', 'é', '日本語', '😀', 'http://example.org/a#b', 'a:b', 'a,b', 'a]b', '@x', '%x', '`x']),
    lambda: R.choice(['hello world', 'Some Title', 'value-1', 'v1.2.3', 'R4', 'STU3', 'fhir.base.template#current', '{{ site.x }}']),
]
WORDS = ['a', 'b', 'key', 'name', 'id', 'url', 'version', 'title', 'x', 'y', 'z', '1', 'true', '~', '<<', 'k k', '2020-01-01', '1.5']


def scalar_text():
    return R.choice(SCALAR_SHAPES)()


def quote(s, style):
    if style == 'single':
        return "'" + s.replace("'", "''") + "'"
    if style == 'double':
        esc = s.replace('\\', '\\\\').replace('"', '\\"')
        if R.random() < 0.2:
            esc += R.choice(['\\n', '\\t', '\\x41', '\\u00e9', '\\U0001F600', '\\ ', '\\_', '\\N', '\\0', '\\/', '\\e', '\\q', '\\\n  '])
        return '"' + esc + '"'
    return s


def node_props():
    p = []
    if R.random() < 0.12:
        p.append('&' + R.choice(['a', 'b', 'c', 'x1', 'anc']))
    if R.random() < 0.1:
        p.append(R.choice(['!!str', '!!int', '!!binary', '!binary', '!', '!foo', '!<tag:yaml.org,2002:str>', '!!map', '!!seq', '!e!x']))
    if len(p) == 2 and R.random() < 0.5:
        p.reverse()
    return ' '.join(p)


def flow_node(depth):
    r = R.random()
    if depth > 3 or r < 0.55:
        if R.random() < 0.08:
            return '*' + R.choice(['a', 'b', 'c', 'x1', 'anc', 'nope'])
        s = scalar_text()
        style = R.choice(['plain'] * 4 + ['single', 'double'])
        props = node_props()
        return (props + ' ' if props else '') + quote(s, style)
    if r < 0.78:
        items = [flow_node(depth + 1) for _ in range(R.randint(0, 4))]
        sep = R.choice([', ', ',', ' , ', ',\n  '])
        return '[' + sep.join(items) + R.choice(['', ',', ' ']) + ']'
    items = []
    for _ in range(R.randint(0, 4)):
        k = flow_node(depth + 2) if R.random() < 0.2 else R.choice(WORDS)
        if R.random() < 0.1:
            items.append(k)
        else:
            items.append(k + R.choice([': ', ':  ', ' : ']) + flow_node(depth + 1))
    return '{' + R.choice([', ', ',']).join(items) + '}'


def block_scalar(indent):
    hdr = R.choice(['|', '>']) + R.choice(['', '-', '+']) + R.choice(['', '', '1', '2'])
    if R.random() < 0.1:
        hdr = R.choice(['|0', '|-+', '>2-', '|+1', '| # c', '|x'])
    lines = []
    for _ in range(R.randint(0, 4)):
        lines.append(' ' * (indent + 2 + R.choice([0, 0, 0, 1, 2])) + R.choice(['text', 'more text', '', 'x: y', '- z', '#nc', '  sp']))
    for _ in range(R.randint(0, 2)):
        lines.append('')
    return hdr + '\n' + '\n'.join(lines)


def block_node(indent, depth):
    r = R.random()
    pad = ' ' * indent
    if depth > 4 or r < 0.4:
        if R.random() < 0.15:
            return ' ' + block_scalar(indent)
        if R.random() < 0.15:
            return ' ' + flow_node(depth)
        if R.random() < 0.06:
            return ' *' + R.choice(['a', 'b', 'c', 'nope'])
        props = node_props()
        s = quote(scalar_text(), R.choice(['plain'] * 5 + ['single', 'double']))
        if R.random() < 0.05:
            s += '\n' + pad + '  ' + scalar_text()
        if R.random() < 0.1:
            s += R.choice([' # comment', '  #c', ' #'])
        return ' ' + (props + ' ' if props else '') + s
    props = node_props()
    head = (' ' + props) if props else ''
    child = indent + R.choice([2, 2, 2, 4, 1, 3])
    if R.random() < 0.06:
        child = indent  # indentless sequences / errors
    cpad = ' ' * child
    lines = []
    if r < 0.7:
        for _ in range(R.randint(1, 4)):
            k = R.choice(WORDS)
            if R.random() < 0.1:
                k = quote(k, R.choice(['single', 'double']))
            if R.random() < 0.05:
                lines.append(cpad + '? ' + k)
                lines.append(cpad + ':' + block_node(child, depth + 1).lstrip('\n'))
                continue
            if R.random() < 0.06:
                lines.append(cpad + '<<: ' + R.choice(['*a', '*b', '{p: 1}', '[*a, *b]', '1']))
                continue
            lines.append(cpad + k + ':' + block_node(child, depth + 1))
    else:
        for _ in range(R.randint(1, 4)):
            lines.append(cpad + '-' + block_node(child, depth + 1))
    if R.random() < 0.1:
        lines.insert(R.randint(0, len(lines)), R.choice(['', '# comment', cpad + '# c', '   ', '\t']))
    return head + '\n' + '\n'.join(lines)


def grammar_doc():
    parts = []
    if R.random() < 0.1:
        parts.append(R.choice(['%YAML 1.1', '%YAML 1.2', '%TAG !e! tag:example.com,2000:', '%YAML 1.3', '%FOO x']))
    if R.random() < 0.3 or parts:
        parts.append(R.choice(['---', '--- ', '--- # c']))
    body = block_node(-2, 0) if R.random() < 0.8 else ' ' + flow_node(0)
    body = body.lstrip('\n')
    if body.startswith(' ') and not (parts and parts[-1].startswith('---')):
        body = body.lstrip(' ')
    if parts and parts[-1].startswith('---') and R.random() < 0.3 and not body.startswith((' ', '\n')):
        parts[-1] = parts[-1] + ' '
    parts.append(body)
    if R.random() < 0.15:
        parts.append(R.choice(['...', '---', '--- x', '...\n--- [', '---\n:']))
    text = '\n'.join(parts) + R.choice(['\n', '', '\n\n'])
    if R.random() < 0.05:
        text = text.replace('\n', R.choice(['\r\n', '\r']))
    if R.random() < 0.03:
        text = '﻿' + text
    return text


# ---- mutations of real files ----
real = []
for line in open(os.path.join(ROOT, 'jekyll', 'tests', 'site', 'yaml-cases.jsonl'), encoding='utf-8'):
    c = json.loads(line)
    if len(c['text']) < 20000:
        real.append(c['text'])
for line in open(os.path.join(ROOT, 'jekyll', 'tests', 'site', 'data-cases.jsonl'), encoding='utf-8'):
    c = json.loads(line)
    p = os.path.join(ROOT, c['host_path'])
    try:
        if os.path.getsize(p) < 6000:
            real.append(open(p, encoding='utf-8').read())
    except (OSError, UnicodeDecodeError):
        pass
ALPHA = list(':-?,[]{}#&*!|>\'"%@` \t\n.~0123456789aZ_+=\\') + ['\r\n', ' ', '\x85', '﻿', '  ', '- ', ': ', '---\n', '...\n', '<<: ', '&a ', '*a', '!!binary ', '! ']


def mutate(text):
    if not text:
        return text
    for _ in range(R.randint(1, 4)):
        op = R.random()
        if op < 0.3:
            i = R.randint(0, len(text))
            text = text[:i] + R.choice(ALPHA) + text[i:]
        elif op < 0.5:
            i = R.randint(0, max(0, len(text) - 1))
            text = text[:i] + text[i + R.randint(1, 3):]
        elif op < 0.65:
            i = R.randint(0, max(0, len(text) - 1))
            text = text[:i] + R.choice(ALPHA) + text[i + 1:]
        elif op < 0.8:
            lines = text.split('\n')
            j = R.randrange(len(lines))
            act = R.random()
            if act < 0.3:
                lines[j] = ' ' * R.randint(0, 3) + lines[j]
            elif act < 0.5:
                lines[j] = lines[j].lstrip(' ')
            elif act < 0.7:
                lines.insert(j, lines[j])
            elif act < 0.85:
                del lines[j]
            else:
                k = R.randrange(len(lines))
                lines[j], lines[k] = lines[k], lines[j]
            text = '\n'.join(lines)
        elif op < 0.9:
            text = text[:R.randint(0, len(text))]
        else:
            # retype a scalar-looking token
            i = R.randint(0, len(text))
            text = text[:i] + scalar_text() + text[i:]
    return text


def byte_mutate(data):
    data = bytearray(data)
    for _ in range(R.randint(1, 3)):
        op = R.random()
        i = R.randint(0, len(data))
        if op < 0.4:
            data[i:i] = bytes([R.choice([0xff, 0xfe, 0xc0, 0xc3, 0xe2, 0xed, 0xf4, 0xf8, 0x80, 0x00, 0x01, 0x7f, 0x85, 0x0b])])
        elif op < 0.6 and data:
            data[min(i, len(data) - 1)] = R.randint(0, 255)
        elif op < 0.75:
            data[0:0] = R.choice([b'\xef\xbb\xbf', b'\xef\xbb', b'\xff\xfe', b'\xfe\xff'])
        elif op < 0.9:
            data[i:i] = ' \x85é😀'.encode()[R.randint(0, 8):R.randint(9, 13)]
        else:
            data = data[:R.randint(0, len(data))]
    return bytes(data)


SOUP = ['a', 'b', 'k', '1', '0x1', '1.5', '~', 'yes', '2020-01-01', '12:30', ' ', '  ', '\t', '\n', '\n  ', '\n- ', ':', ': ',
        '-', '- ', '?', '? ', ',', '[', ']', '{', '}', '#', ' #c', '&a', '&b ', '*a', '*b', '!', '!!str ', '!foo ', '!<x> ',
        '|', '>', '|-', '>+2', "'", "''", '"', '\\', '\\n', '%YAML 1.1\n', '%TAG ! !x\n', '---', '--- ', '...', '\n---\n',
        '\n...\n', '<<', '<<: ', '@', '`', '%', '\r\n', '\r', '\u2028', '\x85', '\ufeff', '\x01', '\u00e9', '\U0001F600', '\x00']


def soup():
    return ''.join(R.choice(SOUP) for _ in range(R.randint(1, 40)))


def big_late_control():
    head = grammar_doc() if R.random() < 0.5 else 'a: 1\n'
    sep = R.choice(['---\n', '...\n', '', '--- |\n  x\n'])
    filler = '#' + 'x' * R.randint(16000, 50000) + '\n'
    tail = R.choice(['\x01', 'b: \x7f\n', '\x00', 'c: [\n', 'd: "\x1b"\n'])
    return head + R.choice([filler + sep, sep + filler]) + tail


n = 0
with open(out, 'w', encoding='utf-8') as f:
    for k in range(count):
        r = R.random()
        cid = 'fuzz/%d/%d' % (seed, k)
        if files_dir and r < 0.08:
            src = R.choice(real) if R.random() < 0.6 else grammar_doc()
            data = byte_mutate(src.encode('utf-8'))
            if R.random() < 0.05:
                data = data + b'\n#' + b'x' * R.randint(16000, 17000) + b'\n' + byte_mutate(b'b: 1\n---\nc: 2\n')
            os.makedirs(files_dir, exist_ok=True)
            p = os.path.join(files_dir, '%d_%d.yml' % (seed, k))
            open(p, 'wb').write(data)
            f.write(json.dumps({'id': cid + '/bytes', 'type': 'yaml-typed-file', 'path': '/w/' + os.path.relpath(p, ROOT)}) + '\n')
        elif r < 0.12:
            f.write(json.dumps({'id': cid + '/soup', 'type': 'yaml-typed', 'text': soup()}) + '\n')
        elif r < 0.125:
            f.write(json.dumps({'id': cid + '/big', 'type': 'yaml-typed', 'text': big_late_control()}) + '\n')
        elif r < 0.55:
            f.write(json.dumps({'id': cid + '/grammar', 'type': 'yaml-typed', 'text': grammar_doc()}) + '\n')
        else:
            f.write(json.dumps({'id': cid + '/mutate', 'type': 'yaml-typed', 'text': mutate(R.choice(real))}) + '\n')
        n += 1
print(n, 'cases ->', out)
