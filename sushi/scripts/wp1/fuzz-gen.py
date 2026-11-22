#!/usr/bin/env python3
"""Generate mutated FSH (or MiniFSH) inputs for the parser differential test (WP-1).

usage: fuzz-gen.py <fsh|mini> <out dir> <count> <seed> <seed file list...>
  seed file list: files with one seed input path per line (corpus FSH files, captured Jest inputs).
Writes <out>/<NNN>/<NNNNNNN>.<fsh|mini>. Deterministic for a given seed and seed list.
Cases: windows of real FSH (often under a synthetic entity header) with 1-4 mutations (token/char/line level), and
"token soup" made of FSH lexemes; MiniFSH cases are RuleSet bodies as MiniFSH sees them ("\n* ..." lines).
"""
import os, random, re, sys

VOCAB = [
    'Alias:', 'Profile:', 'Extension:', 'Instance:', 'InstanceOf:', 'Invariant:', 'ValueSet:', 'CodeSystem:',
    'RuleSet:', 'Mapping:', 'Logical:', 'Resource:', 'Parent:', 'Id:', 'Title:', 'Description:', 'Expression:',
    'XPath:', 'Severity:', 'Usage:', 'Source:', 'Target:', 'Context:', 'Characteristics:', 'Mixins:', 'units',
    'Alias :', 'Profile\n:', '?!', 'MS', 'SU', 'TU', 'N', 'D', 'from', '(example)', '(preferred)', '( extensible )',
    '(required)', 'contains', 'named', 'and', 'only', 'or', 'obeys', 'true', 'false', 'include', 'exclude', 'codes',
    'where', 'valueset', 'system', '(exactly)', 'insert', 'contentReference', '=', '*', ':', ',', '->', '"str"',
    '"a b"', '"x\\"y"', '"""ml\nline"""', '""""q""""', '1', '-2.5e3', '+7', "'mg'", "'kg/m2'", '#code', 'SCT#123',
    '#"a b"', 'http://x.org#frag', '2020', '2020-01-02', '2020-01-02T10:00:00Z', '12:30', '12:30:15.5+01:00',
    '0..1', '1..*', '..', '..*', 'Reference(Patient)', 'Reference( A or B )', 'Reference(A | B)',
    'CodeableReference(X)', 'Canonical(http://x|1.0)', 'Canonical(A or B|2)', '^short', '^extension[0].url',
    '/re[gx]/', '/a\\/b/', '/*c*/', '/* multi\nline */', '//c\n', 'foo', 'bar.baz[x]', 'value[x]', 'component[0]',
    'Foo(a, [[b, c]])', 'Foo(a)', 'Foo( [[x]] , y )', 'Bar', '|', '(', ')', '[[', ']]', '\\', '"', '"""', "'", '#',
    '{a}', '[[{b}]]', '%', '$Alias', 'extension[http://x].valueString', 'MyVS', 'LNC#1234-5 "Display"',
]
SEPS = [' ', ' ', ' ', '\n', '\n* ', '\n  * ', '', '\t', '\n\n', '  ', '\r\n']
HEADERS = [
    'Profile: P\nParent: Observation\n', 'Profile: P\n', 'Extension: E\nContext: Patient\n', 'Extension: E\n',
    'Instance: I\nInstanceOf: Patient\nUsage: #example\n', 'Instance: I\n', 'ValueSet: V\n', 'CodeSystem: C\n',
    'Invariant: inv-1\nSeverity: #error\n', 'RuleSet: R\n', 'RuleSet: R(a, b)\n', 'RuleSet: R([[a]], b)\n',
    'Logical: L\nCharacteristics: #can-bind\n', 'Resource: X\n', 'Mapping: M\nSource: P\nTarget: "http://x"\n',
    'Alias: A = http://a.org\n', '',
]
SPECIAL = list('"#*()[],:=^/\\\'|{}\n \t-.>') + [' ', '\r', 'é', '\U0001F600']

def read_seeds(lists):
    paths = []
    for l in lists:
        with open(l) as f:
            paths += [p.strip() for p in f if p.strip()]
    return sorted(set(paths))

def load(path):
    with open(path, 'rb') as f:
        return f.read().decode('utf-8', 'replace')

def window(rng, text):
    lines = text.split('\n')
    if len(lines) <= 3:
        return text
    n = rng.choice([1, 2, 3, 5, 8, 13, 21, 40])
    start = rng.randrange(0, max(1, len(lines) - 1))
    return '\n'.join(lines[start:start + n])

WORD = re.compile(r'\s+|\S+')

def mutate(rng, s):
    op = rng.randrange(17)
    parts = WORD.findall(s) or ['']
    words = [i for i, p in enumerate(parts) if not p.isspace()]
    if op == 0 and words:  # delete a word
        del parts[rng.choice(words)]
    elif op == 1 and words:  # duplicate a word
        i = rng.choice(words); parts.insert(i, parts[i] + ' ')
    elif op == 2 and words:  # replace a word by a lexeme
        parts[rng.choice(words)] = rng.choice(VOCAB)
    elif op == 3:  # insert a lexeme
        i = rng.randrange(len(parts) + 1); parts.insert(i, rng.choice(SEPS) + rng.choice(VOCAB) + rng.choice(SEPS))
    elif op == 4 and len(words) > 1:  # swap two words
        a, b = rng.sample(words, 2); parts[a], parts[b] = parts[b], parts[a]
    elif op == 5:  # remove a whitespace run (join)
        ws = [i for i, p in enumerate(parts) if p.isspace()]
        if ws: del parts[rng.choice(ws)]
    elif op == 6:  # change a whitespace run
        ws = [i for i, p in enumerate(parts) if p.isspace()]
        if ws: parts[rng.choice(ws)] = rng.choice(SEPS)
    else:
        s = ''.join(parts)
        if op == 7 and s:  # delete a char
            i = rng.randrange(len(s)); return s[:i] + s[i + 1:]
        if op == 8:  # insert a special char
            i = rng.randrange(len(s) + 1); return s[:i] + rng.choice(SPECIAL) + s[i:]
        if op == 9 and s:  # truncate
            return s[:rng.randrange(len(s))]
        lines = s.split('\n')
        if op == 10:  # duplicate a line
            i = rng.randrange(len(lines)); lines.insert(i, lines[i])
        elif op == 11 and len(lines) > 1:  # delete a line
            del lines[rng.randrange(len(lines))]
        elif op == 12 and len(lines) > 1:  # swap lines
            a, b = rng.sample(range(len(lines)), 2); lines[a], lines[b] = lines[b], lines[a]
        elif op == 13:  # change indentation of a line
            i = rng.randrange(len(lines)); lines[i] = rng.choice(['', ' ', '  ', '    ', '\t']) + lines[i].lstrip()
        elif op == 14:  # replace a char by a special char
            if s:
                i = rng.randrange(len(s)); return s[:i] + rng.choice(SPECIAL) + s[i + 1:]
        elif op == 15:  # insert a header line in the middle
            i = rng.randrange(len(lines) + 1); lines.insert(i, rng.choice(HEADERS).rstrip('\n'))
        elif op == 16:  # remove the space after a rule star
            i = rng.randrange(len(lines)); lines[i] = lines[i].replace('* ', '*', 1)
        return '\n'.join(lines)
    return ''.join(parts)

def soup(rng):
    out = []
    for _ in range(rng.randrange(2, 30)):
        out.append(rng.choice(VOCAB)); out.append(rng.choice(SEPS))
    return ''.join(out)

def fsh_case(rng, seeds):
    r = rng.random()
    if r < 0.15:
        s = rng.choice(HEADERS) + soup(rng)
    else:
        s = window(rng, load(rng.choice(seeds)))
        if rng.random() < 0.4:
            s = rng.choice(HEADERS) + s
        for _ in range(rng.choice([0, 1, 1, 1, 2, 2, 3, 4]) if r > 0.2 else 1):
            s = mutate(rng, s)
    return s

def mini_case(rng, seeds):
    text = load(rng.choice(seeds))
    rules = [l for l in text.split('\n') if l.lstrip().startswith('*')]
    if not rules or rng.random() < 0.15:
        body = soup(rng)
    else:
        start = rng.randrange(len(rules))
        body = '\n'.join(rules[start:start + rng.choice([1, 2, 3, 5, 10])])
    s = '\n' + body
    for _ in range(rng.choice([0, 1, 1, 2, 3])):
        s = mutate(rng, s)
    return s

def main():
    mode, out, count, seed = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    seeds = read_seeds(sys.argv[5:])
    rng = random.Random(seed)
    for i in range(count):
        s = fsh_case(rng, seeds) if mode == 'fsh' else mini_case(rng, seeds)
        d = os.path.join(out, '%03d' % (i // 1000))
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, '%07d.%s' % (i, mode)), 'w', encoding='utf-8', newline='',
                  errors='surrogatepass') as f:
            f.write(s)
    print(count, 'cases from', len(seeds), 'seeds')

main()
