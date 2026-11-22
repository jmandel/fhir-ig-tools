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
| hl7ch ch-term | yes | differ only in predefined XML files (below) | 95 | 93 | 65,734 | 2 (X1) |
| WHO smart-anc | yes | differ only in predefined XML files (below) | 2,349 | 2,344 | 44,983 | 5 (X1) |
| **total** | **6/6** | | **5,497** | **5,490** | **182,059** | **7** |

Core and FPL agree on every package and version (`latest` for the automatic dependencies, `x.y.x` patch wildcards
in ch-ekm, exact versions), on the resource count of every package (so core's `.index.json` list equals FPL's
`package/*.json` scan on these packages), and on the winner and order of every lookup that goes to a package.

### Corpus sample

(pending: 30 IGs chosen for `dev` (5), `current` (6), `x.y.x` (5) and `latest` (4) dependencies, plus 10 random.)

## Differences

### X1 — predefined resources in FHIR XML (not a core-vs-FPL difference; Java gap)

- IGs: ch-term (93 XML files), smart-anc (32), 27 corpus IGs in total.
- Lookups: e.g. `fishForPredefinedResource('urn:oid:0.4.0.127.0.16.1.1.2.1', CodeSystem)` in ch-term,
  `'ANCActivityCodes'` (CodeSystem) in smart-anc, and `allPredefinedResources()`.
- TS: FPL's DiskBasedVirtualPackage converts XML with fhir-tool's `xmlToObj` and registers it in `sushi-local#LOCAL`
  (e.g. `CodeSystem/edqm-standardterms` from `edqm-standardterms.xml`). Java: no XML converter yet, so each file logs
  `Failed to register resource at path ...` and is missing (`sushi-local#LOCAL with 1 resources` instead of 94).
- Changes SUSHI output: yes (errors in the log, IG resource entries, lookups of predefined code systems).
- Action: port fhir-tool 5.0.2's xmlToObj (with xml-js/sax 1.2.4 semantics) in `fhirdefs.xml` — in progress.

## Known design-level differences not observed so far

These follow from using core as is; none occurred in the measured IGs. Each is listed so a probe hit can be
classified quickly.

| # | situation | FPL (TS) | core (Java) | effect |
|---|---|---|---|---|
| D1 | `latest` when the registry's `dist-tags.latest` is not the highest version (e.g. a newer ballot/pre-release exists) | `dist-tags.latest` | highest version listed (`getLatestVersion(id, false)` includes pre-releases) | a different package version loads; all lookups into it differ |
| D2 | `x.y.x` when the cache has an older patch than the registry | newest patch on the registry | newest matching patch **in the cache** first, registry only when none is cached | older patch loads |
| D3 | `latest`/`x.y.x` offline | `Latest version of package X could not be determined from the package registry` (error) | newest cached version | Java loads where TS fails |
| D4 | `x.x` (minor wildcard, e.g. `3.x`) | `Incorrect version format for package X: 3.x. Wildcard should only be used to specify patch versions.` | core resolves it like a wildcard | Java loads where TS errors |
| D5 | `x.y` (two-part version) not in the cache | download `x.y` from the registry (normally fails) | newest cached `x.y.*` | Java loads where TS fails |
| D6 | `dev` not cached | message `Falling back to X#current ...` + current build | core falls back to `current` (message kept: logged by our loader) | none expected |
| D7 | `current` freshness | FPL compares package.json `date` with build.fhir.org's manifest and logs `Cached package X#current is out of date ...` | core's own check (`CIBuildClient.isCurrent`); no such log line | log line missing in Java when a stale current build is refreshed |
| D8 | cold cache downloads | `Attempting to download X#V from <url>` (packages.fhir.org first) | core's server order (packages2.fhir.org first); the `Attempting ...` line is not printed; `Downloaded X#V` / `Cached X#V to <dir>` are | one log line per download |
| D9 | package files listed in a package | every `package/*.json` file whose content has a string `resourceType` | core's `.index.json` (rebuilt by core when missing/outdated) | resource counts / lookups would differ if a package's index omits a file; not seen on 6 reference IGs |
