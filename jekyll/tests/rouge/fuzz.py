#!/usr/bin/env python3
"""Differential fuzz cases for the ported Rouge lexers: random token soups built from each lexer's
syntax-relevant pieces, and mutations (insert/delete/duplicate/splice) of the demo and sample texts.

usage: fuzz.py <seed> <per-lexer> > fuzz-cases.jsonl"""
import json, os, random, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '../../..'))
seed, per = int(sys.argv[1]), int(sys.argv[2])
r = random.Random(seed)
DEMOS = os.path.join(ROOT, 'third_party/rouge/lib/rouge/demos')
SAMPLES = os.path.join(ROOT, 'third_party/rouge/spec/visual/samples')
GENERIC = [' ', '  ', '\n', '\n\n', '\t', '\r\n', '"', "'", '`', '\\', '/', '*', '#', '-', '--', '{', '}', '[', ']',
           '(', ')', '<', '>', '=', ':', ';', ',', '.', '...', '?', '!', '@', '$', '%', '&', '|', '^', '~', '+',
           'a', 'Foo', 'x_1', '_', '0', '42', '3.14', '1e10', '0x1F', 'é', 'ü', 'Ω', '中', ' ', ' ', '😀',
           'true', 'null', 'if', 'else', 'return', 'class', 'function', 'def', 'var']
EXTRA = {
    'json': ['"k":', '"v"', '\\"', '\\u00e9', '-0.5e-3', 'false'],
    'json-doc': ['//', '/*', '*/', '...', 'key:'],
    'json5': ['//', '/*', '*/', 'Infinity', 'NaN', '+.5', "'s'"],
    'xml': ['<a>', '</a>', '<b x="1"/>', '<!--', '-->', '<![CDATA[', ']]>', '<?xml version="1.0"?>', '&amp;', '&#x41;', '<!DOCTYPE x>', ' y=', "'v'"],
    'html': ['<p>', '</p>', '<script>', '</script>', '<style>', '</style>', '<!--', '-->', '&nbsp;', '<a href="x">', '<br/>', '<!DOCTYPE html>', 'onclick=', '<div'],
    'css': ['a{', '}', 'color:', 'red;', '#fff', '10px', '!important', '@media', 'url(', '/*', '*/', '.cls', '::before', '--var', 'rgba('],
    'javascript': ['//', '/*', '*/', '/re/g', '`', '${', 'const', 'let', '=>', '===', '?.', '??', 'async', 'await', 'new', 'this', 'class X extends Y', '@dec', '#priv'],
    'typescript': ['interface', 'type', ': string', 'as', 'satisfies', 'readonly', '<T>', '?:', 'enum', '...rest'],
    'http': ['GET /a HTTP/1.1\n', 'HTTP/1.1 200 OK\n', 'Content-Type: application/json\n', 'Content-Type: text/html; charset=utf-8\n', 'Content-Type: application/fhir+json\n', 'Content-Type: application/xml\n', 'Accept: */*\n', 'POST /x HTTPS/2\r\n', 'HTTP/2 404\n'],
    'shell': ['$', '${x}', '$(ls)', '$((1+2))', '<<EOF\n', 'EOF\n', '<<-', 'echo', 'curl', '-X', '--header', '|', '&&', ';;', 'case', 'esac', 'fi', 'then', '\\\n', 'export X=1'],
    'batchfile': ['@echo off', 'rem', '::', '%PATH%', '!x!', '%%i', 'set', 'goto', ':label', '^', 'nul', 'equ', '.exe'],
    'sql': ['SELECT', 'FROM', 'WHERE', '--', '/*', '*/', "''", '``', 'INT', 'varchar', ';'],
    'python': ['"""', "'''", 'f"', 'r"', 'b\'', '{', '}', '{{', '>>> ', 'lambda', 'import', 'from', 'match', 'case', '@deco', '\\n', 'None', '0b1', '1_000', '3j'],
    'java': ['public', 'static', 'void', 'String', 'class', 'import', 'package', '@Override', '"""\n', '//', '/*', '*/', "'c'", '1L', '0x1F', 'foo(', 'int x'],
    'yaml': ['---\n', '...\n', '- ', 'key: ', '? ', '|\n', '>-\n', '&anc', '*ali', '!tag', '!!str', '%YAML 1.2\n', '%TAG ! x\n', '[a, b]', '{a: 1}', '# c', '  ', '    '],
    'markdown': ['# ', '## ', '```\n', '```json\n', '~~~\n', '*', '**', '_', '__', '[a](b)', '![i](u)', '[r]: http://x "t"\n', '> ', '- ', '1. ', '<b>', '&amp;', '    code\n', '---\n', '===\n', '`c`'],
    'diff': ['+', '-', '@@ -1,2 +1,2 @@\n', '--- a/x\n', '+++ b/x\n', 'diff --git a/x b/x\n', 'Index: x\n', '> ', '< ', '!', '=\n'],
    'sparql': ['SELECT', '?x', '$y', 'WHERE', 'PREFIX ex: <http://x/>', 'ex:p', '"s"@en', '^^xsd:int', '"""', "'''", 'FILTER', 'a', 'OPTIONAL'],
    'turtle': ['@prefix', '@base', 'ex:', '<http://x>', '"s"', '"""', "'''", '^^', '@en', 'a', 'true', '.', ';', '1.5E3'],
    'properties': ['key=', 'key:', '! c', '# c', '\\\n', '\\u0041', '"q"', "'s'"],
    'actionscript': ['var', 'function', 'class', 'package', 'import', '//', '/*', '*/', '/re/', 'trace', 'override', '?', ':'],
    'ruby': ['def', 'end', 'do |x|', '@iv', '@@cv', '$g', ':sym', '%w(a b)', '%r{x}i', '%q[x]', '<<~EOS\n', '<<-EOS\n', 'EOS\n', '#{x}', '=begin\n', '=end\n', '__END__', '?a', 'puts', 'module M', 'class << self', '/re/m', '0x1F', '1_000', '=~'],
    'plaintext': [],
}
def read(p):
    try:
        return open(p, encoding='utf-8').read()
    except FileNotFoundError:
        return None
for tag, extra in EXTRA.items():
    pieces = GENERIC + extra * 3
    texts = [t for t in (read(os.path.join(DEMOS, tag)), read(os.path.join(SAMPLES, tag))) if t]
    for k in range(per):
        if texts and r.random() < 0.5:
            t = list(r.choice(texts))
            if len(t) > 3000:
                a = r.randrange(len(t) - 2000); t = t[a:a + r.randrange(50, 2000)]
            for _ in range(r.randrange(1, 6)):
                op = r.random()
                i = r.randrange(len(t) + 1)
                if op < 0.4:
                    t[i:i] = list(r.choice(pieces))
                elif op < 0.7 and t:
                    del t[i:i + r.randrange(1, 10)]
                elif t:
                    j = r.randrange(len(t)); t[i:i] = t[j:j + r.randrange(1, 30)]
            text = ''.join(t)
        else:
            text = ''.join(r.choice(pieces) for _ in range(r.randrange(1, 120)))
        fmt = r.choice(['html', 'html', 'html_legacy', 'html_table'])
        print(json.dumps({'id': f'fuzz{seed}/{tag}/{k}', 'type': 'rouge', 'lang': tag, 'text': text, 'formatter': fmt}, ensure_ascii=False))
