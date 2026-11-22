# Rouge 4.7.0 port (`org.hl7.fhir.jekyll.rouge`)

Faithful Java port of the parts of Rouge 4.7.0 that kramdown 2.5.2 and Jekyll 4.4.1 reach, so that
highlighted code is byte-identical to Jekyll's output **including the per-token
`<span class="…">` markup** (revises decision D7: the IG templates link `pygments-manni.css`,
which styles these classes). Sources: `../third_party/rouge/lib/rouge/`; the upstream repo at the
same tag (for `spec/visual/samples`) is unpacked in `../third_party/rouge/`.

## File map (Ruby → Java)
| Ruby | Java |
|---|---|
| `token.rb` | `Token` (tree node: name, parent, shortname, qualname, `Token[]` cache), `Tokens` (all constants; `Name::Builtin::Pseudo` → `Name_Builtin_Pseudo`, `Str`/`Num` aliases) |
| `lexer.rb` | `Lexer` (`find`, `lookup_fancy`, `find_fancy` with `?k=v` options via `URI.decode_www_form`, `guesses`/`guess`/`guess_by_mimetype`, option casts `as_bool/string/list/lexer/token`, `*_option`, `lex`/`continue_lex` token consolidation), `LexerClass` (a Ruby lexer *class*: metadata + constructor; class-level `lex`/`continue_lex` = fresh instance), `Registry` + generated `LexerData` (all 228 lexer classes in `Lexer.all` order, 392 registry names; `tests/rouge/dump_meta.rb` → `meta.json` → `gen_lexer_data.py`) |
| `regex_lexer.rb` | `RegexLexer` (`Definition` = class-level `state_definitions`/`states`/`start_procs` with parent inheritance, `StateDSL` with `prepended`/`appended`, `Rules` DSL: `rule(re, tok[, next])`, `rule(re) { }` as lambdas, `mixin`; runtime: stack, `step` with mixins, the `^` beginning-of-line hack, `MAX_NULL_SCANS`, `token`/`groups`/`delegate`/`recurse`/`push` (name, block, none)/`pop!`/`goto`/`reset_stack`/`in_state?`/`state?`; `Scanner` = StringScanner) |
| `text_analyzer.rb` | `TextAnalyzer` (`shebang`, `shebang?`, `doctype`, `doctype?`) |
| `guesser.rb`, `guessers/{mimetype,modeline,source,util}.rb` | `Guessers` |
| every `self.detect?` (38 lexers, ported or not) | `Detectors` |
| `formatter.rb`, `formatters/html.rb`, `html_legacy.rb`, `html_pygments.rb`, `html_table.rb`, `html_line_highlighter.rb` | `Formatter` (`format` incl. `filter_escapes`, `token_lines`), `Formatters.{HTML,HTMLLegacy,HTMLPygments,HTMLTable,HTMLLineHighlighter}` |
| `lexers/<x>.rb` | `lexers/<X>.java` (one class per Ruby lexer class), registered in `lexers/Ported.java` |
| (Ruby/Onigmo semantics) | `RRegex` (regex translation), `RubyStrings` (`split`, `strip`, `casecmp`, `to_i`, `str[a...b]`), `BigStack` |

Integration (the only edits outside this package):
- `kramdown/RougeHighlighter.call` = `syntax_highlighter/rouge.rb#call`: `find_fancy(lang || default_lang, text)`,
  the plaintext/`guess_lang`/`disable` checks, `css_class ||= 'highlight'`, `formatter_class(opts).new(opts)`
  (`HTMLLegacy` by default; a constant name → `HTML`/`HTMLLegacy`; unknown name → `HTML` as the
  `NameError` rescue does), `formatter.format(lexer.lex(text))`.
- `site/JekyllTags.HighlightBlock.renderRouge` = `tags/highlight.rb#render_rouge`: `HTML`, wrapped by
  `HTMLLineHighlighter` (`mark_lines`, `String#to_i` values; non-list → the Liquid SyntaxError) and
  `HTMLTable` (`linenos`, gutter/code classes as Jekyll), lexer `find_fancy(lang, code) || PlainText`.

## Ruby regex → java.util.regex (`RRegex`)
Lexers keep Rouge's regex sources verbatim (as Java string literals) and `RRegex` translates them:
`^`/`$` line anchors with only `\n` as terminator (`MULTILINE|UNIX_LINES`, which also makes `.`
exclude only `\n`); `/m` → `DOTALL` (`(?m:` → `(?s:`); `/i` → `CASE_INSENSITIVE|UNICODE_CASE`; `/x` applied by the
translator (whitespace/comments dropped outside classes, kept inside, unlike Java COMMENTS mode;
per-group `(?x-x)` tracked); `\h`/`\H` hex digits; `\v` = VT; `\b`/`\B` Unicode word boundaries
(Onigmo Word = Alphabetic+M+Nd+Pc; verified: `"ifé" !~ /if\b/`) while `\w \d \s` stay ASCII;
`\p{Word}`/`\p{Alpha}`/POSIX brackets Unicode (`[[:alnum:]]` = Alphabetic+Nd, `[[:punct:]]` = P plus
ASCII ``$+<=>^`|~``, as Ruby 3.2); a `{` that is not an interval is literal, `{,n}` = `{0,n}`;
`(?#…)` dropped; named groups renamed. Unsupported constructs throw at class init.
Interpolated Regexp objects embed as `Regexp#to_s` (`(?i-mx:…)`), as in Ruby (`R.toString()`).
StringScanner's default (non-fixed-anchor) mode is reproduced with matcher regions, anchoring and
opaque bounds: `^`/`\A` match at the scan position and look-behind/`\b` cannot see before it
(verified in Ruby: `check(/(?<=\n)b/)` is nil after a newline).

## Lexers ported (23)
plaintext (`text`), json, json-doc (`jsonc`), json5, xml, html, css, javascript (`js`), typescript (`ts`),
http, shell (`bash zsh ksh sh`), batchfile (`bat batch dosbatch winbatch`), sql, python (`py`), java,
yaml (`yml`), markdown (`md mkd`), diff (`patch udiff`), sparql, turtle, properties,
actionscript (`as as3`), plus **ruby** (`rb`; not used by IGs, but it is the language of kramdown's and
Jekyll's own highlighting tests). Every other label resolves exactly as in Rouge (registry of all 392
names; unknown labels such as `mermaid`, `cql`, `fhirpath`, `fsh`, `xhtml` → nil → kramdown's
`default_lang`/plaintext path or Jekyll's `|| PlainText`).

## Delegation
- html → its own `Javascript`/`CSS` instances (`<script>`/`<style>`), with Rouge's state carried
  across chunks and `reset!` at each tag.
- http → `content` option, else `Lexer.guess_by_mimetype(Content-Type)` over all 228 lexers'
  mimetypes (json, xml, html, css, js, yaml, sh, python, sql, … are ported; e.g. `application/fhir+json`
  is no Rouge mimetype → PlainText, as in Ruby). Ruby's quirks kept: `Content-Type: ;` raises
  NoMethodError; an ambiguous guess would raise NameError (`Lexer::AmbiguousGuess` doesn't exist).
- markdown → YAML front matter; inline HTML (its HTML instance); fenced code via
  `find_fancy(lang or "guess", code, @options)` (Guesser::Ambiguous → first alternative, nil →
  PlainText with `token: Str::Backtick`). java → itself (class-level, fresh lex); python f-strings
  → `recurse`.
- **Unported delegation targets** (a fence in markdown, a guessed or mimetype-chosen lexer, any
  label naming an unported lexer such as `console`, `php`, `latex`, `c#`): the lexer *class* is the
  real one (tag, aliases, detect?, mimetypes are all there), but it emits the whole text as one
  Text token (`UnportedLexer`), i.e. escaped text without spans. No IG input reaches this (see the
  mined corpus below); the per-lexer cases of kramdown's suite do.

## Faithfully reproduced oddities
- Rouge loads some lexer files twice (`load_lexer 'x.rb'` and the `lexers/*.rb` glob use different
  keys), so their `start` blocks are registered twice. Only matters for ruby.rb, whose block pushes
  `:expr_start`: every Ruby lex starts with `[:root, :expr_start, :expr_start]` (visible: `????` lexes
  as two Char literals). For html/markdown/yaml/http the duplicated blocks are idempotent.
- batchfile: `"set".casecmp m[1]` is always truthy → every unknown word is Keyword::Declaration+Error.
- yaml: `m[1]` in the block-scalar header is the outer group, so `"-".to_i` (= 0) etc. is added.
- python: the string register is never reset between lexes (only matters after an error).

## Performance
java.util.regex backtracks recursively (Onigmo uses a heap stack), so some Rouge rules can
overflow the thread stack on huge inputs. Two measures, both output-preserving:
1. `R.eq(src)`: an equivalent regex for matching, with its proof in a comment. Used once: the JSON
   key rule `("(?:\\.|[^"\\\n])*?")(\s*)(:)` → possessive unrolled loop (each unit is decided by
   its first char, so the lazy loop has one candidate end). Real IG JSON has 285 KB strings.
2. `BigStack`: on `StackOverflowError` the same `lookingAt` is retried on a helper thread with a
   4 GB (reserved, lazily committed) stack.
3. `RegexLexer.Precheck` (performance-only necessary condition). Used for the Java lexer's
   method-signature rule, which is quadratic in both Ruby and Java on long identifier runs
   (TRACKER P3.16): `JavaSignaturePrecheck` decides exactly, in O(1) per position after one O(n)
   pass, whether the regex can match (proof in its javadoc); the regex still produces the match.
   `JavaSignatureEquivalence`: 14,332,534 positions over 203,381 inputs (demo, sample, suite and fuzz
   Java texts + 200,000 random strings over the rule's alphabet), 0 unsound / 0 inexact.
   Pathological 100 KB input: 37,345 ms → 12.7 ms (Ruby: 17,154 ms); four more pathological
   inputs 13–50 ms vs Ruby 13–32 s.
Timings (`RougeBench` vs `rbbench.rb`, warm): the 66 stress inputs (60–460 KB each) take 1.0 s in
Java vs 97 s in Ruby; on the kramdown corpus Rouge adds no measurable time (`KramdownBench`
~1.5–1.7 s per 2,207 documents either way). Thread safety: definitions are built lazily under a
lock, lexers are per call; `RougeConcurrencyCheck` (16 threads, cold start, 20,073 renders) and
`KramdownConcurrencyCheck` (8 threads, corpus ×3) show 0 mismatches.

## Tests (all with no span stripping: `DiffRunner --no-d7`, `domdiff.py --no-d7`)
Oracle case type `rouge` in `oracle/oracle.rb` (`find_fancy(lang, text) || PlainText`, formatter
`html`/`html_legacy`/`html_table`, optional `mark_lines`); Java side `test/rouge/RougeCaseRunner`.
| suite | cases | result |
|---|---|---|
| per-lexer (`tests/rouge/cases.jsonl`, `gen_cases.py`): demo + visual sample of every ported lexer, ×formatters, line windows, random cuts, every line alone | 4,049 | 4,047 (2 = markdown sample containing a ```` ~~~ latex ```` fence → unported TeX) |
| targeted (`gen_targeted.py`): label resolution, `?` options, `guess` (shebangs, doctypes, modelines, BOM/CRLF, ambiguity error), http content types, markdown fences, formatter edges | 175 | 162 (13 = labels/guesses resolving to unported lexers: console, c#, c++, rust, shell-session, php, tex, postscript, racket, perl, hack, groovy, lua) |
| mined IG fragments (`mine_corpus.py`: every fence/highlight under `repos/*/input/`) | 2,467 | 2,467 |
| stress (`gen_stress.py`: 60–460 KB inputs per lexer) | 66 | 66 |
| fuzz (`fuzzround.sh <seed> <n>`: token soups + mutated samples per lexer) seeds 1–5 ×1000, 6–7 ×2000 | 204,000 | 203,982; all 18 = mutated markdown samples whose fences name unported lexers (latex, console) |
| kramdown corpus `tests/corpus/md-*` | 2,207 | 2,207 |
| Jekyll suite `tests/site/jekyll-*` | 73 | 73 |
| kramdown feature suite `tests/kramdown/cases.jsonl` | 1,043 | 702; all 341 failures are unported lexers: 339 `rouge:<lang>` one-fence-per-registry-name cases + `kd:block/06_codeblock/rouge/simple` ×2 (`php?start_inline=1`) |
| `tests/kramdown/cases-d7.jsonl` (inline codespans, custom formatter name) | 8 | 6 (2 = `rouge/multiple`, which also fences `php`) |
| IG goldens via `tests/rouge/golden-nod7.sh` (domdiff `--no-d7`) | US Core 16,565, genomics-reporting 4,419, ITI.MHD 2,112, cds-hooks 2,053, smart-scheduling-links 1,686 files | 100% identical |
| all 99 valid goldens (`goldens-nod7.sh`, incl. UTG 89,572 files) | 493,294 files | 100% identical |

Helpers: `RougeDebug <lang> <file>` / `--case <cases> <id>` (tokens, or the error with a stack
histogram), `RougeBench`, `rbbench.rb`, `goldens-nod7.sh` (all goldens in `goldens.txt`).
Regenerate: `python3 tests/rouge/gen_cases.py > tests/rouge/cases.jsonl && oracle/oracle.sh tests/rouge/cases.jsonl tests/rouge/expected.jsonl`
(same for `gen_targeted.py`, `gen_stress.py`, `mine_corpus.py <out>`).

## Known gaps
- 205 of Rouge's 228 lexers are not ported (see Delegation); they highlight as plain escaped text.
- Formatters other than HTML/HTMLLegacy(+HTMLTable/HTMLPygments)/HTMLLineHighlighter: a kramdown
  `formatter:` naming `HTMLTable`, `HTMLInline`, `Null`, `Tex`, `Terminal256`, … raises
  `KramdownError` (in Ruby most of these fail too, with NoMethodError, because kramdown passes the
  options hash as their first argument; `Null`/`Tex` would work there). `inline_theme` not ported.
- `guess` by filename (and Disambiguation) is not ported: neither kramdown nor Jekyll passes one.
- Rouge's `debug` option output.
