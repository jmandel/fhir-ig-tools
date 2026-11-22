# kramdown port notes (`org.hl7.fhir.jekyll.kramdown`)

Port of kramdown 2.5.2, kramdown-parser-gfm 1.1.0 and the HTML converter, configured the way
Jekyll 4.4.1 calls them. Tracker: P4.

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

## Test suites
- Corpus: `jekyll/tests/corpus/md-cases.jsonl` (2,207 real markdownify calls): **2207/2207**.
- Feature suite: `jekyll/tests/kramdown/cases.jsonl` (+ `expected.jsonl` from the oracle), generated by
  `gen_cases.py`: every kramdown 2.5.2 and kramdown-parser-gfm 1.1.0 testcase, each with its `.options`
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
