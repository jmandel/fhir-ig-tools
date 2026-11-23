# Where the port does not use org.hl7.fhir.core, and other differences from TypeScript SUSHI

The port's target is TypeScript SUSHI 3.21.1: for the same inputs and packages, the same `fsh-generated/`, console
messages and exit status (README, PORTING.md), except where this file says otherwise (C5 package-loading messages,
C6 predefined FHIR XML). Its runtime uses org.hl7.fhir.core 7.0.6: `org.hl7.fhir.utilities` for
the package cache, registry client and `NpmPackage` (C5) and `ManagedFileAccess` / `ManagedWebAccess` for all I/O
(PORTING.md "I/O policy"), and the r4/r4b/r5 modules' XML parsers for predefined FHIR XML (C6). Everything else FHIR-shaped is SUSHI's own code, ported.

Principle (owner): the port should not carry semantics of its own where core has the capability, unless there is a
real compatibility implication, and then the entry names the IGs that hinge on it. This file lists every such place
(C1-C17), then the differences from TypeScript SUSHI that remain (T1-T6, and a list of minor ones).

Each entry: what and where; what core offers instead; why core isn't used (the TS behaviour matched, how core's
answer differs, which IGs hinge on it); the cost of switching; a verdict. "Corpus" is the 267 IGs of the project's
317-repository corpus that use FSH; "measured" means a run of both implementations, not an estimate.

| # | area | port code | core alternative | verdict |
|---|---|---|---|---|
| C1 | profile authoring: snapshot editing, differential | `fhirtypes` (ElementDefinition, StructureDefinition, Common; ~6,500 lines) | r4/r5 `ProfileUtilities.generateSnapshot` | keep |
| C2 | resource model | JS value model (`common.js`) + SUSHI's `fhirtypes` classes | r4/r5 model classes, element model | keep |
| C3 | JSON read/write | `common.js.Json` (338) | `org.hl7.fhir.utilities.json.parser.JsonParser` | keep |
| C4 | definitions store and lookups | `fhirdefs` index + `FHIRDefinitions`, `utils.MasterFisher` (~800) | `IWorkerContext` / `CanonicalResourceManager` | keep |
| C5 | package resolution and loading | core as is; one rule on top (R2) | `FilesystemPackageCacheManager`, `PackageClient` | core (R2 kept) |
| C6 | predefined FHIR XML to JSON | core now: `fhirdefs.FhirXml` (r4/r4b/r5 parsers); fhir-tool port removed | - | switched (owner); differs from TS |
| C7 | xhtml minify / well-formedness | `xhtml` (1,947) | `utilities.xhtml.XhtmlParser`/`XhtmlComposer` | keep |
| C8 | YAML | `common.yaml` + `yaml.Yaml` (~3,150) | none in core | n/a |
| C9 | ig.ini | core now: `utilities.IniFile`; the `ini` port removed | - | switched |
| C10 | semver | `Processing.Semver`, `IgSupport.maxSatisfying` (~100) | `VersionUtilities`, `SemverParser` | keep |
| C11 | FHIR version table | `utils.FHIRVersionUtils` (69) | `VersionUtilities.isR4Ver/isR5Plus/...` | keep |
| C12 | primitive value checks | regexes in `ElementDefinition`, `PrimitiveTypes`, `utils.ValidUrl` | `regex` extensions of the primitive SDs; `Utilities.isAbsoluteUrl` | keep |
| C13 | instance checks at export | `export.InstanceExporter` and ElementDefinition checks | validator (`InstanceValidator`) | keep |
| C14 | cross-version extensions | `FHIRDefinitions` xver URL handling | `XVerExtensionManager` (services) | keep |
| C15 | R5 definitions for R4 IGs | `fhirdefs.R5DefsForR4` (7 bundled SDs) | the r5 core package, convertors | keep |
| C16 | path arithmetic | `utils.NodePath` (287) | `java.nio.file.Path`, `Utilities.path` | keep |
| C17 | FHIRPath | none (SUSHI evaluates no FHIRPath) | `FHIRPathEngine` | n/a |

## C1 Profile authoring: snapshot editing and the differential

- **What, where.** SUSHI builds a profile by cloning the parent's snapshot, applying each FSH rule to snapshot
  elements (cardinality, types, slicing, bindings, assigned values, contains, obeys, caret rules), unfolding elements
  on demand, and then computing the differential from the changed elements (`ElementDefinition.calculateDiff`).
  `fhirtypes.ElementDefinition` (3,396 lines), `StructureDefinition` (1,192), `Common` (1,916), `SliceTree`,
  `utils.PathUtils` (364); used by `export.StructureDefinitionExporter` (1,458) and the instance exporter.
- **Core instead.** `org.hl7.fhir.r5.conformance.profile.ProfileUtilities.generateSnapshot(base, derived, url,
  webUrl, profileName)` (also r4's `org.hl7.fhir.r4.conformance.ProfileUtilities`), `sortDifferential`.
- **Why not core.** Opposite direction: core turns a differential into a snapshot; SUSHI edits a snapshot and
  derives the differential. Core has no rule-application API and no differential generator, so it could replace only
  the optional `--snapshot` output, not the authoring. SUSHI's differential (what the publisher reads) is the
  product of its rule semantics, error messages and element order. Every corpus IG with a profile, extension or
  logical model hinges on it (214 of 267 define a Profile or Extension, 75 a Logical). The `--snapshot` output
  (experimental in SUSHI, off by default and not used by the publisher's SUSHI call) would differ from core's
  snapshot (SUSHI's help text says so); no corpus run uses `--snapshot`.
- **Cost of switching.** Not a switch but a rewrite of SUSHI's core; plus the r5 (or r4) model jars and a worker
  context.
- **Verdict: keep.** The one plausible core use is emitting core's snapshot for `--snapshot`; that changes that
  option's output, which is SUSHI's to decide.

## C2 Resource model: JS value model and SUSHI's classes instead of core's model

- **What, where.** `org.hl7.fhir.common.js` (`JsObject` insertion-ordered maps, `Double` numbers, `Js`/`Lodash`
  helpers, `Frozen`; 1,112 lines) and SUSHI's `fhirtypes` classes (PORTING.md D2/D3/D3a).
- **Core instead.** r4/r4b/r5 model classes (`org.hl7.fhir.r5.model.*`), or the version-neutral element model
  (`org.hl7.fhir.r5.elementmodel.Element`, `Manager`).
- **Why not core.** Output bytes follow JS object semantics: ElementDefinition/StructureDefinition keys follow
  SUSHI's fixed property lists; CodeSystem, ValueSet and ImplementationGuide keys come in the order the exporter
  assigns them (`resourceType, status, content, name, id, title, ...`); instance keys in rule order; numbers are JS
  doubles (`1.50` is written `1.5`). Core's classes serialise in canonical FHIR order and keep decimal text, so every
  CodeSystem/ValueSet/IG resource and most instances would change key order. SUSHI also works on loose JSON across
  R4-R6 (unknown elements, R4 IGs using R5 elements through cross-version handling), which typed models reject or
  drop. Every corpus IG hinges on it (byte-identical `fsh-generated/` in 259/267, the other 8 differ only by C6).
- **Cost.** r4 + r4b + r5 model jars (or the r5 element model plus a worker context per FHIR version); every output
  file reordered.
- **Verdict: keep.**

## C3 JSON reading and writing

- **What, where.** `common.js.Json` (338 lines): `JSON.parse` / `JSON.stringify(v, null, 2)` semantics (PORTING.md
  D4).
- **Core instead.** `org.hl7.fhir.utilities.json.parser.JsonParser` with `json.model.JsonObject` (already on the
  class path: no new dependency).
- **Why not core.** Measured on vectors (core 7.0.6 vs the port):

  | input | port (= JS) | core |
  |---|---|---|
  | `{"a":1.50}` | `{"a":1.5}` | `{"a":1.50}` |
  | `{"a":1e21}` / `{"a":1E-7}` | `1e+21` / `1e-7` | `1e21` / `1E-7` |
  | `{"x":12345678901234567890}` | `12345678901234567000` | `12345678901234567890` |
  | `{"a":1,"b":2,"a":3}` | `{"a":3,"b":2}` | `{"a":1,"b":2,"a":3}` (with duplicates allowed; else an error) |
  | pretty printing | `"a": [` + one value per line, 2-space indent | `"a" : [1,` + values on the bracket line |

  Core keeps number text (`JsonNumber`) where SUSHI normalises through doubles, and core's pretty printer has a
  different layout. Every output file of every corpus IG hinges on the writer; numbers in fished definitions and
  predefined resources hinge on the parser.
- **Cost.** None in dependencies; every output file changes formatting.
- **Verdict: keep.**

## C4 Definitions store and lookups (fishing)

- **What, where.** `fhirdefs` (`InMemoryPackageDB` 216, `PackageDB`, `ResourceInfo`, `FindResourceInfoOptions`,
  `BasePackageLoader` resource registration, virtual packages, `FHIRDefinitions` 320) and `utils.MasterFisher` (254),
  `FishingUtils` (125). Design: [wp8-design.md](wp8-design.md).
- **Core instead.** `org.hl7.fhir.r5.context.IWorkerContext` / `SimpleWorkerContext` with
  `CanonicalResourceManager` (`fetchResource(Class, url)`), or `NpmPackage` index lookups.
- **Why not core.** SUSHI finds a definition by id OR name OR url in one query across several types and SD
  flavours (Profile/Extension/Type/Resource/Logical, `Instance` as a wildcard); CRM is keyed by canonical URL per
  type and has no name index. Precedence: SUSHI takes the requested type order, then the most recently loaded
  package (its load order puts R5-for-R4 definitions lowest and the extensions pack last); CRM has its own rules
  (version ordering, "core wins", duplicates dropped on load). SUSHI also needs every match in order
  (`fishForMetadatas`), unconverted package JSON (a worker context converts R4 to R5 model objects, C2), and
  packages that are not packages (predefined resources, instances of logical models, R5-for-R4 definitions).
  Every corpus IG hinges on it (every parent, type and value-set reference is fished by name or id). Measured: the
  lookup probe compared 20,178 distinct lookups (414,452 calls) on the 6 reference IGs and 30 corpus IGs, all
  identical to TS.
- **Cost.** r5 model jars and a worker context; a different winner wherever precedence differs (not measured).
- **Verdict: keep.**

## C5 Package resolution and loading

Package resolution and loading are core's (`FilesystemPackageCacheManager`, `PackageClient`, `NpmPackage`, 7.0.6)
unless an IG's `fsh-generated/` or SUSHI's success or failure would differ (PORTING.md D1a). Measured with
`scripts/corpus-diff.sh` / `diff-run.sh` against TS SUSHI 3.21.1 (frozen snapshot `baseline-20261120` plus the
per-IG cache overlay, registries live).

### Kept deviation: R2, `latest` is packages.fhir.org's `dist-tags.latest`

- **Rule.** A `latest` dependency (configured, or the automatic `hl7.fhir.uv.tools.*`, `hl7.terminology.*`,
  `hl7.fhir.uv.extensions.*`, and the `hl7.fhir.uv.xver-*` dependencies SUSHI derives) resolves to the
  `dist-tags.latest` that `https://packages.fhir.org/<id>` returns. If that request fails or has no
  `dist-tags.latest`, core decides (`FilesystemPackageCacheManager.getLatestVersion(id, false)`: its package servers,
  then build.fhir.org, then the newest cached version, else core's error).
- **IG that hinges on it.** HL7__cds-hooks-library (`hl7.fhir.uv.cds-hooks: latest`). With the rule: 2.0.1, as TS.
  With core alone: 3.0.0-ballot, and `ImplementationGuide-hl7.fhir.uv.cds-hooks-library.json` differs at
  `$.dependsOn[0].version` (3.0.0-ballot vs 2.0.1); the log differs in `Resolved ... to concrete version` and
  `Loaded hl7.fhir.uv.cds-hooks#... with 5` vs `1 resources`. Exit 0/0 either way. No other corpus IG: of the 19
  `latest` dependencies in corpus configs and the 6 automatic ones, cds-hooks is the only package whose
  `dist-tags.latest` differs from its highest listed version (checked on both registries).
- **Code, test.** `fhirdefs.CorePackageProvider.resolveLatest`;
  `regress.CorpusRegressionTest.latestIsRegistryDistTagLatest` (network, opt-in `-Dsushi.network.tests=true`). Also
  used by `update-dependencies` (`Processing.defaultRegistryClient`).
- **Why core isn't used.** Core's `PackageClient.getLatestVersion(id, false)` ignores `dist-tags`: it reads the
  `versions` object and keeps the highest version by `VersionUtilities.isThisOrLater` (FULL precision), prereleases
  included. Its server order (packages2.fhir.org first, `PackageServer.PRIMARY_SERVER`) does not matter here: both
  registries list 2.0.1 and 3.0.0-ballot, so core picks 3.0.0-ballot from either.
- **Root cause is two things.** (1) core's version logic picks a prerelease for `latest`; (2) the registries
  disagree: packages.fhir.org has `dist-tags.latest = 2.0.1`, packages2.fhir.org has `3.0.0-ballot`. FPL asks
  packages.fhir.org first, so TS SUSHI gets 2.0.1 only while that server answers.
- **Fixes that would let us drop the rule.** Core: for `latest`, take the highest non-prerelease version when one
  exists (what `milestonesOnly=true` does, minus its failure when only prereleases exist); a one-line change in
  `PackageClient.getLatestVersion` or its caller, giving 2.0.1 from either server. Core reading `dist-tags.latest`
  would not be enough on its own (it asks packages2 first, whose dist-tag is 3.0.0-ballot); a registry-side fix alone
  does not help either, since core ignores dist-tags.

### Following core, measured (FPL behaviour removed from the port)

Each was a fhir-package-loader (FPL) behaviour layered on core; each was run switched off on every corpus IG that
hits it: `fsh-generated/` identical and exit codes equal; only these log lines differ.

| behaviour removed | corpus IGs that hit it | what now differs (log only) |
|---|---|---|
| `dev` without a `#dev` folder falls back to `#current` (FPL), instead of core's cached current build registered as `#dev` | hl7-eu__imaging-api (`ihe.rad.mado: dev`) | TS's info line `Falling back to ihe.rad.mado#current since ihe.rad.mado#dev is not locally cached. ...` is missing; `Loaded ihe.rad.mado#dev with 43 resources` vs `#current`. 17 files identical |
| FPL's `#current` freshness warning (a separate build.fhir.org/ig/qas.json fetch) | HL7-cz__ps (hl7.fhir.cz.lab), hl7-eu__laboratory (hl7.fhir.eu.extensions.r4), JohnMoehrke__ConsentAboutAI (hl7.fhir.uv.extensions.r4) | TS's `warn Failed to download most recent X#current from current builds. Using most recent cached package instead.` is missing, so the summary box says `0 Warnings` vs `1 Warning` (0 errors in both). 80 / 88 / 28 files identical |
| FPL's `Attempting to download X#V from <url>` lines | WorldHealthOrganization__smart-trust (smart.who.int.trust-phw#0.2.0-litlfred, a failed download) | the two `Attempting to download` info lines are missing; the error line that follows is unchanged. 765 files identical, exit 3/3 |
| FPL's `latest` failure (`Failed to resolve version for X#latest`), packages2 dist-tags fallback, `$FPL_REGISTRY` | HL7__davinci-ecdx (derived `hl7.fhir.uv.xver-r4.r4#latest`, a package no registry has) | the one error now reads `Failed to load hl7.fhir.uv.xver-r4.r4#latest: Unable to find the last version for package hl7.fhir.uv.xver-r4.r4: no local copy, and no network access` (core's text); still 1 error, exit 1/1, 5 files identical. `$FPL_REGISTRY` was only honoured for `latest` (downloads always went through core's servers); not set in any corpus run |
| FPL's throw for an unresolvable `x.y.x` (`Latest patch version of package X could not be determined ...`) | none | a failing `x.y.x` is the same single `Failed to load X#x.y.x: Failed to download ... from the registry` error as an exact version |

### Follows core; no corpus IG hinges on it

- `x.y.x` resolves to the newest matching patch in the cache before asking the registry (FPL asks the registry
  first): for all 12 distinct `x.y.x` dependencies in the corpus, the snapshot's newest patch equals
  packages.fhir.org's.
- `latest` / `x.y.x` offline use the newest cached version (FPL fails): the corpus runs are online; not exercised.
- `x.x` and two-part `x.y` versions resolve the core way (FPL errors / fails): no corpus config uses either.
- A package's resources are core's `.index.json` list (FPL scans `package/*.json`): every `Loaded X with N
  resources` line matched TS on all 267 corpus IGs.
- Server order (X2; owner decision 2026-11-23: follow core, no rule and no IG hinges on it): core downloads from packages2.fhir.org first, and its `hl7.fhir.r4.core#4.0.1` tarball has 4,578
  resources (no `structuredefinition-{json,xml,rdf}-type` extensions, no `package/openapi` and `package/other`
  folders) vs 4,581 in packages.fhir.org's original 2019 tarball, which FPL asks first. Only on a cold cache, a
  log-line count difference; no corpus IG refers to the 3 extensions; a cache the IG Publisher filled already has the
  packages2 tarball. Same root as R2's registry disagreement; a core option to ask packages.fhir.org first (or the
  registries serving the same tarball) would remove both. (Core 7.0.6 has no builder setter for the order, but
  `FilesystemPackageCacheManager.Builder.getDefaultServers()` is protected and overridable.)

### Log-only differences that remain

- hl7-eu__imaging-api: no `Falling back to ihe.rad.mado#current ...` line; `Loaded ihe.rad.mado#dev` instead of
  `#current`.
- HL7-cz__ps, hl7-eu__laboratory, JohnMoehrke__ConsentAboutAI: no `Failed to download most recent X#current ...
  Using most recent cached package instead.` warning (summary box 0 vs 1 warnings).
- WorldHealthOrganization__smart-trust: no `Attempting to download smart.who.int.trust-phw#0.2.0-litlfred from ...`
  lines (2).
- HL7__davinci-ecdx: the `latest` error's text is core's (`Unable to find the last version for package ...`), not
  FPL's (`Failed to resolve version for ...`).
- Cold cache only (not in the corpus harness): no `Attempting to download` line before a download;
  `hl7.fhir.r4.core#4.0.1 with 4578` vs `4581 resources`.

How lookups were compared: `scripts/probe/` records every package-layer call TS SUSHI makes (FPL's
`BasePackageLoader` wrapped) and replays it against the Java `FHIRDefinitions` (`ProbeReplay`); see C4 for the
totals.

## C6 Predefined FHIR XML to JSON

Owner decision (2026-11-22): the port reads predefined FHIR XML with org.hl7.fhir.core, not with a port of
fhir-tool. It is the one place where the port's `fsh-generated/` knowingly differs from TypeScript SUSHI's on real
IGs (8 of 267 corpus IGs, in the ImplementationGuide resource and link references only; below).

**Now.** `fhirdefs.FhirXml` (80 lines): core's `XmlParser` then `JsonParser` for the IG's FHIR version
(`org.hl7.fhir.r4.formats` for 4.0.x, `r4b` for 4.3.x, `r5` for 5.x and for 6.0.0 prereleases, since core 7.0.6 has
no R6 model). If the strict parse fails, it is repeated with core's `allowUnknownContent` (elements the model does
not allow at that place are skipped); a file read that way is used, with `warn Predefined XML file <path> was read
only partially: the FHIR R4 XML parser skipped content it does not allow there (<core's message>)`. A file even the
lenient parse cannot read (not FHIR, no FHIR namespace, a type or code the model does not have) is skipped with
`warn Skipped predefined XML file <path>: the FHIR R4 XML parser could not read it (<core's message>)`. A legacy `ig.xml` (configuration from an ImplementationGuide when there
is no `sushi-config.yaml`) is read the same way, trying R4 then R5. Spreadsheet XML is skipped as before. Cost:
`org.hl7.fhir.r4`, `r4b`, `r5` 7.0.6 (r5 without jgit, nimbus-jose-jwt, plantuml-mit) and xpp3 1.1.4c.0;
`sushi.jar` grew from 41 MB to 78 MB; a run that reads XML takes about 3 s longer (class loading; ch-term 24.6 s ->
27.8 s, TypeScript SUSHI 94.8 s). Removed: the port of fhir-tool 5.0.2's `xmlToObj` with xml-js 1.6.11 and sax
1.2.4 and fhir-tool's R4 type data (`fhirdefs.xml`, 1,602 lines + 36 KB), its tests and oracle scripts.

### Support status

SUSHI's own code never reads a predefined XML resource. `predefinedResources.ts` only lists folders (the standard
`input/` resource folders and `path-resource`); fhir-package-loader 2.2.4's `DiskBasedVirtualPackage` reads every
`.json` and `.xml` file in them, and converts XML with fhir-tool's `xmlToObj` (fhir-tool is a dependency of SUSHI's
package.json for this and for `loadConfigurationFromIgResource.ts`, the one place SUSHI calls it itself, to read an
ImplementationGuide when there is no `sushi-config.yaml`). So XML predefined resources work in TypeScript SUSHI as a
side effect of the package loader, not as a documented SUSHI input; per the FHIR core maintainers they are not
officially supported. fhir-tool converts with its own R4 type data for every FHIR version and silently skips
resource types it does not know.

### Which IGs use XML predefined content, and for what

Measured (`scripts/corexml/track.sh`: each corpus IG run once with harness-only copies of `Frozen` and
`FHIRDefinitions` that record every property read on a resource that came from an XML file, and every metadata lookup
that resolves to one, with the SUSHI code that asked). 39 of the 267 corpus FSH IGs have `.xml` files under `input/`:
27 in the standard predefined-resource folders (798 files), 1 in a `path-resource` folder (scc88 apacepi, 2 files),
1 a legacy ImplementationGuide XML read for its configuration (HL7 termchangeset-ig, which has no
`sushi-config.yaml`); the other 10 have XML only where SUSHI does not look (attachments, image sources, an `ig.xml`
next to a `sushi-config.yaml`).

What SUSHI does with an XML resource, in the 28 IGs with predefined XML (census run on the 27; scc88's 2 files are
not read by TS SUSHI at all):

- **IG resource listing** (`IGExporter.addPredefinedResources`): for every predefined resource it copies the
  resource, then writes an `ImplementationGuide.definition.resource` entry from `resourceType`/`id`, `title` or
  `name`, `description`, `meta.profile` (example canonical) and the folder (`isExample`). This feeds
  `ImplementationGuide-<id>.json` and `fsh-link-references.md`. Skipped when the IG has `FSHOnly: true`.
- **Duplicate check** (`Processing.writeFHIRResources`): `url`, `resourceType`, `id`, so SUSHI does not write a FSH
  resource that a predefined one already defines. Identity only.
- **Identity lookups** by name/id/url, resolved to an XML resource, in 5 IGs: a ValueSet's `include.system` from an
  XML CodeSystem (ch-term `edqm-standardterms`; smart-anc 4 code systems), a binding to an XML ValueSet (Nictiz
  AZ-IG; smart-anc `anc-persona`), `Reference`/canonical replacement in instances (smart-anc), type lineage of an XML
  profile or logical model (ch-epr-fhir 2 profiles; sql-on-fhir `ViewDefinition`). Only `url`, `id`, `name`, type
  are used.
- **Definitions** (an XML profile as a parent or `InstanceOf`, codes checked against an XML CodeSystem, an XML
  ValueSet's content, an XML SD's elements): **none** in the corpus. No IG reads anything from an XML resource beyond
  the listing fields and identity.

So the classes (TS SUSHI): **(a) XML content feeds output**: 24 IGs, all through the IG resource listing only, 5 of
them also through identity lookups; **(b) identity lookups only**: 1 (HL7 uv-pocd, `FSHOnly: true`: duplicate check,
no listing); **(c) never touched**: 3 (HL7 ccda-on-fhir and ansforge interop-IG-cda-document-core, whose XML files are
CDA documents, and scc88 apacepi, whose 2 R5 Bundles fhir-tool cannot convert). "Read by TS" and "read now" count
the XML files registered as predefined resources.

| IG | FHIR | XML files | read by TS | read now | IG listing entries (TS) | identity lookups | class |
|---|---|---:|---:|---:|---:|---|---|
| ahdis ch-elm | 4.0.1 | 55 | 55 | 55 | 55 | - | a |
| ansforge interop-IG-cda-document-core | 5.0.0 | 1 | 0 | 0 | - | - | c |
| ehealthsuisse ch-epr-fhir | 4.0.1 | 48 | 48 | 48 | 48 | 2 profiles (type lineage of a FSH element) | a |
| HL7 api-incubator-ig | 6.0.0-snapshot1 | 17 | 17 | 17 | 17 | - | a |
| hl7au au-fhir-erequesting | 4.0.1 | 48 | 48 | 48 | 48 | - | a |
| hl7-be lab | 4.0.1 | 1 | 1 | 1 | 1 | - | a |
| hl7-be mycarenet | 4.0.1 | 32 | 32 | 32 | 32 | - | a |
| HL7 ccda-on-fhir | 4.0.1 | 12 | 0 | 0 | - | - | c |
| HL7 cds-hooks | 4.0.1 | 6 | 6 | 6 | 6 | - | a |
| HL7 cg-incubator | 6.0.0-snapshot1 | 192 | 119 | 124 | 119 | - | a |
| hl7ch ch-core | 4.0.1 | 1 | 1 | 1 | 1 | - | a |
| hl7ch ch-term | 4.0.1 | 93 | 93 | 93 | 93 | CodeSystem edqm-standardterms (ValueSet `include.system`) | a |
| HL7 crmi-ig | 4.0.1 | 6 | 6 | 6 | 6 | - | a |
| HL7 davinci-ra | 4.0.1 | 22 | 22 | 22 | 22 | - | a |
| HL7 ebm-incubator | 6.0.0-snapshot1 | 9 | 6 | 6 | 6 | - | a |
| HL7 emedicinal-product-info | 5.0.0 | 4 | 1 | 4 | 1 | - | a |
| HL7 fhir-sdoh-clinicalcare | 4.0.1 | 2 | 2 | 2 | 2 | - | a |
| HL7 immunization-incubator | 6.0.0-snapshot1 | 17 | 15 | 15 | 15 | - | a |
| hl7-it cda2fhir | 4.0.1 | 11 | 11 | 11 | 11 | - | a |
| HL7 oo-incubator | 6.0.0-snapshot1 | 102 | 96 | 102 | 96 | - | a |
| HL7 sql-on-fhir | 6.0.0-snapshot1 | 5 | 5 | 4 | 5 | StructureDefinition ViewDefinition (type lineage, value check) | a |
| HL7 uv-pocd | 4.0.1 | 60 | 60 | 60 | - (FSHOnly) | - | b |
| litlfred ddcc | 4.0.1 | 2 | 2 | 2 | 2 | - | a |
| Nictiz AZ-IG | 4.0.1 | 4 | 4 | 4 | 4 | ValueSet (binding) | a |
| Nictiz GBB-IG | 4.0.1 | 15 | 15 | 15 | 15 | - | a |
| Nictiz NL-PS-IG | 4.0.1 | 1 | 1 | 1 | 1 | - | a |
| WHO smart-anc | 4.0.1 | 32 | 32 | 31 (3 partially) | 32 | 4 CodeSystems (ValueSet `include.system`, binding, `Reference`/canonical in instances) | a |
| scc88 apacepi | 5.0.0 | 2 | 0 | 2 | - | - | c |

### Core's conversion vs fhir-tool's, file by file

Before the switch, every one of the 798 files was converted both ways (the port's fhir-tool conversion, which is
byte-identical to TypeScript SUSHI's, and core's for the IG's version: r4/r5 classic parsers, R6 through r5's element
model with the hl7.fhir.r6.core definitions) and the two JSONs compared as FHIR content (key order ignored, numbers by
value, xhtml canonicalised, empty and absent alike). Harness: commit 48f46f61 (`scripts/corexml/run.sh`,
`compare.py`, removed with the port).

| result | files | which side is right |
|---|---:|---|
| equivalent | 493 | - |
| equivalent except representation: XML comments as `fhir_comments` (103 files), a decimal as a JSON string (26), xhtml whitespace (22), whitespace in a string attribute (22) | 151 | core: `fhir_comments` is not FHIR JSON since STU3; FHIR JSON decimals are numbers; xml-js drops whitespace-only text in the div; XML 1.0 3.3.3 normalises a literal newline/tab in an attribute value to a space |
| both fail: CDA documents (13), R6 incubator types neither knows, e.g. MolecularDefinition (84) | 97 | - |
| fhir-tool drops content: R5/R6 elements unknown to its R4 type data (`Encounter.class`, `ServiceRequest.code`, `binding.additional`, `processingMode`, `Device.name`, ...) | 39 | core |
| fhir-tool uses R4 cardinality in an R5/R6 resource (`Device.type`, `Bundle.entry.resource.identifier`) | 3 | core |
| fhir-tool fails, core reads: R5 Bundles containing MedicinalProductDefinition (emedicinal-product-info) | 3 | core |
| core fails, fhir-tool reads: R6 IGs' resources that R6 snapshot1 moved out of core (GraphDefinition, ImmunizationEvaluation/Recommendation, SupplyDelivery/Request; 11, all read by r5's parser, which the port now uses for R6), CarePlan/Encounter with elements out of order (3) and a QuestionnaireResponse without the FHIR namespace (1) in smart-anc | 15 | neither, then core for the 11; the 4 smart-anc files are invalid FHIR XML, which fhir-tool accepts because it ignores order and namespaces |

For R6 IGs the port uses r5's classic parser: of the 342 R6 files it reads 268 (the element model with the R6 core
package read 247); 239 of the files both read give the same JSON.

### What changes against TypeScript SUSHI

Gates (after the switch, against TS SUSHI 3.21.1 with the frozen package snapshot, `scripts/diff-run.sh` /
`corpus-diff.sh`): the 6 reference IGs, and every corpus IG with XML under `input/` (39). The other 228 corpus IGs
have no `.xml` file under `input/` (outside `pagecontent`, `includes` and `images`), so the change cannot reach
them (their earlier results stand). Exit codes equal
everywhere.

- **Reference IGs: 4 / 6 identical** (genomics-reporting, ITI.MHD, EEHRxF logical model, ch-ekm). ch-term and
  smart-anc differ, as below.
- **Corpus: `fsh-generated/` identical in 259 / 267**; logs identical in 250 / 267 (the 6 package-loading IGs of C5,
  and 11 here). No profile, extension, logical model, instance, value set or code system changes in any IG; every
  `fsh-generated/` difference is in `ImplementationGuide-<id>.json` (`definition.resource`) and
  `includes/fsh-link-references.md`.

| IG | `fsh-generated/` vs TS | log vs TS | why |
|---|---|---|---|
| hl7ch ch-term | 3 `definition.resource[].description` (NamingSystem ber, uidb, zsr) | same | the descriptions are XML attributes with literal line breaks: TS keeps `\n` and the indentation, core turns each line break into a space (XML attribute-value normalisation, XML 1.0 3.3.3) |
| WHO smart-anc | 1 `definition.resource[72].description` (CodeSystem anc-persona) | 3 "read only partially" warnings, 1 "Skipped" warning; `sushi-local#LOCAL` 74 vs 75 resources; summary 17 vs 13 warnings | the description: as ch-term (`...the personas\nwill vary...` vs `...the personas will vary...`). The warnings: 3 examples with elements that R4 does not allow at that place (`code` and `description` in two CarePlans, `role` in an Encounter's diagnosis) are read without those elements; 1 QuestionnaireResponse without the FHIR namespace is not FHIR XML and is skipped. None of the 4 is listed in the IG resource (TS already reports them as nested too deep) or used otherwise (census) |
| HL7 api-incubator-ig | `definition.resource[4].name` of GraphDefinition/example: `DocumentGenerationTemplate` (TS) vs `Document Generation Template` | same | the R6 GraphDefinition's `title`: fhir-tool's R4 GraphDefinition has no `title`, so TS falls back to `name` |
| HL7 cg-incubator | 1 entry gains `description` `[markdown]` (Group, R5 element fhir-tool drops); 5 GenomicStudy entries gain `isExample: false`; 1 link reference uses the title | 68 "Skipped" (MolecularDefinition, not in R5); 124 vs 119 resources; 69 vs 1 warnings | TS cannot convert GenomicStudy (an R5 type), so those 5 entries came from the configuration alone; core reads them. MolecularDefinition is unknown to both (TS skips silently) |
| HL7 emedicinal-product-info | key order of 3 entries; 3 link references use the Bundle id instead of the configured name | 4 vs 1 resources | TS cannot convert the 3 ePI Bundles (MedicinalProductDefinition, R5), so their entries came from the configuration; core reads them |
| HL7 oo-incubator | key order of 6 entries; 6 link references | 102 vs 96 resources | 6 examples of R5 types (BiologicallyDerivedProductDispense, DeviceDispense, DeviceUsage, InventoryItem, InventoryReport, Transport) that TS cannot convert are now read |
| HL7 sql-on-fhir | entry Bundle/ViewDefinition-search-params missing; its link reference | 1 "Skipped"; 14 vs 15 resources | the Bundle's SearchParameters have `base` = ViewDefinition, which r5's enumeration does not have (an R6 IG read with the R5 model); TS's R4 conversion keeps the code as a string |
| scc88 apacepi | 2 entries added (Bundle paxlovid-sgp-pil-epi, -smpc-epi), 2 link references | 2 vs 0 resources | 2 R5 ePI Bundles in a `path-resource` folder, which TS cannot convert (it skips them silently) |
| HL7 ccda-on-fhir | same | 12 "Skipped" (CDA documents); 14 vs 2 warnings | TS skips them silently |
| ansforge interop-IG-cda-document-core | same | 1 "Skipped" (CDA); 1 vs 0 warnings | as ccda-on-fhir |
| HL7 ebm-incubator | same | 3 "Skipped" (PublicationRecord, not in R5) | TS skips them silently |
| HL7 immunization-incubator | same | 2 "Skipped" (`mappingExceptions` files, not FHIR) | TS skips them silently |
| HL7 termchangeset-ig | same | the extracted configuration's 2 `parameters` lines lose `fhir_comments` | legacy ImplementationGuide XML read for the configuration: core drops XML comments |

The other 26 corpus IGs with XML: `fsh-generated/`, log and exit status identical (ahdis ch-elm, ehealthsuisse
ch-epr-fhir, hl7au au-fhir-erequesting, hl7-be lab, hl7-be mycarenet, HL7 cds-hooks, hl7ch ch-core, HL7 crmi-ig, HL7
davinci-ra, HL7 fhir-sdoh-clinicalcare, hl7-it cda2fhir, HL7 uv-pocd, litlfred ddcc, Nictiz AZ-IG, GBB-IG, NL-PS-IG,
and the 10 whose XML SUSHI does not read; HL7 davinci-ecdx differs only by its C5 log line). Their XML differs from
fhir-tool's conversion only in representation (comments, decimals, whitespace) or in fields SUSHI does not read.

### Options and their consequences

1. **Keep the fhir-tool port** (the state before this change): identical to TypeScript SUSHI on every corpus IG;
   1,602 lines of ported JS (fhir-tool, xml-js, sax 1.2.4) plus R4 type data to maintain, with fhir-tool's errors:
   R5/R6 content silently dropped or given R4 shapes, invalid XML accepted, comments and decimal strings kept.
2. **Core's conversion (chosen)**: the changes listed above, all in the ImplementationGuide resource listing,
   `fsh-link-references.md` and warnings; no IG's profiles, extensions, instances, value sets or code systems
   change, because no corpus IG uses XML content for definitions. +37 MB jar.
3. **Stop reading XML** (warn and skip every predefined XML file): no new dependencies, and the fhir-tool port goes
   too. All 24 (a) IGs lose the IG-resource entries of their XML resources (798 files' worth of
   `definition.resource` entries, e.g. 93 in ch-term, 119 in cg-incubator) and their link references; the 5 IGs with
   identity lookups get errors where a ValueSet, binding or reference names an XML resource (ch-term
   `edqm-standardterms`; smart-anc 4 code systems; Nictiz AZ-IG's bound ValueSet; ch-epr-fhir's 2 profiles; sql-on-fhir
   `ViewDefinition`) no longer resolve, which would change their value sets, profiles and instances (expected from the
   census; not run). The IG Publisher would still read the
   XML files itself, but SUSHI's IG resource would not list them.

## C7 xhtml: html-minifier-terser and sax

- **What, where.** `xhtml` (1,947 lines): html-minifier-terser 5.1.1 (`HtmlMinifier`, `HtmlParser`) as SUSHI calls
  it (`collapseWhitespace`, `keepClosingSlash`, `html5: false`), and sax 1.6.1 strict mode as SUSHI's xhtml
  well-formedness check. Used when an xhtml value is assigned (`ElementDefinition.assignString` ->
  `fixedXhtml`/`patternXhtml`, and instance `text.div`).
- **Core instead.** `org.hl7.fhir.utilities.xhtml.XhtmlParser` (parse, with its own entity and validity rules) and
  `XhtmlComposer` (serialise); already on the class path.
- **Why not core.** The assigned div that SUSHI writes is the minifier's text: whitespace collapsed by its inline and
  block rules, original attribute quoting, entities left as written, `<br/>` kept. `XhtmlComposer` re-serialises the
  parsed tree its own way (entity and quote escaping, whitespace), so the written div text changes; and
  `XhtmlParser`'s accept/reject differs from sax strict mode (HTML entities such as `&nbsp;`, which strict sax
  rejects as undefined). The 82 corpus IGs that assign xhtml hinge on it (WP-9's differential compared every
  `text.div`/xhtml value in them with the JS library: identical).
- **Cost.** None in dependencies; the text of every assigned div changes.
- **Verdict: keep.**

## C8 YAML

- **What, where.** The libyaml 0.2.5 event layer in `common` (`org.hl7.fhir.common.yaml`, shared with the Jekyll
  shim) and the yaml@1.10 composer `yaml.Yaml` (669 lines): `sushi-config.yaml`.
- **Core instead.** Nothing: core has no YAML parser (none of its modules depends on one).
- **Verdict: n/a** (nothing to switch to). The remaining difference from TS (error wording) is T1.

## C9 ig.ini (switched to core)

- **Now.** `IGExporter.processIgIni` reads the IG's `ig.ini` with `org.hl7.fhir.utilities.IniFile` (from the file's
  contents, read through `Processing.readFile`) and checks it as before: an `[IG]` section, `ig` pointing at SUSHI's
  IG resource, `template` present, deprecated properties. `generateIgIniString` (only shown in an error message: a
  `template` in `sushi-config.yaml` without an `ig.ini`) writes the fixed `[IG]` / `ig = ...` / `template = ...` text.
  The port of npm `ini` 5.0.0 (`ig.Ini`, 204 lines) and SUSHI's escaping of `#` in `template` before parsing are gone.
- **How the parsers differ.** `ini` treats `#` and `;` as comment starts, also after a value
  (`template = fhir.base.template#current` reads as `fhir.base.template` unless the `#` is escaped, which SUSHI did
  for `template`), JSON-parses quoted values and supports `key[]` arrays; `IniFile` treats only `;` lines as
  comments (`# ...` lines become properties), keeps `#` in values, and gives a key without `=` the value `""`
  (`ini`: `true`). On all 267 corpus `ig.ini` files the raw key/value maps differ in 234, but the checks SUSHI makes
  (`[IG]` present, `ig` value, `template` present, the deprecated properties found) are the same in **267/267**, so
  no IG's output or messages change; the 6 reference IGs were rerun after the switch with the same result as before.
  The ported `IGExporter` ig.ini tests pass unchanged.
- **Known edge (no corpus IG).** `IniFile` drops the whole file when a property line comes before the first section
  (it fails on the missing section and clears what it read), so such an `ig.ini` gets "The ig.ini file must contain
  an [IG] section." where TS finds the section. A quoted value or a `key[]` in one of the checked keys reads
  differently too.
- **Verdict: switched.**

## C10 semver

- **What, where.** `utils.Processing.Semver.compareLoose` (sorts several versions of one dependency package) and
  `ig.IgSupport.maxSatisfying` (picks the loaded version for an `x.y.x` dependency in `dependsOn`), npm semver 7
  semantics, ~100 lines.
- **Core instead.** `org.hl7.fhir.utilities.VersionUtilities` (`compareVersions`, `versionMatches`,
  `isThisOrLater`) and `SemverParser`.
- **Why not core.** Different ordering and syntax, measured on vectors: `1.0.0-alpha.10` vs `1.0.0-alpha.2` is
  greater in semver (numeric identifiers compare numerically), less in `VersionUtilities.compareVersions`; loose
  semver accepts `v1.2.3`, core throws `Invalid ver1 version`; core also throws on `current`. Corpus: 20 IGs use an
  `x.y.x` dependency, each loads one version, so `maxSatisfying` has one candidate; the sort needs the same package
  listed twice. None observed.
- **Cost.** None in dependencies; small risk on prerelease ordering.
- **Verdict: keep** (tiny, and core's prerelease ordering is not semver's).

## C11 FHIR version table

- **What, where.** `utils.FHIRVersionUtils` (69 lines): SUSHI's regex table from a `fhirVersion` string to
  (R4/R4B/R5/R6/..., core package id, prerelease, supported). Decides which core package is loaded and SUSHI's
  messages for unsupported versions.
- **Core instead.** `VersionUtilities.isR4Ver/isR4BVer/isR5Ver/isR6Ver`, `getCurrentPackageVersion`,
  `packageForVersion`.
- **Why not core.** The table is SUSHI's decision of what it supports, and its edges are SUSHI's: `4.1.0` and
  `4.3.0-snapshot1` are R4B, `dev`/`current` mean R5, `3.x.y` prereleases count as an unsupported R4. Core's
  helpers answer "which release is this" and throw or return false on `current`. All corpus IGs pass through it
  (4.0.1, 4.3.0, 5.0.0, 6.0.0-snapshot1 in the corpus); the corpus versions would map the same either way, the edges
  would not.
- **Verdict: keep** (69 lines of SUSHI policy, not a reimplementation).

## C12 Primitive value checks

- **What, where.** When FSH assigns a string, SUSHI checks it against fixed regexes for `instant`, `date`,
  `dateTime`, `time`, `oid`, `id`, `integer64`, base64 and no-whitespace URIs (`ElementDefinition.assignString`,
  `fhirtypes.PrimitiveTypes`), and URI-ness of value-set, code-system and context references with the npm
  `valid-url` rules (`utils.ValidUrl`, 47 lines).
- **Core instead.** The `http://hl7.org/fhir/StructureDefinition/regex` extensions on each FHIR version's
  primitive type definitions (what the validator uses), `Utilities.isAbsoluteUrl`, `Utilities.isURL`.
- **Why not core.** The accept/reject decides SUSHI's `MismatchedTypeError`/`InvalidUriError` messages. The regexes
  are SUSHI's R4 regexes for every FHIR version. `valid-url` vs `Utilities.isAbsoluteUrl`, measured on vectors:
  `Http://Example.org` and `x-y:z` are URIs for valid-url, not for core; `http://hl7.org/fhir/ValueSet/x|4.0.1`
  and `http://example.org/ä` are not URIs for valid-url (SUSHI splits on `|` before asking), but are for core.
  Not measured on the corpus.
- **Verdict: keep** (message parity; small).

## C13 Instance checks at export

- **What, where.** `export.InstanceExporter` (1,158 lines) checks what it can while building an instance (required
  elements, cardinality, fixed values, types, references) and logs SUSHI's messages.
- **Core instead.** The validator's `InstanceValidator` (org.hl7.fhir.validation), which the IG Publisher runs on
  the result anyway.
- **Why not core.** The messages and the exit status are SUSHI's (error count = exit status). The validator reports
  much more, in its own words, and needs the validation stack.
- **Verdict: keep.**

## C14 Cross-version extensions

- **What, where.** `FHIRDefinitions` recognises `http://hl7.org/fhir/<version>/StructureDefinition/extension-...`
  URLs, rewrites them to the xver packages' form and reports a missing `hl7.fhir.uv.xver-*` dependency.
- **Core instead.** `XVerExtensionManager` (`org.hl7.fhir.services.xver`, r5 context), which synthesises
  cross-version extension definitions on the fly.
- **Why not core.** SUSHI 3.21.1 resolves them only through the xver packages and fails otherwise; core would make
  definitions appear that TS SUSHI reports as missing. IGs using cross-version extensions hinge on it (not counted).
- **Verdict: keep.**

## C15 R5 definitions for R4 IGs

- **What, where.** `fhirdefs.R5DefsForR4` (53 lines) bundles 7 R5 StructureDefinitions (Base, DataType,
  CodeableReference, ActorDefinition, Requirements, SubscriptionTopic, TestPlan) that SUSHI makes available to R4
  and R4B IGs, as the lowest-priority package.
- **Core instead.** Loading `hl7.fhir.r5.core` (or converting with `org.hl7.fhir.convertors`).
- **Why not core.** SUSHI exposes exactly these 7, at the lowest precedence (C4); loading the R5 core package would
  make every R5 definition fishable in an R4 IG.
- **Verdict: keep** (content, not code).

## C16 Path arithmetic

- **What, where.** `utils.NodePath` (287 lines): Node's `path.posix` (`join`, `resolve`, `relative`, `dirname`,
  ...), on Windows also reading `\` and drive roots.
- **Core instead.** `java.nio.file.Path`, `Utilities.path(...)`.
- **Why not core.** Paths appear in SUSHI's messages and in `fsh-index.txt`/`fsh-index.json` exactly as Node
  writes them (`/`, no trailing separator, `..` handling); `Path` normalises differently and uses `\` on Windows.
  Every corpus IG hinges on it (`fsh-index.txt`). See T6 for Windows.
- **Verdict: keep.**

## C17 FHIRPath

SUSHI evaluates no FHIRPath: invariant expressions and extension contexts are written as given. Nothing to compare.

## Other differences from TypeScript SUSHI 3.21.1

None of these changes `fsh-generated/`, on the corpus or (since T6's index-path fix) on Windows.

- **T1 Malformed `sushi-config.yaml`: YAML error wording.** All 266 corpus configurations import identically. On
  1,035 malformed or unusual configurations (`MutationConfigTest`): the same accept/reject decision, configuration
  and exit status everywhere; 44 YAML error messages are worded differently (tab-indented lines 23, mis-indented
  continuation lines 12, other 9; for example TS `All collection items must start at the same column`, Java `Plain
  value cannot start with a tab character`). yaml@1.10 reports the first error of its own CST pass, which depends on
  how it regroups the following lines; libyaml stops at its first error. Exact wording would need a port of
  yaml@1.10's CST parser (~1,500 JS lines). Owner decision (2026-11-22): accepted; revisit if users report it.
- **T2 JS TypeError crash paths.** Where TS crashes with a JS TypeError on unexpected input, the port reproduces it at
  the sites the differential found, with V8's text (corpus WHO smart-trust: `Cannot read properties of undefined
  (reading 'sdType')`); elsewhere Java fails with its own message.
- **T3 Latest-SUSHI-version check off by default; `--require-latest` checks the port's releases** (PORTING.md D7).
  Without the check the summary box has no version block, as in TS when the running release is the latest.
  `-Dsushi.versionCheck=true` / `SUSHI_VERSION_CHECK=true` enables it, with TS's source and messages: the port runs
  no `npm view`, so its first source fails (`Unable to determine the latest version of sushi: npm is not used by the
  Java port of SUSHI`, info) and the npm registry (https://registry.npmjs.org/fsh-sushi) is asked.
  `--require-latest` (which the IG Publisher passes on the ci-build) does not ask npm: TS SUSHI fails once npm lists a
  newer `fsh-sushi`, which for the port would mean every autobuilder build fails on the day TS SUSHI releases. The
  port's releases are fhir-ig-tools releases (each has a `sushi.jar` porting one SUSHI version), so it checks that
  this `sushi.jar` is from the latest one: `GET https://api.github.com/repos/jmandel/fhir-ig-tools/releases/latest`
  through `ManagedWebAccess` (`-Dsushi.releasesUrl=` overrides it), comparing its `tag_name` (without `v`) with the
  release the jar was built as (`org/hl7/fhir/sushi/release.properties`, `-Dsushi.release=` from the tag in
  `release.yml`; else the pom version). Outcomes (`Processing.checkLatestPortRelease`, `App`):
  - this release or newer: passes; the summary has no version block.
  - an older release: `Current SUSHI version (SUSHI 3.21.1 (Java port, fhir-ig-tools release 0.2.0)) is not the
    latest version: the latest fhir-ig-tools release is 0.3.0. Upgrade to the latest release
    (https://github.com/jmandel/fhir-ig-tools/releases/latest: sushi.jar, or the sushi wrapper, which pins its
    version) or run SUSHI again without the --require-latest flag.`, exit 1.
  - a development build (`-SNAPSHOT`, or no recorded release): the same error, `this sushi.jar is a development build,
    not a release;`.
  - the latest release cannot be determined (no network, GitHub's rate limit, no `tag_name`): `... could not be
    checked: the latest fhir-ig-tools release is undetermined (<url>: <reason>).`, exit 1, as TS fails when npm
    can't tell it the latest version.
  - the latest release has no `sushi.jar` asset (v0.1.0 was Jekyll only): not counted as newer; passes.
  Tests: `ProcessingTest$CheckLatestPortRelease` (8), `AppRequireLatestTest` (3), fetch stubbed.
- **T4 `sushi update-dependencies`.** TS re-serialises the whole `sushi-config.yaml` with the yaml library (its
  prompt warns about formatting); the port edits only the version scalars of the top-level `dependencies` block and
  keeps every other byte. Same resulting configuration.
- **T5 Page order (`localeCompare`).** TS sorts unprefixed page file names with ICU collation; the port approximates
  ICU root collation (punctuation, digits, letters in CLDR order; case and accents secondary/tertiary). Identical on
  every reference and corpus IG.
- **T6 Paths on Windows.** `utils.NodePath` is Node's `path.posix`; on Windows it also reads backslashes and drive
  roots, so Windows paths work. The FSH file paths in `fsh-generated/fsh-index.txt` and `fsh-generated/data/fsh-index.json`
  use `\` on Windows, as TS's `path.relative` (`path.win32`) prints them (`NodePath.platformSeparators`; test
  `ProcessingTest$WriteFSHIndex.usesWindowsSeparatorsOnWindows`), so `fsh-generated/` is the same as TS's on Windows
  too. Paths in console messages still use `/` where TS on Windows prints `\` (log only). Linux and macOS unchanged.

Minor (no observable output effect, or diagnostics only): `ElementDefinition.clone()` re-fetches the cached
StructureDefinition instead of deep-copying it; MasterFisher de-duplication treats a missing key like an undefined
one; the "non-conformant resource" box is coloured only when colour is on (TTY/`FORCE_COLOR`), as chalk;
`--log-level debug` stack traces are Java's; the `applyDeferredRules` TypeError branch surfaces as
`Common.JsTypeError`; 42 malformed-config inputs with a wrongly typed raw YAML value (e.g. `title: 2023`) give the
same output but the typed `Configuration` cannot hold the raw value; `sushi init` ships its template as the yaml
library re-serialises it; the deprecated `history` error prints the package-list JSON in importConfiguration's key
order; and (before C9's switch to core) `ini` quirks such as a quoted `'5'` becoming its string form.
