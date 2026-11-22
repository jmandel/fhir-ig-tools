# YAML port (`org.hl7.fhir.jekyll.yaml`): notes

Jekyll 4.4.1 loads `_config.yml`, `_data/*.{yml,yaml,json}` (JSON too) and front matter with
`SafeYAML.load` / `SafeYAML.load_file` (safe_yaml 1.0.5, Psych 5.0.1 branch with no whitelisted tags,
so the streaming `SafeYAML::PsychHandler`), over libyaml **0.2.5** (`Psych::LIBYAML_VERSION` in the
reference image), Ruby 3.2.3 / date 3.3.3. This package ports that whole chain. The libyaml part (reader,
scanner, parser: the event layer) lives in the `common` module as `org.hl7.fhir.common.yaml` so other modules
can reuse it; the Psych/SafeYAML construction layer is here.

## API
```java
Object v = org.hl7.fhir.jekyll.yaml.Yaml.load(String text);   // SafeYAML.load(str): UTF-8 string, no BOM sniffing
Object v = org.hl7.fhir.jekyll.yaml.Yaml.loadFile(Path p);    // SafeYAML.load_file: File.open(p, 'r:bom|utf-8'), path in errors
Object v = Yaml.loadBytes(byte[] utf8);                       // SafeYAML.load of a Ruby string given as raw (maybe invalid) bytes
Object v = Yaml.loadFileBytes(byte[] contents, String name);  // load_file for already-read contents
```
Errors: `YamlException` (`rubyClass()`: `Psych::SyntaxError` (subclass `PsychSyntaxError`, exact Psych
message incl. `(<file>|<unknown>): problem context at line L column C`), `TypeError`, `ArgumentError`).
Thread-safe (no shared mutable state; `YamlMap` uses thread-locals for recursion guards).

## Values
| YAML | Java |
|---|---|
| mapping | `YamlMap` = `LinkedHashMap<Object,Object>` (insertion order; duplicate key: first position, last value) |
| sequence | `YamlList` = `ArrayList<Object>` |
| string | `String` |
| integer | `Long`, `BigInteger` beyond 64 bits |
| float, `.inf`, `.nan`, sexagesimal (`1:30` → 90.0, always Float) | `Double` |
| bool / null | `Boolean` / `null` |
| `YYYY-MM-DD` | `RubyDate` (this package; `to_s` "YYYY-MM-DD", `jd`, Julian before 1582-10-15) |
| timestamps (`TIME_MATCHER`) | `org.hl7.fhir.jekyll.liquid.RubyTime.fixed(instant, offset)` (DateTime#to_time: fixed offset, `utc?` false, `to_s` "+0000" even for `Z`) |
| `!binary` | `String` if the bytes are UTF-8, else `BinaryString` (raw bytes) |
| empty stream (empty / comments only) | `Boolean.FALSE` (`---` alone gives `null`) |

**Non-string keys** stay the Java value of the Ruby key: `1:` → `Long` key, `yes:` → `Boolean.TRUE`,
`1.5:` → `Double`, `2020-01-01:` → `RubyDate`, `? [a, b]` → a `YamlList` key. A String lookup misses
them, as `hash["1"]` misses `1` in Ruby. Keys that load as **nil** (`~:`, `null:`, `: v`) are never
stored: SafeYAML keeps one `@current_key` and treats nil as "no key yet", so the *value* becomes the
next key (`{null: a, b: c}` → `{"a" => "b"}`, "c" dropped). Glue code should iterate maps as
`Map<?,?>`; where Ruby stringifies a key (JSON.generate, `inspect`), use `RubyValues.toS(key)` /
`RubyValues.inspect`.

**Liquid/Jekyll treatment of RubyDate** (not wired yet): Ruby `to_s` = `RubyDate.toS()` (also
`toString()`, so `Values.toS` already prints it right; `Values.inspect` would print the same instead
of `#<Date: 2020-01-01 ((2458850j,0s,0n),+0s,2299161j)>` = `RubyDate.inspect()`). Liquid's `date`
filter calls `Date#strftime` directly (Utils.to_date returns anything that responds to strftime) →
`RubyDate.strftime(fmt)` (midnight, offset +00:00, `%Z` = "+00:00"); `liquid.Utils.toDate` currently
returns null for it, so `| date:` would echo the input. Jekyll's `date_to_xmlschema` & co. call
`Date#to_time` → `RubyDate.toTime()` (local midnight). Comparisons: Date<=>Date by `jd()`.

## Semantics reproduced (all oracle-verified)
- SafeYAML typing order ToSymbol(off) → ToInteger → ToFloat → ToNil → ToBoolean → ToDate, on
  libyaml's `quoted_implicit` flag: block scalars count as quoted; **tagged scalars are typed**
  (`!!str 123` → 123, `! '123'` → 123); only `!binary`/`!!binary`/`x-private:binary` tags matter.
- ToInteger strips `_` and `,` before matching (`_1` → 1, `1,000` → 1000, `0_7` → 7 octal);
  `Float()` errors propagate (`1_.5` → `ArgumentError: invalid value for Float(): "1_.5"`), with
  Ruby's underscore buffer and strtod's 60-digit fraction cut-off; `-1:30` → 30.0 (SafeYAML bug).
- Dates: invalid → stays a String; `Date.parse` with ITALY (1500-02-29 valid, 1582-10-10 not);
  times: `DateTime.parse` subset (sec 60 → 59, 24:00:00 → next day, zone offsets via
  `date_zone_to_diff`, |offset| > 1 day ignored, then `Time.new`'s ±86400 check → String), 128-byte limit.
- Merges: `<<` uses `Hash#merge!` when the value arrives: alias to a map merges; an inline map merges
  while still empty (nothing); a sequence of maps / scalar / nil → `TypeError: no implicit conversion of X into Hash`.
- Aliases re-type the anchored value (`&a "123"` … `*a` → 123; an alias to non-UTF-8 binary →
  `ArgumentError: invalid byte sequence in UTF-8`); unknown alias → nil; recursive structures allowed.
- First document only (parsing stops at its DOCUMENT-END, so errors later in the stream are not
  raised unless libyaml's lookahead already hit them, as in C); empty stream → false.
- Reader: 16 KiB raw-buffer chunking kept, so reader errors (invalid UTF-8, control characters)
  surface at the same point; `load_file` drops a UTF-8 BOM and raises
  `ArgumentError: ASCII incompatible encoding needs binmode` for UTF-16/32 BOMs; in `load(String)` a
  BOM is a character that libyaml skips at column 0 but which shifts that line's column by one
  (`"﻿a: 1\nb: 2"` loads as `{"a"=>1}`).

## File map
| Source | Java (`common/…/org/hl7/fhir/common/yaml` for the libyaml rows, this package for the rest) |
|---|---|
| libyaml-0.2.5 `include/yaml.h` (mark/token/event) | `Mark`, `Token`, `Event` |
| `src/yaml_private.h` (string macros; char-class macros) | `ByteString`; `Reader.at/check/isBreak…` |
| `src/reader.c` (+ `api.c` string read handler) | `Reader` |
| `src/scanner.c` | `Scanner` (token queue, simple keys, indents, flow level) |
| `src/parser.c` | `Parser` (state machine, tag directives) |
| error state of `yaml_parser_t` | `LibyamlError` |
| psych-5.0.1 `ext/psych/psych_parser.c` | `PsychParser` (event loop, make_exception) |
| psych `lib/psych/syntax_error.rb` | `PsychSyntaxError`, base `YamlException` |
| safe_yaml `load.rb` | `Yaml` |
| safe_yaml `psych_handler.rb` | `PsychHandler` |
| safe_yaml `transform.rb`, `transform/*.rb`, `parse/*.rb` | `Transform` (+ nested ToInteger, ToFloat, …, Parse) |
| ruby-3.2.3 `object.c` rb_cstr_to_dbl_raise, `missing/dtoa.c` strtod, `numeric.c` flo_to_s; Integer() | `RubyNumbers` |
| ruby `pack.c` unpack 'm' (Base64.decode64) | `RubyBase64` |
| date-3.3.3 `date_parse.c` (date__parse, zone_to_diff), `date_core.c` (civil/jd, dt_new_by_frags, to_time) | `DateParse`, `RubyDate` |
| value helpers | `YamlMap`, `YamlList` (recursion-safe hash/equals), `BinaryString`, `RubySymbol`, `RubyValues` |

Sources fetched for the port: `third_party/libyaml`, `third_party/psych`, `third_party/date`,
Ruby 3.2.3 `ext/date`/`ext/psych` files (see `third_party/README.md`), `third_party/yaml-test-suite` (release data-2022-01-17).

## Tests (`./build.sh --fast yaml`, then `jekyll/tests/yaml/run-all.sh`)
Runner: `BUILD=build-yaml jekyll/run.sh org.hl7.fhir.jekyll.test.yaml.YamlDiff <cases> <expected>`
(registers `yaml`, `data-file`, and the new oracle types `yaml-typed` / `yaml-typed-file`, whose
output is a typed encoding with error messages compared verbatim; see `oracle.rb` `yaml_typed`).

| Suite | Cases | Result |
|---|---|---|
| `jekyll/tests/site/yaml-cases.jsonl` (real configs/data) | 100 | 100% |
| `jekyll/tests/site/data-cases.jsonl` (real `_data`, 45.8 MB) | 587 | 100% |
| `jekyll/tests/yaml/suite-*` yaml-test-suite, each `in.yaml` as text and as file (240 errors) | 804 | 100% |
| `jekyll/tests/yaml/typing-*` (`gen_typing_cases.py`) | 5,047 | 5,005; the 42 others are the known gap below |
| `jekyll/tests/yaml/frontmatter-*` (real front matter from goldens/repos/templates) | 310 | 100% |
| fuzz (`fuzz.sh`, seeds 1–18: grammar, mutations of real files, token soup, >16 KiB docs, byte-level file mutations) | 343,000 | 0 diffs |

Speed: about the same as the old `Json.parseAsYaml` on the 587 `_data` files (≈3–4 s per pass for
45.8 MB on the shared VM).

## Known gaps
- **Timestamps with a UTC offset beyond ±18:00** (`+19:00` … `+23:59`, valid in Ruby): `RubyTime`
  stores a `java.time.ZoneOffset` (max ±18:00), so these load at UTC with the right instant but the
  wrong offset. Typing cases `typing/scalar/211|215|233/*` (42 cases). Fix belongs in
  `liquid.RubyTime` (store offset seconds).
- Liquid's `Strftime` prints year -1 as `-001` (Ruby: `-0001`); only reachable via `0000-01-01 00:00:00`
  timestamps (the test encoder formats times itself).
- Subsecond precision beyond nanoseconds is truncated (Ruby keeps the exact Rational).
- Hash-key corner cases where Ruby `eql?` differs from Java `equals`: `0.0` vs `-0.0` (one key in
  Ruby, two in Java) and NaN keys (distinct in Ruby, one in Java).
- `ToSymbol` (`deserialize_symbols`) and custom initializers are ported but off, as in Jekyll; tag
  whitelisting (`SafeYAML::PsychResolver` path) is not ported: Jekyll never whitelists tags.
