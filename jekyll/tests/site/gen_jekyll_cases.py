"""Generate `type: jekyll-liquid` oracle cases for the Jekyll glue (P3.3-P3.5): Jekyll's own tags
and filters as the IG corpus uses them.

usage: python3 -I gen_jekyll_cases.py > jekyll-cases.jsonl
"""
import json

KCFG = {'kramdown': {'toc_levels': '1..3', 'smart_quotes': ['apos', 'apos', 'quot', 'quot'],
                     'typographic_symbols': {'laquo': '<<', 'raquo': '>>'}}}
DATA = {
    'fhir': {'path': 'http://hl7.org/fhir/R4/', 'ver': '4.0.1', 'packageId': 'hl7.fhir.us.core'},
    'pages': {'index.html': {'title': 'Home', 'label': '1.0'}, 'a.html': {'title': 'A & B', 'label': '2.3'}},
    'resources': {'StructureDefinition/x': {'name': 'X', 'history': True, 'description': 'Some *md*'},
                  'StructureDefinition/y': {'name': 'Y', 'experimental': True}},
    'list': [{'name': 'b', 'n': '10', 'grp': 'g1', 'tags': ['t1', 't2']},
             {'name': 'a', 'n': '9', 'grp': 'g2'},
             {'name': 'c', 'grp': 'g1', 'n': '2.5', 'tags': 't3'}],
}
cases = []


def add(id_, template, assigns=None, includes=None, page=None, config=None):
    c = {'id': id_, 'type': 'jekyll-liquid', 'template': template, 'data': DATA, 'config': config or KCFG}
    if assigns:
        c['assigns'] = assigns
    if includes:
        c['includes'] = includes
    if page:
        c['page'] = page
    cases.append(c)


# --- filters ---------------------------------------------------------------------------------
md_inputs = ['*em* and **strong**', '# Head\n\ntext "quoted" -- dash...', '', 'a | b\n--|--\n1 | 2',
             '<div markdown="1">*x*</div>', 'line1\nline2', '* a\n* b\n\n1. c', '[l](http://x.org) <b>h</b>']
for i, m in enumerate(md_inputs):
    add(f'markdownify-{i}', '{{ m | markdownify }}', {'m': m})
add('markdownify-nil', '[{{ nothing | markdownify }}]')
add('markdownify-number', '{{ 42 | markdownify }}')
add('markdownify-data', '{{ site.data.resources["StructureDefinition/x"].description | markdownify }}')
add('jsonify-hash', '{{ site.data.pages | jsonify }}')
add('jsonify-list', '{{ site.data.list | jsonify }}')
add('jsonify-scalars', '{{ "a\\"b" | jsonify }}|{{ 1 | jsonify }}|{{ nil | jsonify }}|{{ true | jsonify }}|{{ 1.5 | jsonify }}')
add('jsonify-unicode', '{{ s | jsonify }}', {'s': 'é – “q”   /'})
add('where-1', '{{ site.data.list | where: "grp", "g1" | map: "name" | join: "," }}')
add('where-num', '{{ site.data.list | where: "n", 9 | map: "name" | join: "," }}')
add('where-array-prop', '{{ site.data.list | where: "tags", "t2" | map: "name" | join: "," }}')
add('where-nil', '{{ site.data.list | where: "missing", nil | size }}')
add('where-hash-input', '{{ site.data.resources | where: "name", "Y" | size }}')
add('where_exp-1', '{{ site.data.list | where_exp: "i", "i.grp == \'g1\'" | map: "name" | join: "," }}')
add('where_exp-and', '{{ site.data.list | where_exp: "i", "i.grp == \'g1\' and i.n != \'10\'" | map: "name" | join: "," }}')
add('where_exp-contains', '{{ site.data.list | where_exp: "i", "i.name contains \'a\'" | size }}')
add('where_exp-hash', '{{ site.data.resources | where_exp: "r", "r.experimental" | map: "name" | join: "," }}')
add('find-1', '{{ site.data.list | find: "name", "a" | map: "grp" }}{% assign f = site.data.list | find: "name", "zz" %}[{{ f }}]')
add('find_exp-1', '{% assign f = site.data.list | find_exp: "i", "i.n == \'2.5\'" %}{{ f.name }}')
add('group_by-1', '{% assign g = site.data.list | group_by: "grp" %}{% for x in g %}{{ x.name }}:{{ x.size }}:{{ x.items | map: "name" | join: "+" }};{% endfor %}')
add('group_by-missing', '{% assign g = site.data.list | group_by: "tags" %}{% for x in g %}[{{ x.name }}]{{ x.size }};{% endfor %}')
add('group_by_exp-1', '{% assign g = site.data.list | group_by_exp: "i", "i.name | upcase" %}{% for x in g %}{{ x.name }}={{ x.size }};{% endfor %}')
add('sort-plain', '{{ a | sort | join: "," }}', {'a': ['b', 'A', 'c', 'B']})
add('sort-prop', '{{ site.data.list | sort: "n" | map: "name" | join: "," }}')
add('sort-prop-last', '{{ site.data.list | sort: "tags", "last" | map: "name" | join: "," }}')
add('sort-mixed', '{{ a | sort | join: "," }}', {'a': [3, 1, 2]})
add('push-pop', '{% assign a = "x,y" | split: "," | push: "z" %}{{ a | join: "-" }}|{{ a | pop | join: "-" }}|{{ a | shift | join: "-" }}|{{ a | unshift: "w" | join: "-" }}')
add('push-nonarray', '{{ "s" | push: "z" }}')
add('uri_escape', '{{ "foo, bar \\\\baz?" | uri_escape }}|{{ "a b/c?d=é&e#f" | uri_escape }}|{{ "%20 %zz" | uri_escape }}')
add('uri_escape-pkg', '{{ site.data.fhir.packageId | uri_escape }}')
add('cgi_escape', '{{ "foo,bar;baz? é" | cgi_escape }}')
add('xml_escape', '{{ "foo \\"bar\\" <baz> & \'q\'" | xml_escape }}|{{ nil | xml_escape }}')
add('normalize_whitespace', '[{{ "  a \\n\\t b  " | normalize_whitespace }}]')
add('slugify', '{{ "The _config.yml file?!" | slugify }}|{{ "Ünïcödé Title" | slugify }}|{{ "a b" | slugify: "raw" }}|{{ "Hello, World!" | slugify: "pretty" }}|{{ "x.y_z" | slugify: "default" }}')
add('number_of_words', '{{ "one two  three" | number_of_words }}')
add('array_to_sentence', '{{ a | array_to_sentence_string }}|{{ b | array_to_sentence_string: "or" }}|{{ c | array_to_sentence_string }}',
    {'a': ['x', 'y', 'z'], 'b': ['p', 'q'], 'c': ['only']})
add('to_integer', '{{ "12" | to_integer | plus: 1 }}|{{ true | to_integer }}|{{ "3.9" | to_integer }}')
add('inspect', '{{ site.data.pages["a.html"] | inspect }}|{{ "x<y" | inspect }}|{{ nil | inspect }}')
add('date-filters', '{% assign d = "2026-03-04T05:06:07Z" %}{{ d | date_to_xmlschema }}|{{ d | date_to_rfc822 }}|{{ d | date_to_string }}|{{ d | date_to_long_string }}|{{ d | date_to_string: "ordinal", "US" }}')
add('url-filters', '{{ "/a/b.html" | relative_url }}|{{ "a/b.html" | absolute_url }}|{{ "/x/index.html" | relative_url }}',
    config=dict(KCFG, baseurl='/base', url='https://example.org'))
add('url-filters-nobase', '{{ "/a/b.html" | relative_url }}|{{ "a.html" | absolute_url }}')
add('unknown-filter', '{{ "x" | trim }}|{{ "y" | nosuchfilter: 1 }}')
add('std-filters-mixed', '{{ site.data.pages["a.html"].title | escape_once }}|{{ site.data.fhir.ver | split: "." | first }}|{{ "x" | append: "y" | prepend: "w" | upcase }}')

# --- site / page variables ---------------------------------------------------------------------
add('page-vars', '{{ page.path }}|{{ page.name }}|{{ page.url }}|{{ page.dir }}', page={'path': 'en/a.html', 'name': 'a.html', 'url': '/en/a.html', 'dir': '/en/'})
add('site-data-deep', '{{ site.data.pages[localPage].title }}|{{ site.data.resources[resource_].history }}', {'localPage': 'a.html', 'resource_': 'StructureDefinition/x'})
add('site-config-keys', '[{{ site.baseurl }}][{{ site.url }}][{{ site.markdown }}][{{ site.highlighter }}]')

# --- include semantics -------------------------------------------------------------------------
I = {'frag.html': '<{{ include.a }}|{{ include.b }}|{{ include.c }}>',
     'nest.html': '({{ include.x }}{% include frag.html a="in" %}{{ include.x }})',
     'md.md': '## Title {{ include.t }}\n\nsome *md*',
     'en/sub.html': 'SUB{{ include.lang }}',
     'tpl.html': '{% assign localPage = page.path | split: "/" | last %}[{{ localPage }}]'}
add('include-static', '{% include frag.html %}', includes=I)
add('include-params', '{% include frag.html a="dq" b=\'sq\' c=v %}', {'v': 'VAR'}, includes=I)
add('include-param-escapes', '{% include frag.html a="x\\"y" b=\'p\\\'q\' %}', includes=I)
add('include-param-dotted', '{% include frag.html a=site.data.fhir.ver b=page.path %}', includes=I, page={'path': 'p.html'})
add('include-nested-scope', '{% include nest.html x="X" %}{{ include.x }}', includes=I)
add('include-dynamic', '{% assign p = "frag" %}{% include {{p}}.html a="d" %}', includes=I)
add('include-dynamic-dir', '{% include {{lang}}/sub.html lang=lang %}', {'lang': 'en'}, includes=I)
add('include-leading-slash', '{% include /frag.html a="s" %}{% include /en/sub.html lang="L" %}', includes=I)
add('include-double-slash', '{% include //frag.html %}', includes=I)
add('include-md-markdownify', '{% capture c %}{% include md.md t="T" %}{% endcapture %}{{ c | markdownify }}', includes=I)
add('include-sees-page', '{% include tpl.html %}', includes=I, page={'path': 'en/x/y.html'})
add('include-assign-leaks', '{% include tpl.html %}{{ localPage }}', includes=I, page={'path': 'q.html'})
add('include-missing', '{% include nope.html %}', includes=I)
add('include-bad-params', '{% include frag.html a=b=c %}', includes=I)

# --- highlight -----------------------------------------------------------------------------------
add('highlight-json', '{% highlight json %}\n{"a": [1, "x"]}\n{% endhighlight %}')
add('highlight-xml', '{% highlight xml %}<a b="c">&amp;</a>{% endhighlight %}')
add('highlight-unknown-lang', '{% highlight nosuchlang %}text <x>{% endhighlight %}')
add('highlight-linenos', '{% highlight ruby linenos %}\nx = 1\ny = 2\n{% endhighlight %}')

# --- warn-mode fallbacks from the corpus (data/strict.jsonl) -------------------------------------
add('lax-assign-braces', "{% assign label = {{site.data.pages[localPage].label}} | split: '.0' %}[{{ label }}]", {'localPage': 'a.html'})
add('lax-dot-bracket', "{% if site.data.resources[resource_].['history'] %}H{% endif %}", {'resource_': 'StructureDefinition/x'})
add('lax-append-s', '{% assign t = "A" %}{% assign t = t | append: ","s | append: "B" %}{{ t }}')
add('lax-index-pipe', '{{item["title" | trim ]}}', {'item': {'title': ' T '}})

for c in cases:
    print(json.dumps(c))
