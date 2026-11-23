# sushi: SUSHI in Java (`sushi.jar`)

A Java port of [SUSHI](https://github.com/FHIR/sushi) 3.21.1, the FHIR Shorthand (FSH) compiler: for the same
inputs and FHIR packages it writes byte-identical `fsh-generated/`, prints the same console messages and exits
with the same status as TypeScript SUSHI 3.21.1, with no Node.js or npm, except where it deliberately follows
org.hl7.fhir.core instead (package-loading messages, and predefined resources in FHIR XML:
[`docs/core-divergences.md`](docs/core-divergences.md)). This is the `sushi` module of
[fhir-ig-tools](../README.md) (Java 17, package `org.hl7.fhir.sushi`); it builds `sushi/target/sushi.jar`.

![sushi.jar: FSH is parsed by a hand-written lexer and parser (SUSHI's ANTLR grammar is a test-only oracle), imported into the FSHTank, exported to StructureDefinitions, instances, value sets, code systems and the ImplementationGuide, and written to fsh-generated; configuration through a yaml@1.10 composer on common's YAML events; packages through org.hl7.fhir.core's package cache; fsh-generated identical to TypeScript SUSHI on 4/6 reference and 259/267 corpus IGs, the rest differing only where predefined FHIR XML is read with core](../docs/img/sushi.svg)

## Quick start

The `sushi` wrapper (like `jekyll-shim`, one small file) stands in for the `sushi` command: put it on the `PATH`,
and the IG Publisher, or you, run it exactly like TypeScript SUSHI. A release copy pins its version and the SHA-256
of that release's `sushi.jar`; on first use it downloads the jar into `~/.fhir-ig-sushi/<version>/`, verifies it,
and runs offline from then on. It passes every argument through and keeps SUSHI's output and exit status.

```sh
# Linux / macOS (Java 17+)
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
- **`--require-latest`** (the publisher adds it in its CI build mode). TypeScript SUSHI fails when npm lists a newer
  `fsh-sushi`; the port instead checks that its `sushi.jar` is from the **latest fhir-ig-tools release**
  (`https://api.github.com/repos/jmandel/fhir-ig-tools/releases/latest`, through `ManagedWebAccess`), so a new TS
  SUSHI on npm doesn't break builds, and a new release of this port does, as a new `fsh-sushi` does for TS. Like TS,
  it fails when the latest release can't be determined (no network) and for a development build (`-SNAPSHOT`). A
  build records its release from the tag (`-Dsushi.release=`; [`docs/core-divergences.md`](docs/core-divergences.md)
  T3).
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
  (fhir-package-loader) are kept only where core would change an IG's output: one rule, `latest` =
  packages.fhir.org's `dist-tags.latest` (HL7 cds-hooks-library)
  ([`core-divergences.md`](docs/core-divergences.md) C5). Predefined resources in FHIR XML are read with core's XML
  parsers (C6).
- **Dependencies:** at runtime only `common` (this repository), core's utilities and commons-compress (which core's
  package cache needs). No other libraries; npm libraries SUSHI uses (yaml, lodash, html-minifier, sax, semver, ...)
  are replaced by small in-tree ports, each checked against the JavaScript original.

## Results

| Check | Result |
|---|---|
| 6 reference IGs (genomics-reporting, IHE ITI.MHD, ch-term, EEHRxF logical model, ch-ekm, WHO smart-anc) | **4 / 6 identical** `fsh-generated/` byte for byte, console log and exit status, using `scripts/diff-run.sh`; ch-term and smart-anc read predefined XML: 3 and 1 ImplementationGuide `description` texts differ (XML attribute whitespace), smart-anc also logs 4 XML warnings; exit status equal |
| Corpus: every IG in the project's corpus of 317 IG repositories that uses FSH (267) | **259 / 267 identical** `fsh-generated/`, exit status equal in 267 / 267; the 8 others read predefined FHIR XML (converted with core, not fhir-tool) and differ only in `ImplementationGuide.definition.resource` entries and link references. Console log identical in 250 / 267: 6 differ in package-loading lines (core's package handling), 11 in XML warnings or resource counts. `scripts/corpus-diff.sh`; per IG in `docs/core-divergences.md` C6 |
| Unit tests | **3,735 JUnit tests** in this module, 0 failures (3,693 run in CI, 42 skipped). Of SUSHI's 3,604 Jest tests, **3,581 are ported 1:1 and pass** (matched by their Jest names, `tests/inventory.tsv`); the other 23 are skipped or pending in SUSHI itself (15), spy on a private method (2), test an HTTP wrapper the port doesn't have (3), or read invalid FHIR XML that core's parser rejects (3, C6). Plus oracle tests against the JavaScript libraries |
| Package resolution | 5,497 / 5,497 lookups on the reference IGs pick the same definition as SUSHI |
| `sushi-config.yaml` | all 266 corpus configs import identically; on 1,035 malformed or unusual configs, the same accept/reject decisions everywhere, 44 YAML error messages worded differently |

Every place where the port does not use org.hl7.fhir.core although core could do it (and why, and which IGs hinge
on it), and every known difference from TypeScript SUSHI (predefined XML, package-loading messages, messages on
malformed input, the off-by-default "newer SUSHI available" check, and so on) is in
[`docs/core-divergences.md`](docs/core-divergences.md). On the corpus (Linux, frozen package snapshot) only the XML
change (C6) changes `fsh-generated/` (8 IGs); package resolution (C5: R2 kept, the rest as core) changes it nowhere
there. Outside the corpus, C5's "follows core" cases (e.g. `x.y.x` with an older patch cached, offline `latest`) can
load other package versions than TS would.
The comparisons use a frozen FHIR package snapshot, so both implementations see the same packages.

## Tests

The JUnit suite (3,735 tests in this module, plus 13 in `common`) runs in every build and in
CI with nothing but the repository:

- **In CI (self-contained): 3,693 run and pass, 42 skipped,** on Linux (and the wrapper checks on Linux, macOS, Windows). That includes **3,581 ported Jest tests**: all that
  pass in TypeScript SUSHI except 3 below (`python3 sushi/scripts/test-inventory.py --coverage sushi/tests/inventory.tsv
  <surefire-reports>`). Their fixtures, the files they read from SUSHI 3.21.1's own test tree, are vendored in
  `src/test/resources/sushi-ts` (377 files, about 23 MB; Apache-2.0, see its `README.md`).
- **Skipped as in SUSHI (17):** tests that are `it.skip`/`it.todo` in TypeScript SUSHI, or that spy on a private
  method; each says so in its skip message.
- **Different by design (3):** `loadConfigurationFromIgResource` tests whose fixtures are not valid FHIR XML; TS reads
  them with fhir-tool, the port with core's XML parser, which rejects them (`docs/core-divergences.md` C6;
  `LoadConfigurationFromCoreXmlTest` covers the behaviour).
- **Opt-in (22):** tests over data that cannot be in the repository: the 267-IG FSH corpus and the JS parser's dumps
  of it (parser differentials), TypeScript SUSHI's results on the corpus configurations and the reference IGs, the
  WP-2/WP-7 gates. They are skipped with a message naming the property that enables them:
  `-Dsushi.vmdata=<data root>` (the build VM's layout: `sushi-wp1/`, `sushi-wp3/`, `sushi-port/...`) enables them
  all, or per test `-Dwp1.data=`, `-Dsushi.config.oracle.dir=`, `-Dsushi.wp5.gate.dir=`, `-Dsushi.genomics.fsh=`;
  a few heavy sets also need `-Dwp1.heavy=true`, `-Dwp2.corpus=`, `-Dsushi.wp7gate.ig=`. With `-Dsushi.vmdata` on
  the build VM, 3,703 run and 32 stay skipped.

`-Dsushi.ts.dir=<a full SUSHI checkout>` runs the tests against another copy of SUSHI's test tree.

## More

- [`PORTING.md`](PORTING.md): design decisions (dependencies, the JS value model, the hand-written parser with
  ANTLR as test oracle, package resolution through org.hl7.fhir.core), conventions, the TS → Java module map, the
  tests and the differential harness, and the final status (§9).
- [`docs/core-divergences.md`](docs/core-divergences.md): where the port does not use org.hl7.fhir.core and why
  (with the IGs that hinge on it), package resolution, predefined FHIR XML, and every other known difference from
  TypeScript SUSHI.
- `tests/inventory.tsv`: SUSHI's Jest tests and which JUnit test ports each.
- `tests/e2e/ig`: the small FSH project `scripts/sushi-e2e.sh` runs through the wrapper.
- `scripts/`: `build.sh` (the module build), `diff-run.sh` / `corpus-diff.sh` (differential runs against
  TypeScript SUSHI), `classify-diff.py`; `oracle/`, `xhtml/`, `probe/`, `corexml/` (the XML-usage census), `wp1/`, `ts2junit/` are the
  oracle and test-porting tools used during the port (they expect the corpus workspace and the reference image).

The module's place in the repository (shared code in `common`, module rules, building) is in
[`../docs/MONOREPO.md`](../docs/MONOREPO.md).
