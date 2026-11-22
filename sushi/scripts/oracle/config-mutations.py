#!/usr/bin/env python3
"""WP-3 mutation set: malformed / unusual sushi-config.yaml inputs for the config oracle (config-oracle.js).

usage: python3 scripts/oracle/config-mutations.py <corpus-inputs.json> <out mutation-inputs.json>
Deterministic (seeded). Three families, named by prefix:
  syn/<ig>/<n>   textual mutations of real corpus configs (char insert/delete, line delete/duplicate/swap,
                 indentation shifts, tabs, quotes, YAML indicators)
  sem/<ig>/<n>   valid YAML with wrong types or values for known properties (numbers, booleans, lists, maps, null,
                 missing required keys, FSHOnly, codes)
  hand/<name>    hand-written edge cases (documents, anchors, tags, scalars, BOM, CRLF, flow style, ...)
"""
import json, random, sys

corpus = json.load(open(sys.argv[1]))
rnd = random.Random(20261122)
out = []

def add(name, text):
    out.append({'name': name, 'file': 'sushi-config.yaml', 'text': text})

# ---------------------------------------------------------------- hand-written
MIN = """id: fhir.us.minimal
canonical: http://hl7.org/fhir/us/minimal
name: MinimalIG
status: draft
version: 1.0.0
fhirVersion: 4.0.1
copyrightYear: 2020+
releaseLabel: Build CI
"""
hand = {
    'empty': '',
    'comment-only': '# nothing here\n',
    'scalar-doc': 'just a string\n',
    'number-doc': '42\n',
    'list-doc': '- a\n- b\n',
    'null-doc': '~\n',
    'multi-doc': MIN + '---\nid: other\n',
    'multi-doc-empty-second': MIN + '---\n',
    'doc-end-marker': MIN + '...\n',
    'doc-end-then-comment': MIN + '...\n# trailing\n',
    'explicit-doc-start': '---\n' + MIN,
    'yaml-directive-12': '%YAML 1.2\n---\n' + MIN,
    'yaml-directive-11': '%YAML 1.1\n---\n' + MIN,
    'bom': '\ufeff' + MIN,
    'crlf': MIN.replace('\n', '\r\n'),
    'cr-only': MIN.replace('\n', '\r'),
    'no-final-newline': MIN.rstrip('\n'),
    'dup-key': MIN + 'name: Again\n',
    'dup-key-nested': MIN + 'dependencies:\n  a: 1.0.0\n  a: 2.0.0\n',
    'dup-key-flow': MIN + 'menu: {Home: a.html, Home: b.html}\n',
    'dup-key-num-vs-str': MIN + 'parameters:\n  1: a\n  "1": b\n',
    'dup-key-float-int': MIN + 'parameters:\n  1: a\n  1.0: b\n',
    'anchor-alias': MIN + 'title: &t The Title\ndescription: *t\n',
    'anchor-alias-map': MIN + 'publisher: &p\n  name: Pub\n  url: http://p.org\ncontact: *p\n',
    'alias-unknown': MIN + 'title: *nope\n',
    'alias-as-key': MIN + 'parameters:\n  &k pk: v\n  *k : w\n',
    'merge-key': MIN + 'base: &b {a: 1}\nparameters:\n  <<: *b\n  c: 2\n',
    'tag-str': MIN.replace('version: 1.0.0', 'version: !!str 1.0'),
    'tag-int-bad': MIN + 'copyrightYear: !!int twenty\n',
    'tag-int-good': MIN.replace('copyrightYear: 2020+', 'copyrightYear: !!int 2020'),
    'tag-float-int': MIN + 'parameters:\n  x: !!float 1\n',
    'tag-bool': MIN + 'experimental: !!bool true\n',
    'tag-null': MIN + 'title: !!null ""\n',
    'tag-custom': MIN + 'title: !custom hello\n',
    'tag-nonspecific': MIN + 'parameters:\n  x: ! 12\n',
    'tag-binary': MIN + 'title: !!binary aGVsbG8=\n',
    'tag-timestamp': MIN + 'date: !!timestamp 2020-01-01\n',
    'tag-map-on-scalar': MIN + 'title: !!map hello\n',
    'tag-seq-on-map': MIN + 'publisher: !!seq {name: x}\n',
    'tag-str-on-map': MIN + 'title: !!str {a: 1}\n',
    'tag-undeclared-handle': MIN + 'title: !e!foo bar\n',
    'tag-verbatim': MIN + 'title: !<tag:yaml.org,2002:str> verb\n',
    'tag-directive': '%TAG !e! tag:example.com,2000:\n---\n' + MIN + 'title: !e!foo bar\n',
    'yes-no-on-off': MIN + 'experimental: yes\nFSHOnly: off\n',
    'bool-variants': MIN + 'parameters:\n  a: True\n  b: FALSE\n  c: tRUE\n',
    'null-variants': MIN + 'parameters:\n  a: Null\n  b: NULL\n  c: nULL\n  d: ~\n  e:\n',
    'numbers': MIN + 'parameters:\n  a: 0o17\n  b: 017\n  c: 0x1F\n  d: .inf\n  e: -.Inf\n  f: .NaN\n  g: 1e3\n  h: +12\n  i: 12345678901234567890\n  j: 1_000\n  k: 0.\n  l: .5\n  m: -0\n  n: 1.50\n  o: 0x\n  p: 1e\n  q: 6.02E+23\n',
    'version-number': MIN.replace('version: 1.0.0', 'version: 1.0'),
    'version-int': MIN.replace('version: 1.0.0', 'version: 2'),
    'date-year': MIN + 'date: 2020\n',
    'date-timestamp': MIN + 'date: 2020-01-01T10:00:00Z\n',
    'fhirversion-number': MIN.replace('fhirVersion: 4.0.1', 'fhirVersion: 4.0'),
    'fhirversion-list-number': MIN.replace('fhirVersion: 4.0.1', 'fhirVersion: [4.0.1, 5]'),
    'fhirversion-empty-list': MIN.replace('fhirVersion: 4.0.1', 'fhirVersion: []'),
    'fhirversion-code': MIN.replace('fhirVersion: 4.0.1', "fhirVersion: '#4.0.1'"),
    'status-number': MIN.replace('status: draft', 'status: 1'),
    'status-invalid': MIN.replace('status: draft', 'status: final'),
    'status-code': MIN.replace('status: draft', "status: '#active'"),
    'status-bad-code': MIN.replace('status: draft', "status: '#'"),
    'language-bool': MIN + 'language: true\n',
    'license-list': MIN + 'license: [a, b]\n',
    'title-number': MIN + 'title: 2023\n',
    'title-map': MIN + 'title: {a: 1}\n',
    'experimental-string': MIN + 'experimental: "yes"\n',
    'fshonly-string': MIN + 'FSHOnly: "true"\n',
    'fshonly-true-extra': MIN + 'FSHOnly: true\nmenu:\n  Home: index.html\n',
    'missing-all': 'foo: bar\n',
    'missing-canonical-fshonly': 'FSHOnly: true\nfhirVersion: 4.0.1\n',
    'publisher-string': MIN + 'publisher: HL7\n',
    'publisher-list-null': MIN + 'publisher:\n  - ~\n',
    'publisher-list-strings': MIN + 'publisher: [A, B]\n',
    'publisher-number': MIN + 'publisher: 5\n',
    'contact-no-telecom': MIN + 'contact:\n  name: Bob\n',
    'contact-null': MIN + 'contact:\n  - ~\n',
    'contact-string': MIN + 'contact: Bob\n',
    'contact-telecom-null': MIN + 'contact:\n  name: Bob\n  telecom:\n    - ~\n',
    'contact-telecom-string': MIN + 'contact:\n  name: Bob\n  telecom: [abc]\n',
    'contact-bad-codes': MIN + 'contact:\n  name: Bob\n  telecom:\n    - system: fax2\n      use: '"'#home'"'\n      value: x\n',
    'usecontext-null': MIN + 'useContext:\n  - ~\n',
    'usecontext-string': MIN + 'useContext: foo\n',
    'usecontext-two-values': MIN + "useContext:\n  code: '#gender'\n  valueQuantity: 5 'mm'\n  valueRange: {low: '1'}\n",
    'usecontext-bad-quantity': MIN + "useContext:\n  code: '#x'\n  valueQuantity: lots\n",
    'usecontext-empty-quantity': MIN + "useContext:\n  code: '#x'\n  valueQuantity: \" 'mm'\"\n",
    'jurisdiction-number': MIN + 'jurisdiction: 5\n',
    'jurisdiction-bad-code': MIN + "jurisdiction: '#'\n",
    'jurisdiction-coding-string': MIN + 'jurisdiction:\n  coding: abc\n',
    'meta-security-string': MIN + 'meta:\n  security: abc\n',
    'meta-string': MIN + 'meta: abc\n',
    'text-status-bad': MIN + 'text:\n  status: wrong\n  div: <div/>\n',
    'dependencies-string': MIN + 'dependencies: hl7.fhir.us.core\n',
    'dependencies-list': MIN + 'dependencies:\n  - hl7.fhir.us.core: 3.1.0\n',
    'dependencies-null-version': MIN + 'dependencies:\n  hl7.fhir.us.core:\n',
    'dependencies-bool': MIN + 'dependencies:\n  hl7.fhir.us.core: true\n',
    'dependencies-uppercase': MIN + 'dependencies:\n  HL7.FHIR.us.core: 3.1.0\n',
    'dependencies-object-numver': MIN + 'dependencies:\n  a.b:\n    version: 1.2\n    uri: http://x\n    id: ab\n    reason: r\n',
    'global-null': MIN + 'global:\n  Patient:\n',
    'global-list': MIN + 'global:\n  Patient: [a, b]\n',
    'groups-null': MIN + 'groups:\n  G1:\n',
    'groups-string': MIN + 'groups:\n  G1: abc\n',
    'groups-number': MIN + 'groups:\n  G1: 5\n',
    'groups-list': MIN + 'groups:\n  G1: [a]\n',
    'groups-name-null': MIN + 'groups:\n  G1:\n    name: ~\n    resources: [a]\n',
    'definition-string': MIN + 'definition: abc\n',
    'definition-other': MIN + 'definition:\n  extension: [{url: x, valueString: y}]\n  page: {}\n',
    'resources-null': MIN + 'resources:\n  Patient/a:\n',
    'resources-string': MIN + 'resources:\n  Patient/a: something\n',
    'resources-omit': MIN + "resources:\n  Patient/a: omit\n  Patient/b: '#omit'\n",
    'resources-reference-key': MIN + 'resources:\n  Patient/a:\n    reference: {reference: X}\n    name: N\n',
    'resources-fhirversion-number': MIN + 'resources:\n  Patient/a:\n    fhirVersion: 4.0\n',
    'resources-slash-key': MIN + 'resources:\n  "a~b/c":\n    nme: x\n',
    'pages-null': MIN + 'pages:\n  index.md:\n',
    'pages-string': MIN + 'pages:\n  index.md: abc\n',
    'pages-number': MIN + 'pages:\n  index.md: 5\n',
    'pages-list': MIN + 'pages:\n  index.md: [a, {b: 1}]\n',
    'pages-name': MIN + 'pages:\n  index.md:\n    name: foo\n',
    'pages-bad-ext': MIN + 'pages:\n  index.txt:\n    title: T\n  other.doc: ~\n',
    'pages-bad-generation': MIN + 'pages:\n  index.md:\n    generation: pdf\n',
    'parameters-string': MIN + 'parameters: abc\n',
    'parameters-list': MIN + 'parameters: [a, b]\n',
    'parameters-nested': MIN + 'parameters:\n  a: [[1, 2], {b: 1}]\n  c: ~\n',
    'parameters-bool-false': MIN + 'parameters:\n  a: false\n',
    'copyrightyear-zero': MIN.replace('copyrightYear: 2020+', 'copyrightYear: 0'),
    'copyrightyear-list': MIN.replace('copyrightYear: 2020+', 'copyrightYear: [2020, 2021]'),
    'releaselabel-number': MIN.replace('releaseLabel: Build CI', 'releaseLabel: 1.5'),
    'templates-null': MIN + 'templates:\n  - ~\n',
    'templates-missing': MIN + 'templates:\n  - scope: x\n',
    'templates-code': MIN + "templates:\n  code: '#full'\n  source: s\n",
    'menu-null': MIN + 'menu:\n  Home:\n',
    'menu-number': MIN + 'menu:\n  Home: 5\n',
    'menu-keywords': MIN + 'menu:\n  A: new-tab external http://a\n  B: external new-tab http://b\n  C: http://new-tab x\n',
    'menu-nested-null': MIN + 'menu:\n  A:\n    B:\n',
    'history-string': MIN + 'history: abc\n',
    'history-current-obj': MIN + 'history:\n  current:\n    date: 2020\n  1.0.0:\n    date: 2020\n',
    'history-item-null': MIN + 'history:\n  1.0.0:\n',
    'instanceoptions-bad': MIN + 'instanceOptions:\n  setMetaProfile: sometimes\n  setId: 3\n',
    'instanceoptions-string': MIN + 'instanceOptions: abc\n',
    'unknown-props': MIN + 'cookie: 1\nanother: 2\npages:\n  index.md:\n    tilte: x\n',
    'integer-keys': MIN + 'parameters:\n  2: b\n  1: a\n  x: c\n',
    'proto-key': MIN + 'parameters:\n  __proto__: x\n  constructor: y\n',
    'collection-key': MIN + 'parameters:\n  ? [a, b]\n  : c\n',
    'null-key': MIN + 'parameters:\n  ~: a\n',
    'empty-key': MIN + 'parameters:\n  "": a\n',
    'flow-style': '{id: x, canonical: "http://x", name: N, status: draft, version: 1, fhirVersion: [4.0.1]}\n',
    'flow-multiline': 'id: x\ncanonical: http://x\nname: N\nstatus: draft\nversion: 1\nfhirVersion: [\n  4.0.1\n]\n',
    'block-literal': MIN + 'description: |\n  line one\n  line two\n',
    'block-folded-keep': MIN + 'description: >+\n  folded\n  text\n\n',
    'block-strip': MIN + 'description: |-\n  text\n',
    'block-indent-indicator': MIN + 'description: |2\n   indented\n',
    'quoted-escapes': MIN + 'description: "tab\\there \\u00e9 \\x41 \\/ \\N \\_ \\L \\P"\n',
    'single-quoted': MIN + "description: 'it''s'\n",
    'multiline-plain': MIN + 'description: one\n  two\n\n  three\n',
    'multiline-double': MIN + 'description: "one\n  two\\\n  three"\n',
    'comment-no-space': MIN + "title: 'x'#c\n",
    'hash-in-plain': MIN + 'title: a#b\n',
    'colon-in-plain': MIN + 'title: a:b\n',
    'tab-indent': MIN + 'parameters:\n\ta: b\n',
    'tab-before-value': MIN + 'title:\tTabbed\n',
    'tab-blank-line': MIN + '\t\nlicense: CC0-1.0\n',
    'tab-blank-line-spaces': MIN + '  \t  \nlicense: CC0-1.0\n',
    'tab-in-flow': MIN + 'fhirVersion2: [a,\tb]\n',
    'trailing-tab': MIN + 'license: CC0-1.0\t\n',
    'tab-before-comment': MIN + 'license: CC0-1.0\t# c\n',
    'nbsp-value': MIN + 'title: \u00a0x\u00a0\n',
    'control-char': MIN + 'title: a\u0001b\n',
    'del-char': MIN + 'title: a\u007fb\n',
    'nel-char': MIN + 'title: a\u0085b\n',
    'astral': MIN + 'title: \U0001F600 smile\n',
    'long-key': MIN + 'k' * 1100 + ': v\n',
    'many-aliases': MIN + 'x: &a v\nlist: [' + ', '.join(['*a'] * 120) + ']\n',
    'nested-alias-bomb': MIN + 'a: &a [x, x]\nb: &b [*a, *a, *a, *a, *a, *a, *a, *a, *a, *a]\nc: &c [*b, *b, *b, *b, *b, *b, *b, *b, *b, *b]\nd: [*c, *c, *c]\n',
    'circular': MIN + 'a: &a\n  b: *a\n',
    'anchor-redefined': MIN + 'a: &x 1\nb: *x\nc: &x 2\nd: *x\n',
    'unterminated-double': MIN + 'title: "abc\n',
    'unterminated-single': MIN + "title: 'abc\n",
    'unterminated-flow-seq': MIN + 'fhirVersion2: [a, b\n',
    'unterminated-flow-map': MIN + 'x: {a: 1\n',
    'bad-indent-seq': MIN + 'fhirVersion2:\n  - a\n - b\n',
    'bad-indent-map': MIN + 'publisher:\n  name: a\n    url: b\n',
    'compact-nested-map': MIN + 'a: b: c\n',
    'seq-in-map-value': MIN + 'a: - b\n',
    'stray-dash': MIN + '- x\n',
    'stray-colon': MIN + ': x\n',
    'question-key': MIN + '? complex\n: value\n',
    'reserved-at': MIN + 'title: @foo\n',
    'reserved-backtick': MIN + 'title: `foo\n',
    'percent-plain': MIN + 'title: %foo\n',
    'directive-in-body': MIN + '%YAML 1.2\n',
    'anchor-twice': MIN + 'title: &a &b x\n',
    'alias-with-anchor': MIN + 'x: &a 1\ny: &b *a\n',
    'block-scalar-bad-indicator': MIN + 'description: |x\n  a\n',
    'flow-trailing-comma': MIN + 'fhirVersion2: [a, b,]\n',
    'flow-double-comma': MIN + 'fhirVersion2: [a,, b]\n',
    'flow-map-in-seq-pair': MIN + 'fhirVersion2: [a: 1, b]\n',
    'invalid-escape': MIN + 'title: "\\q"\n',
    'document-end-in-middle': 'id: x\n...\ncanonical: y\n',
}
for k, v in hand.items():
    add('hand/' + k, v)

# ---------------------------------------------------------------- textual mutations of corpus configs
bases = sorted(corpus, key=lambda c: c['name'])
rnd.shuffle(bases)
bases = bases[:40]
INDICATORS = [':', '-', '"', "'", '\t', '[', ']', '{', '}', '#', '&', '*', '!', '|', '>', '%', '@', '`', ',', '?', ' ', '\n', '  ']

def mutate(text):
    lines = text.split('\n')
    kind = rnd.choice(['ins', 'del', 'ldel', 'ldup', 'lswap', 'indent', 'dedent', 'tabline', 'quote', 'dupkey', 'ins', 'del'])
    if kind in ('ins', 'del', 'quote') and len(text) > 0:
        i = rnd.randrange(len(text))
        if kind == 'ins':
            return kind, text[:i] + rnd.choice(INDICATORS) + text[i:]
        if kind == 'del':
            return kind, text[:i] + text[i + 1:]
        return kind, text[:i] + rnd.choice(['"', "'"]) + text[i:]
    nonblank = [i for i, l in enumerate(lines) if l.strip() and not l.strip().startswith('#')]
    if not nonblank:
        return 'none', text
    i = rnd.choice(nonblank)
    if kind == 'ldel':
        del lines[i]
    elif kind == 'ldup':
        lines.insert(i, lines[i])
    elif kind == 'lswap':
        j = rnd.choice(nonblank)
        lines[i], lines[j] = lines[j], lines[i]
    elif kind == 'indent':
        lines[i] = rnd.choice([' ', '  ', '    ']) + lines[i]
    elif kind == 'dedent':
        lines[i] = lines[i][1:] if lines[i].startswith(' ') else lines[i]
    elif kind == 'tabline':
        lines[i] = '\t' + lines[i].lstrip(' ')
    elif kind == 'dupkey':
        top = [k for k in nonblank if not lines[k].startswith(' ') and ':' in lines[k]]
        if top:
            lines.append(lines[rnd.choice(top)])
    return kind, '\n'.join(lines)

for b in bases:
    for n in range(15):
        kind, t = mutate(b['text'])
        add('syn/%s/%02d-%s' % (b['name'], n, kind), t)

# ---------------------------------------------------------------- type/value mutations (valid YAML)
KEYS = ['id', 'canonical', 'name', 'status', 'version', 'fhirVersion', 'title', 'description', 'publisher',
        'license', 'date', 'jurisdiction', 'dependencies', 'pages', 'menu', 'parameters', 'FSHOnly',
        'copyrightYear', 'releaseLabel', 'resources', 'groups', 'instanceOptions', 'contact', 'experimental',
        'language', 'global', 'meta', 'extension', 'templates', 'definition']
REPLACEMENTS = ['42', '1.5', 'true', 'false', '~', '[]', '{}', '[a, 1]', '{x: 1}', "'#code'", "''", 'yes', '.inf']
for b in bases[:20]:
    lines = b['text'].split('\n')
    tops = [i for i, l in enumerate(lines) if l and not l[0].isspace() and not l.startswith('#') and ':' in l]
    for n in range(12):
        key = rnd.choice(KEYS)
        rep = rnd.choice(REPLACEMENTS)
        idx = [i for i in tops if lines[i].split(':', 1)[0].strip() == key]
        if idx and rnd.random() < 0.8:
            i = idx[0]
            # replace the whole value (drop the following indented block)
            j = i + 1
            while j < len(lines) and (lines[j].startswith(' ') or lines[j].startswith('\t') or not lines[j].strip()):
                j += 1
            new = lines[:i] + ['%s: %s' % (key, rep)] + lines[j:]
            add('sem/%s/%02d-%s=%s' % (b['name'], n, key, rep), '\n'.join(new))
        elif idx:
            i = idx[0]
            j = i + 1
            while j < len(lines) and (lines[j].startswith(' ') or lines[j].startswith('\t') or not lines[j].strip()):
                j += 1
            add('sem/%s/%02d-del-%s' % (b['name'], n, key), '\n'.join(lines[:i] + lines[j:]))
        else:
            add('sem/%s/%02d-add-%s=%s' % (b['name'], n, key, rep), b['text'] + '\n%s: %s\n' % (key, rep))

json.dump(out, open(sys.argv[2], 'w'))
print(len(out), 'inputs')
