# Multi-module layout

This repository holds Java replacements for the non-Java stages of the FHIR IG Publisher. Today that is
the Jekyll replacement ("the shim"). A Java port of SUSHI is being built and will move in as a third
module. They share one Maven build, so shared code (YAML, the JS value model) lives in one place.

## Layout

```
pom.xml            aggregator and parent: module list, Java level, plugin versions, reproducible-jar timestamp
build.sh           runs the build: Maven (local or in the reference container), or plain javac (--fast)
common/            zero-dependency shared code
  pom.xml          artifact org.hl7.fhir:fhir-ig-common
  src/main/java/org/hl7/fhir/common/yaml/    libyaml 0.2.5 event layer (Reader, Scanner, Token, Parser, Event,
                                             Mark, LibyamlError, ByteString)
  src/test/java/…                            JUnit tests of the public API
jekyll/            the shim (was shim/ before the move)
  pom.xml          artifact org.hl7.fhir:fhir-ig-jekyll; also writes target/shim.jar, the release jar
  src/main/java/org/hl7/fhir/jekyll/…        Shim, IncludeProvider, ShimException, liquid, kramdown, rouge,
                                             yaml (Psych/SafeYAML construction), site, util
  src/test/java/…  main()-style case runners and checks (DiffRunner, ShimApiCheck, …)
  tests/ oracle/   differential suites and the Ruby oracle
  jekyll-shim, jekyll-shim.cmd               the self-downloading wrappers (stamped at release)
  README.md, INTEGRATION.md, NOTES-*.md
sushi/             (planned) the SUSHI port, see "Adding the SUSHI module" below
scripts/ docs/ docker/ lima/ third_party/ tracker-app/   as before
```

## Naming decisions (placeholders until the owner decides)

| Decision | Choice | Notes |
|---|---|---|
| M1 shared package | `org.hl7.fhir.common.*` (YAML: `org.hl7.fhir.common.yaml`) | Matches the siblings `org.hl7.fhir.jekyll` and `org.hl7.fhir.sushi`. Avoids `org.hl7.fhir.igtools`, which is the IG Publisher's own namespace. |
| M2 Maven coordinates | `org.hl7.fhir:fhir-ig-tools-parent`, `fhir-ig-common`, `fhir-ig-jekyll`, later `fhir-ig-sushi`; version `0.2.0-SNAPSHOT` | Nothing is published to a Maven repository yet. The release version still comes from the git tag (`scripts/stamp-wrappers.sh`), not from the POM. |
| M3 module directories | `common/`, `jekyll/`, `sushi/` (`shim/` was renamed to `jekyll/`) | GitHub does not redirect old `blob/main/shim/...` links, so links to `shim/INTEGRATION.md` and similar paths break once this is pushed. The wrapper, jar and release asset names (`jekyll-shim`, `shim.jar`) are unchanged. |

## Building

You need JDK 17+ and Maven 3.6.3+. Without them, `./build.sh` runs the same commands in the reference image.

```sh
./build.sh                          # = mvn -B -q package: all modules, unit tests, jars
./build.sh -B -ntp package          # same, with normal output (what CI runs)
./build.sh -B -pl jekyll -am package   # one module and what it needs
mvn -B package                      # plain Maven works too
./build.sh --fast                   # plain javac, no Maven: common + jekyll -> jekyll/build/{classes,test-classes,shim.jar}
./build.sh --fast yaml              # one jekyll package (liquid|kramdown|site|yaml|rouge) + util + common -> jekyll/build-yaml/
```

Outputs:
- Maven: `common/target/classes`, `jekyll/target/classes`, `jekyll/target/test-classes`, the thin module jar
  `jekyll/target/fhir-ig-jekyll-<version>.jar`, and **`jekyll/target/shim.jar`**. That last one is self-contained:
  common is shaded in, and `Main-Class` is `org.hl7.fhir.jekyll.site.Main`.
- `--fast` puts common's classes into each output dir's `classes/` as well, so `-cp <dir>/classes:<dir>/test-classes`
  is a complete classpath. The per-package builds let parallel workers compile their own package while someone
  else's half-finished code elsewhere doesn't compile. `BUILD_OUT=<dir>` picks another dir under `jekyll/`.
- The jars are reproducible: the POM sets `project.build.outputTimestamp`, so the same sources built with the
  same JDK give the same `shim.jar` bytes.

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
| Unit tests (JUnit) | part of `./build.sh` (`mvn package`) | today only `common` has JUnit tests |
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
| `common` | 17 (`--release 17`) | nothing (JDK only); test-scope libraries such as JUnit are fine | depend on any other module or library, or carry tool-specific semantics. Put a port's shared lower layer here, not its Ruby/JS-specific behavior. |
| `jekyll` | 17 | `common` only (plus test scope) | depend on `sushi` or any library: `shim.jar` must stay a self-contained, dependency-free jar that runs on Java 17 |
| `sushi` (planned) | 21 | `common`, the FHIR core libraries the IG Publisher ships (`ca.uhn.hapi.fhir:org.hl7.fhir.*`, e.g. `org.hl7.fhir.utilities` for the package cache), test-scope libraries (JUnit, the ANTLR oracle) | be depended on by `common` or `jekyll` |

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

## Adding the SUSHI module

The SUSHI port is developed in `sushi-java/` for now (Maven, Java 21, package `org.hl7.fhir.sushi`; see its
`PORTING.md` §7). To move it in:

1. **Sources.** Copy `sushi-java/src` to `sushi/src` (`main/java`, `test/java`, `test/antlr4`, test resources),
   and `PORTING.md`, `README.md` and `tests/` into `sushi/`. Put `sushi-java/scripts/*` under `sushi/scripts/`
   and fix their `HERE`/root paths. Keep the history if wanted, e.g. with `git subtree add` or
   `git filter-repo --to-subdirectory-filter sushi` followed by a merge.
2. **POM.** Turn `sushi-java/pom.xml` into `sushi/pom.xml` with `<parent>` = `org.hl7.fhir:fhir-ig-tools-parent`,
   `artifactId` `fhir-ig-sushi`, `maven.compiler.release` 21, a dependency on `fhir-ig-common`, and its ANTLR
   test-oracle and build-helper plugins. Move those plugin versions and the `antlr.version` property into the
   root `pluginManagement`/`properties`. JUnit's version is already managed there. Add an enforcer
   `bannedDependencies` rule that allows only `fhir-ig-common`, `ca.uhn.hapi.fhir:org.hl7.fhir.*` and test scope.
   Add `<module>sushi</module>` to the root `pom.xml`, where a commented placeholder already sits. If SUSHI's
   heavy test outputs must stay off the shared folder (D11), keep its `sushi.buildDir` property and have the
   container path of `./build.sh` pass `-Dsushi.buildDir=$VMDATA/...`.
3. **JS value model to `common`.** Move `org.hl7.fhir.sushi.js` (`Js`, `JsNumber`, `JsObject`, `Json`,
   `JsonSerializable`, `Lodash`, about 1k lines) to `common/src/main/java/org/hl7/fhir/common/js`. Rename the
   package to `org.hl7.fhir.common.js` and fix the imports in `sushi`. Move its tests to `common/src/test`. It
   compiles with `--release 17` today (checked 2026-11-22); keep it that way. `FshLogger` stays in `sushi`.
4. **YAML on the shared event layer.** SUSHI's config reader needs `yaml@1.10` semantics (YAML 1.2 core schema,
   duplicate keys are errors, its error wording). Write that composer in `sushi` on top of
   `org.hl7.fhir.common.yaml.Parser`/`Event`/`LibyamlError`; don't copy the event layer. If the composer needs
   more from the event layer (another public accessor, say), change `common` and run the jekyll suites in the
   same commit. libyaml and eemeli/yaml accept and reject slightly different malformed input (PORTING.md §7);
   that difference goes in the composer, not in `common`.
5. **CI and release.**
   - The CI matrix has a JDK 17 leg, which cannot compile `sushi` (Java 21). Either build only the shim
     there (`./build.sh -B -ntp -pl jekyll -am package`) and add a JDK 21 job that builds all modules, or
     build with 21 everywhere and keep a 17 job that only *runs* `shim.jar` (suites, e2e, wrapper). The
     shim's class files are `--release 17` either way.
   - The release workflow builds with JDK 17. Switch it to `-pl jekyll -am`, or to JDK 21, which still
     emits Java 17 class files for the shim. A SUSHI release artifact, if any, is a separate decision. The
     shim's assets don't change.
   - Add SUSHI's own test entry points (the JUnit suite, its differential `scripts/diff-run.sh`) as CI steps.
6. **Docs.** Add SUSHI to this file's layout and module table, to the top-level README's layout table, and to
   `THIRD_PARTY_NOTICES.md` (SUSHI is Apache-2.0).

## What changed for releases

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

## Verification of the move (2026-11-22)

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
