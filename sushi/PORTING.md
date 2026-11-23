# SUSHI port: porting guide and work packages

Java port of **SUSHI 3.21.1** (FHIR/sushi @ 9c1bf5c, v3.21.1+2), intended as SUSHI's permanent Java home.
Hard requirement: for the same inputs and FHIR packages, **byte-identical `fsh-generated/`**, the **same console
messages** (text, order, counts) and the **same exit status** as TypeScript SUSHI 3.21.1 on Node 22.
Design goal (owner): long-term maintainability and no Node/npm — clean, idiomatic Java a maintainer can own, not a
line-by-line transliteration — while keeping SUSHI's class/method structure close enough that its ~3,600 Jest tests
(mostly API-level, ~2,000 assertions on exact log text) port 1:1.

This port lives in the `sushi/` module of this repository (docs/MONOREPO.md); it was developed in its own repository
(`sushi-java`) and moved in with its history (§7). Paths below are relative to `sushi/`; `../work` is the repository's
(git-ignored) workspace directory.

Reference material: TS source (read-only copy) `../work/sushi-spike/src-ro/`; full checkout with tests, fixtures and
node_modules on the VM at `$VMDATA/sushi-spike/sushi`; sizing notes `../work/sushi-spike/README.md`.
Nothing goes upstream (no pushes, PRs, issues, contact with the SUSHI team).

## 1. Build, run, test

```
scripts/build.sh                      # = ../build.sh -pl sushi -am -B -q verify: common + sushi, all tests, jars
scripts/build.sh -B -q test -Dtest=JsonWriterTest
scripts/build.sh -B -q -DskipTests package     # jar: target/sushi.jar (self-contained: java -jar target/sushi.jar <ig>)
SUSHI_BUILD_DIR=$VMDATA/sushi-port/build/<name> scripts/build.sh ...   # target dir elsewhere (the oracle scripts read it there)
../build.sh -B -ntp package           # the whole repository: common, jekyll, sushi
```
- `../build.sh` runs Maven locally when a JDK 21 and `mvn` are available, otherwise Maven 3.9.9 in the reference image
  `ig-publisher-localdev-ca` (Temurin 21, node 22 for the oracles) with its local repository on the Docker host's
  disk (docs/MONOREPO.md "Building").
- Java 21 (`--release 21`). `common`, which sushi uses for the JS value model and the YAML event layer, is Java 17.
- Shared machine: start Maven/node/TS-SUSHI runs with `../work/locks/waitfree.sh timing <cmd>`; kill only your own
  PIDs.
- SUSHI's test fixtures (the files the ported tests read from TS SUSHI's `test/` tree) are vendored in
  `src/test/resources/sushi-ts`; `-Dsushi.ts.dir=<checkout>` points the tests at a full checkout instead. Tests
  over data that can't be in the repository (the corpus, recorded TS runs) are opt-in, `-Dsushi.vmdata=$VMDATA`
  or per-test properties (`testhelpers.OptInData`, README "Tests").

## 2. Key design decisions

**D1 Dependencies.** Runtime: only (a) org.hl7.fhir.core modules the IG Publisher already ships, and (b) our own
code. No other third-party libraries, and **no ANTLR at runtime**.
| dependency | scope | version | why |
|---|---|---|---|
| `ca.uhn.hapi.fhir:org.hl7.fhir.utilities` | compile (on main since WP-8 day 1) | **7.0.6** (release matching the publisher toolchain's 7.0.7-SNAPSHOT) | package cache (`FilesystemPackageCacheManager`, same `~/.fhir/packages` layout), registry/`PackageClient`, `NpmPackage` reading, **package/version resolution as is** (owner decision, see D1a), `VersionUtilities`, ini |
| `org.apache.commons:commons-compress` | **runtime** | 1.26.0 (= publisher's) | optional in core's pom, needed by core's package cache to unpack .tgz packages; not imported by our code |
| `ca.uhn.hapi.fhir:org.hl7.fhir.r4`/`r4b`/`r5` | compile | 7.0.6 | only their `formats.XmlParser`/`JsonParser`: predefined resources and a legacy `ig.xml` in FHIR XML are read with core (`fhirdefs.FhirXml`; owner decision, replacing the earlier port of fhir-tool's xmlToObj; docs/core-divergences.md C6). r5 without jgit, nimbus-jose-jwt and plantuml-mit, which its parsers don't use |
| `org.codelibs:xpp3` | compile | 1.1.4c.0 (= core's) | optional in core's poms; the XML pull parser core's XmlParsers need; not imported by our code |
| `org.antlr:antlr4-runtime` | **test** | 4.13.2 (= publisher's) | oracle parser generated from SUSHI's unmodified `.g4` files (`src/test/antlr4`) |
| `org.junit.jupiter:junit-jupiter` | test | 5.11.4 | tests |
| build plugins: antlr4-maven-plugin 4.13.2, build-helper 3.6.0, compiler/surefire/jar | build | | the antlr plugin's output is excluded from main and added to test sources |
Its transitive libraries (jackson, guava, commons-lang3/text/io/codec/net, okhttp/okio/kotlin-stdlib, icu4j, Saxon-HE,
slf4j, opentelemetry, hapi-fhir-base, xpp3) ship with the publisher but **must not be imported directly**:
`build.DependencyRuleTest` fails the build on any main-code import outside the JDK, `org.hl7.fhir.sushi`,
`org.hl7.fhir.utilities` (and `org.hl7.fhir.r4/r4b/r5` only in `fhirdefs/FhirXml.java`, by import or fully qualified
name).
`target/sushi.jar` is one self-contained jar (maven-shade: our classes, `common`, core utilities and the libraries it
brings; 78 MB since the r4/r4b/r5 modules, 41 MB before), so `java -jar sushi.jar <ig dir>` needs nothing else;
`DependencyRuleTest` also pins the pom's non-test dependencies to exactly `fhir-ig-common`, core utilities, the
r4/r4b/r5 modules, xpp3 and commons-compress, and the pom's enforcer `bannedDependencies` rule checks the same on every
build.
Not used from core, deliberately (each with its reason and the IGs that hinge on it in docs/core-divergences.md): model
classes (R4/R5 `StructureDefinition` etc.) except to read FHIR XML, `ProfileUtilities` snapshot
generation, FHIRPath, validation, core's JSON (`JsonNumber` keeps source text; SUSHI normalises through JS doubles).
SUSHI works on loose JSON across R4–R6 and clones/constrains the parent snapshot itself; reusing those changes output.
npm libraries are replaced by in-tree code: yaml 1.10 (WP-3, on the shared YAML event layer), ajv messages (WP-3),
html-minifier-terser + sax (WP-9), lodash (`js.Js`), winston/chalk (`utils.FshLogger`), text-table, title-case,
sanitize-filename, valid-url, junk, semver, commander (WP-10), fhir-package-loader (WP-8). fhir-tool (FHIR XML) and
ini were ported too (WP-8, WP-10) and then replaced by core (its XML parsers, `IniFile`; docs/core-divergences.md C6, C9).

**D1a Package resolution: core as is, measured, patched only where it differs (owner decision).** WP-8 does not
port fhir-package-loader's precedence rules up front. It uses core's package resolution (which packages/versions load,
`#latest`/`#current`, which resource wins for an id/name/url) unchanged, then measures, for every lookup the
exporters make on the reference IGs and the corpus, which definition wins in Java vs TS (log-and-compare probe), and
ports an FPL rule only where core picks differently. Every such rule is listed in `fhirdefs` with the probe case that
required it. Log *messages* about package loading must still match SUSHI/FPL's.

**D2 FHIR model: SUSHI's own classes over an ordered JS value model, not core's model.** The output bytes depend on JS
object key order, which in SUSHI comes from three different mechanisms, all verified on real output:
1. StructureDefinition / ElementDefinition: fixed property lists (`StructureDefinition.PROPS`,
   `ElementDefinition.PROPS`, ported verbatim into the skeletons; `_x` right after `x`).
2. CodeSystem / ValueSet / ImplementationGuide: TS compiles to ES2018 without `useDefineForClassFields`, so only
   initialised fields exist up front: the JSON is `resourceType`, the defaulted fields (`status`, `content`), then
   **every other property in the order the exporter assigns it** (e.g. `name, id, title, description, url, concept,
   caseSensitive, ...`). These classes therefore extend `js.JsObject` (insertion-ordered) with typed accessors.
3. Instances: arbitrary paths set in rule order (`InstanceDefinition extends JsObject`).
core's model classes serialise in canonical FHIR order, so they cannot reproduce 2 or 3, and they would also round
decimals differently (see D3).

**D3 The JS value model** (`org.hl7.fhir.common.js`, contract, see `Json` Javadoc): object = `Map<String,Object>`
(`JsObject`, insertion order; enumerate with `Js.ownKeys`, which puts array-index keys first like JS), array =
`List<Object>`, string = `String` (UTF-16, so JS indices/lengths carry over), **number = `Double` always** (so
`equals` matches JS and `1.50` prints `1.5`, `1e21` prints `1e+21`), boolean, `null`, JS `undefined` = `Js.UNDEFINED`
only where TS observably keeps an undefined-valued key, `JsonSerializable` = a JS `toJSON()`. `bigint` (FSH integers
beyond 2^53, integer64) = `BigInteger` in fshtypes; JSON.stringify throws on it, so convert before output as TS does.
lodash equivalents: `Js.isEqual` (deep, key order ignored, -0 == 0), `Js.cloneDeep` (`DeepCloneable` for classes),
`Js.truthy`, `Js.toStr` (template-literal interpolation). Add helpers here, coordinated, instead of per module.

**D3a Fished resources are frozen.** Like fhir-package-loader's SafeMode.FREEZE, `FHIRDefinitions` hands out
deeply frozen resources (`js.Frozen`: still `JsObject`/`ArrayList`, but every mutator throws `FrozenMutationError`
with V8's TypeError text). Code that changes a fished resource must `Js.cloneDeep` it first, as the TS code does;
an accidental mutation fails loudly instead of silently corrupting the shared cache. (`FrozenTest`; full suite and
the 6 reference IGs unchanged.)

**D4 JSON writer.** `Json.outputJson(v)` = fs-extra `outputJSONSync(path, v, {spaces: 2})` =
`JSON.stringify(v, null, 2) + "\n"`: 2-space indent, `": "`, empty `{}`/`[]`, undefined members skipped (null in
arrays), ECMAScript Number→String (shortest round-trip, V8 thresholds; JDK's 2-digit minimum fixed up), JSON.stringify
escaping (`"` `\\` `\b\f\n\r\t`, other C0 as `\u00xx`, lone surrogates `\udxxx`, nothing else — no `/`, no non-ASCII
escaping), and a **trailing newline** (the spike README says none; the files on disk have one). `Json.parse` =
JSON.parse (duplicate key: first position, last value; numbers → correctly rounded doubles), `parseFile` strips a BOM
like jsonfile. Verified: node oracle vectors (2,000+ numbers incl. random bit patterns, strings with lone surrogates,
key ordering, pretty printing; `scripts/oracle/js-vectors.js` → `src/test/resources/js/vectors.json`) and a
byte-identical parse→write round trip of all 425 JSON files TS SUSHI 3.21.1 wrote for genomics-reporting and ITI.MHD.

**D5 Logger.** `utils.FshLogger` ports FSHLogger.ts + the winston behaviour SUSHI relies on (verified with node on the
real module): ignore (ignoreWarnings.txt: exact string or `/regex/` with JS `test` = find) → count (always, even when
the level isn't printed: debug counts at level info, the secret logger relies on it) → track (errors/warnings while
`shouldTrack`) → append `\n  File: …`, `\n  Line: a[ - b]`, `\n  Applied in File: …`, `\n  Applied on Line: …` →
print `"<level padded to 5> <message>"` to stdout (`info `, `warn `, `error`, `debug`), colours only on a TTY.
Exit status = `stats.numError`. One process-wide instance (`FshLogger.logger`) like TS; pluggable sink (tests: the
loggerSpy equivalent). Message text: keep every message string **verbatim** next to the code that logs it (no message
catalogue reformatting); error classes in `errors/` carry their TS message templates exactly.

**D6 Parser: hand-written, ANTLR as test oracle.** WP-1 writes a lexer (FSHLexer.g4's 4 modes) + recursive-descent
parser for FSH.g4/MiniFSH.g4. The ANTLR Java target generated from SUSHI's unmodified grammar (test scope,
`org.hl7.fhir.sushi.importer.antlr`) is the oracle: a differential test + fuzzer compares parse trees, importer output
and syntax-error messages + recovery (ANTLR's "mismatched input"/"extraneous input … expecting {…}" text and its
single-token insertion/deletion recovery are the hard part) on corpus FSH, SUSHI fixtures and mutated FSH. Phase 1
checked the oracle: 47 files / 545 entities (genomics + SUSHI fixtures) parse; Java ANTLR runtime 4.13.2 vs SUSHI's JS
runtime 4.13.2 (generated with tool 4.13.0). **Deliberate parity with ANTLR's JavaScript runtime** (not differences from TS SUSHI, which runs on it): the hand
parser reproduces five behaviours where ANTLR's JS runtime 4.13 differs from its Java runtime: (1) the `~(...)`
follow-set bug in expected-token sets; (2) plain "mismatched input" (not "extraneous"/"missing") on some recoveries;
(3) `<missing null>` / `<missing undefined>` token names in missing-token messages; (4) lexer error text not escaped
("token recognition error at: '\n'" with a raw newline); (5) the interleaving of lexer and parser errors in the
message stream. The test-scope ANTLR **Java** oracle is adjusted for these in the differential (WP-1, `importer.parser`
package docs). Note: the JS runtime indexes by UTF-16 code unit, Java CharStreams by
code point — columns after astral characters differ; the hand parser must use UTF-16 columns (JS behaviour).

**D7 Randomness and network.** The summary box's pun (`puns.ts`) is random: port the list and selection, and
differential comparisons drop the pun line (`scripts/classify-diff.py`). The "latest SUSHI version" check
(`npm view fsh-sushi version`) has no Java equivalent: off by default, configurable; comparisons drop its lines.
`#latest`/`#current` package resolution hits the network in both implementations: differential runs use the frozen
package snapshot (`baseline-20261120`) + per-IG overlay.

**Naming decisions (open, for later).** N1 package root `org.hl7.fhir.sushi` (placeholder). N2 artifact
`org.hl7.fhir:fhir-ig-sushi`, jar `sushi.jar` (was `sushi-java`; docs/MONOREPO.md M2). N3 TS `import/` is `importer` (Java keyword); TS names that are Java keywords get a trailing `_`
(`short_`, `_abstract` in Metadata — settle one rule). N4 TS enum `Type` (Fishable) is `FishableType`.

## 3. Conventions

- **Structure.** One Java class per TS class with the same name, the same public methods (camelCase, same name and
  argument order where possible), so tests port 1:1 and a reviewer can read both side by side. Inside methods, write
  clear Java (pattern-matching `switch`, streams where clearer, small private helpers) — behaviour parity, not syntax
  parity. Class header: `// port of sushi/src/<path>.ts (SUSHI 3.21.1)`. Free functions of a TS module become static
  methods of a final class named after the module (`fhirtypes/common.ts` → `fhirtypes.Common`).
- **Nullability.** `undefined`/optional → `null`. Optional numbers `Double`, booleans `Boolean`; counts/lines `int`.
  Don't use `Optional` in fields. JS truthiness of strings (`""` is false) via `Common.truthy` / `Js.truthy`.
- **Determinism: match TS iteration order exactly.** TS `Map`/`Set`/object → `LinkedHashMap`/`LinkedHashSet`/
  `JsObject`; never `HashMap`/`HashSet` where order can leak into output, messages or "first match wins" logic.
  `Object.keys/entries/for..in` → `Js.ownKeys`. `Array.prototype.sort` is stable (TimSort since V8 7.0): use
  `List.sort` (stable) with the same comparator; a comparator returning booleans or NaN in TS must be reproduced, not
  "fixed". `localeCompare` → check the call site (V8 ICU collation ≠ `String.compareTo`).
- **Strings & regex.** Java and JS strings are both UTF-16: indices, `length`, `substring` match. Regex: JS `\s` is
  Unicode whitespace (`Common.JS_WS`), Java's is ASCII; JS `$` without `m` is Java `\z`… careful: JS `$` = end of input
  only, Java `$` also matches before a final `\n`; `split` needs `split(re, -1)` (Java drops trailing empties) and a JS
  string `split(",")` is literal (use `Pattern.quote`); `replace(str, …)` replaces the FIRST occurrence; `\w`, `\d`,
  `\b` are ASCII in both; JS `/u` and sticky flags have no direct equivalent. `trim()` → `Js.trim` (Java `strip()`
  differs on U+00A0, U+FEFF, U+001C–1F). Number → string in messages: `Js.toStr` / `JsNumber.toString`
  (never `String.valueOf(double)`: `1.0E-7` vs `1e-7`, `5.0` vs `5`).
- **lodash.** In `js.Js` (oracle-tested): `isEqual`, `cloneDeep` (fshtypes entities via `FshEntity.cloneDeep()`),
  `isEmpty` (numbers/booleans are empty!), `isMatch` (partial; arrays order-insensitive), `merge`/`mergeWith`
  (arrays by index), `pad/padStart/padEnd`, `upperFirst`, `escapeRegExp`, `words`. Plain Java for the rest, with
  these semantics: `sortBy` = stable `List.sort` by key (undefined/null keys last); `uniqWith`/`uniq` keep the FIRST
  occurrence in order; `difference`/`xor`/`includes` use SameValueZero (`Js.isEqual` on leaves, NaN equal);
  `groupBy` = `LinkedHashMap` in first-seen key order; `partition` keeps order; `pickBy` keeps key order;
  `findLast`, `flatMap`, `flatten` (one level), `sumBy`, `min`, `repeat`, `sample` (`Puns` only).
- **Exceptions.** TS `throw new XError()` caught and logged → Java unchecked exceptions in `errors/` with the same
  class name and the exact TS message; keep the try/catch placement (it decides which message is logged and what is
  skipped).
- **async.** TS async/await (package loading, network) becomes synchronous Java.
- **Fluent setters.** `withLocation/withFile/...` return `this` as the caller's type (`<T extends FshEntity> T`).
- **TS getters** (`get id()`) → `getId()`/`setId()`; `constructorName()` for `constructor.name` checks.
- **Output files.** Write through one helper (WP-10, `utils.Processing`) that writes UTF-8, `\n` line endings, exactly
  the bytes TS writes; JSON via `Json.outputJson`.

### I/O policy (owner requirement)

All file access goes through org.hl7.fhir.core's `ManagedFileAccess` and all network access through its
`ManagedWebAccess`. That is how the IG Publisher and core control I/O (access policy and allowed paths, a host-provided
accessor, `fhir-settings.json`, proxies, offline mode), so it applies to SUSHI too.

- **Files: `utils.Fs`, the one adapter.** Contents are read and written with `ManagedFileAccess.inStream/outStream`,
  folders are listed with `ManagedFileAccess.listFiles`, and metadata (`exists`, `isDirectory`, `isFile`) comes from
  the `File` that `ManagedFileAccess.file()` returns. ManagedFileAccess has no operation for the following, so `Fs`
  does them on the `File` (or its `Path`) that ManagedFileAccess returned, never on a path SUSHI built itself:
  - lstat (`Files.isDirectory/isRegularFile(..., NOFOLLOW_LINKS)`): Node's `Dirent.isDirectory()`/`isFile()`
    describe a symlink itself (DiskBasedVirtualPackage, the `path-resource` `/*` folder walk, emptyDir);
  - `Files.createDirectories` (fs-extra `ensureDirSync`, `outputFileSync`'s parent folders, the package cache folder
    when missing): it keeps NIO's exceptions and their messages, where `File.mkdirs()` only returns false;
  - `Files.delete` (fs-extra `emptyDirSync`, `sushi init`);
  - `Files.setPosixFilePermissions` (`sushi init` makes `_build.sh` executable).

  `Fs` keeps java.nio's semantics: an empty path is the current folder, and failures raise NIO's exception types
  (`NoSuchFileException`, `AccessDeniedException`, ...). It translates the `FileNotFoundException` of core's streams by
  looking at the file, because `Processing.nodeMessage` turns those types into Node's `ENOENT`/`EACCES` texts.
- **Network: `ManagedWebAccess.get(List.of("web"), url)`** for the `#latest` dist-tags lookup on packages.fhir.org
  (`fhirdefs.CorePackageProvider`, rule R2), and for the npm-registry version check and `sushi init`'s `_build`
  script download (`Processing.webGet`, which keeps axios's `Request failed with status code N`). Package downloads
  go through core's package cache, which already uses ManagedFileAccess/ManagedWebAccess. Timeouts are core's (15 s
  per connect/read, one retry). Before this the two Processing calls used their own `HttpClient` with 5 s connect
  and 10 s request timeouts. Both run only for the off-by-default version check (D7) and `sushi init`.
- **Settings.** `App.main` calls `ManagedWebAccess.loadFromFHIRSettings()`, as the publisher does:
  `~/.fhir/fhir-settings.json` (or `-Dfhir.settings.path=`) applies, including `prohibitNetworkAccess`, configured
  package servers and their authentication, and SSRF protection. Tests, which call `App.run`, don't load it.
- **Not file access, allowed:** classpath resources (`Class.getResourceAsStream`: R5-for-R4 definitions, the config
  schema, the `init` templates) and `App.out/err`, UTF-8 `PrintStream`s on the stdout/stderr
  file descriptors.
- **Enforced** by `build.ManagedIoRuleTest`: it reads the constant pool of every compiled main class and fails on any
  direct use of `java.io` file streams/readers/writers, `RandomAccessFile`, non-path `java.io.File` members,
  `java.nio.file.Files`/`FileSystems`/`FileChannel`, `Path.toFile`, `URL.openStream/openConnection`,
  `URLConnection`, `java.net.http`, sockets and `ZipFile`/`JarFile` outside `utils.Fs`. It also fails on
  `ManagedFileAccess.inStream/outStream(File)`, which skip the policy check. The allow-list has one entry,
  `FileOutputStream(FileDescriptor)` for stdout/stderr. A fixture class checks that the scanner finds each kind of use.
  `utils.FsTest` checks Fs's semantics and that core's file and web policies take effect (PROHIBITED → core's
  messages).

## 4. Module map (TS → Java)

LOC = nonblank TS lines; tests = Jest tests (`tests/inventory.tsv`, generated from the spike's full Jest run:
3,604 tests, 3,589 pass, 8 skipped, 7 todo).

| TS (src/…) | LOC | Java package | tests (files) | depends on | WP |
|---|---:|---|---:|---|---|
| fshtypes/*, fshtypes/rules/* | 1,815 | `fshtypes`, `fshtypes.rules` | 253 (fshtypes/**) | js | **done (phase 1)**; test port WP-3 |
| import/FSHDocument, RawFSH | 49 | `importer` | – | fshtypes | **done** |
| (FSHLogger.ts) | 202 | `utils.FshLogger` | 29 (utils/FSHLogger) | fshtypes.SourceInfo | **done**; test port WP-10 |
| utils/Fishable.ts | 34 | `utils.Fishable/FishableType/Metadata` | – | – | **done** |
| (lodash, JSON, Number) | – | `js` | oracle tests | – | **done** |
| antlr/*.g4 (+ generated 10k JS) | 329 g4 | `importer.parser` (hand-written), oracle `importer.antlr` (test) | 23 (FSHErrorListener) + oracle/fuzz | – | WP-1 |
| import/FSHErrorListener | 301 | `importer.parser` | (above) | FshLogger | WP-1 |
| import/FSHImporter, parserContexts, MiniFSHImporter, miniParserContexts, importText, parseCodeLexeme | 3,073 | `importer` | 585 (FSHImporter.*) | WP-1 CST, fshtypes, FshLogger | WP-2 |
| import/FSHTank | 695 | `importer` | 108 | fshtypes, Fishable | WP-2 |
| import/importConfiguration (+YAMLschema.json/ajv), YAMLConfiguration, loadConfigurationFromIgResource, ensureConfigurationFile | 1,455 | `importer.config`, `yaml` | 167 | YAML event layer, fshtypes.Configuration | WP-3 |
| errors/* (57 classes) | 682 | `errors` | – | – | WP-3 (first deliverable) |
| fhirtypes/ElementDefinition, sliceTree | 3,251 | `fhirtypes` | 591 (ElementDefinition.*) | WP-5 SD, Fishable, WP-9 xhtml, errors | WP-4 |
| fhirtypes/StructureDefinition, common, dataTypes, mixins, primitive/metaData/specialTypes, CodeSystem, ValueSet, ImplementationGuide, InstanceDefinition | 3,562 | `fhirtypes` | 260 (incl. applyInsertRules, PathUtils) | WP-4 ED, PathUtils | WP-5 |
| utils/PathUtils | 311 | `utils` | 44 | fshtypes | WP-5 |
| export/StructureDefinitionExporter, Package, FHIRExporter, exportFHIR; utils/MasterFisher, FishingUtils | 2,194 | `export`, `utils` | 656 | WP-4/5, tank, fhirdefs | WP-6 |
| export/InstanceExporter, ValueSetExporter, CodeSystemExporter, MappingExporter | 2,218 | `export` | 562 | WP-4/5/6 | WP-7 |
| fhirdefs/FHIRDefinitions (+R5DefsForR4 json) over core's package layer as is (D1a; FPL rules ported only where the probe shows core differs), utils/FHIRVersionUtils, axiosUtils, Processing.load*/fixCrossVersionDependencies | ~1,000 TS | `fhirdefs` | 80 + probe | core utilities npm, js | WP-8 |
| html-minifier-terser 5.1.1 subset (collapseWhitespace, keepClosingSlash…), sax well-formedness check (as used by ElementDefinition/InstanceExporter xhtml) | (JS lib) | `xhtml` | new: differential vs JS lib | – | WP-9 |
| ig/IGExporter, predefinedResources; app.ts; run/FshToFhir; utils/Processing (rest), puns, text-table/title-case/sanitize-filename/ini/semver/junk equivalents | ~3,580 | `ig`, `run`, `utils`, root `App` | 319 (ig/**, run, Processing, puns, FSHLogger, axios) | everything | WP-10 |

## 5. Work packages

*How the port was organised (historical): all work packages are done; the final status is in §9.*

Each WP: own git worktree/branch; owns its Java packages exclusively; may add (not change) contract types it owns;
changes to contract packages (`js`, `fshtypes`, `utils.Fishable*`, `utils.FshLogger`, skeleton signatures of other
WPs) go through the coordinator. Test claim rule: a JUnit test ports a Jest test by using its exact `fullName`
(describe titles + `it` title, space-joined) as `@DisplayName`, so `scripts/test-inventory.py --coverage` reports
ported/passing per WP. **Definition of done (all WPs):** owned code complete (no `TODO WP-n` left in owned files);
all inventory tests of the WP ported and passing, or listed with a reason (TS-only API, mock-heavy, covered
elsewhere); messages verbatim; no `HashMap` iteration in output paths; `scripts/build.sh` green on the WP branch
rebased on main; plus the WP-specific gate below.

| WP | scope (TS → Java) | LOC | tests | needs (interfaces) | WP-specific gate |
|---|---|---:|---:|---|---|
| **WP-1 parser** | hand-written FSH lexer/parser (4 lexer modes), MiniFSH, FSHErrorListener; oracle diff + fuzzer | 630 g4/TS → ~2.5k Java | 23 + oracle | none. **First deliverable (hours): CST node classes** in `importer.parser` named and shaped like ANTLR's `FSHParser.*Context` (same rule names/accessors), + a test-scope adapter ANTLR tree → CST so WP-2 can start | zero tree/error diffs vs ANTLR on corpus (267 IGs' FSH) + fixtures + ≥100k fuzz cases; error recovery identical |
| **WP-2 importer** | FSHImporter (visitor over the CST), parserContexts, MiniFSHImporter, importText, parseCodeLexeme, FSHTank | 3,768 | 693 | WP-1 CST (via ANTLR adapter until ready), fshtypes | `-p` preprocessed-FSH output and a tank dump identical to TS on the corpus |
| **WP-3 config + errors + fshtypes tests** | errors/ (57 classes, first), importConfiguration, YAMLConfiguration, loadConfigurationFromIgResource, ensureConfigurationFile, YAML (yaml@1.10 semantics on the shared libyaml event layer, §7), ajv-equivalent schema checks with exact message text, fshtypes test port | 2,137 + YAML | 167 + 253 | fshtypes.Configuration | config of all 266 corpus sushi-config.yaml identical to TS (dump JSON); invalid-YAML/ajv messages identical on a mutation set |
| **WP-4 ElementDefinition** | ElementDefinition, sliceTree (slicing, types, assignment, diff/`calculateDiff`, implied props) | 3,251 | 591 | WP-5 StructureDefinition (findElement, elements, fromJSON/toJSON), WP-9 xhtml, Fishable | all ED tests; profile output parity on reference IGs once WP-6 lands |
| **WP-5 StructureDefinition + fhirtypes common** | StructureDefinition, common (setPropertyByPath, applyInsertRules, replaceReferences, orderedCloneDeep…), dataTypes/mixins/…, CodeSystem/ValueSet/IG/InstanceDefinition models, PathUtils | 3,873 | 260 | WP-4 ED (publish method signatures day 1) | key-order fidelity per D2 on reference output |
| **WP-6 SD export core** | StructureDefinitionExporter, Package, FHIRExporter/exportFHIR, MasterFisher, FishingUtils | 2,194 | 656 | WP-4/5, FSHTank, FHIRDefinitions (test defs) | profiles/extensions/logicals/resources byte-identical on the 6 reference IGs |
| **WP-7 instance + terminology export** | InstanceExporter, ValueSetExporter, CodeSystemExporter, MappingExporter | 2,218 | 562 | WP-4/5/6 | instances/VS/CS byte-identical on the 6 reference IGs |
| **WP-8 packages** (+ WP-9 xhtml, same agent) | FHIRDefinitions on core's package resolution **as is** (D1a): cache/registry/reading/version resolution from `org.hl7.fhir.utilities.npm`; virtual + predefined packages, R5-for-R4 defs, FPL-compatible log messages; a lookup probe (TS vs Java winner per id/name/url/type) and FPL rules ported only where core differs; FHIRVersionUtils; Processing dependency loading | ~1,000 | 80 + probe | core utilities | for every reference IG then corpus IG: same loaded-package list and same winner for every lookup the exporters make; each ported FPL rule justified by a probe case |
| **WP-9 xhtml** | html-minifier-terser 5.1.1 subset SUSHI uses + sax-based xhtml well-formedness messages | (JS lib) | new | – | differential vs the JS library on all `text.div`/xhtml values in the 82 corpus IGs + fuzz; identical messages for malformed xhtml |
| **WP-10 IG, CLI, glue** | IGExporter, predefinedResources, App (commander options), run/FshToFhir, Processing (rest: inputs, writing, fsh-index.txt via text-table, preprocessed), puns, title-case, sanitize-filename, ini; logger tests | ~3,580 | 319 | all | full differential: `fsh-generated/` byte-identical + log identical (minus pun/version lines) + same exit status on the 6 reference IGs, then the corpus |

Ten packages, run by 8 agents (phase 2 staffing): WP-8+WP-9 share an agent; WP-10 starts later, its leaf parts
(puns, text-table, title-case, sanitize-filename, valid-url, `testhelpers.LoggerSpy`) are done by the integrator
(on main: `utils.Puns/TextTable/TitleCase/SanitizeFilename/ValidUrl`, test helper `testhelpers.LoggerSpy`). Originally: WP-9 is small (pair it with WP-3 or WP-8), WP-1 can be followed by WP-2 work by the same
agent, and WP-10 can start late (its first weeks: puns/text-table/title-case/sanitize + Processing). Critical path:
WP-5 ↔ WP-4 → WP-6 → WP-7 → WP-10. Day 1 for every WP: replace its skeletons' `TODO` bodies with **real
signatures** (public methods other WPs call), commit to main, then implement.

## 6. Tests and the differential harness

- **Unit tests (hand-ported, 1:1):** `test/<dir>/<X>.test.ts` → `src/test/java/org/hl7/fhir/sushi/<pkg>/<X>Test.java`,
  one `@Nested` class per `describe` (its `@DisplayName` = the describe title, for readability), and **each test
  method's `@DisplayName` = the full Jest `fullName`** (describe titles + `it` title, space-joined): surefire reports
  that as the testcase name, and coverage matches it. `beforeEach`
  → `@BeforeEach`, `loggerSpy` → capture sink on `FshLogger` (shared helper `testhelpers.LoggerSpy`, WP-10 provides
  it first; until then use `FshLogger.logger.setSink`), `getTestFHIRDefinitions` → `testhelpers` over the TS
  fixtures (`test/testhelpers/testdefs`, read in place from `-Dsushi.ts.dir`, not copied: 17 MB). Jest mocks
  (`jest.spyOn`, mock FS/axios) → small fakes; list unportable cases in the WP report.
- **Fixture-driven tests:** tests that read `test/**/fixtures` (IG trees, YAML configs, FSH files) read them from
  the TS checkout path; expected values are in the test code as in TS.
- **Oracles:** JS semantics (`scripts/oracle/*.js` → committed vectors); ANTLR parser (WP-1); html-minifier (WP-9,
  node in the image with SUSHI's node_modules).
- **Inventory:** `tests/inventory.tsv` (wp, file, fullName, status); coverage:
  `python3 scripts/test-inventory.py --coverage tests/inventory.tsv <surefire-reports>`.
- **Differential runner:** `scripts/diff-run.sh <IG> [label]` runs TS SUSHI 3.21.1 (via the spike's
  `work/sushi-spike/tools/run-sushi.sh`, node in a container, frozen package snapshot + per-IG overlay) and the Java
  jar on the same source copy and package overlay, then `scripts/classify-diff.py` writes a classified report
  (`identical / missing / extra / json-format / json-key-order / json-value (first paths) / text`, plus a log diff
  ignoring the pun/version lines) to `../work/sushi-port/diff/<IG>-<label>.json`. Inputs: the 6-IG reference set at
  `$VMDATA/sushi-spike/runs/*-ref-*` (genomics-reporting, ITI.MHD, ch-term, EEHRxF logical, ch-ekm, smart-anc), then
  the 267 FSH IGs of the corpus (`work/sushi-spike/fsh-repos.txt`). Results: §9.

## 6a. Integration (phase 2)

*Historical: how phase 2 was integrated; the port's repository and its request log are no longer used.*

The integrator (phase-1 agent) owns `main` and the contract packages. WPs file `<UTC> | <wp> | merge <branch>@<commit>`
or `| <wp> | contract: <what/why>` lines in `../progress/sushi-port-requests.log`. Merge rule: the branch rebased on
`main` builds green (`scripts/build.sh`), touches only the WP's own packages (plus its tests), or the contract change
was accepted. Accepted contract changes are announced in the `main` commit message and as
`| integrator | contract changed: …` in the requests log. Merges are fast-forward/rebase merges (linear history).

## 6b. Known differences

`docs/core-divergences.md` lists every place where the port does not use org.hl7.fhir.core although core has the
capability (C1-C17: why, which IGs hinge on it, verdict), including package resolution (C5) and FHIR XML (C6), and
every other known behaviour difference from TS SUSHI 3.21.1 (T1-T6 and minor ones). Add entries there, never
silently.

## 7. Monorepo move (done)

The port lives in this repository's `sushi/` module, next to `common` (zero-dependency shared code) and `jekyll`
(the Jekyll shim); see docs/MONOREPO.md for the layout and module rules. It was imported with its history by
`scripts/sushi-sync.sh` (git filter-repo into `sushi/`, then a merge); the mechanical changes are in
`scripts/sushi-restructure.sh`, which a re-sync runs again:
- **JS value model in `common`:** `org.hl7.fhir.sushi.js` is now `org.hl7.fhir.common.js` (`Js`, `JsNumber`,
  `JsObject`, `Json`, `JsonSerializable`, `Lodash`, `Frozen`), compiled with `--release 17` like the rest of
  `common`. `JsonTest` and `FrozenTest` moved with it; `LodashTest` (it also checks `utils.ValidUrl`) is
  `utils.LodashOracleTest`. `utils.FshLogger` stays SUSHI-specific.
- **YAML.** SUSHI uses the `yaml` npm package 1.10 (eemeli/yaml; YAML 1.2 core schema by default — not js-yaml, not
  libyaml): `yes/no/on/off` are strings, timestamps stay strings, `0o` octal, `.inf/.nan`, no `<<` merge, duplicate
  keys are errors, and its own error messages. The syntax layer is the libyaml 0.2.5 event layer in `common`
  (`org.hl7.fhir.common.yaml`: `Parser`, `Event`, `Mark`, `LibyamlError`), shared with the shim. The relaxations
  where yaml@1.10 accepts input libyaml rejects (WP-3, docs/core-divergences.md T1) are an opt-in mode of that layer,
  `new Parser(bytes, encoding, true)`; the shim uses the default, plain libyaml. The yaml@1.10 composer
  (`yaml.Yaml`, `yaml.YamlError`: core-schema scalar resolution, anchors/aliases, duplicate-key and error wording)
  stays in `sushi` and uses only `common`'s public API.
- **Jar:** `target/sushi.jar`, shaded (see D1), replaces the old `lib/` class path.

## 8. Risks for the parallel phase

*Historical: the risks as seen at the start of phase 2; the outcome is in §9.*

1. **ED ↔ SD coupling (WP-4/WP-5)** is the critical path; exporters (WP-6/7) can port tests early but only pass once
   both land. Mitigation: signatures on day 1, small frequent merges to main.
2. **Hand-written parser parity (WP-1)**: ANTLR error recovery and message text; WP-2 must not wait (CST contract +
   ANTLR adapter first).
3. **Package precedence (WP-8)**: core's resolution is used as is (D1a); a wrong winner silently changes base definitions everywhere, so the probe
   against TS must run before exporters' differential results mean anything.
4. **Hidden JS semantics** (key order from assignment order, `Number` formatting in messages, regex, sort
   comparators, `localeCompare`): conventions above; reviewers check them.
5. **YAML**: the event layer is shared with the shim (`common`); changes to it must keep the shim's YAML suites
   passing, and SUSHI-only behaviour goes behind the opt-in yaml110 mode.
6. **Test-port volume** (~3,600 tests, ~80k TS lines) dominates effort; keep tests 1:1 to make it mechanical.

## 9. Final status (phase 2 complete)

**Tests.** Full suite: 3,714 JUnit tests, 0 failures, 29 skipped (3,713 at the end of the port, plus the package-cache regression test added in this repository). Jest inventory (`tests/inventory.tsv`, 3,604 tests)
ported and passing: **3,584**. The other 20 are all accounted for: 15 are skipped/pending/todo in TS SUSHI 3.21.1
itself (ported and kept `@Disabled` with the TS marker), 2 assert a Jest spy on a private method (TS-only), 3 test the
axios HTTP wrapper, which the port doesn't have (HTTP goes through core's ManagedWebAccess).
**Since then:** predefined FHIR XML is read with core instead of the fhir-tool port (owner decision,
docs/core-divergences.md C6): the suite is 3,723 tests in this module (42 skipped), and 3 more WP-3 tests
(`loadConfigurationFromIgResource` on invalid FHIR XML fixtures) are disabled as different by design, so 3,581 are
ported and passing.

| WP | area | passing / inventory | not passing (all `@Disabled` or n/a, with reason) |
|---|---|---|---|
| WP-1 | parser | 23 / 23 | – |
| WP-2 | importer | 690 / 693 | 2 TS-only (`jest.spyOn(importer, 'visitDoc')`), 1 `it.skip` in TS |
| WP-3 | config, errors, YAML, fshtypes tests | 420 / 420 | – |
| WP-4 | ElementDefinition | 589 / 591 | 2 skipped in TS |
| WP-5 | StructureDefinition, common, PathUtils | 260 / 260 | – |
| WP-6 | SD export, Package, MasterFisher | 648 / 656 | 7 `it.todo` + 1 `it.skip` in TS |
| WP-7 | instance + terminology export | 558 / 562 | 4 pending in TS |
| WP-8 | packages (+ WP-9 xhtml) | 77 / 80 | 3 axiosUtils tests: no axios in the port |
| WP-10 | IG, CLI, Processing, logger | 319 / 319 | – |

The one TS test that needed a Jest prototype mock (`StructureDefinition.prototype.validate`) is ported with real
validation errors instead (same logging assertions).

**Gates.**
- End to end (`java -jar`, `scripts/diff-run.sh`): the 6 reference IGs (genomics-reporting, ITI.MHD, ch-term,
  EEHRxF logical-model, ch-ekm, smart-anc; 2,153 files) have byte-identical `fsh-generated/`, identical logs (pun and
  version lines normalised, D7) and equal exit codes.
- Corpus (`scripts/corpus-diff.sh`, WP-10): **267 / 267 FSH IGs with identical `fsh-generated/` and exit codes**
  against TS SUSHI 3.21.1 on Node 22 with the frozen package snapshot; logs identical in 261 / 267. 11 IGs differed on
  the first pass; root causes fixed on main with regression tests (`regress.CorpusRegressionTest`,
  `export.RegressSliceTypeDiffTest`): undefined config.version written as null; cleanResource not walking
  ElementDefinition._original; `#latest` = packages.fhir.org's dist-tags.latest (rule R2); a V8 TypeError text.
  FPL messages that needed package semantics of their own were then dropped in favour of core's (they changed only
  the log): the `dev` -> `current` fallback line (hl7-eu imaging-api), the `#current` freshness warning (HL7-cz ps,
  hl7-eu laboratory, ConsentAboutAI), `Attempting to download` (WHO smart-trust) and `Failed to resolve version for
  X#latest` (davinci-ecdx, now core's message; still one error). Those 6 IGs were rerun: `fsh-generated/` identical,
  exit codes equal; the other 260 contain none of these lines in TS's log.
- Predefined FHIR XML through core (after the port; docs/core-divergences.md C6): reference IGs 4 / 6 identical
  (ch-term and smart-anc differ in ImplementationGuide descriptions, smart-anc also in XML warnings); corpus
  `fsh-generated/` identical in **259 / 267** (8 IGs differ in `ImplementationGuide.definition.resource` entries and
  link references only), logs in 250 / 267, exit codes in 267 / 267.
- Package resolution (WP-8 probe): every lookup on the reference IGs and corpus picks the same definition as TS
  (in hl7-eu imaging-api the package of 5 of them is labelled `ihe.rad.mado#dev`, TS: `#current`); one SUSHI rule kept on top of core (R2, `latest`), everything else is core's (`docs/core-divergences.md` C5).
- Known differences, where core is not used and why: `docs/core-divergences.md`.

