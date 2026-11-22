# Liquid 4.0.4 port (`org.hl7.fhir.jekyll.liquid`)

## Status
`jekyll/tests/liquid/cases.jsonl`: 4167 cases, **4167/4167 pass** in DiffRunner. `LiquidWarnCheck` (extra checks) is
also 4167/4167 for: warnings present exactly when Ruby's are, **exact warning text**, exact error class + message, and
exact `render_errors`.

```
./build.sh --fast liquid
BUILD=build-liquid jekyll/run.sh org.hl7.fhir.jekyll.test.DiffRunner jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl
BUILD=build-liquid jekyll/run.sh org.hl7.fhir.jekyll.test.liquid.LiquidWarnCheck jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl
```

### Where the cases come from
Regenerate with `python3 jekyll/tests/liquid/tools/make_cases.py`, then `jekyll/oracle/oracle.sh jekyll/tests/liquid/cases.jsonl jekyll/tests/liquid/expected.jsonl`.
- `suite-{strict,lax}.jsonl`: liquid-4.0.4's own test suite, recorded by `tools/record_suite.rb`. It runs the gem's
  minitest suite in the reference image with `Template#parse`/`#render` (and the direct `StandardFilters` calls in
  standard_filter_test.rb) patched to record every call whose inputs are plain JSON, including the include files read.
  make_cases replays these in strict, lax and warn modes, about 2100 cases. Drops, procs, custom filters and tags are skipped.
- Hand-written edge cases (each rendered with render and with render!): output `to_s`, deep and bracket lookups,
  whitespace control, every operator against every type pair, `and`/`or` chains, case/for/cycle/tablerow/include,
  each filter against nil/int/float/string/array/hash/bool, arity errors, BigDecimal arithmetic, about 100 date
  strings and every strftime directive and flag, strict_variables/strict_filters, and line numbers in errors.
- ~150 malformed-syntax "quirks" in lax/warn/strict, render and render!.
- Every distinct pattern from `data/strict.jsonl` (real IG sources), with plausible data, in warn/lax/strict.

## Ruby → Java file map
| Ruby (lib/liquid/...) | Java |
|---|---|
| liquid.rb (regex constants) | `Liquid` (+ `scan`/`match` helpers) |
| tokenizer.rb, block_body.rb, document.rb, block.rb, tag.rb | `Tokenizer`, `BlockBody`, `Document`, `Block`, `Tag` (+ `Node`) |
| lexer.rb, parser.rb, parser_switching.rb | `Lexer`, `Parser`, `ParserSwitching` (interface with default methods) |
| expression.rb, variable_lookup.rb, range_lookup.rb, variable.rb, condition.rb | `Expression` (+`MethodLiteral`), `VariableLookup`, `RangeLookup` (+`RubyRange`), `Variable`, `Condition` (+`ElseCondition`) |
| context.rb, strainer.rb, template.rb, parse_context.rb, errors.rb, interrupts.rb, resource_limits.rb, file_system.rb, i18n.rb + locales/en.yml | `Context`, `Strainer`, `Template`, `ParseContext` (+`ParseOptions`, `ErrorMode`), `LiquidException`, `Interrupt`, `ResourceLimits`, `FileSystem`, `I18n` |
| drop.rb, forloop_drop.rb, tablerowloop_drop.rb | `Drop` (interface), `LiquidDrop` (= Liquid::Drop), `ForloopDrop`, `TablerowloopDrop` |
| utils.rb, standardfilters.rb | `Utils`, `StandardFilters` |
| tags/*.rb | `tags/*` (Assign, Break, Capture, Case, Comment, Continue, Cycle, Decrement, For, If, Ifchanged, Include, Increment, Raw, TableRow, Unless) |
| Template.tags / global Strainer / Template.file_system | `Environment` |
| core Ruby semantics | `Values` (to_s, inspect, ==, eql?, <=>, to_i, obj[key]), `RubyStrings`, `RubyError` (::ArgumentError etc.), `RubyTime` + `Strftime` + `TimeParser` (Time, strftime, Time.parse) |

Not ported: the profiler, parse_tree_visitor, `LocalFileSystem` (only the `FileSystem` interface, `BLANK` and `ofMap`),
`include_options_blacklist`, taint mode.

## Deliberate approximations
- **`Values.toS` vs `Values.rubyToS`.** `toS` is the output form from the contract: Array is joined, Hash is inspect.
  Ruby's real `Array#to_s` is `inspect`, and filters see that form (`{{ arr | append: "x" }}` gives `[1, 2]x`).
  `rubyToS` gives it. Glue filters that port Ruby `input.to_s` should call `rubyToS`.
- **Sorting** copies glibc `qsort_r`'s top-down merge sort (`StandardFilters.msort`), which Ruby 3.2 uses for
  `sort { }`. Ties and the inconsistent `nil_safe_compare` (nil vs nil gives 1) therefore come out in Ruby's order.
- **BigDecimal**: `Utils.to_number` turns Floats into `java.math.BigDecimal` as Ruby does. Division uses 40 significant
  digits HALF_UP; Ruby uses at least 32, so `to_f` gives the same double except in pathological cases. `x / 0` on a
  BigDecimal gives ±Infinity/NaN (Ruby's default BigDecimal mode). `round(n<1)` returns an Integer, and negative zero is kept.
- **`Time.parse`** is a subset of `Date._parse`, run in Ruby's order with matched text blanked: weekday, time+zone, eu,
  us, iso, vms, sla, dot, mon, mday, ddd. It also has `s3e`'s year/month/day swapping, 2-digit year completion,
  `make_time`'s fill-from-now, and `force_zone!`. Not done: iso2 week/ordinal dates, jis eras, `_parse_frag`, and the
  BC/yday forms. If a string fails to parse, `date` returns the input unchanged, as Ruby does for strings it rejects.
  Zones follow time.rb's `zone_offset` (UTC/GMT/UT/Z, US zones, military letters, `±hh[:mm[:ss]]`).
  Local time = `RubyTime.localZone` (default: JVM zone; the image is UTC).
- `Cycle` names an unnamed group by Ruby's `@variables.to_s`. For VariableLookups that string holds an object address,
  so each tag gets its own group; I use identityHashCode for the same effect.
- Integers are Long, with BigInteger when arithmetic overflows. JSON input can't carry bignums (`util/Json` turns
  them into Double), so those are only tested as template literals.
- `capitalize`'s first character uses Java's titlecase (plus ß→Ss). Unicode case mapping elsewhere is Java's
  `Locale.ROOT` mapping, which is the same as Ruby's except for exotic cases (final sigma).

## Known gaps
- Resource limits are present but off. MemoryError paths are untested.
- `Drop` lookups have no Ruby `respond_to?` reflection. Drops answer `invokeDrop`/`hasKey`. Optional interfaces
  `Utils.Enumerable` (for/where/map over a drop), `Utils.SliceLoadable` and `Utils.NumberLike` add what Ruby gets
  from duck typing.
- `Template` is safe to render repeatedly but not concurrently (`errors()` and the sticky render! flag are per
  template, as in Ruby).
