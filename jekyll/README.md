# jekyll (the shim): Java replacement for the Jekyll stage of the FHIR IG Publisher

The `jekyll` module of the multi-module build (`../docs/MONOREPO.md`); the libyaml event layer it builds on
lives in `../common` (`org.hl7.fhir.common.yaml`).

See `../docs/TRACKER.md` for goals, decisions (D1–D7), and task IDs. This file holds the
**engineering contracts** between modules so they can be built in parallel.

## Ground rules
- **No external dependencies.** JDK only (plus the repo's own `common` module), Java 17 language level
  (`--release 17`); runs on JDK 21.
- **Ports, not look-alikes (D2).** Mirror the Ruby source structure (roughly one Java class per Ruby
  file/class, same method names where sensible) so a reviewer can put the two side by side. Comment
  each class with the Ruby file it ports, e.g. `// port of liquid-4.0.4/lib/liquid/variable.rb`.
- Reference sources (exact versions the autobuilder runs): `../third_party/liquid`,
  `../third_party/kramdown`, `../third_party/kramdown-parser-gfm`, `../third_party/jekyll`,
  `../third_party/rouge` (ported: core + IG lexers, `jekyll/NOTES-rouge.md`).
- Ruby regexes: port carefully. Ruby `\A`/`\z` → Java `\A`/`\z`; Ruby `$`/`^` are always
  line-anchored (Java needs `(?m)`); Ruby `.` excludes `\n` unless `/m` (Java `(?s)` = Ruby `/m`);
  Ruby `\h` = hex digit; Ruby `\w` is ASCII-only by default, as is Java's (keep it that way);
  possessive/atomic groups exist in both. `String#[]`/`scan`/`gsub` semantics: check them.

## Build / run / test (Docker; the host has no JDK)
Commands run from the repo root.
```
./build.sh                                              # Maven, all modules -> jekyll/target/shim.jar (+ classes, test-classes)
./build.sh --fast [liquid|kramdown|site|yaml|rouge]     # module build -> jekyll/build-<module>/ ; no arg = everything -> jekyll/build/
BUILD=build-liquid jekyll/run.sh <main-class> [args]   # runs java in the reference image; cwd=/w=repo root
jekyll/oracle/oracle.sh <cases.jsonl> <expected.jsonl> # REAL Ruby output for cases (paths must be under the repo root)
BUILD=build-liquid jekyll/run.sh org.hl7.fhir.jekyll.test.DiffRunner jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl
```
- `oracle.rb` documents the case formats (`type: liquid` and `type: kramdown`).
- `DiffRunner` compares exactly (Rouge spans included). `--d7` restores the old text-only rule for highlighted code.
  Each module provides a `*CaseRunner` in `src/test/java/org/hl7/fhir/jekyll/test/<module>/` that
  registers itself into `DiffRunner.IMPLS` in a static initializer. DiffRunner loads
  `org.hl7.fhir.jekyll.test.LiquidCaseRunner` / `KramdownCaseRunner` by name, so put a tiny
  forwarding class with that name in package `org.hl7.fhir.jekyll.test` (file under
  `src/test/java/org/hl7/fhir/jekyll/test/<module>/`, which is fine for javac).
- Real-world kramdown corpus: `jekyll/tests/corpus/md-cases.jsonl` + `md-expected.jsonl` (878 captured
  production markdownify calls; oracle-verified identical).

## Value model (shared)
JSON → Java: object `LinkedHashMap<String,Object>`, array `ArrayList<Object>`, string `String`,
integer `Long`, float `Double`, bool `Boolean`, null `null` (`org.hl7.fhir.jekyll.util.Json`).
Liquid must treat Long like Ruby Integer and Double like Ruby Float (e.g. `{{ 1.0 }}` → `1.0`,
`10 | divided_by: 4` → `2`).

## Contract: `org.hl7.fhir.jekyll.liquid` (port of liquid 4.0.4)
Needed by the Jekyll glue (`site` module), which implements Jekyll's own tags and filters on top:
```java
Environment env = Environment.standard();           // std tags + std filters; mutable copy per site
env.registerTag("include", IncludeTag::new);         // replaces Liquid's include, as Jekyll does
env.registerTag("highlight", HighlightBlock::new);   // blocks register the same way
env.registerFilter("markdownify", (ctx, input, args) -> ...);
Template t = Template.parse(source, new ParseOptions().errorMode(ErrorMode.WARN).lineNumbers(true).environment(env));
t.warnings();                                         // List<LiquidException> (warn mode fallbacks)
Context ctx = new Context(assigns /*Map*/, registers /*Map<String,Object>*/, env);
ctx.strictVariables(false); ctx.strictFilters(false);
String html = t.renderStrict(ctx);                    // == render! (Jekyll uses render!: errors throw)
String html2 = t.render(ctx);                         // == render  (errors rendered inline "Liquid error: …")
```
Extension points the glue relies on (names may be refined, but keep the capability):
- `TagFactory { Tag create(String tagName, String markup, ParseContext pc) }` and abstract
  `Tag`/`Block` classes mirroring `Liquid::Tag`/`Liquid::Block` (Block parses its body from the
  tokenizer like Ruby's; `render(Context, StringBuilder)` or `String render(Context)`).
- `Filter { Object apply(Context ctx, Object input, List<Object> args) }`, plus keyword args if a
  filter needs them (Ruby passes a trailing hash).
- `Context`: `evaluate(Expression)` / `get(String markupExpression)` (Ruby `context[...]`),
  `stack(Map, Runnable)` / push/pop scopes, `set(String, Object)` in the current scope,
  `registers()`, `environments()`, resource limits off by default.
- `Expression.parse(String)` and `VariableLookup` usable by tags (Jekyll's include parses
  `key=value` params and evaluates them).
- Values: `Values.toS(Object)` for Ruby `to_s` output semantics (nil → "", Float formatting like
  Ruby, Array → joined, Hash → Ruby inspect), `Values.truthy(Object)`.
- `Drop` interface (`Object invokeDrop(String key)`, optional `toLiquid()`), for lazily computed
  objects such as Jekyll's `forloop`, `site`, `page`.

## Contract: `org.hl7.fhir.jekyll.kramdown` (port of kramdown 2.5.2 + kramdown-parser-gfm 1.1.0)
```java
String html = Kramdown.toHtml(markdownText, siteKramdownConfig /*Map<String,Object>, string keys*/);
```
`siteKramdownConfig` is the site `_config.yml` `kramdown:` block. The implementation merges it over
Jekyll 4.4.1's defaults exactly as `Jekyll::Converters::Markdown::KramdownParser` does
(`jekyll-4.4.1/lib/jekyll/configuration.rb` DEFAULTS["kramdown"] + `kramdown_parser.rb#setup`:
`syntax_highlighter` = rouge, `syntax_highlighter_opts.default_lang` = plaintext,
`guess_lang` copied in), then applies kramdown's `Options` parsing/coercion (e.g. `toc_levels: "1..3"`,
`smart_quotes` as array or comma string). Input parser = GFM. Output = `Converter::Html`.
Syntax highlighting: exactly what kramdown+Rouge produce, token spans included, via the Rouge port
(`org.hl7.fhir.jekyll.rouge`; D7 reversed).
Must be thread-safe for concurrent conversions (no shared mutable state), and fast.

## Contract: `org.hl7.fhir.jekyll.site` (Jekyll glue + CLI; main agent)
`java -jar shim.jar build --destination <dir>` run in `temp/pages` (what the publisher invokes via
`-jekyll`). Prints Jekyll-like progress and `done in N seconds.`
