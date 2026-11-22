#!/usr/bin/env python3
"""Builds jekyll/tests/liquid/cases.jsonl (oracle format, type: liquid).

Sources:
  1. suite-strict.jsonl / suite-lax.jsonl: liquid-4.0.4's own test suite, recorded by
     record_suite.rb (every Template.parse/render and direct StandardFilters call with plain-JSON
     inputs). Each recorded template is also replayed in warn mode.
  2. Hand-written edge cases below (HAND), each rendered with render and render!.
  3. Real-IG malformed Liquid (data/strict.jsonl patterns), warn mode, with plausible data (IG).

usage: python3 make_cases.py   (writes ../cases.jsonl)
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'cases.jsonl')

cases = []
seen = set()


def add(id_, template, assigns=None, mode='warn', bang=False, files=None, sv=False, sf=False):
    c = {'id': id_, 'type': 'liquid', 'template': template, 'error_mode': mode}
    if assigns:
        c['assigns'] = assigns
    if bang:
        c['render_bang'] = True
    if files:
        c['files'] = files
    if sv:
        c['strict_variables'] = True
    if sf:
        c['strict_filters'] = True
    key = json.dumps({k: v for k, v in c.items() if k != 'id'}, sort_keys=True)
    if key in seen:
        return
    seen.add(key)
    cases.append(c)


# ------------------------------------------------------------------ 1. recorded suite
for mode in ('strict', 'lax'):
    with open(os.path.join(HERE, '..', 'suite-%s.jsonl' % mode)) as f:
        for line in f:
            c = json.loads(line)
            add(c['id'], c['template'], c.get('assigns'), c['error_mode'], c.get('render_bang', False),
                c.get('files'), c.get('strict_variables', False), c.get('strict_filters', False))
n = 0
with open(os.path.join(HERE, '..', 'suite-strict.jsonl')) as f:
    for line in f:
        c = json.loads(line)
        n += 1
        add('suite-warn-%d' % n, c['template'], c.get('assigns'), 'warn', c.get('render_bang', False),
            c.get('files'), c.get('strict_variables', False), c.get('strict_filters', False))

# ------------------------------------------------------------------ 2. hand-written
PAGES = {
    'index': {'title': 'Home', 'label': '1.0', 'titlelang': {'en': 'Home', 'fr': 'Accueil'}},
    'profiles.html': {'title': 'Profiles', 'label': '2.3.0', 'titlelang': {'en': 'Profiles', 'de': 'Profile'}},
}
SITE = {'data': {'pages': PAGES, 'fhir': {'path': 'http://hl7.org/fhir/R4/', 'packageId': 'hl7.fhir.us.core', 'version': '4.0.1'},
                 'resources': {'StructureDefinition/foo': {'name': 'Foo', 'description': 'Foo desc', 'history': 'foo-hist.html'},
                               'OperationDefinition/submit-attachment': {'description': 'Submit it'}},
                 'structuredefinitions': {'foo': {'type': 'Patient', 'url': 'http://x/foo'}},
                 'languages': {'langs': ['en', 'fr']}},
        'time': '2023-05-06 07:08:09 +0000'}

H = []  # (template, assigns)


def h(t, a=None):
    H.append((t, a))


# --- output / to_s
for v in [None, True, False, 0, -5, 1.0, 0.1, 1.5, -0.0, 1e20, 1e15, 1e16, 123456789.123, 1e-4, 1e-5,
          2.5e-7, 100.0, "s", [], [1, [2, [3]], None, "x"], {}, {"a": 1, "b": [1, 2.5, None, True], "c": {"d": "e\"f"}},
          [{"a": 1}, {"b": "x"}], {"s": "line\nbreak\t#{x} \\ é"}]:
    h('[{{ v }}]', {'v': v})
h('{{ 1.0 }}|{{ 2.50 }}|{{ -3.14 }}|{{ 007 }}|{{ 1.2.3 }}|{{ -0 }}|{{ 10000000000000000000000 }}')
h('{{ (1..5) }}|{{ (3..1) }}|{{ (a..b) }}', {'a': '2', 'b': 4.7})
h('{{ nil }}{{ null }}{{ true }}{{ false }}{{ blank }}{{ empty }}|{{ "" }}|{{ \'\' }}')
h('{{ forloop }}{% for i in (1..1) %}{{ forloop }}{% endfor %}')

# --- lookups
deep = {'site': SITE, 'localPage': 'profiles.html', 'include': {'lang': 'de', 'file': 'pages', 'id': 'foo'}, 'page': {'path': 'index', 'id': 'x'}}
h('{{ site.data.pages[localPage].titlelang[include.lang] }}|{{ site.data.pages["index"].title }}|{{ site.data.pages.index.label }}', deep)
h("{{ site.data['pages']['profiles.html']['label'] }}|{{ site.data.pages[page.path].titlelang.fr }}|{{ site.data.pages[nope].title }}", deep)
h('{{ site.data.languages.langs.size }}|{{ site.data.languages.langs.first }}|{{ site.data.languages.langs.last }}|{{ site.data.languages.langs[-1] }}|{{ site.data.languages.langs[5] }}', deep)
h('{{ a.size }}|{{ a.first }}|{{ a.last }}|{{ h.size }}|{{ h.first }}|{{ h.last }}|{{ s.size }}|{{ s.first }}|{{ n.size }}|{{ f.size }}|{{ nil.size }}',
  {'a': [1, 2, 3], 'h': {'k': 'v', 'k2': 'v2'}, 's': 'héllo', 'n': 42, 'f': 1.5})
h('{{ h.size }}|{{ h["size"] }}|{{ h.first }}', {'h': {'size': 'big', 'first': 'f'}})
h('{{ a[0] }}|{{ a[1][0] }}|{{ a[1].size }}|{{ a["0"] }}|{{ a[x] }}|{{ a[y] }}', {'a': ['p', ['q', 'r']], 'x': 1, 'y': '1'})
h('{{ h[1] }}|{{ h["1"] }}|{{ h[k] }}', {'h': {'1': 'one'}, 'k': '1'})
h('{{ [k] }}|{{ ["k"] }}|{{ [0] }}', {'k': 'name', 'name': 'Bob'})
h('{{ a-b }}|{{ a_b }}|{{ a.b-c }}|{{ x? }}', {'a-b': 'dash', 'a_b': 'us', 'a': {'b-c': 'bc'}, 'x?': 'q'})
h('{{ s.size }}{{ s[0] }}{{ s.length }}', {'s': 'abc'})
h('{{ a.first.first }}|{{ a.first.size }}|{{ h.first.first }}', {'a': [[1, 2], 3], 'h': {'x': 1}})
h('{{ product.variants[0].title }}|{{ product.variants.first.title }}|{{ product.variants.last.title | upcase }}',
  {'product': {'variants': [{'title': 'draft151cm'}, {'title': 'element151cm'}]}})
h('{{ a.b.c.d.e }}|{{ a.b.c }}|{{ a.x.y }}', {'a': {'b': {'c': {'d': {'e': 'deep'}}}}})
h('{{ list[list.size] }}|{{ list[-4] }}|{{ list[-3] }}', {'list': [1, 2, 3]})

# --- whitespace control
h('a  {%- if true -%}  b  {%- endif -%}  c')
h('a  {{- "x" -}}  b\n\n{{- "y" }}  \n{{ "z" -}}\n  end')
h('<ul>\n  {%- for i in (1..3) %}\n  <li>{{ i -}} </li>\n  {%- endfor %}\n</ul>')
h('{%- assign x = 1 -%}\n\n  [{{ x }}]\n{%- comment -%} c {%- endcomment -%}\n  [y]')
h('{% raw -%}  {{ x }}  {%- endraw %}  after')
h(' \t\n{%- capture c -%}\n  inner  \n{%- endcapture -%}\n[{{ c }}]')
h('a\n{%- if false %}x{% elsif true -%}\n  y\n{%- else %}z{%- endif %}\nb')
h('{{-x-}}|{{- x}}|{{x -}}|{%-if true-%}t{%-endif-%}|', {'x': 'X'})
h('a \n {%- -%} b')

# --- assign / capture
h('{% assign a = "x" | append: "y" | upcase %}{{ a }}|{% assign b = a %}{{ b }}|{% assign c=1 %}{{ c | plus: 1 }}')
h('{% assign a.b = 5 %}{{ a.b }}|{{ ["a.b"] }}')
h('{% assign x = list %}{% assign x = x | sort %}{{ x | join: "," }}|{{ list | join: "," }}', {'list': [3, 1, 2]})
h('{% capture x %}a{{ 1 | plus: 2 }}b{% endcapture %}{{ x }}{{ x.size }}')
h('{% for i in (1..2) %}{% assign last = i %}{% capture cap %}{{ i }}{% endcapture %}{% endfor %}{{ last }}{{ cap }}')
h('{% if true %}{% assign inner = "set" %}{% endif %}{{ inner }}')
h('{% assign v = "env" %}{{ v }}', {'v': 'orig'})
h('{% assign = 5 %}x')
h('{% assign x %}')
h('{% assign x = %}[{{ x }}]')
h('{% capture %}x{% endcapture %}')
h("{% capture 'q' %}x{% endcapture %}{{ q }}")
h('{% assign x = 1 2 %}{{ x }}')
h('{% assign arr = "a,b,c" | split: "," %}{{ arr[1] }}{{ arr.size }}{{ arr | last }}')

# --- if / unless / conditions
for cond in ['a == b', 'a != b', 'a <> b', 'a < b', 'a > b', 'a <= b', 'a >= b', 'a contains b', 'a', 'b']:
    for a, b in [(1, 1), (1, 1.0), (1, 2), ('1', 1), ('a', 'b'), (None, None), (None, 1), ('abc', 'b'), ([1, 2], 2), ([1, 2], '2'),
                 ({'x': 1}, 'x'), (True, False), ('a', 1), (1, 'a'), (2.5, 'x'), ([], []), ({}, {})]:
        h('{%% if %s %%}T{%% else %%}F{%% endif %%}' % cond, {'a': a, 'b': b})
h('{% if a == empty %}E{% endif %}{% if empty == a %}E2{% endif %}{% if a == blank %}B{% endif %}{% if a != empty %}N{% endif %}', {'a': ''})
for v in ['', ' ', [], {}, None, 0, False, 'x', [None], {'k': None}]:
    h('{% if v == empty %}E{% else %}e{% endif %}{% if v == blank %}B{% else %}b{% endif %}{% if v %}T{% else %}F{% endif %}{% unless v %}U{% endunless %}', {'v': v})
h('{% if true and false or true %}1{% endif %}{% if false or true and false %}2{% endif %}{% if true or false and false %}3{% endif %}{% if false and true or true %}4{% endif %}')
h('{% if a and b or c %}x{% else %}y{% endif %}', {'a': True, 'b': False, 'c': True})
h('{% if a or b and c %}x{% else %}y{% endif %}', {'a': False, 'b': True, 'c': False})
h('{% if 1 == 1 and 2 == 2 and 3 == 4 %}x{% else %}y{% endif %}')
h('{% if "foo" contains "o" %}y{% endif %}{% if "foo" contains "x" %}n{% endif %}{% if 123 contains 2 %}n{% endif %}{% if s contains 1 %}y{% endif %}', {'s': 'a1b'})
h('{% if h contains "a" %}y{% endif %}{% if h contains "b" %}n{% endif %}{% if l contains 1.0 %}y{% endif %}{% if l contains nil %}n{% endif %}{% if (1..5) contains 3 %}r{% endif %}',
  {'h': {'a': 1}, 'l': [1, 2]})
h('{% if a contains false %}n{% endif %}{% if nil contains "x" %}n{% endif %}{% if a contains "" %}e{% endif %}', {'a': 'abc'})
h('{% if x == "1" %}s{% elsif x == 1 %}i{% else %}o{% endif %}', {'x': 1})
h('{% if a > b %}gt{% endif %}', {'a': 'b', 'b': 'a'})
h('{% if a < b %}lt{% endif %}', {'a': 'Z', 'b': 'a'})
h('{% if a < b %}lt{% endif %}after', {'a': 1, 'b': 'a'})
h('{% if a > b %}gt{% endif %}after', {'a': 'x', 'b': 2})
h('{% if a >= b %}ge{% endif %}after', {'a': [1], 'b': [0]})
h('{% if a < b %}lt{% endif %}after', {'a': {'x': 1}, 'b': 2})
h('{% if a == b %}eq{% endif %}', {'a': [1, {'x': 2}], 'b': [1.0, {'x': 2.0}]})
h('{% unless a %}not a{% elsif b %}b{% else %}else{% endunless %}', {'a': True, 'b': True})
h('{% unless a == 1 %}n1{% else %}one{% endunless %}', {'a': 1})
h('{% if site.data.languages.langs.size() != 1 %}multi{% endif %}', deep)
h('{% if x %}{% if y %}xy{% else %}x{% endif %}{% elsif z %}z{% endif %}', {'x': True, 'y': None, 'z': True})
h('{% if %}x{% endif %}')
h('{% if a b %}x{% endif %}', {'a': 1, 'b': 2})
h('{% if a = b %}x{% endif %}', {'a': 1, 'b': 1})
h('{% if a == %}x{% endif %}', {'a': 1})
h('{% if a == 1 or %}x{% endif %}', {'a': 1})
h('{% if a and %}x{% endif %}', {'a': 1})
h('{% if a xor b %}x{% endif %}', {'a': 1, 'b': 1})
h('{% if a == "x y" %}x{% endif %}{% if a == \'x y\' and true %}y{% endif %}', {'a': 'x y'})
h('{% if a.b == nil %}nil{% endif %}{% if a.b == null %}null{% endif %}', {'a': {}})
h('{% if a %}x{% else %}y{% else %}z{% endif %}', {'a': False})
h('{% if a %}x{% elsif %}y{% endif %}', {'a': False})
h('{% if a %}x{% endunless %}', {'a': True})
h('{% if a %}x', {'a': True})
h('{% else %}')
h('{% end %}')
h('{% endif %}')
h('{% foo %}')
h('{% if true %}{% foo %}{% endif %}')
h('{% if (1..3) == (1..3) %}r{% endif %}{% if (1..a) contains 2 %}c{% endif %}', {'a': 3})

# --- case
h('{% case x %}{% when 1 %}one{% when 2, 3 %}two-three{% when "a" or "b" %}ab{% else %}other{% endcase %}', {'x': 3})
for x in [1, 2, 'a', 'b', 'z', None, 1.0, '1']:
    h('{% case x %}{% when 1 %}one{% when 2, 3 %}two-three{% when "a" or "b" %}ab{% else %}other{% endcase %}', {'x': x})
h('{% case x %}{% when 1 %}a{% when 1 %}b{% endcase %}', {'x': 1})
h('{% case x %}{% when 1 %}a{% else %}b{% when 2 %}c{% endcase %}', {'x': 2})
h('{% case x %}{% when 1 %}a{% else %}b{% when 2 %}c{% endcase %}', {'x': 1})
h('{% case x %}junk{% when 1 %}a{% endcase %}', {'x': 1})
h('{% case x %}{% when empty %}e{% when blank %}b{% endcase %}', {'x': ''})
h('{% case %}{% when 1 %}a{% endcase %}')
h('{% case x %}{% else junk %}a{% endcase %}', {'x': 1})
h('{% case x %}{% when %}a{% endcase %}', {'x': 1})
h('{% case x.y %}{% when "z" %}Z{% endcase %}', {'x': {'y': 'z'}})
h('{% case x %}{% when 1 or 2 or 3 %}m{% endcase %}{% case x %}{% when 4,5 , 3 %}n{% endcase %}', {'x': 3})

# --- for
h('{% for i in (1..5) limit:2 %}{{ i }}{% endfor %}|{% for i in (1..5) offset:2 %}{{ i }}{% endfor %}|{% for i in (1..5) limit:2 offset:1 %}{{ i }}{% endfor %}')
h('{% for i in (1..5) reversed %}{{ i }}{% endfor %}|{% for i in (1..5) reversed limit:2 %}{{ i }}{% endfor %}|{% for i in (1..5) limit:2 reversed %}{{ i }}{% endfor %}')
h('{% for i in a limit: n offset: m %}{{ i }}{% endfor %}', {'a': [1, 2, 3, 4, 5], 'n': '2', 'm': 1.9})
h('{% for i in a limit:2 %}{{ i }}{% endfor %}|{% for i in a offset:continue limit:2 %}{{ i }}{% endfor %}|{% for i in a offset:continue %}{{ i }}{% endfor %}|{% for i in a offset:continue %}{{ i }}{% else %}none{% endfor %}',
  {'a': [1, 2, 3, 4, 5]})
h('{% for i in a %}{{ forloop.index }}/{{ forloop.index0 }}/{{ forloop.rindex }}/{{ forloop.rindex0 }}/{{ forloop.first }}/{{ forloop.last }}/{{ forloop.length }}/{{ forloop.name }} {% endfor %}',
  {'a': ['x', 'y', 'z']})
h('{% for i in a %}{% for j in b %}{{ forloop.parentloop.index }}.{{ forloop.index }}{{ forloop.parentloop.parentloop }} {% endfor %}{% endfor %}', {'a': [1, 2], 'b': [1, 2]})
h('{% for i in a %}{{ forloop.foo }}{{ forloop["index"] }}{{ forloop[0] }}{% endfor %}', {'a': [1]})
h('{% for i in a %}{% if i == 3 %}{% break %}{% endif %}{{ i }}{% endfor %}|{% for i in a %}{% if i == 3 %}{% continue %}{% endif %}{{ i }}{% endfor %}', {'a': [1, 2, 3, 4]})
h('{% for i in a %}{% for j in a %}{% if j == 2 %}{% break %}{% endif %}{{ i }}{{ j }} {% endfor %}{% endfor %}', {'a': [1, 2, 3]})
h('{% for i in a %}{{ i }}{% break %}x{% endfor %}y{% break %}z', {'a': [1, 2]})
h('{% for kv in h %}{{ kv[0] }}={{ kv[1] }};{% endfor %}|{% for kv in h %}{{ kv }};{% endfor %}', {'h': {'a': 1, 'b': [2, 3]}})
h('{% for c in s %}[{{ c }}]{% endfor %}|{% for c in e %}[{{ c }}]{% else %}empty{% endfor %}|{% for c in n %}[{{ c }}]{% else %}num{% endfor %}', {'s': 'abc', 'e': '', 'n': 5})
h('{% for i in nil %}x{% else %}nil{% endfor %}{% for i in missing %}x{% else %}missing{% endfor %}{% for i in (1..0) %}x{% else %}range{% endfor %}')
h('{% for i in (a..b) %}{{ i }}{% endfor %}', {'a': '1', 'b': '3'})
h('{% for i in (a..b) %}{{ i }}{% endfor %}', {'a': 1.5, 'b': 3})
h('{% for i in (1..3) %}{% for j in (i..3) %}{{ j }}{% endfor %},{% endfor %}')
h('{% for i in (1..3) %}{{ i }}{% else %}e{% else %}f{% endfor %}')
h('{% for i in %}x{% endfor %}')
h('{% for in a %}x{% endfor %}', {'a': [1]})
h('{% for i a %}x{% endfor %}', {'a': [1]})
h('{% for i in a limit %}{{ i }}{% endfor %}', {'a': [1, 2]})
h('{% for i in a foo:2 %}{{ i }}{% endfor %}', {'a': [1, 2]})
h('{% for i in a limit:1 offset %}{{ i }}{% endfor %}', {'a': [1, 2]})
h('{% for i in a | sort %}{{ i }}{% endfor %}', {'a': [2, 1]})
h('{% for i in a.b %}{{ i }}{% endfor %}{% for i in a["b"] reversed %}{{ i }}{% endfor %}', {'a': {'b': [1, 2]}})
h('{% for i in a limit:true %}{{ i }}{% endfor %}', {'a': [1, 2]})
h('{% for i in a limit:-1 %}{{ i }}{% endfor %}|{% for i in a offset:-1 %}{{ i }}{% endfor %}|{% for i in a offset:10 %}{{ i }}{% else %}none{% endfor %}', {'a': [1, 2, 3]})
h('{% for i in a %}{{ i }}{% endfor %}{{ i }}', {'a': [1, 2]})
h('{% for i in a %}{% assign i = "x" %}{{ i }}{% endfor %}[{{ i }}]', {'a': [1, 2]})
h('{% for item in my_array offset:1 %}{{ item }}{% endfor %}', {'my_array': ['a', 'b', 'c']})
h('{% for item in my_array offset 1 %}{{ item }}{% endfor %}', {'my_array': ['a', 'b', 'c']})
h('{% for i in "a,b" | split: "," %}{{ i }}{% endfor %}')

# --- cycle, increment, decrement, ifchanged, tablerow
h('{% for i in (1..4) %}{% cycle "a", "b", "c" %}{% endfor %}|{% for i in (1..3) %}{% cycle "a", "b", "c" %}{% endfor %}')
h('{% for i in (1..4) %}{% cycle g: "a", "b" %}{% cycle g: "x", "y" %}{% endfor %}')
h('{% for i in (1..4) %}{% cycle x, y %}{% endfor %}|{% for i in (1..2) %}{% cycle x, y %}{% endfor %}', {'x': 1, 'y': 2})
h('{% for i in (1..3) %}{% cycle name: 1, 2 %}{% endfor %}', {'name': 'n'})
h('{% cycle %}')
h('{% cycle "a" "b" %}{% cycle "a" "b" %}')
h('{% cycle nil, "a" %}{% cycle nil, "a" %}')
h('{% cycle "a", "b" %}{% cycle "a","b" %}{% cycle \'a\', \'b\' %}')
h('{% increment x %}{% increment x %}{% decrement x %}{% decrement y %}{% decrement y %}{{ x }}{{ y }}')
h('{% assign x = 10 %}{% increment x %}{% increment x %}{{ x }}')
h('{% increment x %}{{ x }}', {'x': 5})
h('{% increment x %}{{ x }}', {'x': 1.5})
h('{% increment x %}', {'x': 'str'})
h('{% decrement x %}', {'x': 'str'})
h('{% increment a b %}{% increment a b %}{{ ["a b"] }}')
h('{% for i in a %}{% ifchanged %}{{ i }}{% endifchanged %}{% endfor %}', {'a': [1, 1, 2, 2, 1, 3]})
h('{% tablerow i in a cols:2 %}{{ i }}{% endtablerow %}', {'a': [1, 2, 3, 4, 5]})
h('{% tablerow i in a cols:2 limit:3 offset:1 %}{{ tablerowloop.col }}{{ tablerowloop.row }}{{ tablerowloop.first }}{{ tablerowloop.col_first }}{{ tablerowloop.col_last }}{{ tablerowloop.index }}{% endtablerow %}',
  {'a': [1, 2, 3, 4, 5]})
h('{% tablerow i in a %}{{ i }}{% endtablerow %}|{% tablerow i in nope %}{{ i }}{% endtablerow %}|{% tablerow i in e cols:3 %}{{ i }}{% endtablerow %}', {'a': [1, 2], 'e': []})
h('{% tablerow i in (1..3) cols:1 %}{{ i }}{% endtablerow %}')
h('{% tablerow %}{% endtablerow %}')

# --- comment / raw
h('a{% comment %} {{ x | nope: }} {% if %} {% endcomment %}b')
h('a{% comment %}{% if true %}{% endcomment %}b')
h('a{% comment %}{% comment %}x{% endcomment %}y{% endcomment %}b')
h('a{% comment %}{% for %}{% endcomment %}b')
h('a{% comment %}{{ x | | }}{% endcomment %}b')
h('{% raw %}{{ x }}{% if %}{% endraw %}|{% raw %}{%{% endraw %}|{% raw %}{% endraw')
h('{% raw x %}y{% endraw %}')
h('{% raw %}a{%- endraw -%}b{% endraw %}c')
h('{% raw %}{% endraw x %}after')
h('{% raw %}{% comment %}{% endraw %}{% endcomment %}')

# --- include (Liquid's own)
FILES = {'product': 'Product: {{ product.title }} ', 'locale_variables': 'Locale: {{echo1}} {{echo2}}', 'variant': 'Variant: {{ variant.title }}',
         'nested': '{% include "product" %}[{{ x }}]', 'err': '{{ 1 | divided_by: 0 }}', 'bad': '{% if %}', 'dir/p': 'dir:{{ p }}',
         'assign': '{% assign v = "from-include" %}', 'loop': '{% include "loop" %}'}
h('{% include "product" with prod %}|{% include "product" %}|{% include "product" for prods %}',
  {'prod': {'title': 'A'}, 'product': {'title': 'B'}, 'prods': [{'title': 'C'}, {'title': 'D'}]})
h('{% include "locale_variables" echo1: "a", echo2: b %}|{% include "variant" for variants %}|{% include "nested" x: 1 %}',
  {'b': 'B', 'variants': [{'title': 't1'}, {'title': 't2'}], 'product': {'title': 'P'}})
h('{% include "err" %}after')
h('{% include "missing" %}after')
h('{% include tmpl %}|{% include nil %}|', {'tmpl': 'product', 'product': {'title': 'T'}})
h('{% include "dir/p" p: 5 %}|{% include "assign" %}{{ v }}')
h('{% include "bad" %}after')
h('{% include %}')
h('{% include "product" with %}')

# --- filters: strings
s3 = {'s': 'Hello World', 'n': None, 'i': 42, 'f': 3.5, 'a': ['b', 'a', 'C'], 'h': {'k': 'v'}, 'e': '', 'b': True}
for flt in ['upcase', 'downcase', 'capitalize', 'escape', 'h', 'escape_once', 'url_encode', 'url_decode', 'strip', 'lstrip', 'rstrip',
            'strip_html', 'strip_newlines', 'newline_to_br', 'size', 'first', 'last', 'reverse', 'join', 'sort', 'sort_natural', 'uniq',
            'compact', 'abs', 'ceil', 'floor', 'round', 'default', 'date: "%Y"', 'split: "o"', 'append: "!"', 'prepend: "!"',
            'remove: "l"', 'remove_first: "l"', 'replace: "l", "L"', 'replace_first: "l", "L"', 'truncate: 5', 'truncatewords: 1',
            'slice: 1', 'slice: 1, 2', 'plus: 1', 'minus: 1', 'times: 2', 'divided_by: 2', 'modulo: 2', 'at_least: 5', 'at_most: 5',
            'map: "k"', 'where: "k"', 'concat: a']:
    h('{% for k in keys %}[{{ v[k] | ' + flt + ' }}]{% endfor %}', {'v': s3, 'keys': list(s3.keys()), 'a': ['b', 'a', 'C']})
h('{{ "ǆemo" | capitalize }}|{{ "ßa" | capitalize }}|{{ "ÉCOLE" | downcase }}|{{ "straße" | upcase }}|{{ "hELLO wORLD" | capitalize }}')
h('{{ "a<b>&\'\\"" | escape }}|{{ "&amp; &lt; &#39; &foo; &#x27; & <" | escape_once }}|{{ "<p>a</p>" | escape_once }}')
h('{{ "a b+c/d?e=é&~*\'()!" | url_encode }}|{{ "a+b%20c%2Fd%E2%9C%93%zz%4" | url_decode }}|{{ "%E9" | url_decode }}')
h('{{ "<script>x</script><p class=\'a\'>b</p><!-- c -->d<style>e</style><br/>" | strip_html }}|{{ "<div\n>x</div\n>" | strip_html }}|{{ "<SCRIPT>x</SCRIPT>y" | strip_html }}')
h('{{ "a\nb\r\nc\rd" | strip_newlines }}|{{ "a\nb\r\nc" | newline_to_br }}')
h('{{ " \t\n x \n\t " | strip }}|{{ "  x  " | lstrip }}|{{ "  x  " | rstrip }}|')
h('{{ "1234567890" | truncate: 7 }}|{{ "1234567890" | truncate: 20 }}|{{ "1234567890" | truncate: 0 }}|{{ "1234567890" | truncate: 2, "..." }}|{{ "1234567890" | truncate: 5, "" }}|{{ "1234567890" | truncate: 5, 1 }}|{{ "测试测试测试" | truncate: 5 }}|{{ "😀😀😀😀😀😀" | truncate: 4, "…" }}')
h('{{ "one two three" | truncatewords: 2 }}|{{ "one two three" | truncatewords: 3 }}|{{ "one two three" | truncatewords: 0 }}|{{ "one  two\tthree\nfour" | truncatewords: 2, "--" }}|{{ "one two" | truncatewords: 5 }}|{{ 5 | truncatewords: 1 }}')
h('{{ "x" | truncate: "a" }}')
h('{{ "x" | truncate: 2.5 }}|{{ "abc" | truncate: "2" }}|{{ "abcdef" | truncate: " 3 " }}|{{ "abcdef" | truncate: "0x4" }}|{{ "abcdef" | truncate: "010" }}|{{ "abcdef" | truncate: "1_0" }}')
h('{{ "a,b,,c,," | split: "," | join: "|" }}|{{ ",a" | split: "," | size }}|{{ "abc" | split: "" | join: "|" }}|{{ " a  b c " | split: " " | join: "|" }}|{{ "a  b" | split: "  " | join: "|" }}|{{ "" | split: "," | size }}|{{ nil | split: "," | size }}|{{ "a1b1c" | split: 1 | join: "|" }}|{{ "abc" | split: nil | join: "|" }}')
h('{{ "a.b.c" | split: "." | last }}|{{ "a.b" | split: "x" | first }}|{{ "a,b" | split: "," | reverse | join }}|{{ "😀a😀" | split: "" | join: "-" }}')
h('{{ "foobar" | slice: 1, 3 }}|{{ "foobar" | slice: -2, 2 }}|{{ "foobar" | slice: -1 }}|{{ "foobar" | slice: 100 }}|{{ "foobar" | slice: 6 }}|{{ "foobar" | slice: 6, 1 }}|{{ "foobar" | slice: 1, -1 }}|{{ "foobar" | slice: "1", "2" }}|{{ "héllo😀x" | slice: 4, 2 }}')
h('{{ a | slice: 1, 2 | join: "," }}|{{ a | slice: -1 | join: "," }}|{{ a | slice: 3 | size }}|{{ a | slice: 4 | size }}|{{ 12345 | slice: 1, 2 }}', {'a': [1, 2, 3]})
h('{{ "x" | slice: nil }}')
h('{{ "x" | slice: 1.5 }}')
h('{{ "hello" | replace: "l", "\\\\0\\\\0" }}|{{ "a.b" | replace: ".", "\\\\&!" }}|{{ "hello" | replace_first: "l", "[\\\\0]" }}|{{ "abc" | replace: "", "-" }}|{{ "aaa" | replace: "a" }}|{{ "a\\\\b" | replace: "\\\\", "/" }}')
h('{{ "hello" | remove: "l" }}|{{ "hello" | remove_first: "l" }}|{{ "hello" | remove: "" }}|{{ 1234 | remove: 2 }}')
h('{{ a | append: "x" }}|{{ "x" | append: a }}|{{ h | append: "" }}|{{ a | upcase }}|{{ a | prepend: 1 }}|{{ nil | append: nil }}|{{ 1.5 | append: 2 }}', {'a': [1, 'b', None, [2]], 'h': {'k': 1}})
h('{{ a | size }}|{{ a | join }}|{{ a | join: ", " }}|{{ a | join: 1 }}|{{ a | join: nil }}|{{ h | join }}|{{ "str" | join }}|{{ 5 | join }}|{{ nil | join }}',
  {'a': [1, [2, [3, None]], 'x', {'k': 'v'}], 'h': {'a': 1}})
h('{{ a | first }}|{{ a | last }}|{{ h | first }}|{{ h | last }}|{{ "abc" | first }}|{{ (1..3) | first }}|{{ (1..3) | last }}|{{ e | first }}', {'a': [1, 2], 'h': {'x': 1}, 'e': []})

# --- filters: arrays
h('{{ a | sort | join: "," }}|{{ a | sort_natural | join: "," }}|{{ a | reverse | join: "," }}|{{ a | uniq | join: "," }}', {'a': ['b', 'B', 'a', 'A', 'c', 'b']})
h('{{ a | sort | join: "," }}', {'a': [3, 1.5, 2, -1, 10]})
h('{{ a | sort | join: "," }}', {'a': [3, None, 1, None, 2]})
h('{{ a | sort | join: "," }}', {'a': [1, 'a']})
h('{{ a | sort | join: "," }}after', {'a': [1, 'a', 2]})
h('{{ a | sort_natural | join: "," }}', {'a': [10, 2, 'b', 'A', None, 1.5, True]})
h('{{ a | sort: "n" | map: "n" | join: "," }}|{{ a | sort_natural: "s" | map: "s" | join: "," }}',
  {'a': [{'n': 3, 's': 'b'}, {'n': 1, 's': 'C'}, {'n': None, 's': 'a'}, {'s': None}, {'n': 2}]})
h('{{ a | sort: "k" | map: "id" | join: "," }}', {'a': [{'k': 1, 'id': i} for i in range(12)] + [{'k': 0, 'id': 'z'}, {'id': 'n1'}, {'id': 'n2'}, {'k': 1, 'id': 'last'}]})
h('{{ a | sort: "k" | map: "id" | join: "," }}', {'a': [{'id': 'n%d' % i} for i in range(40)] + [{'k': 1, 'id': 'one'}]})
h('{{ a | sort: "k" | join: "," }}', {'a': [{'k': 1}, 'str', 5]})
h('{{ a | sort: "k" | join: "," }}', {'a': [{'k': 1}, None]})
h('{{ a | sort: "k" | join: "," }}', {'a': [{'k': 1}, [1, 2]]})
h('{{ a | sort: "k" | size }}', {'a': [{'k': 'a'}, {'k': 1}]})
h('{{ a | sort: "k" | size }}', {'a': []})
h('{{ h | sort | join }}|{{ "x" | sort | join }}|{{ 5 | sort }}|{{ nil | sort | size }}', {'h': {'b': 1, 'a': 2}})
h('{{ a | sort | join: "," }}', {'a': [[2, 1], [1, 2], [1]]})
h('{{ a | where: "ok" | map: "id" | join: "," }}|{{ a | where: "ok", true | map: "id" | join: "," }}|{{ a | where: "ok", false | map: "id" | join: "," }}|{{ a | where: "v", 1 | map: "id" | join: "," }}|{{ a | where: "v", "1" | map: "id" | join: "," }}',
  {'a': [{'id': 1, 'ok': True, 'v': 1}, {'id': 2, 'ok': False, 'v': 1.0}, {'id': 3, 'v': '1'}, {'id': 4, 'ok': 'yes'}]})
h('{{ a | where: "x" | size }}|{{ e | where: "x" | size }}|{{ s | where: "x" }}|{{ h | where: "k" | size }}', {'a': [{'x': 1}, 'str', {'x': None}], 'e': [], 's': 'str', 'h': {'k': 1}})
h('{{ a | where: "x" | size }}', {'a': [{'x': 1}, 5]})
h('{{ a | where: "x" | size }}', {'a': [5, {'x': 1}]})
h('{{ a | where: "x" | size }}', {'a': [[1], [2]]})
h('{{ a | uniq | join: "," }}|{{ a | uniq: "k" | size }}', {'a': [1, 1.0, '1', 1, None, None, [1], [1]]})
h('{{ a | uniq: "k" | map: "id" | join: "," }}|{{ e | uniq: "k" | size }}|{{ s | uniq: "k" }}', {'a': [{'k': 1, 'id': 'a'}, {'k': 1, 'id': 'b'}, {'k': 2, 'id': 'c'}, {'id': 'd'}, {'id': 'e'}], 'e': [], 's': 5})
h('{{ a | map: "name" | join: "," }}|{{ a | map: "to_liquid" | size }}|{{ h | map: "a" }}|{{ "abc" | map: "b" }}|{{ "abc" | map: "x" }}|{{ n | map: "x" | size }}',
  {'a': [{'name': 'x'}, {'name': 'y'}, None, [{'name': 'z'}]], 'h': {'a': 'A'}, 'n': None})
h('{{ a | map: "name" }}', {'a': [[1, 2]]})
h('{{ a | map: "name" }}after', {'a': [5]})
h('{{ a | map: "name" }}after', {'a': [1.5]})
h('{{ a | compact | join: "," }}|{{ b | compact: "k" | size }}|{{ e | compact: "k" | size }}|{{ n | compact: "k" }}', {'a': [1, None, 2, [None, 3]], 'b': [{'k': 1}, {'k': None}, {}], 'e': [], 'n': 5})
h('{{ a | concat: b | join: "," }}|{{ a | concat: c | size }}|{{ n | concat: b | join: "," }}|{{ "s" | concat: b | join: "," }}', {'a': [1, [2]], 'b': [3, [4]], 'c': [], 'n': None})
h('{{ a | concat: "x" }}', {'a': [1]})
h('{{ a | concat: h }}', {'a': [1], 'h': {'x': 1}})
h('{{ a | reverse | join }}|{{ "abc" | reverse }}|{{ h | reverse | size }}|{{ (1..3) | reverse | join }}', {'a': [1, [2, 3]], 'h': {'a': 1}})
h('{{ (1..4) | join: "-" }}|{{ (1..4) | size }}|{{ (1..4) | map: "x" | size }}|{{ (1..4) | sort | last }}|{{ (1..3) | concat: a | join }}', {'a': [9]})

# --- filters: numbers
nums = [0, 1, -1, 7, -7, 2.5, -2.5, 0.1, 0.2, 1.0, 10, 3, '3', '3.5', ' 4 ', '4abc', 'abc', '', None, True, [1], {'a': 1}, 1e20, 12345678901234567, '-0.5', '1e3', '0x10', '1_000']
for op in ['plus', 'minus', 'times', 'divided_by', 'modulo']:
    h('{% for x in xs %}{% for y in ys %}{{ x | ' + op + ': y }} {% endfor %}|{% endfor %}', {'xs': [10, -7, 2.5, '3', '0.3', 0.1, None, 'x'], 'ys': [3, -2, 0.5, 0.2, '2', '1.5', 4.0]})
h('{{ 10 | divided_by: 0 }}|{{ 10 | modulo: 0 }}|{{ 1.5 | divided_by: 0 }}|{{ 1 | divided_by: 0.0 }}|{{ 5 | modulo: 0.0 }}|after')
h('{{ 0.1 | plus: 0.2 }}|{{ 1.1 | times: 3 }}|{{ 1 | divided_by: 3.0 }}|{{ 2 | divided_by: 3.0 }}|{{ 10 | divided_by: 7.0 }}|{{ 1e20 | plus: 1 }}|{{ 0.1 | times: 0.1 }}|{{ 3.0 | minus: 1 }}|{{ 9007199254740993 | plus: 0.0 }}')
h('{{ 9223372036854775807 | plus: 1 }}|{{ 4611686018427387904 | times: 4 }}|{{ -9223372036854775808 | minus: 1 }}|{{ 99999999999999999999 | divided_by: 7 }}|{{ 99999999999999999999 | modulo: 7 }}')
for x in nums:
    h('{{ x | abs }}|{{ x | ceil }}|{{ x | floor }}|{{ x | round }}|{{ x | round: 1 }}|{{ x | at_least: 2 }}|{{ x | at_most: 2 }}|{{ x | plus: 0 }}', {'x': x})
h('{{ 2.5 | round }}|{{ 3.5 | round }}|{{ -2.5 | round }}|{{ 2.45 | round: 1 }}|{{ 2.55 | round: 1 }}|{{ 1234 | round: -2 }}|{{ 1250 | round: -2 }}|{{ 1.23456 | round: 3 }}|{{ 5 | round: 2 }}|{{ 2.7 | round: 0 }}|{{ 2.7 | round: "0" }}|{{ 2.7 | round: 0.0 }}|{{ 1.5 | round: -1 }}|{{ 15 | round: -1 }}|{{ -15 | round: -1 }}')
h('{{ 1.1 | ceil }}|{{ -1.1 | ceil }}|{{ 1.9 | floor }}|{{ -1.9 | floor }}|{{ "1.1" | ceil }}|{{ 5 | ceil }}|{{ 4.5 | at_least: 4.6 }}|{{ 4 | at_least: 4.6 }}|{{ 4 | at_most: "3.5" }}|{{ "x" | at_least: 1 }}')
h('{{ 5 | plus }}')
h('{{ 5 | plus: 1, 2 }}')
h('{{ "a" | upcase: 1 }}')
h('{{ x | default: "d" }}|{{ e | default: "d" }}|{{ f | default: "d" }}|{{ z | default: "d" }}|{{ a | default: "d" }}|{{ h | default: "d" }}|{{ s | default: "d" }}|{{ x | default }}|{{ sp | default: "d" }}',
  {'e': '', 'f': False, 'z': 0, 'a': [], 'h': {}, 's': 'set', 'sp': ' '})
h('{{ x | default: "d", allow_false: true }}', {'x': False})
h('{{ x | nosuchfilter }}|{{ x | nosuchfilter: 1, 2 | upcase }}', {'x': 'v'})

# --- date
dates = ['2006-07-05', '2006-07-05 10:00:00', '2006-07-05T10:00:00Z', '2006-07-05T10:00:00+02:00', '2006-07-05T10:00:00.123-0500',
         'Wed, 05 Jul 2006 10:00:00 +0000', 'July 5, 2006', '5 July 2006', 'Jul 5 2006', '2006/07/05', '07/05/2006', '05.07.2006',
         '2006', '2006-07', 'Jul 2006', '2006-07-05 10:00 PM', '2019-01-01T00:00:00.000Z', '2021-03-25T12:00:00-04:00',
         '2023-12-05 08:00:00 -0500', 'Tuesday', 'foo', '2006-13-45', '1/2/3', '2006-07-05 25:00', '12 Dec 2020 14:30 EST',
         '2020-02-30', '1152093784', 1152093784, 0, '', None, 1.5, True, [], '20060705', '20060705T101112', '2006-07-05T10:00:00 UTC',
         '2023-01-01T00:00:00+00:00', '2022-11-21T15:42:33.5+01:00', '1999-12-31 23:59:59 -1200', '2010-10-10 10:10:10 PST',
         'Sat Nov 19 2022', 'March 2nd, 2020', '1 Jan 99', '1 Jan 70', "1 Jan '99"]
for d in dates:
    h('{{ d | date: "%Y-%m-%d %H:%M:%S %z|%Z|%a %A %b %B %e %j %y %I %p %U %W %w %u %s" }}', {'d': d})
fmts = ['%c', '%x', '%X', '%D', '%F', '%T', '%R', '%r', '%v', '%+', '%%', '%-d/%-m/%-y', '%_m|%_d', '%^a %^B', '%#b %#p', '%10A|%-10A|%010d|%_5d|%-5d',
        '%3N|%6N|%L|%N', '%:z %::z', '%G-W%V-%u %g', '%C %h %n%t|', '%Q %E %O %i %J %q', '%k|%l|%P', '%Ey %Oy %Ez', 'plain', '', '%', 'abc%', '%-', '%5', '%5Q',
        "%d %b %Y", "%B %-d, %Y", "%Y%m%d", "%A, %B %d, %Y at %I:%M %p"]
for f in fmts:
    h('{{ d | date: f }}', {'d': '2006-07-05 09:03:04.5', 'f': f})
h('{{ "2006-07-05" | date }}')
h('{{ "2006-07-05" | date: nil }}|{{ "2006-07-05" | date: "" }}|{{ nil | date: "%Y" }}|{{ "now" | date: "" }}')
h('{{ "now" | date: "%Y" | size }}|{{ "today" | date: "%s" | size }}|{{ "NOW" | date: "%z" }}')
h('{{ site.time | date: "%Y-%m-%d" }}|{{ site.time | date: "%B %-d, %Y" }}', deep)


# --- more dates (Time.parse subset) and week numbers
dates2 = ['2006-07-05T10:00:00', '2006-07-05T10:00', '2020-01-01T00:00:00.000+0000', '2016-04-13T00:00:00+10:00', ' 2006-07-05 ',
          'Fri Jun 05 2020', '2020.01.02', '02-Jan-2020', 'Jan 2, 2020 3:04:05 PM', '3pm', '10am', 'Jan 2 2020 10:00 am', '2020-01-01Z',
          '2020-1-2', '2020-01-02 3:4:5', '1/2/2020', '13/12/2020', '2020/13/01', 'December 25', '25 December', '2nd Feb 2021',
          'Feb 29 2021', '2024-02-29', 'mon, 1 jan 2018', 'JANUARY 1 2018', '2018-01-01 00:00:00 GMT', '2018-01-01 00:00:00 UT',
          '2018-01-01T00:00:00-00:00', '2018-01-01 12:00 CEST', '2018-01-01 12:00 +05:30', '2018-01-01 12:00 +5', '2018-01-01 12:00 -0330',
          '2018-01-01 12:00:00 Europe/Paris', 'tomorrow', 'yesterday', '1e9', '-5', '2018-01-01T24:00:00', '2018-01-01 12:60', '2018-01-01 12:00:60',
          '2006-07-05 10:00:00 +0000 UTC', 'Wed Jul  5 10:00:00 2006', '2006-07-05 10h30', '20:15', '2006-07-05\n10:00']
for d in dates2:
    h('{{ d | date: "%Y-%m-%d %H:%M:%S %z|%Z|%s" }}', {'d': d})
for d in ['2006-01-01', '2007-12-31', '2010-01-03', '2008-12-29', '2005-01-01', '2012-12-31', '2015-06-15', '1970-01-01', '1969-12-31 23:59:59', '1900-03-01', '2100-02-28', '0001-01-01', '9999-12-31']:
    h('{{ d | date: "%U %W %V %G %g %j %u %w %a %C %y %s" }}', {'d': d})
for n in [0, 1, -1, 86399, 1700000000, 4102444800, 253402300799]:
    h('{{ n | date: "%c %z %Z" }}|{{ s | date: "%F %T" }}', {'n': n, 's': str(n)})

# --- more floats / numbers through output and filters
for v in [0.5, 1.25, 3.0e-5, 1.0e-4, 9.999999999999999e15, 1e16, 123456789012345.6, 0.30000000000000004, 1/3, 2/3, 5e-324, 1.7976931348623157e308, -1e-7, 4.35, 2.675]:
    h('{{ v }}|{{ v | plus: 0 }}|{{ v | times: 1 }}|{{ v | round: 2 }}|{{ v | ceil }}|{{ v | divided_by: 1 }}|{{ v | modulo: 1 }}|{{ v | minus: v }}|{{ v | abs }}', {'v': v})
h('{{ 1.0 | divided_by: 0.0 | floor }}|{{ 0 | divided_by: 0.0 }}|{{ -1 | divided_by: 0.0 }}|{{ 1.5 | divided_by: 0 | round: 2 }}|{{ 1.5 | divided_by: 0 | plus: 1 }}|{{ 1.5 | divided_by: 0 | abs }}|{{ 1.5 | divided_by: 0 | at_least: 5 }}')
h('{{ 7 | modulo: -3 }}|{{ -7 | modulo: 3 }}|{{ 7.5 | modulo: -2 }}|{{ -7.5 | modulo: 2 }}|{{ -7 | divided_by: 2 }}|{{ 7 | divided_by: -2 }}|{{ -7.0 | divided_by: 2 }}')
h('{{ "1.5" | plus: "2.5" }}|{{ "1." | plus: 1 }}|{{ ".5" | plus: 1 }}|{{ "-1.5" | abs }}|{{ "1.50" | times: 2 }}|{{ " 1.5 " | plus: 1 }}|{{ "+3" | plus: 1 }}|{{ "1,000" | plus: 1 }}')

# --- deep nesting / stack
h('{% for a in (1..1) %}' * 30 + 'x' + '{% endfor %}' * 30)
h('{% if true %}' * 101 + 'x' + '{% endif %}' * 101)
h('{% if true %}' * 99 + 'x' + '{% endif %}' * 99)

# --- errors, strictness
h('{{ 1 | divided_by: 0 }} and {{ "x" | slice: nil }} and {% if 1 < "a" %}x{% endif %} end')
h('{{ x.y.z }}{{ undefined }}{% if undefined.foo %}x{% endif %}ok', {'x': {}})
h('{% for i in (1..3) %}{{ i | divided_by: 0 }}{% endfor %}')
h('a\n\nb {{ 1 | divided_by: 0 }}\n{% if 1 > "x" %}{% endif %}')
h('{{ "a" | concat: "b" }}')

# --- lax quirks (render in all modes)
QUIRKS = [
    '{{ a | append: "," s | append: b }}',
    '{{ a | append: ","s | append: b }}',
    '{{ a | | upcase }}',
    '{{ a | upcase | }}',
    '{{ a || upcase }}',
    '{{ a upcase }}',
    '{{ a | upcase: }}',
    '{{ a | append: b c }}',
    '{{ a | append: , b }}',
    '{{ a | append: b, }}',
    '{{ a | append:b }}',
    '{{ a | append : b }}',
    '{{ a | 123 }}',
    '{{ a | "upcase" }}',
    '{{ a ! b }}',
    '{{ a.b.[c] }}',
    '{{ a[b | c] }}',
    '{{ a[ "b" ] }}',
    '{{ a["b" | upcase ] }}',
    '{{ "x }}',
    "{{ 'x }}",
    '{{ a.. }}',
    '{{ ..a }}',
    '{{ (1..) }}',
    '{{ (1.. 3) }}',
    '{{ (a..b }}',
    '{{ a b c }}',
    '{{ -1 | abs }}',
    '{{ - 1 }}',
    '{{ a- }}',
    '{{ 1-1 }}',
    '{{ a?.b }}',
    '{{ a.b? }}',
    '{{ a | append: "x" | }}',
    '{{ | upcase }}',
    '{{ }}',
    '{{}}',
    '{{ , }}',
    '{{ a, b }}',
    '{{ "a" "b" }}',
    '{{ a:b }}',
    '{{ a | append: k: "v" }}',
    '{{ a | append: k:"v", "w" }}',
    '{{ a | default: "x", foo: 1 }}',
    '{{ a == b }}',
    '{{ a contains b }}',
    '{{ a.size() }}',
    '{{ a[0 }}',
    '{{ a] }}',
    '{{ [a }}',
    '{{ a.first.size | plus: 1 }}',
    '{{ a | split: "," | first | upcase }}',
    '{{ 12abc }}',
    '{{ 1.2.3 }}',
    '{{ 1e5 }}',
    '{{ .5 }}',
    '{{ 5. }}',
    '{{ -.5 }}',
    '{{ a | plus: 1 | | times: 2 }}',
    '{{ a % b }}',
    '{{ a & b }}',
    '{{ a | upcase; }}',
    '{{ 😀 }}',
    '{{ a | append: "😀" }}',
    '{{ a\n| upcase\n}}',
    '{{\ta\t|\tupcase\t}}',
    '{% if a == b == c %}x{% endif %}',
    '{% if a == "b %}x{% endif %}',
    '{% if (a) %}x{% endif %}',
    '{% if a | size > 1 %}x{% endif %}',
    '{% if a.size > 1 %}x{% endif %}',
    '{% if a && b %}x{% endif %}',
    '{% if a || b %}x{% endif %}',
    '{% if a = "x" or b = "y" %}x{% endif %}',
    '{% if not a %}x{% endif %}',
    '{% if a == "x" and %}x{% endif %}',
    '{% if a contains"x" %}x{% endif %}',
    '{% if a contains %}x{% endif %}',
    '{% if a=="x" %}x{% endif %}',
    '{% if a!=b %}x{% endif %}',
    '{% if a <b %}x{% endif %}',
    '{% if a =! b %}x{% endif %}',
    '{% if a === b %}x{% endif %}',
    '{% if a == b, c %}x{% endif %}',
    '{% if a and b and %}x{% endif %}',
    '{% if a or %}x{% endif %}',
    '{% if and %}x{% endif %}',
    '{% if a andb %}x{% endif %}',
    '{% if a orb %}x{% endif %}',
    '{% if a == "x" or b == "y" or c %}x{% endif %}',
    '{% unless a == b %}x{% endunless %}',
    '{% unless a b %}x{% endunless %}',
    '{% elsif %}',
    '{% if a %}{% elsif a b %}y{% endif %}',
    '{% for i in a reversed limit:1 %}{{ i }}{% endfor %}',
    '{% for i in a limit: 1 offset: 1 reversed %}{{ i }}{% endfor %}',
    '{% for i in a limit:"1" %}{{ i }}{% endfor %}',
    '{% for i in a, b %}{{ i }}{% endfor %}',
    '{% for i in a b %}{{ i }}{% endfor %}',
    '{% for i in (1..3) | reverse %}{{ i }}{% endfor %}',
    '{% for i in a.b[0] %}{{ i }}{% endfor %}',
    '{% for i-j in a %}{{ i-j }}{% endfor %}',
    '{% for 1 in a %}x{% endfor %}',
    '{% for i in a limit:1, offset:1 %}{{ i }}{% endfor %}',
    '{% for i in a offset:continue %}{{ i }}{% endfor %}',
    '{% for i in a limit:x %}{{ i }}{% endfor %}',
    '{% for i in a %}{{ i }}{% endfor x %}',
    '{% case a b %}{% when 1 %}x{% endcase %}',
    '{% case a %}{% when 1 2 %}x{% endcase %}',
    '{% case a %}{% when "a b" %}x{% endcase %}',
    '{% case a %}{% when a, %}x{% endcase %}',
    '{% cycle a: %}',
    '{% cycle "a": "b", "c" %}',
    '{% cycle , %}',
    '{% cycle a b c %}',
    '{% tablerow i in a cols:2 limit:x %}{{ i }}{% endtablerow %}',
    '{% assign x = a | append: "," s %}{{ x }}',
    '{% assign x = a b %}{{ x }}',
    '{% assign x = a | %}{{ x }}',
    '{% assign x = "a" | append: k: 1 %}{{ x }}',
    '{% assign x = (1..3) %}{{ x }}',
    '{% assign x == 1 %}{{ x }}',
    '{% assign x[0] = 1 %}{{ x[0] }}',
    '{% assign "x" = 1 %}{{ x }}',
    '{% assign x = a %}{{ x }}',
    '{%assign x=a%}{{x}}',
    '{% capture x y %}c{% endcapture %}{{ x }}{{ y }}',
    '{% increment %}',
    '{%if a%}x{%endif%}',
    '{% if a %}x{% endif%}',
    '{%   if   a   %}x{%   endif   %}',
    '{% if\na\n%}x{% endif %}',
    '{%- if a -%}x{%- endif -%}',
    '{% IF a %}x{% ENDIF %}',
    '{% if a %}x{%endif%}{%endif%}',
    '{% %}',
    '{%%}',
    '{% if a',
    '{{ a',
    '{{ a }',
    '{{ a } b }}',
    '{% if a %}{{ a }',
    'text {% end',
    'a { b } c {{{ x }}} d',
    'a {% raw %}',
    '{{{{a}} }}',
    '{% comment %}',
    '}} {{ a }} %} {% if true %}y{% endif %}',
    '{{ a }}}',
    '{{ a }}}}',
    '{{ "}}" }}',
    '{{ "{{" }}',
    "{{ '%}' }}",
    '{% assign x = "%}" %}{{ x }}',
    '{% if "%}" %}y{% endif %}',
]
QA = {'a': 'ab,c', 'b': 'B', 'c': True, 's': 'S', 'k': 'K'}
for t in QUIRKS:
    for mode in ('lax', 'warn', 'strict'):
        add('quirk-%s-%d' % (mode, QUIRKS.index(t)), t, QA, mode)
        add('quirk-%s-%d-bang' % (mode, QUIRKS.index(t)), t, QA, mode, True)
QA2 = {'a': [3, 1, 2], 'b': {'c': [5, 6]}, 'x': 1}
for t in QUIRKS:
    if '{% for' in t or '{% tablerow' in t:
        add('quirk-arr-%d' % QUIRKS.index(t), t, QA2, 'warn')

# strict_variables / strict_filters
SV = ['{{ x }}', '{{ y }}', '{{ x.a }}', '{{ x.b }}', '{{ x.a.size }}', '{{ l[5] }}', '{{ l.size }}', '{{ l.foo }}', '{{ x["a"] }}',
      '{% if y %}t{% else %}f{% endif %}', '{% for i in y %}{{ i }}{% endfor %}', '{{ x | nofilter }}', '{{ x | upcase | nofilter }}',
      '{% for i in (1..2) %}{{ forloop.nope }}{{ forloop.index }}{% endfor %}', 'a{{ y }}b{{ x.a }}c', '{% assign z = y %}{{ z }}',
      '{{ x.a | default: y }}', '{% increment y %}{{ y }}', '{% cycle y, x %}']
for t in SV:
    for bang in (False, True):
        add('sv-%d-%s' % (SV.index(t), bang), t, {'x': {'a': 'A'}, 'l': [1]}, 'warn', bang, sv=True)
        add('sf-%d-%s' % (SV.index(t), bang), t, {'x': {'a': 'A'}, 'l': [1]}, 'warn', bang, sf=True)
        add('svsf-%d-%s' % (SV.index(t), bang), t, {'x': {'a': 'A'}, 'l': [1]}, 'warn', bang, sv=True, sf=True)

# line numbers in errors and warnings
add('lines-1', 'line1\n{{ a | | x }}\nline3\n{% if a b %}\n{% endif %}\n{{ 1 | divided_by: 0 }}\n{% for i in a, b %}{% endfor %}\n{{ "x" | slice: nil }}', {'a': 1}, 'warn')
add('lines-2', 'l1\n\n\n{% if true %}\n\n{% foo %}\n{% endif %}', None, 'warn')
add('lines-3', 'l1\n{% raw %}\n\n{% endraw %}\n{{ 1 | divided_by: 0 }}', None, 'warn')
add('lines-4', 'l1\n{% comment %}\n{{ a | | }}\n{% endcomment %}\n{% if 1 < "a" %}{% endif %}', None, 'warn')
add('lines-5', '{% include "err" %}\n{{ 1 | divided_by: 0 }}', None, 'warn', files={'err': 'x\n{{ 1 | divided_by: 0 }}'})
add('lines-6', 'a\n{% if true %}\n{{ 1 | divided_by: 0 }}\n{% endif %}', None, 'warn')
add('lines-7', 'a\n{% if true\n %}\n{{ "x" | upcase: 1 }}{% endif %}', None, 'warn')
add('lines-8', '{{ a |\n|\n b }}\n{{ 1 | divided_by: 0 }}', None, 'warn')
add('lines-9', '\n\n{% if a %}', None, 'warn')
add('lines-10', '\n\n{{ a ', None, 'warn')
add('lines-11', 'x\n{% for i in (1..2) %}\n{% if i == 2 %}{% break %}{% endif %}{{ i | foo: }}\n{% endfor %}', None, 'warn')

for i, (t, a) in enumerate(H):
    add('hand-%d' % i, t, a, 'warn')
    add('hand-%d-bang' % i, t, a, 'warn', True)

# include cases that use FILES
for i, (t, a) in enumerate(H):
    if '{% include' in t:
        add('hand-inc-%d' % i, t, a, 'warn', False, FILES)
        add('hand-inc-%d-bang' % i, t, a, 'warn', True, FILES)
        add('hand-inc-%d-strict' % i, t, a, 'strict', False, FILES)

# ------------------------------------------------------------------ 3. real IG malformed Liquid (data/strict.jsonl), warn mode
IG = [
    ('{{my_types | append: ","s | append: r_type }}', {'my_types': 'Patient', 'r_type': 'Observation'}),
    ('{% assign my_types = "" %}{% for r in site.data.resources %}{% assign r_type = r[0] | split: "/" | first %}{{my_types | append: ","s | append: r_type }}|{% endfor %}', {'site': SITE}),
    ('{% for r in site.data.resources %}{% assign my_types = my_types | append: ","s | append: r.name %}{% endfor %}[{{my_types}}]', {'site': SITE}),
    ('{{site.data.[include.file] }}', deep),
    ('{{site.data.[include.file].index.title }}', deep),
    ('{% assign my_array = "a,b,c" | split: "," %}{% for item in my_array offset 1 %}{{ item }}{% endfor %}', None),
    ('{% assign my_array = "a,b,c" | split: "," %}{% for item in my_array offset:1 %}{{ item }}{% endfor %}', None),
    ('{% for i in search_code_row.shall_chain | split: "," %}[{{ i }}]{% endfor %}', {'search_code_row': {'shall_chain': 'a,b'}}),
    ('{% for i in search_code_row.shall_chain | split: "," %}[{{ i }}]{% endfor %}', {'search_code_row': {'shall_chain': ['x', 'y']}}),
    ('{% for item in items %}{{item["title" | trim ]}}|{% endfor %}', {'items': [{'title': 'T1'}, {'title': 'T2', 'trim': 'X'}]}),
    ('{% for item in items %}{{item["title" | trim ]}}|{% endfor %}', {'items': [{'title': 'T1'}], 'trim': 'title'}),
    ("{% assign stem_URL = 'us-core-x' %}{{{{site.data.fhir.path}} | append: 'valueset-' | append: stem_URL | append: '.html' }}", {'site': SITE}),
    ('{{%practitionerRoles.entry.resource.practitioner.resolve().id|%serviceConditions.entry.resource.asserter.resolve().ofType(Practitioner).id}}', None),
    ('{{site.data.cdex-parameters-example3.parameter[7].part.[2].resource.content[0].attachment.data}}',
     {'site': {'data': {'cdex-parameters-example3': {'parameter': [{}] * 7 + [{'part': [{}, {}, {'resource': {'content': [{'attachment': {'data': 'B64'}}]}}]}]}}}}),
    ("{{ site.data.resources.['OperationDefinition/submit-attachment']['description'] }}", {'site': SITE}),
    ("{{ site.data.resources.['StructureDefinition/foo']['description'] }}", {'site': SITE}),
    ('{{{{page.id}} }}', deep),
    ('{{{{include.id}} }}', deep),
    ('{{site.data.StructureDefinition-FASTConsent.description.key_Consent.extension:manager}}',
     {'site': {'data': {'StructureDefinition-FASTConsent': {'description': {'key_Consent': {'extension': 'EXT'}}}}}}),
    ('{% assign r_hash = "StructureDefinition/foo" | split: "|" %}{{r_hash[0] | split: "/"" | first }}', None),
    ('{{"[`ActivityDefinition`](https://www.hl7.org/fhir/{{R-code}}/composition.html) }}', {'R-code': 'R5'}),
    ('{{"[`ActivityDefinition`](https://www.hl7.org/fhir/R5/composition.html) }}', None),
    ('{{"[IHE-XC-WADO](https://www.ihe.net/uploadedFiles/Documents/Radiology/IHE_RAD-Suppl_XC-WADO.pdf) }}', None),
    ('{{render:infobox}}', {'render': 'R'}),
    ("{% assign localPage = 'profiles.html' %}{{{{site.data.pages[localPage].label}} | split: '.0' }}", {'site': SITE}),
    ("{{{{site.data.pages[page.path].label}} | split: '.0' }}", deep),
    ("{{{{site.data.pages[page.path].label}} | split: '.0' | first }}", deep),
    ("{% assign resource_ = 'StructureDefinition/foo' %}{{site.data.resources[resource_].['history'] }}", {'site': SITE}),
    ('{{site.data.structuredefinitions.[include.id].type }}', deep),
    ('{% if site.data.languages.langs.size() != 1 %}multi{% else %}single{% endif %}', deep),
    ('{% if site.data.languages.langs.size() != 1 %}multi{% else %}single{% endif %}', {'site': {'data': {'languages': {'langs': ['en']}}}}),
    ('{% if resourceName="Extension" or resourceName="extension-registry" %}ext{% else %}other{% endif %}', {'resourceName': 'Extension'}),
    ('{% if resourceName="Extension" or resourceName="extension-registry" %}ext{% else %}other{% endif %}', {'resourceName': 'Patient'}),
    ('{{{site.data.fhir.packageId  | uri_escape }}', {'site': SITE}),
    ('{{{site.data.fhir.packageId  | upcase }}', {'site': SITE}),
    ('{{ item | trim }}|{{ " x " | trim | upcase }}', {'item': ' v '}),
]
for i, (t, a) in enumerate(IG):
    add('ig-%d' % i, t, a, 'warn')
    add('ig-%d-bang' % i, t, a, 'warn', True)
    add('ig-%d-lax' % i, t, a, 'lax')
    add('ig-%d-strict' % i, t, a, 'strict')

with open(OUT, 'w') as f:
    for c in cases:
        f.write(json.dumps(c, ensure_ascii=False) + '\n')
print('wrote %d cases to %s' % (len(cases), OUT))
