# sushi: SUSHI in Java (`sushi.jar`)

A Java port of [SUSHI](https://github.com/FHIR/sushi) 3.21.1, the FHIR Shorthand (FSH) compiler: for the same
inputs and FHIR packages it writes byte-identical `fsh-generated/`, prints the same console messages and exits
with the same status as TypeScript SUSHI 3.21.1, with no Node.js or npm. This is the `sushi` module of
[fhir-ig-tools](../README.md) (Java 21, package `org.hl7.fhir.sushi`); it builds `sushi/target/sushi.jar`.

![sushi.jar: FSH is parsed by a hand-written lexer and parser (SUSHI's ANTLR grammar is a test-only oracle), imported into the FSHTank, exported to StructureDefinitions, instances, value sets, code systems and the ImplementationGuide, and written to fsh-generated; configuration through a yaml@1.10 composer on common's YAML events; packages through org.hl7.fhir.core's package cache; identical to TypeScript SUSHI on 6/6 reference and 267/267 corpus IGs](../docs/img/sushi.svg)

## Quick start

The `sushi` wrapper (like `jekyll-shim`, one small file) stands in for the `sushi` command: put it on the `PATH`,
and the IG Publisher, or you, run it exactly like TypeScript SUSHI. A release copy pins its version and the SHA-256
of that release's `sushi.jar`; on first use it downloads the jar into `~/.fhir-ig-sushi/<version>/`, verifies it,
and runs offline from then on. It passes every argument through and keeps SUSHI's output and exit status.

```sh
# Linux / macOS (Java 21+)
curl -fLO https://github.com/jmandel/fhir-ig-tools/releases/latest/download/sushi && chmod +x sushi
mv sushi ~/bin/                       # any folder on the PATH, ahead of an npm-installed sushi
sushi path/to/ig                      # = SUSHI 3.21.1; or let the publisher run it:
java -jar publisher.jar -ig path/to/ig
```

On Windows, put [`sushi.cmd`](https://github.com/jmandel/fhir-ig-tools/releases/latest/download/sushi.cmd) in a
folder on the `PATH` (the publisher runs `cmd /C sushi ...`).

- **Caveat: `fsh.ini`.** When an IG's `fsh.ini` pins `sushi-version`, the publisher runs
  `npx fsh-sushi@<version>` instead of `sushi`, so the wrapper is not used for that IG.
- **Caveat: npm path.** If the publisher is configured with an npm path (`FhirSettings`), it puts that folder
  first on the `PATH` it gives SUSHI, so an npm-installed `sushi` there wins over the wrapper.
- **Caveat: `--require-latest`.** In the publisher's CI build mode it adds `--require-latest`; like TypeScript
  SUSHI 3.21.1, the port then fails once the npm registry lists a newer `fsh-sushi` than 3.21.1
  ([`docs/differences.md`](docs/differences.md) KD-10).
- Offline machines: put `sushi.jar` next to the wrapper, or set `FHIR_IG_SUSHI_JAR=/path/to/sushi.jar`.
  `FHIR_IG_SUSHI_URL` overrides the download URL, `FHIR_IG_SUSHI_HOME` moves the cache, `SUSHI_JAVA_OPTS` adds JVM
  options. Without the wrapper: `java -jar sushi.jar path/to/ig`.
- Files and network go through org.hl7.fhir.core's `ManagedFileAccess` and `ManagedWebAccess`, as in the IG
  Publisher ([`PORTING.md`](PORTING.md) "I/O policy"). So `~/.fhir/fhir-settings.json` applies (another file:
  `SUSHI_JAVA_OPTS=-Dfhir.settings.path=...`): package servers and their credentials, and
  `"prohibitNetworkAccess": true` to build offline from the package cache. Offline, `#latest` dependencies that
  need the registry then fail at once instead of after network timeouts. A proxy is set with the standard JVM
  properties (`-Dhttps.proxyHost=... -Dhttps.proxyPort=...`; `-Dhttp.proxyUser/-Dhttp.proxyPassword` for an
  authenticating proxy) in `SUSHI_JAVA_OPTS`.

From source (in the repository root): `sushi/scripts/build.sh -B -q -DskipTests package` builds
`sushi/target/sushi.jar`; `sushi/sushi` in a source checkout runs that jar.

## Why

SUSHI is the FSH compiler most IGs use. It needs Node.js and npm packages on every machine that builds an IG,
next to the publisher's JVM, and a change to its behaviour means TypeScript work. The port gives SUSHI a
permanent Java home: the same compiler as plain Java code that FHIR Java maintainers can read, debug and own,
with no Node/npm at all.

## Approach

- **A port of SUSHI 3.21.1, not a reimplementation of FSH.** One Java class per TypeScript class, with the same
  method names, so SUSHI's tests carry over 1:1 and the two code bases can be read side by side. Inside methods it
  is ordinary Java.
- **Output parity is the requirement**: for the same inputs and FHIR packages, byte-identical `fsh-generated/`,
  the same console messages (text, order, counts) and the same exit status as TypeScript SUSHI on Node 22. That
  covers JavaScript details that show in the output: JSON key order, number formatting, sort stability, string
  semantics ([`PORTING.md`](PORTING.md) D2–D4).
- **Hand-written FSH parser.** The lexer and recursive-descent parser follow SUSHI's ANTLR grammar. The ANTLR
  parser generated from SUSHI's unmodified grammar is used only in tests, as the oracle: parse trees, error
  messages and error recovery are compared on the corpus FSH, SUSHI's fixtures and fuzzed input (D6).
- **Packages through org.hl7.fhir.core.** FHIR package loading uses core's package cache and registry client
  (`org.hl7.fhir.utilities`, the version the publisher ships), unchanged. SUSHI's own resolution rules
  (fhir-package-loader) were ported only where measurement showed core choosing differently
  ([`package-resolution-differences.md`](docs/package-resolution-differences.md)).
- **Dependencies:** at runtime only `common` (this repository), core's utilities and commons-compress (which core's
  package cache needs). No other libraries; npm libraries SUSHI uses (yaml, lodash, html-minifier, sax, ini, ...)
  are replaced by small in-tree ports, each checked against the JavaScript original.

## Results

| Check | Result |
|---|---|
| 6 reference IGs (genomics-reporting, IHE ITI.MHD, ch-term, EEHRxF logical model, ch-ekm, WHO smart-anc) | **6 / 6 identical**: `fsh-generated/` byte for byte (2,153 files), console log and exit status, using `scripts/diff-run.sh` |
| Corpus: every IG in the project's corpus of 317 IG repositories that uses FSH (267) | **267 / 267 identical** (files, log, exit status), `scripts/corpus-diff.sh` |
| Unit tests | **3,714 JUnit tests**, 0 failures. Of SUSHI's 3,604 Jest tests, **3,584 are ported 1:1 and pass** (matched by their Jest names, `tests/inventory.tsv`); the other 20 are skipped or pending in SUSHI itself (15), spy on a private method (2) or test an HTTP wrapper the port doesn't have (3). Plus oracle tests against the JavaScript libraries |
| Package resolution | 5,497 / 5,497 lookups on the reference IGs pick the same definition as SUSHI |
| `sushi-config.yaml` | all 266 corpus configs import identically; on 1,035 malformed or unusual configs, the same accept/reject decisions everywhere, 44 YAML error messages worded differently |

Every known difference from TypeScript SUSHI (messages on malformed input, debug stack traces, the off-by-default
"newer SUSHI available" check, and so on) is listed with its reason in
[`docs/differences.md`](docs/differences.md). None changes `fsh-generated/` on real input.
The comparisons use a frozen FHIR package snapshot, so both implementations see the same packages.

## More

- [`PORTING.md`](PORTING.md): design decisions (dependencies, the JS value model, the hand-written parser with
  ANTLR as test oracle, package resolution through org.hl7.fhir.core), conventions, the TS → Java module map, the
  tests and the differential harness, and the final status (§9).
- [`docs/differences.md`](docs/differences.md): every known behaviour difference from TypeScript SUSHI, with
  evidence and decision. [`docs/package-resolution-differences.md`](docs/package-resolution-differences.md): where
  core's package resolution differed from fhir-package-loader and what was ported.
- `tests/inventory.tsv`: SUSHI's Jest tests and which JUnit test ports each.
- `tests/e2e/ig`: the small FSH project `scripts/sushi-e2e.sh` runs through the wrapper.
- `scripts/`: `build.sh` (the module build), `diff-run.sh` / `corpus-diff.sh` (differential runs against
  TypeScript SUSHI), `classify-diff.py`; `oracle/`, `xhtml/`, `fhirtool/`, `probe/`, `wp1/`, `ts2junit/` are the
  oracle and test-porting tools used during the port (they expect the corpus workspace and the reference image).

The module's place in the repository (shared code in `common`, module rules, building) is in
[`../docs/MONOREPO.md`](../docs/MONOREPO.md).
