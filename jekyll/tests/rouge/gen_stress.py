#!/usr/bin/env python3
"""Long-input stress cases per ported lexer (deep regex recursion / backtracking checks)."""
import json, random
N = 200_000
r = random.Random(7)
def words(n):
    return ' '.join(r.choice(['alpha', 'beta', 'x1', 'Foo', 'bar_baz', 'if', 'return', 'null']) for _ in range(n))
blob = ''.join(r.choice('abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/') for _ in range(N))
esc = ''.join(r.choice(['a', 'b', '\\\\', '\\"', ' ', 'é']) for _ in range(N // 2))
cases = {
    'json': ['{"data": "%s"}' % blob, '{"k": "%s"}' % esc, '[' + ','.join(str(i) for i in range(30000)) + ']',
             '{' + ','.join('"k%d": {"a": [1, 2.5, true, null, "s"]}' % i for i in range(5000)) + '}', '"' + blob],
    'json-doc': ['{ /* %s */ "a": 1 // x\n}' % blob],
    'json5': ["{a: '%s', /* %s */ b: 0x1F}" % (blob, blob[:50000])],
    'xml': ['<a b="%s">%s</a>' % (blob, blob), '<!-- %s -->' % blob, '<![CDATA[%s]]>' % blob, '<x>' * 20000],
    'html': ['<script>var s = "%s";</script>' % blob, '<style>a { b: "%s" }</style>' % blob, '<p>%s</p>' % words(30000),
             '<div a="%s">' % blob],
    'css': ['a { content: "%s"; }' % blob, '/* %s */' % blob, '.x { content: "%s" }' % esc],
    'javascript': ['var s = "%s";' % blob, 'var t = `%s`;' % blob, '/* %s */' % blob, 'f(' * 5000 + ')' * 5000,
                   'x = /%s/g;' % blob[:50000], words(40000)],
    'typescript': ['let s: string = "%s";' % blob, words(40000)],
    'http': ['POST /x HTTP/1.1\nContent-Type: application/json\n\n{"data": "%s"}' % blob,
             'HTTP/1.1 200 OK\nContent-Type: text/html\n\n<p>%s</p>' % words(20000)],
    'shell': ['echo "%s"' % blob, "echo '%s'" % blob, 'cat <<EOF\n%s\nEOF\n' % ('line\n' * 20000), words(40000)],
    'batchfile': ['echo "%s"' % blob, words(40000)],
    'sql': ["SELECT '%s' FROM t;" % blob, '/* %s */' % blob, words(40000)],
    'python': ['s = "%s"' % blob, 's = """%s"""' % blob, "f'%s'" % blob[:50000], words(40000)],
    'java': ['String s = "%s";' % blob, '/* %s */' % blob, 'class A { %s }' % words(20000), 'String s = "%s";' % esc,
             'int ' * 20000 + 'f(x);', '.'.join(['a'] * 50000) + '(', 'a ' * 30000 + '1 f(', 'List<String> ' * 10000 + 'g (', 'a\n' * 30000 + 'b('],
    'yaml': ['a: %s\n' % blob, 'a: "%s"\n' % blob, ''.join('k%d:\n  - v %d\n' % (i, i) for i in range(10000)), 'a: |\n' + '  line\n' * 20000],
    'markdown': ['%s\n' % words(30000), '```json\n{"a": "%s"}\n```\n' % blob, '*%s*' % blob, '`%s`' % blob],
    'diff': [''.join('+line %d\n-line %d\n' % (i, i) for i in range(20000))],
    'sparql': ['SELECT ?x WHERE { ?x <p> "%s" }' % blob],
    'turtle': ['<a> <b> "%s" .' % blob, '<a> <b> """%s""" .' % blob],
    'properties': ['a = %s\n' % blob, 'a = "%s"\n' % blob],
    'actionscript': ['var s = "%s";' % blob, words(40000)],
}
for lang, texts in cases.items():
    for k, t in enumerate(texts):
        print(json.dumps({'id': f'stress/{lang}/{k}', 'type': 'rouge', 'lang': lang, 'text': t, 'formatter': 'html'}, ensure_ascii=False))
