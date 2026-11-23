# Multi-module layout

This repository holds Java replacements for the non-Java stages of the FHIR IG Publisher: the Jekyll
replacement ("the shim", module `jekyll`) and the Java port of SUSHI (module `sushi`). They share one Maven
build, so shared code (the YAML event layer, the JS value model) lives in one place, module `common`.

## Layout

```
pom.xml            aggregator and parent: module list, Java level, plugin versions, reproducible-jar timestamp
build.sh           runs the build: Maven (local or in the reference container), or plain javac for the shim (--fast)
common/            zero-dependency shared code, Java 17
  pom.xml          artifact org.hl7.fhir:fhir-ig-common
  src/main/java/org/hl7/fhir/common/yaml/    libyaml 0.2.5 event layer (Reader, Scanner, Token, Parser, Event,
                                             Mark, LibyamlError, ByteString), with an opt-in yaml@1.10 mode
  src/main/java/org/hl7/fhir/common/js/      the JS value model: Js, JsNumber, JsObject, Json, JsonSerializable,
                                             Lodash, Frozen (JSON.stringify/parse, Number formatting, lodash)
  src/test/java/…                            JUnit tests (event layer API, JS value model with node-generated vectors)
jekyll/            the shim (was shim/ before the move)
  pom.xml          artifact org.hl7.fhir:fhir-ig-jekyll; also writes target/shim.jar, the release jar
  src/main/java/org/hl7/fhir/jekyll/…        Shim, IncludeProvider, ShimException, liquid, kramdown, rouge,
                                             yaml (Psych/SafeYAML construction), site, util
  src/test/java/…  main()-style case runners and checks (DiffRunner, ShimApiCheck, …)
  tests/ oracle/   differential suites and the Ruby oracle
  jekyll-shim, jekyll-shim.cmd               the self-downloading wrappers (stamped at release)
  README.md, INTEGRATION.md, NOTES-*.md
sushi/             the SUSHI port, Java 21 (sushi-java before the move, see "The SUSHI module")
  pom.xml          artifact org.hl7.fhir:fhir-ig-sushi; also writes target/sushi.jar (self-contained, shaded)
  src/main/java/org/hl7/fhir/sushi/…         App (CLI), importer (hand-written FSH parser, importer, config),
                                             export, fhirtypes, fshtypes, fhirdefs (packages on org.hl7.fhir.core),
                                             ig, run, utils, xhtml, yaml (the yaml@1.10 composer over common's events)
  src/test/java/…, src/test/antlr4/          JUnit tests (~3.7k), SUSHI's grammar for the ANTLR test oracle
  scripts/         build.sh (module build), diff-run.sh / corpus-diff.sh (differential vs TypeScript SUSHI), oracles
  PORTING.md, README.md, docs/, tests/inventory.tsv
scripts/           as before, plus sushi-sync.sh and sushi-restructure.sh (importing/re-syncing the SUSHI port)
docs/ docker/ lima/ third_party/ tracker-app/   as before
```

## Naming decisions (placeholders until the owner decides)

| Decision | Choice | Notes |
|---|---|---|
| M1 shared package | `org.hl7.fhir.common.*` (YAML: `org.hl7.fhir.common.yaml`) | Matches the siblings `org.hl7.fhir.jekyll` and `org.hl7.fhir.sushi`. Avoids `org.hl7.fhir.igtools`, which is the IG Publisher's own namespace. |
| M2 Maven coordinates | `org.hl7.fhir:fhir-ig-tools-parent`, `fhir-ig-common`, `fhir-ig-jekyll`, `fhir-ig-sushi`; version `0.2.0-SNAPSHOT` | Nothing is published to a Maven repository yet. The release version still comes from the git tag (`scripts/stamp-wrappers.sh`), not from the POM. |
| M3 module directories | `common/`, `jekyll/`, `sushi/` (`shim/` was renamed to `jekyll/`; `sushi/` was the separate `sushi-java` repository) | GitHub does not redirect old `blob/main/shim/...` links, so links to `shim/INTEGRATION.md` and similar paths break once this is pushed. The wrapper, jar and release asset names (`jekyll-shim`, `shim.jar`) are unchanged. |

## Building

You need JDK 21 and Maven 3.6.3+ for the whole build (JDK 17 is enough for the shim alone). Without them,
`./build.sh` runs the same commands in the reference image, whose JDK is Temurin 21. `common` and `jekyll` are
compiled with `--release 17` whatever the JDK, so `shim.jar` keeps running on Java 17.

```sh
./build.sh                          # = mvn -B -q package: all modules, unit tests, jars
./build.sh -B -ntp package          # same, with normal output (what CI runs)
./build.sh -B -pl jekyll -am package   # one module and what it needs (the shim: works on JDK 17)
./build.sh -B -pl sushi -am package    # SUSHI (sushi/scripts/build.sh wraps this; SUSHI_BUILD_DIR moves sushi's target/)
mvn -B package                      # plain Maven works too
./build.sh --fast                   # shim only, plain javac, no Maven: common + jekyll -> jekyll/build/{classes,test-classes,shim.jar}
./build.sh --fast yaml              # one jekyll package (liquid|kramdown|site|yaml|rouge) + util + common -> jekyll/build-yaml/
```

There is no `--fast` build for `sushi`: it needs Maven for org.hl7.fhir.core and for the ANTLR test oracle.
On JDK 17 the sushi module fails fast (enforcer `requireJavaVersion` 21) with a hint to use `-pl jekyll -am`.

Outputs:
- Maven: `common/target/classes`, `jekyll/target/classes`, `jekyll/target/test-classes`, the thin module jar
  `jekyll/target/fhir-ig-jekyll-<version>.jar`, and **`jekyll/target/shim.jar`**. That last one is self-contained:
  common's YAML event layer is shaded in (its JS value model is left out, the shim doesn't use it), and
  `Main-Class` is `org.hl7.fhir.jekyll.site.Main`.
- Maven, sushi: `sushi/target/fhir-ig-sushi-<version>.jar` (module classes only) and **`sushi/target/sushi.jar`**
  (about 40 MB): `common`, org.hl7.fhir.utilities, commons-compress and the libraries core brings, shaded into one
  jar with `Main-Class` `org.hl7.fhir.sushi.App` and `Multi-Release: true` (some bundled libraries are multi-release
  jars). `java -jar sushi.jar <ig dir>` runs like `sushi <ig dir>`.
- `--fast` puts common's classes into each output dir's `classes/` as well, so `-cp <dir>/classes:<dir>/test-classes`
  is a complete classpath. The per-package builds let parallel workers compile their own package while someone
  else's half-finished code elsewhere doesn't compile. `BUILD_OUT=<dir>` picks another dir under `jekyll/`.
- The jars are reproducible: the POM sets `project.build.outputTimestamp`, so the same sources built with the
  same JDK give the same `shim.jar` (and `sushi.jar`) bytes.

`./build.sh` runs locally when `javac` and `jar` actually run (macOS's `/usr/bin/javac` stub without a JDK
doesn't count) and, for Maven builds, when `mvn` is on the PATH. Otherwise it runs in the reference image
(`ig-publisher-localdev-ca`; `DOCKER_CONTEXT` / `DOCKER_HOST` select the daemon) with:
- the checkout mounted at its own path, so `target/` dirs appear in the checkout as with a local build;
- Maven 3.9.9 and its local repository on the Docker host's disk (`$VMDATA/fhir-ig/{tools,m2}`, D11), not in the
  checkout. Maven is fetched from Maven Central on first use and checked against a pinned SHA-512.

`BUILD_DOCKER=1` forces the container. `SHIM_BUILD_DOCKER=1`, the old variable, still works.

## Testing

| What | Command | Notes |
|---|---|---|
| Unit tests (JUnit) | part of `./build.sh` (`mvn package`) | `common` (event layer API, JS value model) and `sushi` (~3.7k, mostly 1:1 ports of SUSHI's Jest tests; tests that need SUSHI's TypeScript checkout or recorded reference runs skip when absent, `-Dsushi.ts.dir=` points at the checkout) |
| SUSHI differential, one IG | `sushi/scripts/diff-run.sh <IG> [label]` | TypeScript SUSHI 3.21.1 vs `sushi/target/sushi.jar` on the same source copy and frozen package snapshot: classified file diff, log diff, exit codes. Needs the corpus workspace (`ROOT`, default the repository root, with `work/sushi-spike`) and the reference runs on the VM |
| SUSHI differential, corpus | `sushi/scripts/corpus-diff.sh [-j N] [list]` | `diff-run.sh` over the 267 FSH IGs |
| Differential suites vs recorded Ruby output | `scripts/test-suites.sh` | builds with Maven first unless `SHIM_NO_BUILD=1`. Runs against `jekyll/target/shim.jar` + `test-classes`, i.e. the release jar itself. `SHIM_BUILD=jekyll/build[-<module>]` tests a `--fast` build instead. `SHIM_W_SUITES=1` (repo at `/w`) adds the YAML file suites. |
| In-process API | `java -cp jekyll/target/shim.jar:jekyll/target/test-classes org.hl7.fhir.jekyll.test.site.ShimApiCheck` | |
| Wrapper end to end | `scripts/e2e.sh` | `jekyll/jekyll-shim` picks up `jekyll/target/shim.jar` (then `jekyll/build/shim.jar`) in a source checkout |
| Wrapper bootstrap | `scripts/test-wrapper.sh [jar]` | defaults to `jekyll/target/shim.jar` |
| Golden corpus | `scripts/shim-golden.sh`, `scripts/shim-all.sh` | use a `--fast` build dir (default `build-site`) |
| Output regression between two jars | `scripts/jar-compare.sh <golden> <ref.jar> <new.jar>...` | byte-for-byte, every output file; for refactors that must not change output |

`jekyll/run.sh` and `jekyll/oracle/oracle.sh` work as before. `BUILD=<dir>` selects a `--fast` dir; leave
`BUILD` unset to use the Maven build.

## Module rules

The Maven enforcer plugin checks the dependency rules on every build (`bannedDependencies`).

| Module | Java | May depend on | Must not |
|---|---|---|---|
| `common` | 17 (`--release 17`) | nothing (JDK only); test-scope libraries such as JUnit are fine | depend on any other module or library, or carry one tool's semantics by default. Put a port's shared lower layer here; behavior only one tool needs is opt-in (like the event layer's yaml110 mode), never the default. |
| `jekyll` | 17 | `common` only (plus test scope) | depend on `sushi` or any library: `shim.jar` must stay a self-contained, dependency-free jar that runs on Java 17 |
| `sushi` | 21 | `common`, the FHIR core libraries the IG Publisher ships (`ca.uhn.hapi.fhir:org.hl7.fhir.*`; today `org.hl7.fhir.utilities` for the package cache), commons-compress at runtime (core's package cache needs it), test-scope libraries (JUnit, the ANTLR oracle). Enforcer `bannedDependencies` checks the direct dependencies; `build.DependencyRuleTest` checks that main code imports only the JDK, `org.hl7.fhir.{sushi,common,utilities}` (and r4/r5 model classes in `importer/config`), not the libraries core brings along | be depended on by `common` or `jekyll` |

More rules:
- Each package lives in exactly one module (`org.hl7.fhir.common.*` in `common`, `org.hl7.fhir.jekyll.*` in `jekyll`,
  `org.hl7.fhir.sushi.*` in `sushi`). No split packages, so a JPMS module split stays possible later.
- `common` keeps a Java 17 floor because the shim's does. Code moved into `common` must compile with `--release 17`:
  no pattern-matching `switch`, no `SequencedCollection` methods (`getFirst()`, `reversed()`, ...), and so on.
- `common`'s public types are an API for both tools. Change them in the same commit as all their callers, and
  run both modules' suites.

### The YAML event layer in `common`

`org.hl7.fhir.common.yaml` is the libyaml 0.2.5 port (reader, scanner, parser), moved unchanged from
`org.hl7.fhir.jekyll.yaml`. The public surface is what an event consumer needs:

```java
Parser p = new Parser(bytes, Parser.UTF8_ENCODING);   // or ANY_ENCODING (BOM detection), UTF16LE/BE_ENCODING
for (Event e; (e = p.parse()) != null; ) {
  switch (e.type) { case SCALAR -> ... e.value, e.anchor, e.tag, e.style, e.plainImplicit, e.quotedImplicit, e.startMark ... }
}
// errors: LibyamlError (RuntimeException) with error (READER/SCANNER/PARSER_ERROR), problem, problemOffset,
//         problemValue, problemMark, context, contextMark: libyaml's own error state, no tool-specific wording
```

`Event` mirrors `yaml_event_t`. Its fields are public and should be treated as read-only. Strings are UTF-8
`byte[]`, and the `*_STYLE` constants are on `Event`. `Reader`, `Scanner`, `Token` and `ByteString` stay
package-private. The Psych/SafeYAML construction (`PsychParser`, `PsychHandler`, `Transform`, `Yaml`, the `Ruby*`
value types, `PsychSyntaxError` wording) stays in `jekyll`.

`new Parser(bytes, encoding, true)` selects the **yaml110 mode**, which SUSHI's yaml@1.10 composer uses: the few
relaxations where eemeli/yaml 1.10 (YAML 1.2) accepts input libyaml 0.2.5 rejects (any character allowed and only
CR/LF break lines; tabs on otherwise blank lines and at the end of plain-scalar lines; `%` starting a plain scalar
after column 0; a block-mapping value without a key). It is off unless asked for; the two-argument constructor,
which the shim uses, is exactly libyaml. The composer itself (`org.hl7.fhir.sushi.yaml.Yaml`, `YamlError`: YAML 1.2
core-schema typing, anchors, duplicate keys, yaml@1.10's error wording) stays in `sushi` and uses only this public
API.

### The JS value model in `common`

`org.hl7.fhir.common.js` is SUSHI's model of JavaScript values (`sushi/PORTING.md` D3/D4): objects are
insertion-ordered `JsObject` maps, numbers are `Double`, `Json.stringify`/`parse`/`outputJson` behave like
`JSON.stringify`/`JSON.parse` (ECMAScript number formatting, escaping, key order), `Js`/`Lodash` hold the lodash
functions SUSHI uses, and `Frozen` gives deeply frozen views. It is zero-dependency and Java 17, so other modules
can use it; the shim doesn't today.

## The SUSHI module

The SUSHI port was developed in its own repository (`sushi-java`; Maven, Java 21, package `org.hl7.fhir.sushi`)
and moved in with its history. Two scripts do the mechanical part, so the move can be repeated when the port's
repository gets more commits:

- `scripts/sushi-sync.sh <sushi-port-repo> [branch]` clones the branch, rewrites it with git filter-repo (every
  path under `sushi/`; author and committer identities set to this repository's `user.name`/`user.email`;
  workspace paths in file contents and commit messages replaced), and merges it (the first time with
  `--allow-unrelated-histories`). filter-repo is deterministic, so a re-sync rewrites the commits imported before to
  the same ids and the merge brings only the new ones. Then it runs:
- `scripts/sushi-restructure.sh` (idempotent): moves `org.hl7.fhir.sushi.js` to `common` as `org.hl7.fhir.common.js`
  (with `JsonTest`, `FrozenTest` and their vectors; `LodashTest`, which also checks `utils.ValidUrl`, becomes
  `sushi`'s `utils.LodashOracleTest`), rewrites every reference to the old package in code (not in Markdown, where the docs name it on purpose), deletes sushi's vendored copy of
  the libyaml event layer, and points the yaml@1.10 composer at `common`'s event layer in yaml110 mode. It stops
  with an error if something new needs a hand decision (a new test in the js package, the composer using the event
  layer's internals).

Changes made by hand, as ordinary commits after the import: `sushi/pom.xml` (parent, `fhir-ig-sushi`, enforcer
rules, the shaded `sushi.jar` instead of the old `lib/` class path), `DependencyRuleTest` (allows `common`, expects
`fhir-ig-common` among the runtime dependencies), `sushi/scripts` (build through `../build.sh`; `diff-run.sh` runs
and mounts `sushi/target/sushi.jar`; `ROOT` defaults to the repository root instead of a fixed path), and the docs.
On a re-sync, a conflict in one of those files means the port's repository changed it too: keep this repository's
version and re-apply the upstream change by hand.

## What changed for releases (SUSHI move)

- **Build with JDK 21.** CI and the release workflow now use JDK 21 for the whole build; `common` and `jekyll` are
  still compiled with `--release 17` (`shim.jar`'s class files are version 61, checked in CI), and a CI job runs that
  same `shim.jar` on JDK 17 and 21 on Linux, macOS and Windows.
- **shim.jar:** same assets, names, `Main-Class` and behavior. Its only content change is the event layer's
  opt-in yaml110 code in `org/hl7/fhir/common/yaml/` (unused by the shim). common's JS value model is filtered
  out of it.
- **sushi.jar is built and tested but not published.** The release workflow has a disabled step that would add it
  as an asset; shipping it is the owner's decision (it bundles core's libraries, whose notices would have to be
  included, see `THIRD_PARTY_NOTICES.md`).

## What changed for releases (multi-module move)

- **Same artifact.** `shim.jar` is still one self-contained jar: the same `Main-Class`
  (`org.hl7.fhir.jekyll.site.Main`), the same CLI (`java -jar shim.jar build --destination <dir>`), and no
  dependencies. Its only difference in content is that the libyaml classes now sit under
  `org/hl7/fhir/common/yaml/` (shaded in from `common`) instead of `org/hl7/fhir/jekyll/yaml/`, plus one
  `package-info`. The public API (`org.hl7.fhir.jekyll.Shim`, `IncludeProvider`, `ShimException`) is unchanged.
- **Built by Maven.** CI and the release workflow run `./build.sh -B -ntp package` (with `setup-java`'s Maven
  cache). The jar is at `jekyll/target/shim.jar` instead of `shim/build/shim.jar`, and `jekyll/INTEGRATION.md`
  is the file copied into the zip. The asset names (`jekyll-shim`, `jekyll-shim.cmd`, `shim.jar`,
  `fhir-ig-jekyll.zip`, `SHA256SUMS`), the stamping, and the version-from-tag rule are unchanged.
- **Wrappers.** The only change is the source-checkout lookup (`target/shim.jar`, then `build/shim.jar`).
  Stamped release copies behave as before.
- **Embedding.** The in-process route (the publisher calling `Shim.build`) can keep using `shim.jar` as one jar.
  A Maven consumer would instead use `fhir-ig-jekyll` + `fhir-ig-common`, which are not published yet.

## Verification of the multi-module move (2026-11-22)

All checks were run from the `multimodule` branch, in the reference image.
- Unit tests: `common` JUnit 5/5.
- `scripts/test-suites.sh` with `SHIM_W_SUITES=1`, run against the Maven `shim.jar` and again against a
  `--fast` build: every suite passed, with the same `known` counts as before.
- Extra YAML and markdown suites: real `_config`/`_data` 100/100, front matter 310/310, `_data` files 583/583
  (4 cases name a golden no longer on disk), markdown corpus 2207/2207.
- `LiquidWarnCheck`, the kramdown/Rouge concurrency checks, `PreMatchEquivalence`, `JavaSignatureEquivalence`
  and `ShimApiCheck` (13/13) all pass on both builds.
- `scripts/e2e.sh` and `scripts/test-wrapper.sh` pass with the Maven jar.
- Release `Package` dry run, without `zip` (the image has none): passed.
- `actionlint`: clean on both workflows.
- Golden sample: `scripts/jar-compare.sh` ran the v0.1.0 release `shim.jar`, the pre-move `main` jar and the
  new Maven `shim.jar` on 25 IGs. All 205,545 output files were byte-identical across the three jars. The IGs:
  HL7__US-Core, IHE__ITI.MHD, FHIR__fhir-tools-ig, HL7__genomics-reporting, kitchen-sink, kitchen-sink-r4,
  HL7__fhir-extensions, HL7__UTG, hl7ch__ch-core, hl7au__au-fhir-core, hl7-eu__xshare-ips-plus,
  HL7__fhir-us-quality-core, hl7korea__krcore, HL7Chile__IPS-CL, HL7__FHIRPath, HL7__sql-on-fhir,
  HL7__bulk-data, HL7__cds-hooks, WorldHealthOrganization__smart-base, HL7__davinci-dtr, IHE__PCC.ODH,
  Nictiz__NL-PS-IG, HL7__fhir-udap-security-ig, HL7__case-reporting, hl7-eu__extensions.
- A 26th IG, `fut-infrastructure__implementation-guide`, cannot match v0.1.0, by design: v0.1.0 fails on
  `{% include /menu.xml %}`, which commit fc062482 fixed after the release. On that IG the new jar is
  byte-identical to `main` (17,800 files).
- The CLI behaves the same on bad or missing arguments, and a clean rebuild reproduces `shim.jar`
  byte for byte.

## Verification of the SUSHI move (2026-11-22)

Branch `monorepo-sushi` (from `multimodule` 29c24084), with the SUSHI port imported from its repository's final
`main` (258aa8c) by `scripts/sushi-sync.sh`. All checks in the reference image (Temurin 21).
- **Build:** `./build.sh -B -ntp clean package` green. JUnit: `common` 13/13 (event layer 6, incl. the yaml110
  opt-in; JS value model 7), `sushi` 3,706 run, 0 failures, 30 skipped (also 30 in the port's own repository, checked at 1de1e56).
  Together that is 3,713, the same count as the port's own repository at 258aa8c. Enforcer rules pass; the
  dependency rule test passes.
- **SUSHI differential:** `sushi/scripts/diff-run.sh` with the new `sushi/target/sushi.jar` on the 6 reference IGs
  (HL7__genomics-reporting, IHE__ITI.MHD, hl7ch__ch-term, EEHRxF__logical-model, ahdis__ch-ekm,
  WorldHealthOrganization__smart-anc): all identical to TypeScript SUSHI 3.21.1, 2,153 files byte for byte, logs
  identical, exit codes 0/0, the same result as the port's own jar.
- **Shim suites:** `scripts/test-suites.sh` with `SHIM_W_SUITES=1` against the Maven `shim.jar` and against a
  `--fast` build: every suite passes with the same pass and `known` counts as on `multimodule`. Extra checks: real
  `_config`/`_data` 100/100, front matter 310/310, `_data` files 583/587 (the same 4 cases name a golden no longer on
  disk), markdown corpus 2207/2207, `LiquidWarnCheck`, the kramdown/Rouge concurrency checks, `PreMatchEquivalence`,
  `JavaSignatureEquivalence`, `ShimApiCheck` 13/13.
- **Golden sample:** `scripts/jar-compare.sh` with the `multimodule` `shim.jar` (rebuilt from 29c24084, byte-identical
  to the one verified above) against the new one on IHE__ITI.MHD, FHIR__fhir-tools-ig, HL7__genomics-reporting,
  kitchen-sink and hl7ch__ch-core: all 21,174 output files byte-identical. (Run before the final re-sync, which
  changed only sushi's tests; the rebuilt `shim.jar` is byte-identical to the one compared.)
- `scripts/e2e.sh`, `scripts/test-wrapper.sh` and a dry run of the release `Package` step (without `zip`) pass.
- `actionlint` (1.7.7, with shellcheck): clean on both workflows.
- Reproducible: a clean rebuild gives the same `shim.jar` and `sushi.jar` bytes.
- History: the 129 imported commits carry this repository's author identity and no workspace paths; a second
  `sushi-sync.sh` run on the updated `main` reused the earlier rewritten commits and merged only the new ones.
