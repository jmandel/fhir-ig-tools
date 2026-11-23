# third_party: upstream reference sources (developers only)

These are **optional** git submodules: the exact upstream versions the FHIR autobuilder image runs,
which the Java code in `jekyll/` ports. Nothing in the build or at runtime reads them: `./build.sh`
compiles without them, and the differential oracle runs the real gems inside the pinned Docker image.
They are for reading the Ruby beside the Java, and for a few test-case generators
(`jekyll/tests/{kramdown,rouge,yaml}/gen_*.py`; the YAML suite cases point into `yaml-test-suite/`).

```
git submodule update --init --depth 1     # ~26 MB, shallow
```

| Path | Upstream | Tag | Why |
|---|---|---|---|
| `liquid/` | Shopify/liquid | v4.0.4 | ported → `org.hl7.fhir.jekyll.liquid` |
| `jekyll/` | jekyll/jekyll | v4.4.1 | ported → `org.hl7.fhir.jekyll.site` |
| `kramdown/` | gettalong/kramdown | REL_2_5_2 | ported → `org.hl7.fhir.jekyll.kramdown` |
| `kramdown-parser-gfm/` | kramdown/parser-gfm | REL_1_1_0 | GFM input parser (Jekyll's default) |
| `rouge/` | rouge-ruby/rouge | v4.7.0 | ported (core + IG lexers) → `org.hl7.fhir.jekyll.rouge` |
| `safe_yaml/` | dtao/safe_yaml | 1.0.5 | Jekyll's YAML typing rules → `org.hl7.fhir.jekyll.yaml` |
| `psych/` | ruby/psych | v5.0.1 | Ruby YAML binding (scalar scanner, tags) |
| `libyaml/` | yaml/libyaml | 0.2.5 | the C YAML parser under Psych |
| `date/` | ruby/date | v3.3.3 | Date/Time parsing for YAML timestamps |
| `addressable/` | sporkmonger/addressable | addressable-2.9.1 | one function (URI unescape for static paths) |
| `yaml-test-suite/` | yaml/yaml-test-suite | data-2022-01-17 | conformance test data |

Not a submodule (ruby/ruby is large): the YAML port's number parsing/formatting follows these Ruby
3.2.3 files: [`object.c`](https://github.com/ruby/ruby/blob/v3_2_3/object.c),
[`numeric.c`](https://github.com/ruby/ruby/blob/v3_2_3/numeric.c),
[`missing/dtoa.c`](https://github.com/ruby/ruby/blob/v3_2_3/missing/dtoa.c),
[`util.c`](https://github.com/ruby/ruby/blob/v3_2_3/util.c),
[`string.c`](https://github.com/ruby/ruby/blob/v3_2_3/string.c),
[`bignum.c`](https://github.com/ruby/ruby/blob/v3_2_3/bignum.c),
[`pack.c`](https://github.com/ruby/ruby/blob/v3_2_3/pack.c),
[`time.c`](https://github.com/ruby/ruby/blob/v3_2_3/time.c).

To bump a version: `cd third_party/<x> && git fetch --depth 1 origin tag <TAG> && git checkout <TAG>`,
then commit the new submodule pointer and update this table.
