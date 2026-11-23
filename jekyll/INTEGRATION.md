# Integrating the shim into the IG Publisher

Status and evidence are in [`../docs/TRACKER.md`](../docs/TRACKER.md), and for the work up to v0.1.0 (P1.11, P3.7, P6) in
[`../docs/archive/TRACKER-shim-v0.1.md`](../docs/archive/TRACKER-shim-v0.1.md). This note is for publisher maintainers.

## 1. Drop-in today (no publisher changes)
The publisher already lets you replace the Jekyll command:

    java -jar publisher.jar -ig . -jekyll /path/to/jekyll-shim

`jekyll-shim` (a release copy downloads and verifies its pinned `jekyll.jar` on first use; see the README) runs `java -XX:MaxRAMPercentage=75 -jar jekyll.jar "$@"` (no -Xss/-Xmx needed: the build runs on its own large-stack thread and the heap grows as needed). The publisher calls it exactly as it calls Jekyll
(`<cmd> build --destination <output>` with cwd = `temp/pages`), and the shim prints Jekyll's console
lines, including `done in N seconds.`, which `PublisherGenerator#passJekyllFilter` keys on.
Errors print `Liquid Exception: …` and exit 1, like Jekyll. Like Jekyll, a failed build leaves the destination as it
was (Jekyll renders every page before its cleaner and writes run; the shim streams pages into a staging folder
`<destination>/.jekyll-shim-staging-*` and moves them into place only after the whole build succeeded, so a
failure creates or deletes nothing in the destination; a destination that didn't exist isn't created).

Tunables (environment): `SHIM_RENDER_THREADS` (default = CPUs), `SHIM_IO_THREADS` (default 2×CPUs,
4–32), `SHIM_TIMING=1` (per-phase timings on stderr).

## 2. In-process (prototyped)
Running the shim as a library inside the publisher's JVM removes the JVM startup and lets the publisher
skip the temp/pages → output disk round trip later. An experimental publisher on the `publisher-integration`
branch does this (`-jekyll-engine shim`; see section 5 and the README). Public API (`org.hl7.fhir.jekyll`; the CLI `Main` is a
thin wrapper over it, with the same console lines):

```java
Shim.Result r = Shim.build(Path.of(pf.tempDir), Path.of(pf.outputDir), new Shim.Options()
    .log(line -> log("Jekyll: " + line.trim()))   // Jekyll's console lines (default System.out)
    .includeProvider(registry)                    // optional IncludeProvider: name -> content | null
    .rescanStaticsAfterRender(true));             // copy files that lazy generation wrote meanwhile
// throws ShimException (isRenderFailure() = Jekyll's "Liquid Exception", exit 1); r has counts/timings
```
`IncludeProvider.include(name)` is consulted before the disk for `{% include %}` (and `include_relative`
into `_includes`): non-null = the file's content, null = look on disk (missing everywhere still fails
with Jekyll's message). Each name is asked once per build (the content is kept for later uses); calls
come from the render threads, so a provider over non-thread-safe code serializes itself. This would let the
publisher generate fragments on first use (~75–86% of `_includes` are never included); the prototype showed that
doing so safely needs publisher changes (renderers that read only settled state), so it is not being pursued for
now (README, "Work in progress").
The build runs on its own large-stack thread. The shim depends only on the JDK and this repository's `common`
module (the libyaml event layer), so it can be used as the one self-contained `jekyll.jar` (common shaded in), or
as the `jekyll` and `common` modules (`org.hl7.fhir.jekyll.*`, `org.hl7.fhir.common.yaml`).

## 3. Rollout plan
1. **Shadow mode (autobuilder):** build with Jekyll as today and also run the shim into a side
   directory; normalized-DOM diff (`scripts/domdiff.py` rules, TRACKER D1/D7); report diffs; ship Jekyll's
   output. Exit criterion: zero diffs on all builds over N weeks.
2. **Opt-in:** `-jekyll-in-process` (or `ig.ini` `jekyll = shim`) for IG authors.
3. **Default with fallback:** shim by default; on a shim error, log it and rerun real Jekyll if present.
4. **Remove Ruby** from the autobuilder image (and from author setup instructions).

## 4. Compatibility contract
- Target: Jekyll 4.4.1 / Liquid 4.0.4 / kramdown 2.5.2 + kramdown-parser-gfm 1.1.0, as configured by
  the publisher's templates (TRACKER D9). Upstream's image moved to kramdown 2.6.1 on 2026-11-19; the
  real corpus renders identically under both (P0.11); the small delta is tracked as P9.1.
- Equivalence: normalized-DOM equality (D1), including Rouge's token `<span>`s in highlighted code (D7;
  the templates style them). Fence languages whose Rouge lexers aren't ported (none that IGs use) render as
  plain text.
- Liquid error mode `warn` (strict, then lax fallback with the same warning text), unknown filters
  silently ignored, `render!` semantics: all as Jekyll does (D4).
- Not supported (unused by IGs): Jekyll plugins, layouts, collections/posts, Sass, `smartify`,
  `sassify`/`scssify`, pagination, incremental builds, `--watch`.

## 5. Performance (Lima VM, 16 vCPU, idle, input and output on local disk; `scripts/bench.sh`)
Jekyll stage, Jekyll 4.4.1 → shim: ITI.MHD 2.4s → 0.8s, US Core 14.7s → 2.8s, fhir-extensions 16.3s → 3.3s,
UTG 49.6s → 6.8s (3–7×). The shim needs 50–78% of Jekyll's peak RSS (with a heap cap); uncapped, the JVM
grows into free memory. In-process (`-jekyll-engine shim`, branch `publisher-integration`), US Core's
Jekyll stage drops from 16–19s to 3.1s. Pathological kramdown inputs (long paragraphs with unmatched
emphasis) that take tens of seconds in Ruby take milliseconds in the shim, with identical output (P0.12, P4.9).
