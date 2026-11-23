# kramdown port notes (`org.hl7.fhir.jekyll.kramdown`)

Port of kramdown 2.6.1, kramdown-parser-gfm 1.1.0 and the HTML converter, configured the way
Jekyll 4.4.1 calls them. Tracker: P4. (Ported from 2.5.2; the 2.5.2 → 2.6.1 delta, applied 2026-11-23, is listed
under "2.6.1 delta" below.)

## API
```java
String html = Kramdown.toHtml(markdownText, siteKramdownConfig);           // Map<String,Object>, may be null
String html = Kramdown.convert(markdownText, siteKramdownConfig, warnings); // also collects kramdown warnings
```
- `siteKramdownConfig` is the site `_config.yml` `kramdown:` block (JSON/YAML values: String, Long, Double,
  Boolean, List, Map). It is deep-merged over Jekyll's `DEFAULTS["kramdown"]` (nil values keep the
  default, as in `Jekyll::Utils.deep_merge_hashes`), then `KramdownParser#setup` is applied
  (`syntax_highlighter ||= rouge`, `syntax_highlighter_opts.default_lang ||= plaintext`, `guess_lang`
  copied in, legacy `coderay` block), then `Kramdown::Options.merge`.
- `markdownify` input: pass Ruby `input.to_s`. `null` text is treated as `""` (nil.to_s).
- Errors that Ruby raises (bad option values such as `toc_levels: "abc"`, `input: gfm`) are thrown as
  `KramdownError` (a RuntimeException). Jekyll would abort the build in those cases.
- Thread-safe: each call builds its own parser and converter. Static state is limited to immutable
  regexes, the parser registry and caches of immutable `Pattern`s (concurrent maps).

## Ruby → Java file map
| Ruby | Java |
|---|---|
| `jekyll/converters/markdown/kramdown_parser.rb`, `configuration.rb` (kramdown defaults) | `Kramdown.java` |
| `kramdown/options.rb`, `kramdown-parser-gfm/.../gfm/options.rb` | `Options.java` (+ `MiniYaml.java` for YAML-string option values) |
| `kramdown/element.rb` | `Element.java` |
| `kramdown/error.rb` | `KramdownError.java` |
| `kramdown/utils/string_scanner.rb` + Ruby `::StringScanner` | `StrScanner.java` |
| `kramdown/utils/entities.rb` | `Entities.java` (table generated from the Ruby file) |
| `kramdown/utils/html.rb` | `HtmlUtils.java` |
| `kramdown/parser/base.rb`, `parser/kramdown.rb`, `parser/kramdown/*.rb`, `Parser` module of `parser/html.rb` | `KramdownParser.java` (one section per Ruby file, marked `// ==== port of ...`) |
| `parser/html.rb` `Constants`, REXML name/reference fragments | `HtmlConstants.java` |
| `kramdown-parser-gfm/lib/kramdown/parser/gfm.rb` | `GfmParser.java` |
| `converter/base.rb`, `converter/html.rb`, `converter/math_engine/mathjax.rb` | `HtmlConverter.java` |
| `converter/syntax_highlighter/rouge.rb` + Rouge 4.7.0 wrapper/registry | `RougeHighlighter.java` |
| Ruby String helpers (strip incl. NUL, chomp, split, gsub/sub, Integer()) | `RubyStr.java` |

Regex conventions: every pattern is compiled with MULTILINE|UNIX_LINES (Ruby `^`/`$` are line anchors and
`.` only excludes `\n`); Ruby `/m` → DOTALL; embedded Ruby regexes keep their flags via `(?s-i:...)`
(`RubyStr.emb`). Ruby `\b`/`\B` are Unicode-aware while `\w` is ASCII; Java 19+ `\b` is ASCII, so
`HtmlConstants.WB/NWB` emulate Ruby's. `[[:alpha:]]`/`[[:alnum:]]`/`\p{Word}` map to
`\p{IsAlphabetic}`, `+\p{IsDigit}`, `+\p{M}\p{Nd}\p{Pc}`. Ruby's `[^}\2]` (octal 2 inside a class) is
`[^}\x02]`. The scanner reproduces strscan's non-fixed-anchor mode with Java matcher regions (anchoring,
opaque bounds): `^`/`\A` match at the scan position and look-behind cannot see before it.

## Deliberate approximations
- **Rouge (D7):** lexers are not ported. Code text is emitted as one Text token (escaped `&<>` as
  `Rouge::Formatters::HTML` does) inside the exact `HTMLLegacy`/`HTMLPygments` wrapper; known vs unknown
  language follows Rouge 4.7.0's registry (tags + aliases, case-sensitive; `jekyll/tests/kramdown/rouge-registry.txt`),
  `plaintext`/`text` + `guess_lang`, `default_lang`, `disable`, `css_class`, `wrap`, span/block sub-options,
  `formatter: HTML`/unknown name (bare tokens). The suite checks all 391 lexers preserve text exactly.
  `lang=guess` / `?`-options: lexer guessing is approximated by plaintext. `line_numbers` (HTMLTable),
  `inline_theme` and other formatters are not reproduced.
- Performance-only shortcuts (no semantic change): cheap necessary-condition prefilters before block start
  regexes, and a linear-time equivalent of `LINK_DEFINITION_START` when no title can match.
- Line numbers (`options[:location]`) are tracked like Ruby but only matter for warnings.
- `transliterated_header_ids: true`: Ruby needs the `stringex` gem, which the reference image lacks, so a
  converter-generated id raises (Gem::MissingSpecError); the port throws `KramdownError` at the same point.
  (GFM-generated ids, i.e. normal headers, are unaffected, as in Ruby.)
- `MiniYaml` replaces `YAML.safe_load` for option values given as strings (flow maps/lists, simple
  `key: value` lines, scalars); malformed input is rejected similarly to Psych.

## Known gaps (not ported)
- `html_to_native: true` (`Parser::Html::ElementConverter`): throws. Only reachable by config; no IG uses it.
- `template` option (ERB templates), `minted` highlighter, other input parsers (`markdown`, `html`).
  `input: kramdown` (the plain kramdown parser) is supported.
- Rouge token spans (D7); Rouge lexer runtime errors (e.g. the `stan` lexer raising
  "unknown state: :string" on some text) are not reproduced.

## 2.6.1 delta (applied 2026-11-23)

The autobuilder's images moved to kramdown 2.6.1 on Ruby 3.4.13 on 2026-11-19 (INTEGRATION.md "Compatibility
contract"). `diff -ru` of the two gems' `lib/` is 156+/48− lines in 17 files; what changes output, and where it is:

| 2.6.1 change | Effect | Java |
|---|---|---|
| `header_links` takes a hash `{content:, position:}` (true = `{content: '', position: :start}`; a hash without `position` puts the link at the end; the string `"false"` is now an error) | header anchors with text, before or after the header text | `Options` (validator), `HtmlConverter.convertHeader` |
| footnote with an empty definition: `li.children.last&.type` | renders (a new paragraph with the backlink) instead of `NoMethodError` | `HtmlConverter` footnotes |
| intraword `_`: `[[:alnum:]]` (was `[[:alpha:]]`), matched within the last 50 characters of `pre_match` | `a1_b_`, `9_x_` stay literal; a run of more than 49 `_` before `_em_` no longer blocks emphasis | `KramdownParser.emIntraword` (O(1), `PreMatchEquivalence` checks it against the regex) |
| one nesting counter for `parse_blocks`/`parse_spans` (> 100) and `parse_raw_html` (> 200) | `Kramdown::Error: Elements nested too deep at block/span/HTML raw level` instead of output | `KramdownParser.stackCounter` |
| `![alt][ref]`: `el.attr['alt'] ||= alt_text` | an `alt` from the link definition's IAL wins | `KramdownParser.addLink` |
| failed bracket scans cached per (position, emphasis context); a failed or cached scan inside a link text ends that text's scan (`terminate`) | none (linear time on unclosed brackets) | `KramdownParser.parseLink` |
| `ALD_TYPE_ANY` gets a last alternative `(\S+)` | warning "IAL/ALD contains invalid part: …" (warnings only) | `KramdownParser.parseAttributeList` |
| `current_line_number` counts forwards only (`byteslice(...).count("\n")`) | line numbers in warnings after a scanner moved backwards | `StrScanner.currentLineNumber` |
| HTMLLegacy formatter inlined; comment/PI/CDATA scans guarded by `exist?`; `check` → `match?`; abbreviation regexps; list pattern cache bounded | none (performance / Rouge deprecation) | not needed |

With Ruby 3.4 also came two message formats the shim reproduces: `Hash#inspect` is `{"a" => 1}` (Liquid output of a
hash, `inspect` filter; `liquid.Values`, `yaml.RubyValues`) and `NoMethodError` reads `undefined method 'x' for nil`
/ `for an instance of String` (`liquid.RubyError.noMethod`, Jekyll filters, Rouge lexers).

Ruby 3.4's Onigmo also counts Join_Control (U+200C ZWJ, U+200D ZWNJ) as `\p{Word}`, which GFM header ids keep
(`HtmlConstants.WORD`, `RRegex.WORD`; the Java set equals Ruby 3.4.13's on every code point). Found by the corpus
run below (a ZWJ+VS16 emoji in a header of hl7-eu base).

Verification: all committed suites re-recorded with the 2.6.1 / Ruby 3.4 oracle (`ORACLE_IMAGE=…-next`); the
feature suite gained kramdown 2.6.1's 3 new testcases (+ changed `imagelinks`) and 27 targeted `d261-*` cases
(boundaries of each change, recorded under both images: 2.5.2 and 2.6.1 differ on exactly the ones above). Shim
= 2.6.1 on every suite; the 2,207 real markdownify calls are identical under both images and in the shim. Corpus
(2026-11-23): Ruby Jekyll of the 2.6.1 image vs the shim on the 268 goldens' `jekyll-in` (1,021,376 files):
262 identical; 3 differ only in build timestamps (`site.time`); in 3 both fail with the same Liquid Exception
(include names differing only in case, the goldens were recorded on a case-insensitive disk) and both write no
output.
Known edge not reproduced: the O(1) emphasis skip (P4.9) skips sub-parses that provably fail, so a > 100-deep
nesting reached only inside such a skipped attempt raises in Ruby but not in the shim.

## Test suites
- Corpus: `jekyll/tests/corpus/md-cases.jsonl` (2,207 real markdownify calls): **2207/2207**.
- Feature suite: `jekyll/tests/kramdown/cases.jsonl` (+ `expected.jsonl` from the oracle), generated by
  `gen_cases.py`: every kramdown 2.6.1 and kramdown-parser-gfm 1.1.0 testcase, each with its `.options`
  (or kramdown's test default `{auto_ids: false, footnote_nr: 1}`) and again merged into the typical IG
  config; ~130 targeted cases (`targeted.md`: footnotes, definition lists, table footers, `{::options}`,
  math, IALs/ALDs, `markdown="1|block|span|0"`, `{:toc}`/`.no_toc`, header ids incl. duplicates and
  non-ASCII, task lists, hard wraps, entities, smart quotes, highlighter options, error parity, ...);
  one fenced block per Rouge lexer. **1043/1043**.
  Regenerate: `python3 jekyll/tests/kramdown/gen_cases.py && jekyll/oracle/oracle.sh jekyll/tests/kramdown/cases.jsonl jekyll/tests/kramdown/expected.jsonl`.
- Excluded upstream testcases (17): `html_to_native` (block/03_paragraph/with_html_to_native,
  block/09_html/html_to_native/* (12), block/14_table/empty_tag_in_cell) and minted
  (block/06_codeblock/highlighting-minted, highlighting-minted-with-opts, span/03_codespan/highlighting-minted).
- `cases-d7.jsonl` (8 cases): output differs from Ruby **only** by Rouge token spans outside
  `<pre class="highlight"><code>`: inline code spans with a language (`` `x`{: .language-ruby} ``) and a
  non-wrapping Rouge formatter. They pass once all `<span class="…">`/`</span>` are stripped. DiffRunner
  strips spans only inside `<pre class="highlight"><code>`; extending that to
  `<code class="… highlighter-rouge">` would make these pass (DiffRunner change needed, not done here).
  The real corpus has no such spans.
- Differential fuzzing (`fuzz.py`, `fuzzround.sh <seed> <count>`): ~35k random/mutated documents (suite
  texts and windows of the real corpus, 9 configs) showed no differences other than the D7 inline spans.
- `KramdownConcurrencyCheck`: 8 threads, shuffled, 3× corpus: identical to sequential output.

## Performance
Whole 2,207-case corpus (42 MB of Markdown), one pass after JIT warmup (`KramdownBench`): **~1.65 s CPU**;
Ruby (same gems, `rbbench.rb`) **~6.0 s CPU**. The original first 878 cases: ~0.38 s vs Ruby ~0.98 s. Measured on a loaded shared machine (load avg ~30).
