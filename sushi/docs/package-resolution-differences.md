# Package resolution: org.hl7.fhir.core vs fhir-package-loader (SUSHI 3.21.1)

Owner decision D1a: the Java port resolves and loads packages with org.hl7.fhir.core 7.0.6 **as is**
(`FilesystemPackageCacheManager`, `PackageClient`, `NpmPackage`), not with a port of fhir-package-loader 2.2.4 (FPL).
This document lists every case where the two pick a different definition, measured, and what was done about it.
Design and the split between core and our code: [wp8-design.md](wp8-design.md).

## Method

A probe records, for one IG, everything SUSHI's package layer is asked and answers:

- TS: `scripts/probe/probe.js`, preloaded into TypeScript SUSHI 3.21.1 (`node -r probe.js`), wraps FPL's
  `BasePackageLoader`: every `loadPackage`/`loadVirtualPackage` (name, version, status), the dependency-loading and
  predefined-resource inputs, and every package-DB lookup (`findResourceInfo`/`findResourceInfos` with key, types,
  scope, sort, limit; plus `findPackageInfo(s)`/`findPackageJSON`), deduplicated with call counts. The result rows
  are `package#version, resourceType, id, url, version, sdFlavor, file`. The whole SUSHI run happens (all exporters),
  so these are exactly the lookups SUSHI makes.
- Java: `ProbeReplay` (test scope) loads the same configuration through the Java `Processing.loadExternalDependencies`
  and `FHIRDefinitions` on core, loads predefined resources from the same folders, replays every recorded lookup,
  and writes the same rows. `scripts/probe/run-java-probe.sh` runs it with the same frozen package snapshot
  (`baseline-20261120`, overlay per IG) and network access as the TS run (so `latest`/`current` resolve live in both).
- `scripts/probe/compare.py` compares: loaded packages (order, versions, status), the package-loading log lines
  (`Resolved ...`, `Loaded X#Y with N resources`, `Failed ...`, `Found N non-JSON ...`), and every lookup's result
  (winner and the full ordered match list).

## Results

### Reference IGs (6)

| IG | packages loaded (TS = Java) | loading log lines | unique lookups | identical | calls | differing |
|---|---|---|---:|---:|---:|---|
| IHE ITI.MHD | yes (8) | identical | 685 | 685 | 8,031 | — |
| HL7 genomics-reporting | yes | identical | 1,238 | 1,238 | 40,835 | — |
| EEHRxF logical-model (R5) | yes | identical | 271 | 271 | 13,332 | — |
| ahdis ch-ekm (3 `x.y.x` deps) | yes | identical | 859 | 859 | 9,144 | — |
| hl7ch ch-term (93 predefined XML files) | yes | identical | 95 | 95 | 65,734 | — |
| WHO smart-anc (32 predefined XML files) | yes | identical | 2,349 | 2,349 | 44,983 | — |
| **total** | **6/6** | **6/6** | **5,497** | **5,497** | **182,059** | **0** |

Core and FPL agree on every package and version (`latest` for the automatic dependencies, `x.y.x` patch wildcards
in ch-ekm, exact versions), on the resource count of every package (so core's `.index.json` list equals FPL's
`package/*.json` scan on these packages), and on the winner and order of every lookup that goes to a package.

### Corpus sample (30 IGs)

Chosen for their dependency versions: `dev` (tewhatuora actnow, HL7Chile IPS-CL, hl7-eu imaging-api, HL7 ebm,
Minsal-CL tei), `current` (hl7-be workflow, unscripted-rx us-pharmacy, TEHIK-EE ig-ee-spd, WHO smart-ccc, HL7
imaging-service-request, HL7 davinci-pdex-formulary, ...), `x.y.x` (euridice eu-health-data-api, hl7ch ch-ems, IHE
ITI.DIS, hl7ch ch-vacd, hl7ch ch-emr), `latest` (3 ansforge IGs, HL7 ebm-incubator) and 10 random ones.

- Packages loaded (names, versions, order, status): identical in **30/30**.
- Lookups: **14,681 unique / 232,393 calls, all identical** (after rule R1 below; before it, hl7-eu imaging-api had 5
  differing lookups).
- Loading log lines: identical in 25/30. The 5 others differ only by D7/D8 (below): TS printed
  `Cached package X#current is out of date ...` + `Attempting to download ...` + `Downloaded` + `Cached ... to` when
  it refreshed a stale `current` build (hl7.fhir.uv.cow, hl7.fhir.us.davinci-pdex-plan-net), and two
  `Attempting to download smart.who.int.trust-phw#0.2.0-litlfred from ...` lines before a failed download
  (smart-trust); core refreshes/attempts silently. (One more mismatch, smart-ccc, was the compare script matching an
  unrelated `Found both a "menu" property ...` line.)

**Totals (6 reference + 30 corpus IGs): 20,178 unique lookups / 414,452 calls compared, 20,178 identical, 0
different; 1 FPL rule ported (R1); 2 message-only differences observed (D7, D8).**

## Differences

### X1 — predefined resources in FHIR XML (not a core-vs-FPL difference; was a Java gap, fixed)

- IGs: ch-term (93 XML files), smart-anc (32), 27 corpus IGs in total.
- Lookups: e.g. `fishForPredefinedResource('urn:oid:0.4.0.127.0.16.1.1.2.1', CodeSystem)` in ch-term,
  `'ANCActivityCodes'` (CodeSystem) in smart-anc, and `allPredefinedResources()`.
- TS: FPL's DiskBasedVirtualPackage converts XML with fhir-tool's `xmlToObj` and registers it in `sushi-local#LOCAL`
  (e.g. `CodeSystem/edqm-standardterms` from `edqm-standardterms.xml`). Java: no XML converter yet, so each file logs
  `Failed to register resource at path ...` and is missing (`sushi-local#LOCAL with 1 resources` instead of 94).
- Changes SUSHI output: yes (errors in the log, IG resource entries, lookups of predefined code systems).
- Action: ported fhir-tool 5.0.2's xmlToObj (with xml-js 1.6.11 / sax 1.2.4 semantics, fhir-tool's own R4 type
  data) in `fhirdefs.xml`. Differential vs the JS library (`scripts/fhirtool/diff.sh`): all 798 XML files in the
  corpus IGs' predefined-resource folders + 13,331 mutations, 0 differences (JSON text, error messages, stdout).
  First probe run (before the port): ch-term 2 and smart-anc 5 of their lookups differed; after: 0.

### R1 — `dev` not in the cache, only `current` (core differs; FPL rule ported)

- IG: hl7-eu imaging-api (`ihe.rad.mado: dev`; the cache has `ihe.rad.mado#current`, not `#dev`).
- Lookups: every lookup that hits the package, e.g.
  `https://profiles.ihe.net/RAD/MADO/StructureDefinition/ext-R5-DocumentReference.bodySite` (Extension), and
  `findResourceInfos('*', {type: ImplementationGuide})`.
- TS: FPL sees no `#dev` folder, logs `Falling back to ihe.rad.mado#current since ihe.rad.mado#dev is not locally
  cached. To avoid this, ...`, loads the current build and registers it as `ihe.rad.mado#current`.
- core: `loadPackageFromCacheOnly(id, "dev")` returns the cached `current` build, so the package was registered as
  `ihe.rad.mado#dev` and the fallback message was not printed. Same resources, same winners, different package label.
- Changes SUSHI output: yes — the console log (missing line, `Loaded ihe.rad.mado#dev` instead of `#current`), and
  IGExporter's `findPackageJSON(id, 'dev')` (used for a dependency's canonical when the package has no IG resource)
  would find the package in Java but not in TS.
- Action: ported FPL's rule — `CorePackageProvider.isCached(id, "dev")` is true only when the `id#dev` folder exists
  (`CorePackageProviderTest`); everything else stays core's (the current build is then loaded with core's
  `loadPackage(id, "current")`).

### R2 — `latest` resolves to a prerelease (core differs; FPL rule ported)

- IG: HL7 cds-hooks-library (`hl7.fhir.uv.cds-hooks: latest`), from the corpus differential (WP-10).
- TS: FPL's `lookUpLatestVersion` takes the registry's `dist-tags.latest` (`2.0.1`), trying packages.fhir.org, then
  packages2.fhir.org (or only `$FPL_REGISTRY`).
- core: `getLatestVersion` picks the highest listed version, `3.0.0-ballot`.
- Changes SUSHI output: yes — `ImplementationGuide.dependsOn[].version`, and the `Resolved ... / Loaded ... with N
  resources` lines.
- Action: `CorePackageProvider.resolveLatest` ports FPL's lookup (HTTP via core's ManagedWebAccess); when every
  registry fails, loading `X#latest` fails with FPL's `Failed to resolve version for X#latest` (corpus case
  HL7__davinci-ecdx). Tests: `regress.CorpusRegressionTest` (network test opt-in: `-Dsushi.network.tests=true`).

### X2 — `hl7.fhir.r4.core#4.0.1` downloaded into an empty cache (core differs; observed; not changed)

- Found by the empty-cache check of the I/O-policy work (no `~/.fhir` at all, network on, IHE ITI.MHD, TS and Java
  run back to back): `fsh-generated/` identical (125 files), exit codes 0/0, and one log line differs:
  `Loaded hl7.fhir.r4.core#4.0.1 with 4581 resources` (TS) vs `... with 4578 resources` (Java). The jar from before
  that work gives the same result. Every other package of the run has the same count in both.
- Cause: the two package servers serve **different tarballs** for the same `hl7.fhir.r4.core#4.0.1`, and the
  implementations ask them in opposite order (D8):
  - FPL (TS) asks packages.fhir.org first: the original 2019 tarball (12.8 MB, SHA-256 `ebd7731df7d3…`, 5,046
    entries);
  - core asks its `PackageServer.PRIMARY_SERVER`, packages2.fhir.org, first: a later re-packaging (4.5 MB, SHA-256
    `b090bf929e1f…`, 4,742 entries; the same file as `https://hl7.org/fhir/R4/hl7.fhir.r4.core.tgz`).
  - The re-packaged tarball lacks 3 resources, `StructureDefinition-structuredefinition-json-type.json`,
    `-xml-type.json` and `-rdf-type.json` (the draft R4 extensions
    `http://hl7.org/fhir/StructureDefinition/structuredefinition-{json,xml,rdf}-type`, context
    `ElementDefinition.type.code`), and the non-resource folders `package/openapi` (156 files) and `package/other`
    (307 files, mostly PNGs). It is not an extraction or counting difference: core indexes every resource of the
    tarball it downloaded, FPL every resource of its own.
  - The port prints FPL's `Attempting to download hl7.fhir.r4.core#4.0.1 from https://packages.fhir.org/...` (D8)
    even though core fetched the package from packages2.fhir.org.
- Effect on output: the log line on a cold cache. `fsh-generated/` changes only for an IG that uses one of the 3
  extensions (by name, id or URL): TS finds it, Java does not and reports the unresolved reference. None of the 267
  corpus FSH IGs does (searched their FSH, `sushi-config.yaml`, and JSON/XML inputs), and SUSHI's own code doesn't
  refer to them. A cache that the IG Publisher (core) filled has the packages2 tarball, so TS SUSHI sees 4578 resources
  there too. The frozen snapshot that the differential runs use holds FPL's tarball, so both report 4581.
- Possible fix (not applied: it changes which tarball goes into the user's cache, which needs an owner decision under
  D1a): give `CorePackageProvider` FPL's server order. Core 7.0.6 has no builder setter for it, but
  `FilesystemPackageCacheManager.Builder.getDefaultServers()` is protected and overridable, so
  `new Builder() { protected List<PackageServer> getDefaultServers() { return List.of(PackageServer.secondaryServer(),
  PackageServer.primaryServer()); } }.withCacheFolder(...)` would ask packages.fhir.org first, as FPL does. Servers
  configured in fhir-settings.json would still come first. That would also make the `Attempting to download ... from
  packages.fhir.org` line true.

## Known design-level differences (core as is; kept)

These follow from using core as is; D7 and D8 occurred (log lines only, no lookup differs), the others did not. Each is listed so a probe hit can be
classified quickly.

| # | situation | FPL (TS) | core (Java) | effect |
|---|---|---|---|---|
| D1 | `latest` when the registry's `dist-tags.latest` is not the highest version (e.g. a newer ballot/pre-release exists) | `dist-tags.latest` | highest version listed (`getLatestVersion(id, false)` includes pre-releases) | a different package version loads; all lookups into it differ |
| D2 | `x.y.x` when the cache has an older patch than the registry | newest patch on the registry | newest matching patch **in the cache** first, registry only when none is cached | older patch loads |
| D3 | `latest`/`x.y.x` offline | `Latest version of package X could not be determined from the package registry` (error) | newest cached version | Java loads where TS fails |
| D4 | `x.x` (minor wildcard, e.g. `3.x`) | `Incorrect version format for package X: 3.x. Wildcard should only be used to specify patch versions.` | core resolves it like a wildcard | Java loads where TS errors |
| D5 | `x.y` (two-part version) not in the cache | download `x.y` from the registry (normally fails) | newest cached `x.y.*` | Java loads where TS fails |
| D7 (observed; the "Failed to download most recent X#current ... Using most recent cached package instead." warning for builds missing from qas.json IS reproduced since main@integrator, corpus case HL7-cz__ps: hl7-be workflow, HL7 imaging-service-request, HL7 davinci-pdex-formulary) | `current` freshness | FPL compares package.json `date` with build.fhir.org's manifest and logs `Cached package X#current is out of date ...` | core's own check (`CIBuildClient.isCurrent`); no such log line | log line missing in Java when a stale current build is refreshed |
| D8 (observed: WHO smart-trust; the `Attempting to download ...` lines are now reproduced in FPL's wording -- one line on success, both registries on failure; corpus smart-trust identical) | cold cache downloads | `Attempting to download X#V from <url>` (packages.fhir.org first) | core's server order (packages2.fhir.org first); the `Attempting ...` line is not printed; `Downloaded X#V` / `Cached X#V to <dir>` are | one log line per download |
| D9 | package files listed in a package | every `package/*.json` file whose content has a string `resourceType` | core's `.index.json` (rebuilt by core when missing/outdated) | resource counts / lookups would differ if a package's index omits a file; not seen on 6 reference IGs |
