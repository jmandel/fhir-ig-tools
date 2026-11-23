#!/usr/bin/env python3
"""Targeted SafeYAML typing suite -> jekyll/tests/yaml/typing-cases.jsonl (+ files under typing-files/).

Covers every Transform branch (ToInteger, ToFloat, ToNil, ToBoolean, ToDate incl. Time, sexagesimal,
binary), in several node contexts (block/flow, key/value, quoted, tagged, anchored/aliased), plus
merges, aliases, tags, block scalars (all chomping/indent indicators), folding of multi-line
plain/quoted scalars, escapes, comments, document markers, line breaks, BOM/encodings.
"""
import json, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
OUT = os.path.join(ROOT, 'jekyll', 'tests', 'yaml', 'typing-cases.jsonl')
FILES = os.path.join(ROOT, 'jekyll', 'tests', 'yaml', 'typing-files')
cases = []


def add(cid, text):
    cases.append({'id': 'typing/' + cid, 'type': 'yaml-typed', 'text': text})


def add_file(cid, data):
    os.makedirs(FILES, exist_ok=True)
    name = cid.replace('/', '_') + '.yml'
    with open(os.path.join(FILES, name), 'wb') as f:
        f.write(data)
    cases.append({'id': 'typing/file/' + cid, 'type': 'yaml-typed-file',
                  'path': '/w/' + os.path.relpath(os.path.join(FILES, name), ROOT)})


INTS = ['0', '-0', '+0', '1', '+1', '-1', '007', '0_7', '08', '019', '0o17', '0x1F', '0X1f', '0x', '0xg', '-0x1F',
        '+0x1f', '-0X1F', '0x_1F', '0x1_F', '0b101', '0b1_01', '0B101', '-0b101', '0b', '0b2', '0b_', '1_000', '1,000',
        '1,,0', '_1', '1_', '__', '_', '1__2', '12345678901234567890', '-9223372036854775808', '9223372036854775807',
        '9223372036854775808', '-9223372036854775809', '1:30', '1:3', '1:60', '1:59', '190:20:30', '-1:30', '+1:30',
        '1_0:30', '1,0:30', '01:30', '1:30:00', '0:0', '1:0_0', '1:2:3:4:5:6:7:8:9:10:11:12:13:14:15:16', '0_0', '00',
        '0,1', '1_000_000_000_000_000_000', '+_1', '-_1', '1e3', '0x1F_', '0xFFFFFFFFFFFFFFFFFF']
FLOATS = ['1.0', '.5', '-.5', '+.5', '1.', '1.e5', '1.0e5', '1.0e+5', '1.0E-5', '1.0e-05', '1.0e5x', '1_000.5',
          '1__0.5', '1_.5', '._5', '1._5', '1.5_', '1.5_5', '00.5', '0_0.5', '-0.0', '+0.0', '0.0', '1.0e+400',
          '1.0e-400', '1.0e+0400', '1.0e+000000005', '.inf', '.Inf', '.INF', '-.inf', '-.Inf', '-.INF', '+.inf',
          '.iNf', '.nan', '.NaN', '.NAN', '-.nan', '1:30.5', '1:30.', '1:30._5', '1:30.5_', '-1:30.5', '1_:30.5',
          '1:30.5_5', '0.1', '0.30000000000000004', '123456789012345678901234567890.5', '1e5', '1.5e5',
          '3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798',
          '0.000000000000000000000000000000000000000000000000000000000000000000000000000001234567890123456789012345678901234567890123456789012345678901234567890123',
          '1_2_3_4_5_6_7_8_9_0_1_2_3_4_5_6_7_8_9_0_1_2_3_4_5_6_7_8_9_0_1_2_3_4_5_6_7_8_9_0_1_2_3_4.5',
          '1.7976931348623157e+308', '1.7976931348623159e+308', '4.9e-324', '2.4703282292062328e-324', '1.0e+5_0',
          '100.0', '1e+5', '0.0001', '0.00001', '12345678.5', '1234567890123456.0', '12345678901234567.0', '.0',
          '-.0_0', '9.999999999999999e15', '1.0000000000000002', '123.456e+7', '.5e-3']
NILS = ['~', 'null', 'Null', 'NULL', 'nUlL', 'nulls', 'nil', 'None', '~~', 'nul']
BOOLS = ['yes', 'Yes', 'YES', 'yEs', 'no', 'No', 'NO', 'on', 'On', 'ON', 'off', 'Off', 'OFF', 'true', 'True', 'TRUE',
         'false', 'False', 'FALSE', 'fAlSe', 'y', 'n', 'Y', 'N', 'yess', 'truee', 'ok']
DATES = ['2020-01-01', '2020-1-1', '2020-02-29', '2021-02-29', '2020-02-30', '2020-13-01', '2020-00-01',
         '2020-01-00', '0000-01-01', '1500-02-29', '1582-10-04', '1582-10-05', '1582-10-14', '1582-10-15',
         '1700-02-29', '1900-02-29', '2000-02-29', '9999-12-31', '2020-01-01x', '12020-01-01', '2020-01-32',
         '1930-02-29', '1931-12-31', '1582-02-29', '1600-02-29']
TIMES = ['2001-12-14t21:59:43.10-05:00', '2001-12-14T21:59:43.10-05:00', '2001-12-14 21:59:43.10 -5',
         '2001-12-14 21:59:43.10', '2001-12-14 21:59:43', '2001-12-14 21:59:43Z', '2001-12-14 21:59:43 Z',
         '2001-12-14T21:59:43+0530', '2001-12-14T21:59:43+530', '2001-12-14T21:59:43+5:30', '2001-12-14T21:59:43+05',
         '2001-12-14T21:59:43-05:00', '2001-12-14T21:59:43+24:00', '2001-12-14T21:59:43+2400',
         '2001-12-14T21:59:43+2359', '2001-12-14T21:59:43+1800', '2001-12-14T21:59:43-1800', '2001-12-14T21:59:43+99',
         '2001-12-14T21:59:43+23:00', '2001-12-14T21:59:43-12:60', '2001-12-14 24:00:00', '2001-12-14 24:00:01',
         '2001-12-14 23:60:00', '2001-12-14 23:59:60', '2001-12-14 23:59:61', '2001-1-1 1:00:00',
         '2001-12-14 21:59:43.', '2001-12-14 21:59:43.123456789123', '2001-12-14 21:59:43.000000001',
         '2001-12-14  21:59:43', '2001-12-14 21:59:43   +01:00', '1582-10-10 00:00:00', '1500-02-29 12:00:00',
         '0000-01-01 00:00:00', '2001-02-30 00:00:00', '2001-12-31 24:00:00', '2001-12-31 23:59:59 -23:59',
         '2001-12-14 21:59:43.' + '1' * 120, '2001-12-14 21:59:43.' + '1' * 107, '2001-12-14 21:59:43.' + '1' * 108,
         '2001-12-14T21:59:43.5Z', '2001-12-14 9:59:43', '2001-12-14 21:59:43 +0', '2001-12-14 21:59:43 -00:00',
         '2001-13-14 21:59:43', '1969-12-31 23:59:59', '1931-01-01 00:00:00 +05:00', '1700-02-29 00:00:00',
         '2001-12-14 21:59:43+1:00', '2001-12-14 21:59:43 +12345']
STRINGS = ['abc', 'a b', '<<', '=', '-', '0x', 'x:y', 'a:b', 'http://x.org/a:b', '1 2', '1.2.3', '1-2-3', '+',
           '1st', 'é', '日本', '😀', 'yes!', '12:30pm', '1/2', '$10', '%x', '@x']
SCALARS = INTS + FLOATS + NILS + BOOLS + DATES + TIMES + STRINGS

for i, s in enumerate(SCALARS):
    k = '%03d' % i
    add('scalar/%s/map-value' % k, 'v: %s\n' % s)
    add('scalar/%s/seq' % k, '- %s\n' % s)
    add('scalar/%s/flow-seq' % k, '[%s]\n' % s)
    add('scalar/%s/flow-map' % k, '{k: %s}\n' % s)
    add('scalar/%s/key' % k, '%s: v\nother: w\n' % s)
    add('scalar/%s/top' % k, s + '\n')
    add('scalar/%s/single' % k, "v: '%s'\n" % s.replace("'", "''"))
    add('scalar/%s/double' % k, 'v: "%s"\n' % s.replace('\\', '\\\\').replace('"', '\\"'))
    add('scalar/%s/str-tag' % k, 'v: !!str %s\n' % s)
    add('scalar/%s/bang-tag' % k, 'v: ! %s\n' % s)
    add('scalar/%s/bang-quoted' % k, "v: ! '%s'\n" % s.replace("'", "''"))
    add('scalar/%s/local-tag-quoted' % k, 'v: !foo "%s"\n' % s.replace('\\', '\\\\').replace('"', '\\"'))
    add('scalar/%s/anchor-alias' % k, 'a: &x %s\nb: *x\n' % s)
    add('scalar/%s/quoted-anchor-alias' % k, "a: &x '%s'\nb: *x\nc: [*x]\n" % s.replace("'", "''"))
    add('scalar/%s/literal' % k, 'v: |\n  %s\nw: *nope\n' % s)
    add('scalar/%s/alias-of-literal' % k, 'a: &x |-\n  %s\nb: *x\n' % s)
    add('scalar/%s/trailing-comment' % k, 'v: %s # c\n' % s)

# alias of a quoted string with a trailing newline: the \Z-before-final-newline paths
for i, s in enumerate(['12', '2020-01-01', '1.5', 'null', 'yes', '0x1F', '1:30', '2001-12-14 21:59:43', '1.5_']):
    add('alias-newline/%02d' % i, 'a: &x "%s\\n"\nb: *x\n' % s)
    add('alias-2newline/%02d' % i, 'a: &x "%s\\n\\n"\nb: *x\n' % s)
    add('alias-space/%02d' % i, 'a: &x "%s "\nb: *x\n' % s)
    add('alias-literal-keep/%02d' % i, 'a: &x |\n  %s\nb: *x\n' % s)

# merges
MERGES = {
    'alias': 'b: &b {x: 1, y: 1}\na:\n  <<: *b\n  y: 2\n',
    'alias-after': 'b: &b {x: 1, y: 1}\na:\n  y: 2\n  <<: *b\n',
    'inline': 'a: {<<: {x: 1}, y: 2}\n',
    'inline-block': 'a:\n  <<:\n    x: 1\n  y: 2\n',
    'seq': 'b: &b {x: 1}\nc: &c {z: 3}\na: {<<: [*b, *c], y: 2}\n',
    'scalar': 'a: {<<: 1}\n',
    'string': 'a: {<<: foo}\n',
    'nil': 'a: {<<: ~}\n',
    'empty': 'a: {<<: }\n',
    'true': 'a: {<<: yes}\n',
    'float': 'a: {<<: 1.5}\n',
    'date': 'a: {<<: 2020-01-01}\n',
    'time': 'a: {<<: 2020-01-01 00:00:00}\n',
    'quoted-key': "b: &b {x: 1}\na: {'<<': *b}\n",
    'double-quoted-key': 'b: &b {x: 1}\na: {"<<": *b}\n',
    'str-tag-key': 'b: &b {x: 1}\na: {!!str <<: *b}\n',
    'nested': 'b: &b {x: {p: 1}}\na: {<<: *b, x: 2}\n',
    'self': 'a: &a {x: 1, <<: *a}\n',
    'twice': 'b: &b {x: 1}\nc: &c {x: 2, z: 3}\na:\n  <<: *b\n  <<: *c\n',
    'top': 'b: &b {x: 1}\n<<: *b\n',
    'in-seq': 'b: &b {x: 1}\nl:\n  - <<: *b\n    y: 2\n',
    'unknown-alias': 'a: {<<: *nope}\n',
    'seq-alias': 'b: &b [1, 2]\na: {<<: *b}\n',
    'merge-into-anchor': 'b: &b {x: 1}\na: &a\n  <<: *b\nc: *a\n',
    'mutate-after': 'b: &b {x: 1}\na: {<<: *b}\nc: *b\n',
}
for k, v in MERGES.items():
    add('merge/' + k, v)

# aliases, anchors, keys
MISC = {
    'alias-map': 'a: &x {p: 1}\nb: *x\n',
    'alias-seq': 'a: &x [1, 2]\nb: *x\n',
    'alias-unknown': 'a: *nope\n',
    'alias-key': 'a: &k key\n*k : v\n',
    'alias-key-map': 'a: &k {p: 1}\n? *k\n: v\n',
    'alias-recursive-seq': 'a: &a [1, *a]\n',
    'alias-recursive-map': 'a: &a {x: *a}\n',
    'anchor-redefined': 'a: &x 1\nb: &x 2\nc: *x\n',
    'anchor-on-key': '&k a: b\nc: *k\n',
    'anchor-tag-order': 'a: !!str &x 12\nb: &y !!str 13\nc: *x\nd: *y\n',
    'anchor-empty': 'a: &x\nb: *x\n',
    'dup-key': 'a: 1\nb: 2\na: 3\n',
    'int-key': '1: a\n"1": b\n',
    'bool-key': 'yes: a\ntrue: b\n',
    'nil-key': '~: a\nb: c\n',
    'nil-key-flow': '{null: a, b: c}\n',
    'empty-key': ': a\nb: c\n',
    'float-key': '1.5: a\n',
    'date-key': '2020-01-01: a\n',
    'time-key': '2001-12-14 21:59:43: a\n',
    'complex-seq-key': '? [a, b]\n: c\n',
    'complex-map-key': '? {a: b}\n: c\nd: e\n',
    'complex-map-key-2': '? {a: b, c: d}\n: e\nf: g\n',
    'complex-nested-key': '? - a\n  - b\n: c\n',
    'key-no-value': '? a\nb: c\n',
    'key-only': '? a\n',
    'value-only': ': v\n',
    'set': '? a\n? b\n? c\n',
    'flow-pair': '[a: b, c]\n',
    'flow-pair-key': '[? a : b, ? c]\n',
    'flow-map-no-values': '{a, b, c: d}\n',
    'flow-nested': '{a: [1, {b: [2, 3]}], c: {d: e}}\n',
    'flow-multiline': '[a,\n  b,\n c\n]\n',
    'flow-empty': '[{}, [], {a: []}]\n',
    'flow-trailing-comma': '[a, b, ]\n',
    'flow-map-trailing-comma': '{a: 1, }\n',
    'flow-colon-adjacent': '{"a":b, "c":[d]}\n',
    'flow-colon-error': '[a:,b]\n',
    'flow-in-block': 'a: [1, 2]\nb: {c: d}\n',
    'indentless-seq': 'a:\n- 1\n- 2\nb: 3\n',
    'nested-seq': '- - a\n  - b\n- - c\n',
    'seq-of-maps': '- a: 1\n  b: 2\n- c: 3\n',
    'empty-values': 'a:\nb: \nc: ~\n',
    'empty-seq-entries': '-\n- \n- ~\n',
    'deep': ''.join('  ' * i + 'k%d:\n' % i for i in range(150)) + '  ' * 150 + 'v\n',
    'deep-flow': '[' * 120 + ']' * 120 + '\n',
    'long-key': 'k' * 1100 + ': v\n',
    'long-key-1023': 'k' * 1023 + ': v\n',
    'long-key-flow': '{' + 'k' * 1100 + ': v}\n',
    'tab-indent': 'a:\n\tb: c\n',
    'tab-after-value': 'a:\t1\n',
    'tab-in-flow': '[\t1,\t2]\n',
    'tab-in-plain': 'a: b\tc\n',
}
for k, v in MISC.items():
    add('misc/' + k, v)

# tags
TAGS = {
    'binary': 'a: !binary SGVsbG8=\n',
    'binary-yaml': 'a: !!binary SGVsbG8gd29ybGQ=\n',
    'binary-verbatim': 'a: !<tag:yaml.org,2002:binary> SGk=\n',
    'binary-x-private': 'a: !<x-private:binary> SGk=\n',
    'binary-block': 'a: !!binary |\n  SGVsbG8g\n  d29ybGQ=\n',
    'binary-garbage': 'a: !!binary "S*G#V$s bG8"\n',
    'binary-no-pad': 'a: !!binary SGVsbG8\n',
    'binary-one-extra': 'a: !!binary SGVsbG8gd\n',
    'binary-invalid-utf8': 'a: !!binary //79\n',
    'binary-empty': 'a: !!binary\n',
    'binary-pad-mid': 'a: !!binary SG=VsbG8=\n',
    'binary-typed-looking': 'a: !!binary MTIz\n',
    'binary-alias': 'a: &x !!binary MTIz\nb: *x\n',
    'binary-key': '!!binary MTIz: v\n',
    'binary-on-map': 'a: !!binary {x: 1}\n',
    'int-tag': 'a: !!int "12"\n',
    'float-tag': 'a: !!float 1\n',
    'null-tag': 'a: !!null ""\n',
    'bool-tag': 'a: !!bool "yes"\n',
    'timestamp-tag': 'a: !!timestamp 2001-12-14\n',
    'map-tag': 'a: !!map {x: 1}\n',
    'seq-tag': 'a: !!seq [1]\n',
    'omap': 'a: !!omap\n  - x: 1\n',
    'set-tag': 'a: !!set {x: , y: }\n',
    'ruby-object': 'a: !ruby/object:Object {}\n',
    'ruby-sym': 'a: !ruby/symbol foo\n',
    'ruby-regexp': 'a: !ruby/regexp /x/\n',
    'local': 'a: !foo bar\n',
    'local-empty': 'a: !foo\nb: c\n',
    'bang-empty': 'a: !\nb: c\n',
    'verbatim': 'a: !<tag:x.org,2000:x> 12\n',
    'escaped-uri': 'a: !foo%21bar 12\n',
    'escaped-uri-bad': 'a: !foo%zz 12\n',
    'escaped-nul': 'a: !binary%00 SGk=\n',
    'tag-directive': '%TAG !e! tag:example.com,2000:\n---\na: !e!foo 12\n',
    'tag-directive-binary': '%TAG !y! tag:yaml.org,2002:\n---\na: !y!binary SGk=\n',
    'tag-directive-bang': '%TAG ! tag:yaml.org,2002:\n---\na: !binary SGk=\n',
    'tag-undefined-handle': 'a: !e!foo 12\n',
    'tag-dup-directive': '%TAG !e! a:\n%TAG !e! b:\n---\nx: 1\n',
    'tag-before-anchor': 'a: !!str &x 1\nb: *x\n',
    'tag-flow-comma': '[!!str , a]\n',
    'tag-bad-end': 'a: !foo[ 1\n',
    'tag-seq-entry': '- !!str\n- 1\n',
}
for k, v in TAGS.items():
    add('tag/' + k, v)

# block scalars
BODY = '\n  line1\n   more\n\n  line2\n\n\n'
for ind in ['|', '>']:
    for chomp in ['', '-', '+']:
        for n in ['', '1', '2', '3']:
            for order in ([0, 1] if n and chomp else [0]):
                hdr = ind + (chomp + n if order == 0 else n + chomp)
                add('block/%s/%s' % (hdr.replace('|', 'lit').replace('>', 'fold'), 'body'), 'a: ' + hdr + BODY + 'b: x\n')
                add('block/%s/%s' % (hdr.replace('|', 'lit').replace('>', 'fold'), 'eof'), 'a: ' + hdr + BODY)
                add('block/%s/%s' % (hdr.replace('|', 'lit').replace('>', 'fold'), 'leading'), 'a: ' + hdr + '\n\n\n  x\n   y\n  z\n\n')
                add('block/%s/%s' % (hdr.replace('|', 'lit').replace('>', 'fold'), 'empty'), 'a: ' + hdr + '\nb: 1\n')
                add('block/%s/%s' % (hdr.replace('|', 'lit').replace('>', 'fold'), 'top'), '--- ' + hdr + '\n x\n\n y\n')
BLOCKS = {
    'comment-header': 'a: | # comment\n  x\n',
    'bad-header': 'a: | x\n  y\n',
    'zero-indent': 'a: |0\n  x\n',
    'tab-indent': 'a: |\n\tx\n',
    'tab-content': 'a: |\n  \tx\n  y\n',
    'folded-more-indented': 'a: >\n  a\n  b\n\n    c\n    d\n\n  e\n  f\n',
    'folded-spaces': 'a: >\n  a \n  b  \n',
    'literal-less-indented': 'a: |\n    x\n  y\n',
    'literal-in-seq': '- |\n  x\n- >\n  y\n',
    'literal-crlf': 'a: |\r\n  x\r\n  y\r\n',
    'literal-doc-marker': 'a: |\n  x\n---\nb: 1\n',
    'literal-dots': '--- |\n  x\n...\n',
    'indicator-indent-nested': 'a:\n  b: |2\n     x\n    y\n',
    'trailing-spaces-only-lines': 'a: |\n  x\n  \n   \n  y\n',
    'folded-trailing-lines-keep': 'a: >+\n  x\n\n\n',
    'literal-unicode-breaks': 'a: |\n  x\u2028y\u2029z\x85w\n',
}
for k, v in BLOCKS.items():
    add('blockmisc/' + k, v)

# folding of multi-line plain and quoted scalars, escapes
FOLD = {
    'plain-multiline': 'a: one\n  two\n\n  three\n\n\n  four\nb: x\n',
    'plain-trailing-ws': 'a: one   \n  two\t\n',
    'plain-comment-between': 'a: one\n  # not a comment? \n  two\n',
    'plain-hash': 'a: one#two #three\n',
    'plain-top-multiline': 'one\ntwo\n\nthree\n',
    'plain-flow-multiline': '[one\n two,\n three\n\n four]\n',
    'plain-less-indented': 'a:\n  b: one\n two\n',
    'plain-colons': 'a: b:c d:e\n',
    'plain-question': '? a\n: ?b\n',
    'plain-dash': 'a: -b\nc: - d\n',
    'single-multiline': "a: 'one\n  two\n\n  three'\n",
    'single-escape': "a: 'it''s'\n",
    'single-trailing-ws': "a: 'one   \n   two'\n",
    'single-crlf': "a: 'one\r\n  two'\r\n",
    'double-escapes': r'a: "\0\a\b\t\	\n\v\f\r\e\ \"\/\\\N\_\L\P\x41\u00e9\U0001F600"' + '\n',
    'double-escaped-break': 'a: "one\\\n  two\\\n\n  three"\n',
    'double-escaped-space-break': 'a: "one \\\n  two"\n',
    'double-multiline': 'a: "one\n  two\n\n  three  \n   four"\n',
    'double-bad-escape': 'a: "\\q"\n',
    'double-bad-hex': 'a: "\\x4g"\n',
    'double-surrogate': 'a: "\\ud800"\n',
    'double-too-big': 'a: "\\U00110000"\n',
    'double-unterminated': 'a: "abc\n',
    'double-doc-marker': 'a: "abc\n---\n"\n',
    'double-nul': 'a: "a\\0b"\n',
    'double-ls': 'a: "x\u2028  y"\n',
    'double-nel': 'a: "x\x85  y"\n',
    'double-tab-break': 'a: "x\t\n\ty"\n',
}
for k, v in FOLD.items():
    add('fold/' + k, v)

# comments and document markers
DOCS = {
    'comments-everywhere': '# c0\n--- # c1\na: 1 # c2\n# c3\nb: # c4\n  - x # c5\n  # c6\n  - y\n... # c7\n# c8\n',
    'comment-no-space': 'a: 1#c\n',
    'empty': '',
    'only-comment': '# x\n',
    'only-marker': '---\n',
    'marker-scalar': '--- a\n',
    'marker-end': '---\n...\n',
    'end-only': '...\n',
    'two-docs': 'a: 1\n---\nb: 2\n',
    'two-docs-error-second': 'a: 1\n--- [\n',
    'two-docs-error-second-explicit': 'a: 1\n...\n--- [\n',
    'error-in-same-chunk-after': 'a: 1\n--- b\n? c: [\n',
    'second-doc-first-empty': '---\n---\na: 1\n',
    'explicit-end-then-garbage': 'a: 1\n... x\n',
    'yaml-11': '%YAML 1.1\n---\na: 1\n',
    'yaml-12': '%YAML 1.2\n--- a\n',
    'yaml-20': '%YAML 2.0\n--- a\n',
    'yaml-13': '%YAML 1.3\n--- a\n',
    'yaml-dup': '%YAML 1.1\n%YAML 1.1\n--- a\n',
    'directive-no-doc': '%YAML 1.1\na: 1\n',
    'directive-unknown': '%FOO bar\n--- a\n',
    'directive-comment': '%YAML 1.1 # c\n--- a\n',
    'marker-in-plain': 'a: b\n--- \nc\n',
    'marker-indented': 'a: b\n ---\n',
    'dots-indented': 'a: |\n  x\n ...\n',
    'crlf': 'a: 1\r\nb:\r\n  - x\r\n',
    'cr-only': 'a: 1\rb: 2\r',
    'nel-breaks': 'a: 1\x85b: 2\x85',
    'ls-breaks': 'a: 1\u2028b: 2\u2029',
    'bom-start': '\ufeffa: 1\nb: 2\n',
    'bom-start-seq': '\ufeff- 1\n- 2\n',
    'bom-start-marker': '\ufeff---\na: 1\nb: 2\n',
    'bom-line': 'a: 1\n\ufeffb: 2\n',
    'bom-mid': 'a: x\ufeffy\n',
    'no-final-newline': 'a: 1',
    'control-char': 'a: "\x01"\n',
    'del-char': 'a: x\x7fy\n',
    'nel-in-plain': 'a: x\x85y\n',
    'astral': 'a: 😀\n',
    'cannot-start': 'a: @x\n',
    'backtick': 'a: `x\n',
    'percent-plain': 'a: %x\n',
    'block-in-flow': '[a, - b]\n',
    'mapping-in-seq-error': '- a\nb: c\n',
    'bad-indent': 'a:\n  b: 1\n c: 2\n',
    'simple-key-multiline': 'a\nb: c\n',
    'required-key': 'a: 1\nb\n',
}
for k, v in DOCS.items():
    add('doc/' + k, v)

# files: BOM / encodings / invalid UTF-8 (SafeYAML.load_file opens with 'r:bom|utf-8')
add_file('utf8', 'a: é\n'.encode())
add_file('utf8-bom', b'\xef\xbb\xbf' + 'a: é\nb: 1\n'.encode())
add_file('utf8-bom-twice', b'\xef\xbb\xbf\xef\xbb\xbfa: 1\nb: 2\n')
add_file('utf16le-bom', b'\xff\xfe' + 'a: é\nb: [1, 2]\n'.encode('utf-16-le'))
add_file('utf16be-bom', b'\xfe\xff' + 'a: é\nb: [1, 2]\n'.encode('utf-16-be'))
add_file('utf16le-astral', b'\xff\xfe' + 'a: 😀\n'.encode('utf-16-le'))
add_file('utf16le-odd', b'\xff\xfe' + 'a: 1\n'.encode('utf-16-le') + b'\x41')
add_file('utf16le-lone-low', b'\xff\xfe' + 'a: '.encode('utf-16-le') + b'\x00\xdc' + '\n'.encode('utf-16-le'))
add_file('utf16le-lone-high', b'\xff\xfe' + 'a: '.encode('utf-16-le') + b'\x00\xd8\x41\x00' + '\n'.encode('utf-16-le'))
add_file('utf32le-bom', b'\xff\xfe\x00\x00' + 'a: 1\n'.encode('utf-32-le'))
add_file('utf32be-bom', b'\x00\x00\xfe\xff' + 'a: 1\n'.encode('utf-32-be'))
add_file('utf16-no-bom', 'a: 1\n'.encode('utf-16-le'))
add_file('invalid-lead', b'a: \xff\n')
add_file('invalid-trail', b'a: \xc3\x28\n')
add_file('overlong', b'a: \xc0\xaf\n')
add_file('surrogate', b'a: \xed\xa0\x80\n')
add_file('too-big', b'a: \xf4\x90\x80\x80\n')
add_file('incomplete', b'a: \xe2\x82')
add_file('latin1', 'a: café\n'.encode('latin-1'))
add_file('nul-byte', b'a: 1\x00\n')
add_file('control', b'a: \x01\n')
add_file('invalid-after-16k', b'a: 1\n' + b'# ' + b'x' * 17000 + b'\n' + b'b: \xff\n')
add_file('invalid-in-second-doc-16k', b'a: 1\n---\n' + b'# ' + b'x' * 17000 + b'\n' + b'b: \xff\n')
add_file('invalid-in-second-doc-near', b'a: 1\n---\nb: \xff\n')
add_file('invalid-chunk-boundary', b'a: 1\n#' + b'x' * (16384 - 8) + b'\n' + 'é'.encode() * 3 + b'\n---\n\xff')
add_file('empty', b'')
add_file('bom-only', b'\xef\xbb\xbf')
add_file('crlf-bom', b'\xef\xbb\xbfa: 1\r\nb: 2\r\n')
add_file('error-message-path', b'a: [1\n')

with open(OUT, 'w') as f:
    for c in cases:
        f.write(json.dumps(c) + '\n')
print(len(cases), 'cases ->', OUT)
