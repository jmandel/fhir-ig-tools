=== footnotes-basic
Here is a footnote reference[^1] and another[^note].
And the first again[^1].

[^1]: The first footnote.
[^note]: A *named* footnote
    with a second paragraph line.

    And a second paragraph.

=== footnotes-unreferenced
Text without markers.

[^unused]: Never referenced.

=== footnotes-missing-def
Reference to a missing footnote[^nope].

=== footnotes-in-list-and-table
* item with note[^a]
* another

| col | col2 |
|-----|------|
| x[^b] | y |

[^a]: Note A.
[^b]: Note B, with `code`.

=== footnotes-placement
Text[^x].

* placeholder
{:footnotes}

More text.

[^x]: Footnote placed above.

=== footnotes-nr {"footnote_nr": 7}
One[^a] two[^b].

[^a]: A.
[^b]: B.

=== footnotes-backlink-config {"footnote_backlink": "back", "footnote_prefix": "p-"}
Footnote[^z].

[^z]: Zed.

=== footnotes-backlink-inline {"footnote_backlink_inline": true}
Footnote[^z].

[^z]: Zed

    > quoted last

=== footnote-codeblock-content
Text[^c].

[^c]:
    ~~~
    code in footnote
    ~~~

=== deflist-basic
Term 1
: Definition 1

Term 2
Term 2b
: Definition 2a
: Definition 2b

    with a second paragraph

Term 3
{: .cls}
: *def* with **markup**

=== deflist-ial-autoids
{:auto_ids}
first term
: def

second term
: def 2

=== table-footer
| Head 1 | Head 2 |
|:-------|-------:|
| a      | b      |
| c      | d      |
|========|========|
| foot 1 | foot 2 |

=== table-multiple-bodies
| h1 | h2 | h3 |
|----|:--:|----|
| a | b | c |
|----|----|----|
| d | e |
| f | g | h | i |

=== table-no-leading-pipe
h1 | h2
---|---
`a|b` | <code>c|d</code>
e \| f | g

=== table-escaped-and-inline
| *em* | **strong** | [link](http://x.org) |
|---|---|---|
| ~~del~~ | `code` | <span>html</span> |

=== table-not-a-table
This line has a | pipe but is paragraph text
and continues here.

=== table-ial
| a | b |
|---|---|
| 1 | 2 |
{: .table .table-striped #tbl}

=== options-midstream
# Header A

{::options auto_ids="false" /}

# Header B

{::options toc_levels="2" parse_block_html="true" /}

<div>
*parsed* because parse_block_html is on
</div>

=== options-smart-quotes
"Double" and 'single' quotes.

{::options smart_quotes="lsquo,rsquo,ldquo,rdquo" /}

"Double" and 'single' quotes again.

=== options-unknown-and-forbidden
{::options foo="bar" template="x" /}

Text after.

=== options-span-and-hardwrap
Text {::options hard_wrap="true" /} here
next line.

=== math-block-inline
Inline $$a^2 + b^2$$ math.

$$
\sum_{i=1}^n i = \frac{n(n+1)}{2}
$$

\$$not math$$

$$x < y$$
{: .math-class}

=== math-no-engine {"math_engine": null}
Inline $$a$$ and

$$
b
$$

=== ial-block-span
A paragraph.
{: #para-id .c1 .c2 data-x="y z"}

{:ref: .refcls title="ald title"}

Another *emph*{: .em-class} and [link](u){: ref}
{: ref}

> quote
{: .bq}

* list item
{: .li-class}

=== ial-before-block
{: .before}
Paragraph with IAL before it.

{: #h-id}
# Header with IAL before

=== markdown-attr-variants
<div markdown="1">
*block parsed*

* list
</div>

<div markdown="span">
*span parsed*
</div>

<div markdown="0">
*not parsed*
</div>

<div markdown="block">
# Header in div
</div>

<span markdown="1">*span*</span>

<p markdown="1">**p content**</p>

=== markdown-div-nested
<div class="outer" markdown="1">
<div class="inner" markdown="1">
## Inner header

text
</div>
</div>

=== toc-basic
* TOC
{:toc}

# Header One

## Sub *two*

### Third [link](x)

#### Fourth (not in 1..3)

## Excluded
{:.no_toc}

# Header One

=== toc-ol-with-id
1. toc
{:toc #mytoc .cls}

# A

## B[^f]

[^f]: note

=== header-ids
# Simple Header
# Simple Header
# Simple Header
## Ünïcödé Ḧeader
### 123 numbers first
#### code `x` and *em*
##### Ampersand &amp; entity &copy;
###### punctuation: hello, world! (yes)
# Header with {#custom-id}
# Header ending with hashes ##
#No space header
# Ελληνικά
# 日本語のヘッダー
# emoji 😀 header
#   
# tab	header

=== header-setext
Setext H1
=========

Setext H2
---------

Not *setext*
-

=== header-ids-off {"auto_ids": false}
# No id here
## Custom {#cid}

=== header-ids-prefix {"auto_id_prefix": "pre-"}
# Prefixed
# Prefixed

=== header-offset {"header_offset": 2}
# Level one
##### Level five

=== header-in-html-div
<div markdown="1">
# Kramdown style id
# Kramdown style id
# 1. numbered
</div>

# Kramdown style id

=== tasklist
- [ ] unchecked
- [x] checked
- [X] also checked
- not a task

1. [ ] ordered task
2. second

=== strikethrough
~~deleted~~ and ~~multi word strike~~ but not ~~ spaced ~~ or ~single~.

=== hard-break-backslash
line one\
line two  
line three\\
line four

=== hard-wrap-on {"hard_wrap": true}
line one
line two

* item
  continued

=== codeblocks-langs
```json
{"a": 1, "b": "<x> & y"}
```

```unknownlang
code <b>
```

```
no lang
```

~~~ ruby
puts "hi"
~~~

```text
plain text alias
```

```plaintext
plaintext
```

```ruby?parent=html
x
```

```guess
<div>guessed</div>
```

    indented code
    	with tab

```JSON
uppercase lang
```

=== codeblocks-ial
```xml
<a/>
```
{: .extra}

~~~
x
~~~
{: .language-sh}

=== codeblock-guess-off {"guess_lang": false}
```
no lang no highlight
```

```text
text lang
```

```json
{}
```

=== codeblock-default-lang {"syntax_highlighter_opts": {"default_lang": "json"}}
```
{"a":1}
```

=== codespans
Use `code` and ``double `tick` code`` and `<b>&amp;</b>`.

Not code: ` spaced `.

=== codespan-lang-json
A `json`{: .language-json} span and `{"a": 1}`{: .language-json} too.

=== codespan-lang-plaintext
A `x`{: .language-plaintext} span and `y`{: .language-text} span.

=== entities
&nbsp; &copy; &#169; &#xA9; &lt; &gt; &amp; &quot; &unknown; &#128; &#0; &#x1F600; & alone.

=== entities-numeric {"entity_output": "numeric"}
&copy; "quotes" -- dash ... ellipsis &lt;

=== entities-symbolic {"entity_output": "symbolic"}
&copy; "quotes" -- dash ... ellipsis &#xA9;

=== typography
Dashes -- and --- and ellipsis... and <<guillemets>> and << spaced >> and \<< escaped.

'Single' "double" 'tis the '90s, it's "nested 'quotes'" ok.

=== typography-config {"typographic_symbols": {"hellip": "...", "mdash": "---", "ndash": "--"}}
Dashes -- and --- and ellipsis...

=== no-auto-typographic {"gfm_quirks": ["paragraph_end", "no_auto_typographic"]}
Dashes -- and --- and ellipsis...

=== paragraph-end-quirk-off {"gfm_quirks": []}
para
# not header
* not list

=== paragraph-end-quirk-on
para
# header
* list
> quote
```
fence
```

=== escapes
\* \_ \` \# \\ \[ \] \{ \} \( \) \! \: \| \" \' \$ \= \- \~ \< \> \. \+ \a

=== autolinks
<http://example.com/path?a=1&b=2> <mailto:me@example.com> <me@example.com> <ftp://x.y>

=== emphasis
*em* **strong** _em_ __strong__ ***both*** snake_case_word *not em * a*b*c foo_bar_baz _a_b

=== links-and-images
[inline](http://a.com "Title") [ref][r1] [implicit][] [Implicit] ![img](i.png "T") ![ref img][r1]
[paren url](http://a.com/(x)) [angle](<http://a b.com>) [empty]() [nested [brackets]](u)
[missing ref][nope]

[r1]: http://ref.com  "Ref Title"
[implicit]: /imp
[ignored]: /x 'single quoted'

=== link-def-ial
[x][ld]

[ld]: http://ld.com
{: .ldc}

=== link-defs-config {"link_defs": {"pre": ["http://pre.com", "Pre"]}}
[Predefined][pre]

=== abbreviations
The HTML and W3C specs. HTML is great.

*[HTML]: Hyper Text Markup Language
*[W3C]:  World Wide Web Consortium
*[specs]:

=== html-blocks
<div class="x">
**not parsed**
</div>

<!-- a comment -->

<table>
<tr><td markdown="1">*cell*</td></tr>
</table>

<script>
var a = "<b>" && 1 < 2;
</script>

<pre>
pre   text
</pre>

<p>raw p <em>x</em></p>

Text <span class="s">*span html*</span> and <br> and <img src="x.png"> and <unknown>x</unknown>.

<![CDATA[ cdata <b> ]]> inline.

=== html-unclosed
<div>
unclosed div

text after

=== html-closing-mismatch
Text </span> stray close.

<div>
</p>
</div>

=== html-markdown-in-li
<ul>
<li markdown="1">*item*</li>
</ul>

=== lists-nested
* a
  * b
    * c
* d

1. one
2. two
   1. nested
10. ten

- loose

- list

+ plus
+ list
{: .plus}

* item

  para in item

      code in item

=== lists-lazy-and-eob
* item
lazy continuation
* item 2
^
* new list

=== lists-ordered-start
3. three
4. four

=== blockquotes
> quote
> * list
>
> > nested
lazy

> ## header in quote

=== hr
***
- - -
___

=== nomarkdown-comment
{::comment}
This is a comment.
{::comment}

{::nomarkdown}
<b>raw *html*</b>
{:/nomarkdown}

{::nomarkdown type="latex"}
\latex
{:/}

Inline {::nomarkdown}<i>raw</i>{:/} and {::comment}hidden{:/comment}.

=== eob-marker
para one
^
para two

=== mixed-ig-page
<div markdown="1">

### Summary

This profile defines **constraints** on the [Patient](http://hl7.org/fhir/R4/patient.html) resource.

| Element | Card. | Notes |
|---------|-------|-------|
| identifier | 1..* | Must support |
{: .grid}

</div>

{% raw %}not liquid here{% endraw %}

=== crlf-input
Line one
Line two

# Head

=== trailing-whitespace-only
   

=== empty-input

=== unicode-text
Ünïcödé — “already smart” ‘quotes’ and emoji 🎉 and 中文。

=== cjk-breaks {"remove_line_breaks_for_cjk": true}
中文
中文

=== toc-levels-array {"toc_levels": [2, 3]}
* toc
{:toc}

# H1
## H2
### H3

=== toc-levels-comma {"toc_levels": "1,3"}
* toc
{:toc}

# H1
## H2
### H3

=== smart-quotes-string {"smart_quotes": "lsaquo,rsaquo,laquo,raquo"}
"double" 'single'

=== smart-quotes-numeric {"smart_quotes": ["39", "39", "34", "34"]}
"double" 'single' it's

=== standalone-image
![alt text](img.png){:standalone}

=== header-links {"header_links": true}
# Linked header

=== span-ial-after-text
text{: .x} and *em*{: .y}

=== html-attr-newlines
Text <a href="x"
title="multi
line">link</a>.

=== block-html-span-element-start
<span>starts a paragraph</span> with more text.

<em>also</em>

=== inline-html-in-header
# Header with <code>code</code> and <span>span</span>

=== lists-with-ial-items
* {:.first} first
* {: #second} second

=== codeblock-show-whitespace
    a  b	c   
{: .show-whitespaces}

=== definition-list-lazy
term
: definition
lazy line

: second def

=== setext-after-para
para
line
===

=== link-in-link
[outer [inner](i) text](o)

=== img-in-link
[![img](i.png)](link)

=== strong-em-nesting
**bold *italic* bold** and *italic **bold** italic*

=== html-entities-in-code
`&copy;` and

    &copy; <tag>

=== xml-pi
<?xml version="1.0"?>
text

=== table-with-html-pipes
| a | <span>x|y</span> |
|---|---|
| b | c |

=== table-only-header
| a | b |
|---|---|

=== table-sep-start
|---|---|
| a | b |

=== html-span-block-tag
Text <div>block in span</div> end.

=== list-then-code
* item

```
code
```

=== numbered-header-ids
# 1 Introduction
# 1.1 Scope
# 2) Two

=== guillemets-config-ig
<< left and right >> and <<both>>

=== err-toc-levels {"toc_levels": "abc"}
# x

=== err-smart-quotes-count {"smart_quotes": "a,b,c"}
"x"

=== err-footnote-nr {"footnote_nr": "abc"}
x[^1]

[^1]: y

=== err-footnote-link-text {"footnote_link_text": "no placeholder"}
x

=== err-input {"input": "gfm"}
x

=== hl-none {"syntax_highlighter": "none"}
```json
{}
```

`x`

=== hl-disable-block {"syntax_highlighter_opts": {"block": {"disable": true}}}
```json
{}
```

`span`

=== hl-css-class-nowrap {"syntax_highlighter_opts": {"css_class": "hl", "span": {"css_class": "s"}}}
```text
x & y
```

=== hl-wrap-false {"syntax_highlighter_opts": {"wrap": false}}
```text
x & y
```

=== hl-formatter-html {"syntax_highlighter_opts": {"formatter": "HTML"}}
```text
x & y
```

=== kramdown-input {"input": "kramdown"}
# Header
## Header

~~~ ruby
x
~~~

line\
break ~~no strike~~

=== options-converter-level
{::options footnote_nr="5" entity_output="numeric" auto_id_prefix="z-" /}

# Head &copy;

Note[^q].

[^q]: q.

=== options-syntax-highlighter-inline
{::options syntax_highlighter="nil" /}

```json
{}
```

=== transliterated {"transliterated_header_ids": true}
# Ünïcödé header

=== d261-nest-blockquote-98
> > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > x

=== d261-nest-blockquote-99
> > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > x

=== d261-nest-blockquote-100
> > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > > x

=== d261-nest-span-html-97
<span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span>x</span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span>

=== d261-nest-span-html-98
<span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span>x</span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span>

=== d261-nest-span-html-99
<span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span><span>x</span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span></span>

=== d261-nest-raw-html-198
<div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div>x</div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div>

=== d261-nest-raw-html-199
<div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div>x</div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div>

=== d261-nest-raw-html-200
<div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div><div>x</div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div></div>

=== d261-em-digit
a1_b_ 9_x_ x9_y_z 1-_m_ and _ok_ 2_

=== d261-em-window-48
x_________________________________________________em_ end and woooooooooooooooooooooooooooooooooooooooooooooooo_rd_ fin

=== d261-em-window-49
x__________________________________________________em_ end and wooooooooooooooooooooooooooooooooooooooooooooooooo_rd_ fin

=== d261-em-window-50
x___________________________________________________em_ end and woooooooooooooooooooooooooooooooooooooooooooooooooo_rd_ fin

=== d261-em-window-51
x____________________________________________________em_ end and wooooooooooooooooooooooooooooooooooooooooooooooooooo_rd_ fin

=== d261-em-window-astral
𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀𝐀_em_ x

=== d261-header-links-true {"header_links": true}
# Head

## Sub {#custom}

=== d261-header-links-hash-end {"header_links": {"content": "\u00b6", "position": "end"}}
# Head

## Sub

=== d261-header-links-hash-nopos {"header_links": {"content": "#"}}
# Head

=== d261-header-links-hash-start {"header_links": {"content": "<x>", "position": "start"}}
# Head *em*

=== d261-header-links-yaml-string {"header_links": "{content: L, position: start}"}
# Head

=== d261-header-links-false-string {"header_links": "false"}
# Head

=== d261-footnote-empty
Text[^1] and[^2].

[^1]:

[^2]: two

=== d261-img-alt-ial
![alt text][id] and ![other](y.png){: alt="span"}

[id]: x.png
{: alt="from def"}

=== d261-ald-invalid
para
{: .cls bad"x #id title="t" foo}

{:ref: .r junk=}

use
{: ref}

=== d261-unclosed-brackets
[a [b [c](u) d

[x *[y* ](u)

![i [j](k) [l

*[em [x](y)* [z

=== d261-unclosed-brackets-many
[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[[x

=== d261-ruby34-word-zwj
# ‍️ Entity models

## a‌b c‍d
