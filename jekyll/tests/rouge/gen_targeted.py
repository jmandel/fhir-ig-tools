#!/usr/bin/env python3
"""Targeted Rouge cases: lexer resolution (aliases, unknown labels, case), find_fancy '?' options,
the 'guess' path (Modeline/Source guessers incl. detect? of unported lexers, ambiguity errors),
HTTP content-type delegation, markdown fence delegation, formatter edge cases."""
import json
cases = []
def add(i, lang, text, fmt='html', **kw):
    c = {'id': 't/' + i, 'type': 'rouge', 'lang': lang, 'text': text, 'formatter': fmt}
    c.update(kw); cases.append(c)
J = '{"a": [1, true, null, "x\\"y"]}\n'
labels = ['json', 'JSON', 'Json', 'jsonc', 'json-doc', 'json5', 'xml', 'XML', 'html', 'xhtml', 'css', 'js', 'javascript', 'ts',
          'typescript', 'http', 'HTTP', 'shell', 'bash', 'sh', 'zsh', 'ksh', 'console', 'batchfile', 'bat', 'batch', 'cmd', 'sql',
          'python', 'py', 'java', 'yaml', 'yml', 'markdown', 'md', 'mkd', 'diff', 'patch', 'udiff', 'sparql', 'turtle', 'ttl',
          'properties', 'actionscript', 'as', 'as3', 'plaintext', 'text', 'txt', 'mermaid', 'cql', 'fhirpath', 'fsh', 'none', '',
          'c#', 'c++', 'rs,ignore', 'shell-session', 'json ', ' json', 'jsonp', 'application/json']
for l in labels:
    add('label/' + l, l, J)
# find_fancy options
for l in ['json?foo=bar', 'json?', '?', 'json?a=1&a=2', 'plaintext?token=Name.Tag', 'plaintext?token=Bogus', 'text?token=Comment.Single',
          'http?content=json', 'http?content=xml', 'http?content=nope', 'guess?mimetype=application/json', 'guess?mimetype=text/html',
          'guess?mimetype=nope/x', 'unknown?x=1', 'json?x=%41%2', 'json?x=+y', 'markdown?token=x']:
    add('opt/' + l, l, 'GET /x HTTP/1.1\nContent-Type: text/plain\n\n{"a": 1}\n' if l.startswith('http') else J)
# guess
guess_src = {
    'json': J, 'empty': '', 'bash-shebang': '#!/bin/bash\necho hi\n', 'env-python': '#!/usr/bin/env python3\nprint(1)\n',
    'node': '#!/usr/bin/env node\nconsole.log(1)\n', 'xml-decl': '<?xml version="1.0"?>\n<a/>\n', 'html-doctype': '<!DOCTYPE html>\n<html></html>\n',
    'xml-doctype': '<!DOCTYPE note SYSTEM "x.dtd">\n<note/>\n', 'html-tag': '<html><body>x</body></html>\n', 'yaml': '%YAML 1.2\n---\na: 1\n',
    'diff': 'diff --git a/x b/x\n--- a/x\n+++ b/x\n@@ -1 +1 @@\n-a\n+b\n', 'index': 'Index: foo\n', 'ruby': '#!/usr/bin/ruby\nputs 1\n',
    'php': '<?php echo 1; ?>\n', 'emacs': '# -*- mode: python -*-\nx = 1\n', 'vim': '# vim: ft=yaml\na: 1\n', 'vim2': '// vim: set ft=javascript :\nvar a;\n',
    'modeline-unknown': '# vim: ft=nosuch\nx\n', 'tex': '\\documentclass{article}\n', 'ps': '%!PS\n', 'racket': '#lang racket\n(define x 1)\n',
    'compdef': '#compdef foo\n', 'perl': '#!/usr/bin/perl\nprint 1;\n', 'hack': '<?hh\necho 1;\n', 'bom-xml': '\ufeff<?xml version="1.0"?><a/>',
    'crlf': '#!/bin/sh\r\necho\r\n', 'ambig': '#!/usr/bin/env python\n<?xml version="1.0"?>\n', 'xml-and-diff': '<?xml version="1.0"?>\n--- a\n+++ b\n',
    'groovy': '#!/usr/bin/env groovy\nprintln 1\n', 'lua': '#!/usr/bin/lua\nprint(1)\n',
}
for k, t in guess_src.items():
    add('guess/' + k, 'guess', t)
# http content delegation
for ct, body in [('application/json', J), ('application/fhir+json', J), ('application/json; charset=utf-8', J), ('APPLICATION/JSON', J),
                 ('text/xml', '<a b="1">x</a>\n'), ('application/xml', '<a/>'), ('application/fhir+xml', '<a/>'), ('text/html', '<p>hi</p>'),
                 ('text/plain', 'plain'), ('application/x-www-form-urlencoded', 'a=1&b=2'), ('text/css', 'a{b:c}'), ('application/javascript', 'var a=1;'),
                 ('text/x-yaml', 'a: 1\n'), ('application/x-sh', 'echo $x'), (';', 'x'), ('', 'x'), ('text/x-python', 'def f(): pass')]:
    add('http/' + ct, 'http', f'POST /Patient HTTP/1.1\nHost: x\nContent-Type: {ct}\n\n{body}')
    add('http-resp/' + ct, 'http', f'HTTP/1.1 201 Created\r\nContent-Type: {ct}\r\n\r\n{body}')
add('http/two-ct', 'http', 'GET / HTTP/1.1\nContent-Type: text/html\ncontent-type : application/json\n\n{"a":1}')
add('http/no-body', 'http', 'GET /a HTTP/1.1\nAccept: application/json\n')
add('http/garbage', 'http', 'not http at all\n{"a": 1}')
# markdown delegation
for lang in ['json', 'xml', 'yaml', 'bash', 'http', '', 'guess', 'nosuch', 'ruby', 'json?x=1', 'python']:
    add('md-fence/' + (lang or 'none'), 'markdown', f'# T\n\n```{lang}\n{{"a": 1}}\n<b>x</b>\necho $HOME\n```\n\ntext *em* and `code` <i>html</i> &amp;\n')
add('md/unclosed', 'markdown', '```json\n{"a": 1}\nmore\n')
add('md/tilde', 'markdown', '~~~ yaml\na: 1\n~~~\n')
add('md/frontmatter', 'markdown', '---\ntitle: x\n---\n# H\n')
# formatters
for f in ['html', 'html_legacy', 'html_table']:
    add('fmt/empty/' + f, 'json', '', f)
    add('fmt/nl/' + f, 'json', '\n', f)
    add('fmt/nonl/' + f, 'json', '{"a":1}', f)
    add('fmt/amp/' + f, 'xml', '<a>&amp; & < > " \'</a>', f)
add('fmt/mark', 'json', '{\n"a": 1,\n"b": 2\n}\n', 'html', mark_lines=[2, 4, 9])
add('fmt/mark-table', 'json', '{\n"a": 1,\n"b": 2\n}', 'html_table', mark_lines=[1])
for c in cases:
    print(json.dumps(c, ensure_ascii=False))
