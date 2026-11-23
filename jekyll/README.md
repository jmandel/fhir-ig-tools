# jekyll: the IG Publisher's Jekyll stage in Java (`jekyll.jar`)

A Java replacement for the Jekyll (Ruby) stage of the [FHIR IG Publisher](https://github.com/HL7/fhir-ig-publisher):
no Ruby, the same output, 3–7× faster. This is the `jekyll` module of [fhir-ig-tools](../README.md); it builds
`jekyll/target/jekyll.jar`, and its YAML stack sits on the shared libyaml event layer in [`../common`](../common/README.md).

![jekyll.jar: temp/pages from the IG Publisher is read, rendered with Java ports of Liquid, kramdown, Rouge and Jekyll's YAML stack, and written as the same output folder Jekyll writes; 260 of 266 real IGs identical; how the publisher calls it; how the output is verified](../docs/img/jekyll.svg)

**Quick start** (Java 17+; on Windows use [`jekyll-shim.cmd`](https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll-shim.cmd) and pass its full path):

```sh
curl -fLO https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll-shim && chmod +x jekyll-shim
java -jar publisher.jar -ig . -jekyll "$PWD/jekyll-shim"    # first run fetches and verifies jekyll.jar, then works offline
```

## Why

To render an IG's narrative pages, the publisher writes `temp/pages` and runs `jekyll build`. That puts
a whole second toolchain (Ruby 3.2, Jekyll 4.4.1, Liquid, kramdown, Rouge, libyaml) into every place an
IG is built: the autobuilder, CI images, and every author's laptop. It has its own install pains,
especially on Windows, and its own security patch cycle, and it is the slow, memory-hungry part of the
pipeline that nobody on the Java side can debug.

The publisher uses only a small, stable slice of Jekyll. Layouts are pre-expanded, front matter is
empty, and most of the input is `{% raw %}` fragments. So this project doesn't try to be "a Jekyll in
Java". It is a **faithful, file-by-file port of the slice real IGs use**, checked against the real gems
until the output is identical.

## Results

### Output: identical

The bar is **exact equality with real Jekyll** after DOM normalization (attribute order, insignificant
whitespace, entity spelling). Syntax-highlight `<span>`s are compared too, because the templates style
them.

| Check | Result |
|---|---|
| Golden corpus: real publisher + real Jekyll on real IGs from build.fhir.org, then the shim on the same input | **266 IGs (the IGs built on build.fhir.org over a month): 260 identical, 1,024,171 files**, including UTG (259k input → 89,572 output files). The other 6 can't be identical by construction: 3 differ only in build timestamps (`site.time`), 2 include files under a name that differs only in case (the goldens were recorded on a case-insensitive disk), and in 1 real Jekyll itself fails |
| Full publisher run (`-jekyll jekyll-shim`) vs a real-Jekyll publisher run (UDAP) | identical apart from per-run data (build timestamps, GUIDs, QA timing counters) |
| Liquid 4.0.4: differential vs the real gem | 4,167 / 4,167 cases (+150 CRLF variants) |
| kramdown 2.5.2 + GFM | 2,207 / 2,207 real IG fragments · 4,000 / 4,000 adversarial emphasis cases · upstream suite 702 / 1,043 exact, the other 341 being code fences in languages no IG uses, whose Rouge lexers aren't ported (they render as plain text) |
| YAML (SafeYAML → Psych → libyaml) | 687 real IG files · 804 yaml-test-suite cases · 343k fuzz, typed values identical |
| Rouge 4.7.0 | 2,467 / 2,467 highlighted fragments mined from real IGs |
| Jekyll site behavior | 75 / 75 cases (filters, includes, `site.*`, static-file paths, collisions) |
| Template/IG surface inventory | 79 / 79 features observed in sampled IGs covered |

### Speed: 3–7× faster on the Jekyll stage

Same machine (Lima VM, 16 vCPU, otherwise idle), same input on local disk, output to local disk, each tool
run twice alternately and the warm run reported (`scripts/bench.sh`):

| IG | Input → output files | Jekyll 4.4.1 | Shim | Speed-up |
|---|---:|---:|---:|---:|
| IHE ITI.MHD | 12k → 2.1k | 2.4 s | 0.8 s | 3.0× |
| US Core | 35.5k → 16.6k | 14.7 s | 2.8 s | 5.3× |
| fhir-extensions | 179k → 29.8k | 16.3 s | 3.3 s | 4.9× |
| UTG | 259k → 89.6k | 49.6 s | 6.8 s | 7.3× |

Where the input sits matters a lot: read over a VM's shared folder (virtiofs), and with other jobs running, the same
builds took Jekyll 10.6 s / 75.6 s / 571.7 s / 197.6 s and the shim 3.5 s / 21.7 s / 106.0 s / 65.9 s.
In-process integration (below) removes the temp/pages round trip altogether.

### Memory: less than Ruby needs, if you let it

Peak RSS of the whole process tree (`scripts/peakmem.sh`), same runs as above:

| IG | Jekyll 4.4.1 | Shim with a heap cap | Shim, wrapper default |
|---|---:|---:|---:|
| IHE ITI.MHD | 592 MB | 422 MB (256 MB heap) | 1,168 MB |
| US Core | 983 MB | 767 MB (512 MB heap) | 1,969 MB |
| fhir-extensions | 1,678 MB | 1,119 MB (1 GB heap) | 3,681 MB |
| UTG | 4,089 MB | 1,982 MB (1.5 GB heap) | 10,179 MB |

The shim **needs** 50–78% of Jekyll's memory, and the caps above cost no time. By default, though, the
wrapper sets no `-Xmx` (only `-XX:MaxRAMPercentage=75`) so that big IGs never need tuning flags, and the
JVM then grows its heap into free memory: about 2× Jekyll's RSS on this 96 GB VM. On a memory-constrained
machine, set `SHIM_JAVA_OPTS=-Xmx1g` (enough for every IG measured but UTG, which needs 1.5 GB). In-process,
the shim simply shares the publisher's heap.

### How much of a full build is Jekyll?

Jekyll is usually a small part of a publisher run: **2–5%** on the 8 IGs timed in detail (US Core:
37.6 s of 787 s). UTG is the outlier, with Jekyll taking roughly 5–13% of a 63-minute build depending on
machine load. Shaving those minutes is nice, but the main wins are removing the Ruby toolchain, and what
in-process integration enables next: no temp/pages round trip, and fragments generated only when used.

### The "long lines are slow" problem: fixed, same output

Some pages took Jekyll orders of magnitude longer than their size suggests. The cause is kramdown's
emphasis parser backtracking on long paragraphs with many unmatched `_` or `*`, which grows faster than
quadratically. The classic trigger is an unfenced JSON example with `"_birthDate"`-style keys: an 80-entry FHIR Bundle pasted
without a code fence takes Jekyll 13–15 s, against 0.013 s fenced. Rouge's
Java lexer has a similar quadratic rule. The port replaces those paths with proven-equivalent linear
checks (verified over millions of positions, with output unchanged):

| Input | Ruby | Shim |
|---|---:|---:|
| `"_b "` × 1000 (3 KB paragraph) | 6.9–10 s | 0.008 s |
| `"_word "` × 1000 | > 20 s | 0.005 s |
| `"*a* _b* "` × 5000 | > 20 s | 0.029 s |
| Pathological 100 KB ```` ```java ```` block | 17.2 s | 0.013 s |

On the real corpus, Jekyll's markdown throughput was flat (about 3.5 MB/s) and no IG hit the cliff
badly, but hand-authored pages can.

### Portability

Plain JDK 17+ with no native code. File paths, CRLF input and Windows line endings follow Ruby's
semantics, and number and date formatting is locale-independent (verified under Arabic, Turkish and
Thai default locales). Wrappers are provided for Unix (`jekyll-shim`) and Windows (`jekyll-shim.cmd`). CI runs
the test suites, an end-to-end site and the wrapper bootstrap on Linux, macOS and Windows; the golden
corpus runs on Linux.

## What it supports, and what it doesn't

Supported: everything the sampled IGs use, including:
- Liquid 4.0.4 in Jekyll's warn mode (strict parse with lax fallback, the same warnings), with all
  standard tags and filters
- Jekyll's `include` / `include_relative` / `highlight` tags and its filters (`markdownify`, `jsonify`,
  `where`, `where_exp`, `group_by`, `sort`, `date_to_*`, and more)
- `site.pages`, `site.data` (JSON / YAML / CSV / TSV) and `site.static_files`
- kramdown with GFM, TOC, auto ids, smart quotes and footnotes; Rouge highlighting for every fence
  language IGs use
- the `liquid:` and `kramdown:` options in `_config.yml`

Not implemented: Jekyll features the publisher pipeline doesn't use, such as Ruby plugins, themes and
Sass, `jekyll serve` / watch mode, and other site features no sampled IG uses. Behavior on unusual input,
such as unknown filters being ignored or warn-mode fallbacks, follows Jekyll's.

## Use it

Needs Java 17+, which the IG Publisher already requires. Like the Gradle wrapper, one small file is all
you need: it pins a release, downloads that release's `jekyll.jar` on first use, checks its SHA-256, caches
it in `~/.fhir-ig-jekyll/<version>/`, and runs offline from then on.

```sh
# Linux / macOS
curl -fLO https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll-shim && chmod +x jekyll-shim
java -jar publisher.jar -ig . -jekyll "$PWD/jekyll-shim"
```

On Windows, download
[`jekyll-shim.cmd`](https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll-shim.cmd)
and pass its full path: `-jekyll C:\path\to\jekyll-shim.cmd`. You can commit the wrapper into an IG
repository (like `gradlew`) or put it on your `PATH`. To move to a newer version, download the newer wrapper.

- Offline or air-gapped:
  [`fhir-ig-jekyll.zip`](https://github.com/jmandel/fhir-ig-tools/releases/latest/download/fhir-ig-jekyll.zip)
  bundles `jekyll.jar` with both wrappers, which use the jar next to them. Alternatively, set
  `FHIR_IG_JEKYLL_JAR=/path/to/jekyll.jar`.
- Mirrors: `FHIR_IG_JEKYLL_URL` overrides the download URL; `FHIR_IG_JEKYLL_HOME` moves the cache.
- Just the jar:
  [`jekyll.jar`](https://github.com/jmandel/fhir-ig-tools/releases/latest/download/jekyll.jar), run in a
  Jekyll source folder: `java -jar jekyll.jar build --destination ../output`.

From source: `./build.sh -B -pl jekyll -am package` (Maven) builds `jekyll/target/jekyll.jar`. It uses your JDK and
Maven, or the reference Docker image if there is no working JDK or `mvn`; `./build.sh --fast` is a plain-`javac`
build that needs only a JDK. See [Building and testing](../README.md#building-and-testing) and [`docs/MONOREPO.md`](../docs/MONOREPO.md).

Knobs (all optional): `SHIM_RENDER_THREADS`, `SHIM_IO_THREADS`, `SHIM_TIMING=1`.

## Integration roadmap

1. **CLI drop-in:** works today with an unmodified publisher (`-jekyll <cmd>`).
2. **In-process library:** the API is in the shim today: `org.hl7.fhir.jekyll.Shim.build(source,
   destination, options)`, called from the publisher's `runJekyll`. No subprocess or second JVM, and
   errors arrive as exceptions for QA. A publisher-side prototype runs it (see below).
3. **Lazy fragments:** `Shim.Options.includeProvider(...)` lets the publisher supply `_includes` content
   on demand, so it can generate each narrative fragment only when a page first includes it. 75–86% of
   `_includes` files are never included (UTG: 134k of 166k, 1.25 of 2.06 GB), so this skips most of the
   fragment generation and `temp/pages` writing. The API is there; the publisher-side prototype showed
   that using it safely needs publisher changes, so it is not being pursued for now (see below).

Details: [`INTEGRATION.md`](INTEGRATION.md).

## Work in progress: in-process publisher integration

The [`publisher-integration`](https://github.com/jmandel/fhir-ig-tools/tree/publisher-integration)
branch is an experimental IG Publisher (based on Release v3.0.4) that runs this project's `Shim.build`
inside the publisher's JVM (`-jekyll-engine shim`; `shim-mem` also keeps the `_includes` fragments in
memory instead of writing them). On US Core and three other IGs the output matches a default build, and
US Core's Jekyll stage drops from 16–19 s to 3.1 s. See its
[`INTEGRATION-DEMO.md`](https://github.com/jmandel/fhir-ig-tools/blob/publisher-integration/INTEGRATION-DEMO.md).

We also tried generating fragments only when a page includes them (`shim-lazy`; 75–86% of fragments are
never included). It is **not being pursued**: deferred renderers read publisher state that changes later
in the build (getters with side effects, later rewrites, first-caller caches), so output could depend on
thread timing, and making it safe would need read-only renderers enforced in the publisher core, for
about 9% of build time. The investigation and evidence are in the same document. Not for production use.

## For contributors: module contracts

The rest of this file holds the **engineering contracts** between the shim's packages, so they can be built in
parallel. See `../docs/TRACKER.md` for the current plan, and `../docs/archive/TRACKER-shim-v0.1.md` for the decisions
(D1–D10) and task IDs of the work up to v0.1.0.

### Ground rules
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

### Build / run / test (a local JDK, or Docker)
Commands run from the repo root.
```
./build.sh                                              # Maven, all modules -> jekyll/target/jekyll.jar (+ classes, test-classes)
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

### Value model (shared)
JSON → Java: object `LinkedHashMap<String,Object>`, array `ArrayList<Object>`, string `String`,
integer `Long`, float `Double`, bool `Boolean`, null `null` (`org.hl7.fhir.jekyll.util.Json`).
Liquid must treat Long like Ruby Integer and Double like Ruby Float (e.g. `{{ 1.0 }}` → `1.0`,
`10 | divided_by: 4` → `2`).

### Contract: `org.hl7.fhir.jekyll.liquid` (port of liquid 4.0.4)
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

### Contract: `org.hl7.fhir.jekyll.kramdown` (port of kramdown 2.5.2 + kramdown-parser-gfm 1.1.0)
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

### Contract: `org.hl7.fhir.jekyll.site` (Jekyll glue + CLI; main agent)
`java -jar jekyll.jar build --destination <dir>` run in `temp/pages` (what the publisher invokes via
`-jekyll`). Prints Jekyll-like progress and `done in N seconds.`
